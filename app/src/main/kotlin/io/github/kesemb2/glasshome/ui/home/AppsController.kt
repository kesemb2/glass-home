package io.github.kesemb2.glasshome.ui.home

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.kesemb2.glasshome.GlassHomeApp
import io.github.kesemb2.glasshome.data.apps.AppEntry
import io.github.kesemb2.glasshome.data.apps.AppRepository
import io.github.kesemb2.glasshome.data.apps.GridItem
import io.github.kesemb2.glasshome.data.apps.HomeLayout
import io.github.kesemb2.glasshome.data.apps.HomeView
import io.github.kesemb2.glasshome.data.settings.AppSettings
import io.github.kesemb2.glasshome.image.AppVisuals
import io.github.kesemb2.glasshome.ui.apps.CardSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Where an app or folder is being moved. */
sealed interface MoveArea {
    data object Favorites : MoveArea
    data object Grid : MoveArea
    data class Folder(val id: String) : MoveArea
}

/**
 * Owns the apps, their arrangement and their card images while the launcher is visible. Listens for installs,
 * updates and removals (LauncherApps callback) only while started; the arrangement is saved on every edit.
 */
@Stable
class AppsController(
    private val app: GlassHomeApp,
    private val scope: CoroutineScope,
    val settings: AppSettings,
) {
    var apps by mutableStateOf<Map<String, AppEntry>>(emptyMap())
        private set
    var layout by mutableStateOf(HomeLayout())
        private set
    var loaded by mutableStateOf(false)
        private set

    val visuals = AppVisuals(app)

    private val repository = AppRepository(app)
    private val prefs = app.getSharedPreferences("layout", Context.MODE_PRIVATE)
    private val launcherApps = app.getSystemService(LauncherApps::class.java)
    private var callback: LauncherApps.Callback? = null
    private var refreshJob: Job? = null
    private var debounceJob: Job? = null
    private var visualsJob: Job? = null

    init {
        layout = HomeLayout.fromJson(prefs.getString(KEY_LAYOUT, null))
    }

    /** The current screen contents for the current "show phone apps" setting. */
    fun view(): HomeView {
        val phone = settings.showPhoneApps
        return HomeView.of(layout, apps) { it.tv || phone }
    }

    fun onStart() {
        if (callback == null && launcherApps != null) {
            val cb = object : LauncherApps.Callback() {
                override fun onPackageRemoved(packageName: String?, user: UserHandle?) = scheduleRefresh()
                override fun onPackageAdded(packageName: String?, user: UserHandle?) = scheduleRefresh()
                override fun onPackageChanged(packageName: String?, user: UserHandle?) = scheduleRefresh()
                override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) =
                    scheduleRefresh()
                override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) =
                    scheduleRefresh()
            }
            launcherApps.registerCallback(cb, Handler(Looper.getMainLooper()))
            callback = cb
        }
        refresh()
    }

    fun onStop() {
        callback?.let { launcherApps?.unregisterCallback(it) }
        callback = null
        debounceJob?.cancel()
        refreshJob?.cancel()
        visualsJob?.cancel()
        visuals.clear()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val list = withContext(Dispatchers.IO) { runCatching { repository.query() }.getOrNull() } ?: return@launch
            apps = list.associateByTo(LinkedHashMap()) { it.packageName }
            update(layout.reconcile(list.map { it.packageName }))
            loaded = true
            loadVisuals()
        }
    }

    /** Loads card images for what is on screen: the favorites first, then the grid, then folder contents. */
    fun loadVisuals() {
        visualsJob?.cancel()
        visualsJob = scope.launch {
            val view = view()
            val order = LinkedHashSet<AppEntry>()
            order.addAll(view.favorites)
            view.grid.forEach { if (it is GridItem.App) order.add(it.app) }
            view.grid.forEach { if (it is GridItem.Folder) order.addAll(it.apps) }
            val density = app.resources.displayMetrics.density
            val size = CardSize(settings.appColumns)
            visuals.load(order.toList(), (size.width.value * density).roundToInt(), (size.height.value * density).roundToInt())
        }
    }

    fun launch(entry: AppEntry): Boolean = repository.launch(entry)

    fun openAppInfo(entry: AppEntry): Boolean = repository.openAppInfo(entry.packageName)

    fun toggleFavorite(pkg: String) = update(layout.toggleFavorite(pkg))

    fun hide(pkg: String) = update(layout.hide(pkg))

    fun unhide(pkg: String) {
        update(layout.unhide(pkg))
        loadVisuals()
    }

    /** Creates a folder holding [pkg] and returns its id. [name] gets the folder's number. */
    fun newFolder(pkg: String, name: (number: Int) -> String): String {
        val (next, id) = layout.newFolder(pkg, name(layout.nextFolder))
        update(next)
        return id
    }

    fun addToFolder(pkg: String, folderId: String) = update(layout.addToFolder(pkg, folderId))

    fun removeFromFolder(pkg: String) = update(layout.removeFromFolder(pkg))

    fun removeFolder(folderId: String) = update(layout.removeFolder(folderId))

    fun setShowPhoneApps(show: Boolean) {
        settings.showPhoneApps = show
        loadVisuals()
    }

    /** Cards are rendered at the size they are shown at, so a new size renders them again. */
    fun setAppColumns(columns: Int) {
        settings.appColumns = columns
        loadVisuals()
    }

    /** Moves an entry by [delta] places among what is shown in [area]. */
    fun move(area: MoveArea, key: String, delta: Int) {
        val phone = settings.showPhoneApps
        fun shownApp(pkg: String) = apps[pkg]?.let { it.tv || phone } == true
        val view = view()
        val shownKeys = view.grid.mapTo(HashSet()) { it.key }
        update(
            when (area) {
                MoveArea.Grid -> layout.moveInGrid(key, delta) { it in shownKeys }
                MoveArea.Favorites -> layout.moveInFavorites(key, delta, ::shownApp)
                is MoveArea.Folder -> layout.moveInFolder(area.id, key, delta, ::shownApp)
            },
        )
    }

    private fun update(next: HomeLayout) {
        if (next == layout) return
        layout = next
        prefs.edit().putString(KEY_LAYOUT, next.toJson()).apply()
    }

    private fun scheduleRefresh() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(CHANGE_DEBOUNCE_MS)
            refresh()
        }
    }

    private companion object {
        const val KEY_LAYOUT = "layout"
        const val CHANGE_DEBOUNCE_MS = 300L
    }
}
