package io.github.kesemb2.glasshome.system

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import io.github.kesemb2.glasshome.MainActivity

/**
 * The Home helper: an accessibility service, because on many Android TV builds this is the only way for another
 * launcher to own the Home button. It reads no screen content: it sees only the Home key and which activity comes
 * to the front, and it opens Glass Home when the Home button is pressed (Settings › Home button) or the TV's own
 * home screen appears, including right after startup (Settings › Replace the TV's home screen).
 *
 * An app with a bound accessibility service may start activities from the background, which also covers starting
 * at boot.
 */
class HomeButtonService : AccessibilityService() {
    private var otherHomes: Set<ComponentName> = emptySet()
    private var lastLaunch = 0L

    override fun onServiceConnected() {
        otherHomes = HomeSystem.otherHomes(this)
        // Just after boot the TV shows its own home screen, possibly before this service was connected.
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS && HomeSystem.replaceHomeEnabled(this)) launchHome()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME || !HomeSystem.homeButtonEnabled(this)) return false
        if (event.action == KeyEvent.ACTION_UP) {
            HomeSystem.clearBypass(this)
            launchHome()
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return
        if (otherHomes.none { it.packageName == pkg && it.className == cls }) return
        if (!HomeSystem.replaceHomeEnabled(this) || HomeSystem.bypassed(this)) return
        launchHome()
    }

    override fun onInterrupt() = Unit

    private fun launchHome() {
        val now = SystemClock.uptimeMillis()
        if (now - lastLaunch < DEBOUNCE_MS) return
        lastLaunch = now
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_HOME, true),
            )
        } catch (_: Exception) {
            // The system refused (rare); the Home button then does what the TV does.
        }
    }

    private companion object {
        const val BOOT_WINDOW_MS = 3 * 60 * 1000L
        const val DEBOUNCE_MS = 600L
    }
}
