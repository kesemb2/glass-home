package io.github.kesemb2.glasshome.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max

class DominantColorTest {

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF

    @Test
    fun transparentIconGivesTheFallback() {
        assertEquals(DominantColor.FALLBACK, DominantColor.of(IntArray(100) { argb(0, 255, 0, 0) }))
        assertEquals(DominantColor.FALLBACK, DominantColor.of(IntArray(0)))
    }

    @Test
    fun brandColourBeatsALargerWhiteBackground() {
        val pixels = IntArray(100) { if (it < 65) argb(255, 255, 255, 255) else argb(255, 20, 60, 230) }
        val c = DominantColor.of(pixels)
        assertTrue("expected blue, got ${Integer.toHexString(c)}", b(c) > r(c) && b(c) > g(c))
    }

    @Test
    fun brightColoursAreDarkenedForWhiteText() {
        val c = DominantColor.of(IntArray(10) { argb(255, 250, 40, 40) })
        assertTrue(max(r(c), max(g(c), b(c))) <= (0.52f * 255).toInt() + 1)
        assertTrue(r(c) > g(c) * 3)
    }

    @Test
    fun blackIsLiftedToADarkGrey() {
        val c = DominantColor.of(IntArray(10) { argb(255, 0, 0, 0) })
        assertEquals(r(c), g(c))
        assertEquals(g(c), b(c))
        assertTrue(r(c) in 50..60)
    }

    @Test
    fun alphaIsAlwaysOpaque() {
        val c = DominantColor.of(IntArray(10) { argb(200, 30, 200, 90) })
        assertEquals(0xFF, c ushr 24)
    }
}
