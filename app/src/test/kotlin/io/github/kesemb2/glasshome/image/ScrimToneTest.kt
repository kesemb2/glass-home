package io.github.kesemb2.glasshome.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max

class ScrimToneTest {

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF
    private fun value(c: Int) = max(r(c), max(g(c), b(c)))

    @Test
    fun greyImageGivesNearBlack() {
        val c = ScrimTone.of(IntArray(100) { argb(255, 120 + it % 3, 120, 121) })
        assertEquals(r(c), g(c))
        assertEquals(g(c), b(c))
        assertTrue("too bright: ${Integer.toHexString(c)}", value(c) <= (ScrimTone.MIN_VALUE * 255).toInt() + 1)
    }

    @Test
    fun colourfulImageGivesADarkTintOfItsColour() {
        val c = ScrimTone.of(IntArray(100) { argb(255, 230, 50, 40) })
        assertTrue("expected red, got ${Integer.toHexString(c)}", r(c) > g(c) * 2 && r(c) > b(c) * 2)
        assertTrue("too bright: ${Integer.toHexString(c)}", value(c) <= (ScrimTone.MAX_VALUE * 255).toInt() + 1)
    }

    @Test
    fun theHueWithMoreColourWins() {
        // A dark frame, a large blue sky and a small orange subject: blue carries more colour.
        val pixels = IntArray(100) {
            when {
                it < 40 -> argb(255, 10, 10, 12)
                it < 85 -> argb(255, 40, 110, 220)
                else -> argb(255, 240, 140, 30)
            }
        }
        val c = ScrimTone.of(pixels)
        assertTrue("expected blue, got ${Integer.toHexString(c)}", b(c) > r(c) && b(c) > g(c))
    }

    @Test
    fun aDullImageTintsLess() {
        val vivid = ScrimTone.of(IntArray(100) { argb(255, 40, 200, 90) })
        val dull = ScrimTone.of(IntArray(100) { if (it < 10) argb(255, 40, 200, 90) else argb(255, 30, 30, 30) })
        assertTrue(value(dull) < value(vivid))
        assertTrue(g(dull) - r(dull) < g(vivid) - r(vivid))
    }

    @Test
    fun transparentOrEmptyGivesNearBlack() {
        for (pixels in listOf(IntArray(0), IntArray(10) { argb(0, 255, 0, 0) })) {
            val c = ScrimTone.of(pixels)
            assertEquals(0xFF, c ushr 24)
            assertTrue(value(c) <= (ScrimTone.MIN_VALUE * 255).toInt() + 1)
        }
    }

    @Test
    fun alphaIsAlwaysOpaque() {
        assertEquals(0xFF, ScrimTone.of(IntArray(10) { argb(200, 30, 200, 90) }) ushr 24)
    }
}
