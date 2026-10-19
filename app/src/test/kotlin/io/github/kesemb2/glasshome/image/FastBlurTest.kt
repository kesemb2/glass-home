package io.github.kesemb2.glasshome.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FastBlurTest {

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun uniformImageIsUnchanged() {
        val color = argb(255, 40, 120, 200)
        val pixels = IntArray(30 * 20) { color }
        FastBlur.blur(pixels, 30, 20, radius = 4)
        assertTrue(pixels.all { it == color })
    }

    @Test
    fun brightPixelSpreadsSymmetricallyAndDims() {
        val w = 21
        val h = 21
        val pixels = IntArray(w * h) { argb(255, 0, 0, 0) }
        pixels[10 * w + 10] = argb(255, 255, 255, 255)
        FastBlur.blur(pixels, w, h, radius = 2)
        fun red(x: Int, y: Int) = (pixels[y * w + x] shr 16) and 0xFF
        assertTrue(red(10, 10) in 1..254)
        assertEquals(red(9, 10), red(11, 10))
        assertEquals(red(10, 9), red(10, 11))
        assertTrue(red(10, 10) >= red(12, 10))
        assertEquals(0, red(0, 0))
    }

    @Test
    fun zeroRadiusIsANoOp() {
        val pixels = intArrayOf(argb(255, 1, 2, 3), argb(255, 200, 100, 50))
        val copy = pixels.copyOf()
        FastBlur.blur(pixels, 2, 1, radius = 0)
        assertTrue(pixels.contentEquals(copy))
    }

    @Test
    fun saturationKeepsGreysAndAlpha() {
        val grey = argb(128, 90, 90, 90)
        val pixels = intArrayOf(grey, argb(255, 200, 100, 100))
        FastBlur.saturate(pixels, 1.15f)
        assertEquals(grey, pixels[0])
        val red = (pixels[1] shr 16) and 0xFF
        val green = (pixels[1] shr 8) and 0xFF
        assertTrue("red channel should grow", red > 200)
        assertTrue("green channel should shrink", green < 100)
        assertEquals(255, pixels[1] ushr 24)
    }

    @Test
    fun darkLogoNeedsGlowAndLightLogoDoesNot() {
        val dark = IntArray(100) { argb(255, 20, 20, 30) }
        val light = IntArray(100) { argb(255, 230, 230, 230) }
        val transparent = IntArray(100) { argb(0, 0, 0, 0) }
        assertTrue(LogoAnalysis.isMostlyDark(dark))
        assertFalse(LogoAnalysis.isMostlyDark(light))
        assertFalse(LogoAnalysis.isMostlyDark(transparent))
    }
}
