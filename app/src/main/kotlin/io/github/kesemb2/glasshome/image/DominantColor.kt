package io.github.kesemb2.glasshome.image

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The fill behind an app icon that has no TV banner: the icon's most common colour, with colourful pixels
 * counting more than greys (so a brand colour wins over a white background), then kept dark enough for white
 * text and light enough to read as a colour. Pixels are ARGB_8888 ints; mostly transparent ones are ignored.
 */
object DominantColor {
    const val FALLBACK = 0xFF2C2F38.toInt()
    private const val MIN_VALUE = 0.22f
    private const val MAX_VALUE = 0.52f

    fun of(pixels: IntArray): Int {
        // 4 bits per channel: 4096 bins of (weight, r, g, b) sums.
        val weight = FloatArray(4096)
        val sumR = FloatArray(4096)
        val sumG = FloatArray(4096)
        val sumB = FloatArray(4096)
        for (p in pixels) {
            if ((p ushr 24) < 128) continue
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val hi = max(r, max(g, b))
            val lo = min(r, min(g, b))
            val saturation = if (hi == 0) 0f else (hi - lo) / hi.toFloat()
            val w = 0.3f + saturation
            val bin = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            weight[bin] += w
            sumR[bin] += r * w
            sumG[bin] += g * w
            sumB[bin] += b * w
        }
        var best = -1
        for (i in weight.indices) if (weight[i] > 0f && (best < 0 || weight[i] > weight[best])) best = i
        if (best < 0) return FALLBACK
        val w = weight[best]
        return clampValue(sumR[best] / w, sumG[best] / w, sumB[best] / w)
    }

    /** Scales the colour so its HSV value lies in [MIN_VALUE, MAX_VALUE], keeping hue and saturation. */
    internal fun clampValue(r: Float, g: Float, b: Float): Int {
        val value = max(r, max(g, b)) / 255f
        val target = value.coerceIn(MIN_VALUE, MAX_VALUE)
        if (value <= 0.001f) {
            val grey = (target * 255f).roundToInt()
            return (0xFF shl 24) or (grey shl 16) or (grey shl 8) or grey
        }
        val k = target / value
        fun c(v: Float) = (v * k).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (c(r) shl 16) or (c(g) shl 8) or c(b)
    }
}
