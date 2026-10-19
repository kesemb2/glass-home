package io.github.kesemb2.glasshome.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Back on the home screen. The activity takes the Back key before anything else sees it and hands it here:
 * Compose would spend it moving focus out of a scrolling list (the settings), and the system's back dispatching
 * did not reliably reach the screen's own handlers. The newest enabled handler gets it, so a menu shown inside a
 * screen goes before the screen itself.
 */
class HomeBack {
    internal class Handler {
        var enabled = true
        var onBack: () -> Unit = {}
    }

    private val handlers = ArrayList<Handler>()

    /** False when nothing on screen takes Back. */
    fun back(): Boolean {
        val handler = handlers.lastOrNull { it.enabled } ?: return false
        handler.onBack()
        return true
    }

    internal fun add(handler: Handler) {
        handlers.add(handler)
    }

    internal fun remove(handler: Handler) {
        handlers.remove(handler)
    }
}

val LocalHomeBack = staticCompositionLocalOf<HomeBack?> { null }

/** Runs [onBack] on Back while [enabled]; handlers composed later (inner ones) go first. */
@Composable
fun HomeBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val back = LocalHomeBack.current
    val handler = remember { HomeBack.Handler() }
    SideEffect {
        handler.enabled = enabled
        handler.onBack = onBack
    }
    DisposableEffect(back, handler) {
        back?.add(handler)
        onDispose { back?.remove(handler) }
    }
}
