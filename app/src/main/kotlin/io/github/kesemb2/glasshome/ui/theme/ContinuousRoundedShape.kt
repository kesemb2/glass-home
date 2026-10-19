package io.github.kesemb2.glasshome.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

/**
 * Rounded rectangle with continuous ("squircle-like") corners: each corner starts 1.5 x radius from the
 * vertex and eases in with a single cubic, so curvature ramps up from zero instead of jumping like a
 * circular arc. At 45 degrees it sits where a circular corner of the same radius would.
 */
class ContinuousRoundedShape(private val radius: Dp) : Shape {

    fun radiusPx(density: Density): Float = with(density) { radius.toPx() }

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }
        val p = min(r * 1.5f, min(size.width, size.height) / 2f)
        val a = p * 0.2f
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(p, 0f)
            lineTo(w - p, 0f)
            cubicTo(w - a, 0f, w, a, w, p)
            lineTo(w, h - p)
            cubicTo(w, h - a, w - a, h, w - p, h)
            lineTo(p, h)
            cubicTo(a, h, 0f, h - a, 0f, h - p)
            lineTo(0f, p)
            cubicTo(0f, a, a, 0f, p, 0f)
            close()
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean = other is ContinuousRoundedShape && other.radius == radius

    override fun hashCode(): Int = radius.hashCode()
}
