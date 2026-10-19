package io.github.kesemb2.glasshome.image

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Settings › Gradient colour "from the image": a near-black tinted with the hero image's main colour. The hue
 * that carries the most colour decides the tint (pixels vote with their chroma, so blacks, whites and greys do not
 * vote); a colourful image gives a deeper tint, a grey one stays almost black. Pixels are ARGB_8888 ints; mostly
 * transparent ones are ignored. The result is opaque ARGB.
 */
object ScrimTone {
    private const val HUE_BINS = 24
    private const val MIN_CHROMA = 12

    /** Mean chroma (0..1) of an image colourful enough for the full tint. */
    private const val FULL_CHROMA = 0.18f
    private const val MAX_SATURATION = 0.75f

    /** HSV value of the tone: a grey image gets the lower one, a colourful image the higher. */
    internal const val MIN_VALUE = 0.07f
    internal const val MAX_VALUE = 0.17f

    fun of(pixels: IntArray): Int {
        val weight = FloatArray(HUE_BINS)
        val sumR = FloatArray(HUE_BINS)
        val sumG = FloatArray(HUE_BINS)
        val sumB = FloatArray(HUE_BINS)
        var opaque = 0
        var chromaSum = 0f
        for (p in pixels) {
            if ((p ushr 24) < 128) continue
            opaque++
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val hi = max(r, max(g, b))
            val chroma = hi - min(r, min(g, b))
            chromaSum += chroma
            if (chroma < MIN_CHROMA) continue
            val bin = (hue(r, g, b, hi, chroma) / 6f * HUE_BINS).toInt().coerceIn(0, HUE_BINS - 1)
            val w = chroma.toFloat()
            weight[bin] += w
            sumR[bin] += r * w
            sumG[bin] += g * w
            sumB[bin] += b * w
        }
        if (opaque == 0) return grey(MIN_VALUE)

        // The strongest hue with its two neighbours, so a colour split across a bin edge still wins.
        var best = -1
        var bestWeight = 0f
        for (i in 0 until HUE_BINS) {
            val w = weight[i] + weight[(i + 1) % HUE_BINS] + weight[(i + HUE_BINS - 1) % HUE_BINS]
            if (w > bestWeight) {
                best = i
                bestWeight = w
            }
        }
        if (best < 0) return grey(MIN_VALUE)
        var r = 0f
        var g = 0f
        var b = 0f
        for (d in -1..1) {
            val i = (best + d + HUE_BINS) % HUE_BINS
            r += sumR[i]
            g += sumG[i]
            b += sumB[i]
        }
        r /= bestWeight
        g /= bestWeight
        b /= bestWeight

        val hi = max(r, max(g, b))
        val chroma = hi - min(r, min(g, b))
        val strength = sqrt((chromaSum / opaque / 255f) / FULL_CHROMA).coerceIn(0f, 1f)
        val saturation = min(chroma / hi * 1.2f, MAX_SATURATION) * strength
        val value = MIN_VALUE + (MAX_VALUE - MIN_VALUE) * strength
        return fromHsv(hue(r, g, b, hi, chroma), saturation, value)
    }

    /** Hue in 0..6 (red 0, yellow 1, green 2, cyan 3, blue 4, magenta 5) of a colour with chroma > 0. */
    private fun hue(r: Float, g: Float, b: Float, hi: Float, chroma: Float): Float {
        val h = when (hi) {
            r -> (g - b) / chroma
            g -> (b - r) / chroma + 2f
            else -> (r - g) / chroma + 4f
        }
        return if (h < 0f) h + 6f else h
    }

    private fun hue(r: Int, g: Int, b: Int, hi: Int, chroma: Int): Float =
        hue(r.toFloat(), g.toFloat(), b.toFloat(), hi.toFloat(), chroma.toFloat())

    private fun fromHsv(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val x = c * (1f - abs(h % 2f - 1f))
        val (r, g, b) = when (h.toInt().coerceIn(0, 5)) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = v - c
        return argb(r + m, g + m, b + m)
    }

    private fun grey(v: Float) = argb(v, v, v)

    private fun argb(r: Float, g: Float, b: Float): Int {
        fun c(v: Float) = (v * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (c(r) shl 16) or (c(g) shl 8) or c(b)
    }
}
