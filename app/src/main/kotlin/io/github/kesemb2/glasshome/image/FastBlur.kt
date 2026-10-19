package io.github.kesemb2.glasshome.image

import kotlin.math.roundToInt

/**
 * CPU blur for small bitmaps (the ~1/8-scale hero backdrops and logo glows). Three box-blur passes
 * approximate a Gaussian; each pass is separable and O(pixels) regardless of radius.
 * Pixels are ARGB_8888 ints, as returned by Bitmap.getPixels.
 */
object FastBlur {

    fun blur(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int = 3) {
        require(pixels.size == width * height) { "pixels.size != width * height" }
        if (radius < 1 || width == 0 || height == 0) return
        val scratch = IntArray(pixels.size)
        repeat(passes) {
            boxPass(pixels, scratch, width, height, radius, horizontal = true)
            boxPass(scratch, pixels, width, height, radius, horizontal = false)
        }
    }

    /**
     * Pushes colours away from their luminance (factor 1 keeps the image, > 1 saturates) and multiplies them by
     * [gain]. Alpha is preserved.
     */
    fun saturate(pixels: IntArray, factor: Float, gain: Float = 1f) {
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = p ushr 24
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
            pixels[i] = (a shl 24) or
                (channel((l + (r - l) * factor) * gain) shl 16) or
                (channel((l + (g - l) * factor) * gain) shl 8) or
                channel((l + (b - l) * factor) * gain)
        }
    }

    private fun channel(value: Float): Int = value.roundToInt().coerceIn(0, 255)

    private fun boxPass(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        val window = 2 * radius + 1
        val last = length - 1
        for (line in 0 until lines) {
            val base = if (horizontal) line * width else line
            val step = if (horizontal) 1 else width
            var sa = 0
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -radius..radius) {
                val p = src[base + i.coerceIn(0, last) * step]
                sa += p ushr 24
                sr += (p shr 16) and 0xFF
                sg += (p shr 8) and 0xFF
                sb += p and 0xFF
            }
            for (i in 0 until length) {
                dst[base + i * step] = ((sa / window) shl 24) or ((sr / window) shl 16) or ((sg / window) shl 8) or (sb / window)
                val outgoing = src[base + (i - radius).coerceIn(0, last) * step]
                val incoming = src[base + (i + radius + 1).coerceIn(0, last) * step]
                sa += (incoming ushr 24) - (outgoing ushr 24)
                sr += ((incoming shr 16) and 0xFF) - ((outgoing shr 16) and 0xFF)
                sg += ((incoming shr 8) and 0xFF) - ((outgoing shr 8) and 0xFF)
                sb += (incoming and 0xFF) - (outgoing and 0xFF)
            }
        }
    }
}

/** Pure checks used by the logo transformation. */
object LogoAnalysis {

    /** True when the visible (mostly opaque) pixels of a logo are dark on average, so it needs a light glow. */
    fun isMostlyDark(pixels: IntArray, threshold: Float = 0.28f): Boolean {
        var weight = 0L
        var luminance = 0.0
        for (p in pixels) {
            val a = p ushr 24
            if (a < 128) continue
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            luminance += a * (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
            weight += a
        }
        if (weight == 0L) return false
        return luminance / weight < threshold
    }
}
