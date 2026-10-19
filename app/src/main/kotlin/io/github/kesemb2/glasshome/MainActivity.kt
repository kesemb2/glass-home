package io.github.kesemb2.glasshome

import android.content.ActivityNotFoundException
import android.content.ComponentCallbacks2
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import io.github.kesemb2.glasshome.data.watchnext.WatchNextAccess
import io.github.kesemb2.glasshome.ui.home.HomeController
import io.github.kesemb2.glasshome.ui.home.HomeScreen
import io.github.kesemb2.glasshome.ui.motion.FocusTravel
import io.github.kesemb2.glasshome.ui.theme.GlassTypography
import io.github.kesemb2.glasshome.ui.theme.LocalGlassType
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

class MainActivity : ComponentActivity() {

    private val scope = MainScope()
    private lateinit var controller: HomeController

    private val visible = mutableStateOf(false)
    private val resumed = mutableStateOf(false)
    private val lastKeyAt = mutableLongStateOf(0L)
    private var askedThisSession = false

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { controller.refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = HomeController(application as GlassHomeApp, scope)
        setContent {
            val typography = remember { GlassTypography.create(this) }
            CompositionLocalProvider(LocalGlassType provides typography) {
                HomeScreen(
                    controller = controller,
                    visible = visible.value,
                    resumed = resumed.value,
                    lastKeyAt = { lastKeyAt.longValue },
                    onPlay = { controller.play(this, it) },
                    onRequestAccess = { requestWatchNextAccess(fromButton = true) },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        visible.value = true
        controller.onStart()
        if (!askedThisSession) requestWatchNextAccess(fromButton = false)
    }

    override fun onResume() {
        super.onResume()
        resumed.value = true
        controller.refresh()
    }

    override fun onPause() {
        resumed.value = false
        super.onPause()
    }

    override fun onStop() {
        visible.value = false
        controller.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // The home screen is hidden: drop decoded images; they come back from the disk cache.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            (application as GlassHomeApp).imageLoader.memoryCache?.clear()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            FocusTravel.record(event.keyCode)
            lastKeyAt.longValue = SystemClock.uptimeMillis()
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * Asks for the runtime TV-listings permission. After the user has denied it for good, the system no longer
     * shows a dialog, so the "Allow" button opens this app's settings page instead.
     */
    private fun requestWatchNextAccess(fromButton: Boolean) {
        val requestable = WatchNextAccess.requestable(this)
        if (requestable.isEmpty()) return
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        val askedBefore = prefs.getBoolean(KEY_ASKED, false)
        val deniedForGood = askedBefore && requestable.none { shouldShowRequestPermissionRationale(it) }
        if (deniedForGood) {
            if (fromButton) openAppSettings()
            return
        }
        askedThisSession = true
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        permissionRequest.launch(requestable)
    }

    private fun openAppSettings() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } catch (_: ActivityNotFoundException) {
            // Some TV builds strip the app-details screen.
        }
    }

    private companion object {
        const val KEY_ASKED = "asked_tv_listings"
    }
}
