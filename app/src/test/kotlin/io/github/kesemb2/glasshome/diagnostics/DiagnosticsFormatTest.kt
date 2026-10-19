package io.github.kesemb2.glasshome.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticsFormatTest {

    @Test
    fun uiSurfaceConvertsPixelsToDp() {
        assertEquals("1920 × 1080 px · 960 × 540 dp", DiagnosticsFormat.uiSurface(1920, 1080, 2f))
        assertEquals("1280 × 720 px · 960 × 540 dp", DiagnosticsFormat.uiSurface(1280, 720, 1.3333334f))
    }

    @Test
    fun pxToDpSurvivesMissingDensity() {
        assertEquals(1920, DiagnosticsFormat.pxToDp(1920, 0f))
    }

    @Test
    fun displayModeTrimsRefreshRate() {
        assertEquals("3840 × 2160 @ 60 Hz", DiagnosticsFormat.displayMode(3840, 2160, 60.000004f))
        assertEquals("3840 × 2160 @ 59.94 Hz", DiagnosticsFormat.displayMode(3840, 2160, 59.94006f))
        assertEquals("1920 × 1080 @ 23.98 Hz", DiagnosticsFormat.displayMode(1920, 1080, 23.976f))
    }

    @Test
    fun densityNamesTheBucket() {
        assertEquals("320 dpi · xhdpi · ×2", DiagnosticsFormat.density(320, 2f))
        assertEquals("213 dpi · tvdpi · ×1.33", DiagnosticsFormat.density(213, 1.33125f))
        assertEquals("mdpi", DiagnosticsFormat.densityBucket(160))
        assertEquals("xxhdpi", DiagnosticsFormat.densityBucket(480))
    }

    @Test
    fun memoryRoundsToMegabytes() {
        val fourGb = 4L * 1024 * 1024 * 1024 - 300L * 1024 * 1024
        assertEquals("3796 MB RAM · heap 256 MB", DiagnosticsFormat.memory(fourGb, 256, isLowRam = false))
        assertEquals("1024 MB RAM · heap 128 MB · low-RAM", DiagnosticsFormat.memory(1024L * 1024 * 1024, 128, isLowRam = true))
    }
}
