package io.github.kesemb2.glasshome.ui.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.tv.TvContract
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.kesemb2.glasshome.GlassHomeApp
import io.github.kesemb2.glasshome.data.hero.HeroItem
import io.github.kesemb2.glasshome.data.hero.HeroResolver
import io.github.kesemb2.glasshome.data.watchnext.PermissionStatus
import io.github.kesemb2.glasshome.data.watchnext.WatchNextAccess
import io.github.kesemb2.glasshome.data.watchnext.WatchNextRepository
import io.github.kesemb2.glasshome.data.watchnext.WatchNextRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

enum class AccessState { Unknown, Granted, Requestable, Blocked }

@Immutable
data class HeroUiState(
    val loaded: Boolean = false,
    val items: List<HeroItem> = emptyList(),
    val access: AccessState = AccessState.Unknown,
    val permissions: List<PermissionStatus> = emptyList(),
    val providerRows: Int = 0,
    val error: String? = null,
)

/**
 * Owns the Continue Watching data while the launcher is visible: reads Watch Next on start/resume and on
 * provider changes (ContentObserver, debounced), resolves items, and launches playback. Does no work while
 * stopped: the observer is unregistered and pending refreshes are cancelled.
 */
@Stable
class HomeController(private val app: GlassHomeApp, private val scope: CoroutineScope) {

    var hero by mutableStateOf(HeroUiState())
        private set

    private val repository = WatchNextRepository(app)
    private val resolver = HeroResolver(app, app.tmdb)
    private val enriched = ConcurrentHashMap<String, HeroItem>()
    private var refreshJob: Job? = null
    private var debounceJob: Job? = null
    private var observer: ContentObserver? = null

    fun onStart() {
        if (observer != null) return
        val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = scheduleRefresh()
        }
        try {
            app.contentResolver.registerContentObserver(TvContract.WatchNextPrograms.CONTENT_URI, true, contentObserver)
            observer = contentObserver
        } catch (_: SecurityException) {
            // No access to the provider: refreshes on resume still pick up permission changes.
        }
    }

    fun onStop() {
        observer?.let { app.contentResolver.unregisterContentObserver(it) }
        observer = null
        debounceJob?.cancel()
        refreshJob?.cancel()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val permissions = WatchNextAccess.status(app)
            val result = withContext(Dispatchers.IO) { runCatching { repository.queryAll() } }
            val entries = result.getOrDefault(emptyList())
            val selected = WatchNextRules.select(entries, app.packageName, repository.dismissedKeys())
            val items = withContext(Dispatchers.IO) {
                selected.map { enriched[it.dismissKey] ?: resolver.base(it) }
            }
            hero = HeroUiState(
                loaded = true,
                items = items,
                access = accessState(permissions),
                permissions = permissions,
                providerRows = entries.size,
                error = result.exceptionOrNull()?.javaClass?.simpleName,
            )
            for (item in items) {
                if (item.enriched) continue
                val full = resolver.enrich(item)
                enriched[item.entry.dismissKey] = full
                hero = hero.copy(items = hero.items.map { if (it.key == full.key) full else it })
            }
        }
    }

    /** "Remove from row": hidden locally, the provider is left untouched. */
    fun dismiss(item: HeroItem) {
        repository.dismiss(item.entry)
        hero = hero.copy(items = hero.items.filter { it.key != item.key })
        refresh()
    }

    /** Opens the entry's own intent; falls back to launching its app. */
    fun play(context: Context, item: HeroItem) {
        val deepLink = item.entry.intentUri?.let { uri ->
            try {
                Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
            } catch (_: Exception) {
                null
            }
        }?.apply {
            // The intent comes from another app's provider row: never let it carry a selector or URI grants.
            selector = null
            flags = flags and URI_GRANT_FLAGS.inv()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (deepLink != null && start(context, deepLink)) return
        val pm = context.packageManager
        val packageName = item.entry.packageName
        val launch = pm.getLeanbackLaunchIntentForPackage(packageName) ?: pm.getLaunchIntentForPackage(packageName)
        launch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { start(context, it) }
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun scheduleRefresh() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(OBSERVER_DEBOUNCE_MS)
            refresh()
        }
    }

    private fun accessState(permissions: List<PermissionStatus>): AccessState = when {
        permissions.any { it.granted } -> AccessState.Granted
        permissions.any { it.protection == "runtime" } -> AccessState.Requestable
        else -> AccessState.Blocked
    }

    private companion object {
        const val OBSERVER_DEBOUNCE_MS = 500L
        const val URI_GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
    }
}
