package io.github.kesemb2.glasshome.diagnostics

import android.app.Activity
import android.app.ActivityManager
import android.graphics.Point
import android.os.Build
import android.os.Debug
import android.view.Display
import java.util.Locale

/** A one-shot snapshot of the facts the later stages are sized against. */
data class DeviceDiagnostics(
    val uiWidthPx: Int,
    val uiHeightPx: Int,
    val density: Float,
    val densityDpi: Int,
    val modeWidthPx: Int,
    val modeHeightPx: Int,
    val refreshRateHz: Float,
    val androidRelease: String,
    val sdkInt: Int,
    val model: String,
    val totalRamBytes: Long,
    val memoryClassMb: Int,
    val isLowRamDevice: Boolean,
    val appPssKb: Long,
) {
    companion object {
        fun read(activity: Activity): DeviceDiagnostics {
            val windowManager = activity.windowManager
            val (uiWidth, uiHeight) = if (Build.VERSION.SDK_INT >= 30) {
                val bounds = windowManager.currentWindowMetrics.bounds
                bounds.width() to bounds.height()
            } else {
                val size = Point()
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealSize(size)
                size.x to size.y
            }
            val display: Display? = if (Build.VERSION.SDK_INT >= 30) {
                activity.display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            }
            val mode = display?.mode
            val metrics = activity.resources.displayMetrics

            val activityManager = activity.getSystemService(ActivityManager::class.java)
            val memoryInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }

            val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.ROOT) }
            val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL.takeIf { it != Build.UNKNOWN } else null

            return DeviceDiagnostics(
                uiWidthPx = uiWidth,
                uiHeightPx = uiHeight,
                density = metrics.density,
                densityDpi = metrics.densityDpi,
                modeWidthPx = mode?.physicalWidth ?: uiWidth,
                modeHeightPx = mode?.physicalHeight ?: uiHeight,
                refreshRateHz = mode?.refreshRate ?: display?.refreshRate ?: 0f,
                androidRelease = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT,
                model = listOfNotNull("$manufacturer ${Build.MODEL}", soc).joinToString(" · "),
                totalRamBytes = memoryInfo.totalMem,
                memoryClassMb = activityManager.memoryClass,
                isLowRamDevice = activityManager.isLowRamDevice,
                appPssKb = Debug.getPss(),
            )
        }
    }
}

/** Pure formatting helpers, kept separate so they can be unit-tested on the JVM. */
object DiagnosticsFormat {

    fun uiSurface(widthPx: Int, heightPx: Int, density: Float): String {
        val widthDp = pxToDp(widthPx, density)
        val heightDp = pxToDp(heightPx, density)
        return "$widthPx × $heightPx px · $widthDp × $heightDp dp"
    }

    fun displayMode(widthPx: Int, heightPx: Int, refreshRateHz: Float): String =
        "$widthPx × $heightPx @ ${formatHz(refreshRateHz)} Hz"

    fun density(densityDpi: Int, density: Float): String =
        "$densityDpi dpi · ${densityBucket(densityDpi)} · ×${trimFloat(density)}"

    fun memory(totalRamBytes: Long, memoryClassMb: Int, isLowRam: Boolean): String {
        val total = "${megabytes(totalRamBytes)} MB RAM · heap $memoryClassMb MB"
        return if (isLowRam) "$total · low-RAM" else total
    }

    fun megabytes(bytes: Long): Long = (bytes + MB / 2) / MB

    fun pxToDp(px: Int, density: Float): Int =
        if (density <= 0f) px else Math.round(px / density)

    fun densityBucket(densityDpi: Int): String = when {
        densityDpi <= 120 -> "ldpi"
        densityDpi <= 160 -> "mdpi"
        densityDpi <= 213 -> "tvdpi"
        densityDpi <= 240 -> "hdpi"
        densityDpi <= 320 -> "xhdpi"
        densityDpi <= 480 -> "xxhdpi"
        else -> "xxxhdpi"
    }

    private fun formatHz(hz: Float): String = trimFloat(Math.round(hz * 100f) / 100f)

    private fun trimFloat(value: Float): String =
        if (value == value.toLong().toFloat()) value.toLong().toString()
        else String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')

    private const val MB = 1024L * 1024L
}
