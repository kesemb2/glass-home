package io.github.kesemb2.glasshome.ui.debug

import android.app.Activity
import android.content.ContextWrapper
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.KeyEvent
import android.view.Window
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.kesemb2.glasshome.ui.motion.FocusTravel
import io.github.kesemb2.glasshome.ui.theme.LocalGlassType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Rendered frames over the last second (from the window's frame metrics, so an idle screen honestly shows ~0),
 * slow frames, the 95th-percentile frame time, the process's memory (PSS) and the last remote key (to tell what
 * a remote's button sends). Only runs while the setting is on; its own text updates once a second.
 */
@Composable
fun PerfOverlay(modifier: Modifier = Modifier) {
    val view = LocalView.current
    val type = LocalGlassType.current
    val stats = remember { FrameStats() }
    var text by remember { mutableStateOf("") }

    DisposableEffect(view) {
        val window = view.findWindow() ?: return@DisposableEffect onDispose {}
        val thread = HandlerThread("perf-overlay").apply { start() }
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            stats.add(
                metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP),
                metrics.getMetric(FrameMetrics.TOTAL_DURATION),
            )
        }
        window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper))
        onDispose {
            window.removeOnFrameMetricsAvailableListener(listener)
            thread.quitSafely()
        }
    }

    LaunchedEffect(Unit) {
        var memory = ""
        var tick = 0
        while (true) {
            if (tick % 2 == 0) memory = withContext(Dispatchers.IO) { memoryLine() }
            tick++
            val frames = stats.summary(System.nanoTime())
            text = String.format(
                Locale.US,
                "%d fps · %d slow · p95 %.1f ms\n%s\nkey %s",
                frames.fps, frames.slow, frames.p95Millis, memory,
                KeyEvent.keyCodeToString(FocusTravel.lastKeyCode).removePrefix("KEYCODE_"),
            )
            delay(1_000)
        }
    }

    // Numbers and English labels: always left to right, also in Hebrew.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier
                .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            BasicText(
                text,
                style = type.caption.copy(
                    color = Color.White,
                    fontFeatureSettings = "tnum",
                    textDirection = TextDirection.Ltr,
                ),
            )
        }
    }
}

private fun memoryLine(): String {
    val info = Debug.MemoryInfo()
    Debug.getMemoryInfo(info)
    fun mb(key: String) = (info.getMemoryStat(key)?.toIntOrNull() ?: 0) / 1024
    return "PSS ${info.totalPss / 1024} MB · java ${mb("summary.java-heap")} · native ${mb("summary.native-heap")}" +
        " · graphics ${mb("summary.graphics")}"
}

private fun android.view.View.findWindow(): Window? {
    var c = context
    while (c is ContextWrapper) {
        if (c is Activity) return c.window
        c = c.baseContext
    }
    return null
}

/** The last frames' start times and durations (ns), written from the metrics thread, read once a second. */
class FrameStats(private val capacity: Int = 256) {
    private val starts = LongArray(capacity)
    private val durations = LongArray(capacity)
    private var count = 0
    private var next = 0

    data class Summary(val fps: Int, val slow: Int, val p95Millis: Double)

    @Synchronized
    fun add(startNanos: Long, durationNanos: Long) {
        starts[next] = startNanos
        durations[next] = durationNanos
        next = (next + 1) % capacity
        if (count < capacity) count++
    }

    /** Frames that started within the second before [nowNanos]. */
    @Synchronized
    fun summary(nowNanos: Long): Summary {
        val recent = ArrayList<Long>()
        for (i in 0 until count) {
            if (nowNanos - starts[i] in 0..WINDOW_NANOS) recent.add(durations[i])
        }
        if (recent.isEmpty()) return Summary(0, 0, 0.0)
        recent.sort()
        val p95 = recent[((recent.size - 1) * 0.95).toInt()]
        return Summary(recent.size, recent.count { it > SLOW_NANOS }, p95 / 1_000_000.0)
    }

    private companion object {
        const val WINDOW_NANOS = 1_000_000_000L
        const val SLOW_NANOS = 16_700_000L
    }
}
