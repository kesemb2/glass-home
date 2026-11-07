package io.github.kesemb2.glasshome.ui.glass

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Automatic glass quality (Settings › Glass › Automatic quality): watches the window's frame times and, when the
 * TV falls behind (a third of the last 30 frames over 20 ms), switches live glass to the cached kind until the
 * screen has been still for a moment, then back. Costs nothing while nothing is drawn: frame metrics only arrive
 * for frames that are rendered.
 */
object GlassQuality {
    /** True while live glass should give way to cached glass. */
    var reduced by mutableStateOf(false)
        private set

    private const val WINDOW = 30
    private const val SLOW_LIMIT = 10
    private const val SLOW_NANOS = 20_000_000L

    /** How long the screen must be still before live glass comes back. */
    private const val SETTLE_MS = 1_500L

    private val slow = BooleanArray(WINDOW)
    private var next = 0
    private var slowCount = 0

    @Volatile
    private var reducedNow = false

    @Volatile
    private var resetCounts = false

    private var attached = false

    fun attach(window: Window) {
        if (attached) return
        attached = true
        val main = Handler(Looper.getMainLooper())
        val restore = Runnable {
            reducedNow = false
            resetCounts = true
            reduced = false
        }
        val thread = HandlerThread("glass-quality").apply { start() }
        window.addOnFrameMetricsAvailableListener(
            { _, metrics, _ ->
                if (resetCounts) {
                    slow.fill(false)
                    slowCount = 0
                    resetCounts = false
                }
                val isSlow = metrics.getMetric(FrameMetrics.TOTAL_DURATION) > SLOW_NANOS
                if (slow[next]) slowCount--
                slow[next] = isSlow
                if (isSlow) slowCount++
                next = (next + 1) % WINDOW
                if (!reducedNow && slowCount >= SLOW_LIMIT) {
                    reducedNow = true
                    main.post { reduced = true }
                }
                if (reducedNow) {
                    main.removeCallbacks(restore)
                    main.postDelayed(restore, SETTLE_MS)
                }
            },
            Handler(thread.looper),
        )
    }
}
