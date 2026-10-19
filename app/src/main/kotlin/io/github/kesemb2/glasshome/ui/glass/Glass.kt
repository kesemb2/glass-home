package io.github.kesemb2.glasshome.ui.glass

import android.graphics.Matrix
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.kesemb2.glasshome.ui.theme.GlassColors
import io.github.kesemb2.glasshome.ui.theme.Springs

/**
 * The pre-blurred image that glass surfaces "refract". It covers the whole screen (centre-cropped to the
 * screen's aspect ratio), so a surface samples the crop of it that lies behind its own bounds.
 * Nothing here blurs at draw time: the bitmap is blurred once, off the main thread, when it is loaded.
 */
@Stable
class GlassBackdrop {
    var current by mutableStateOf<ImageBitmap?>(null)
        private set
    var previous by mutableStateOf<ImageBitmap?>(null)
        private set
    internal val fade = Animatable(1f)

    /**
     * Screen-space layers drawn over the sample (for example the hero's scrims), so a glass surface carries the
     * same darkening as the screen behind it. Arguments: root size and the surface's origin in the root.
     */
    var shade: ((root: Size, origin: Offset) -> List<Brush>)? = null

    /** Cross-fades glass surfaces to [bitmap] (null shows tint only). */
    suspend fun show(bitmap: ImageBitmap?) {
        if (bitmap == current) return
        previous = current
        current = bitmap
        fade.snapTo(0f)
        fade.animateTo(1f, Springs.content())
        previous = null
    }

    fun clear() {
        previous = null
        current = null
    }
}

val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/**
 * Liquid-glass surface, drawn behind the content in five cheap layers: the matching crop of the
 * pre-blurred backdrop, a light tint (stronger when [focus] is 1), a soft inner highlight along the top,
 * and a 1 dp rim that is bright at the top-start corner and fades out. Every layer is a path fill, so
 * edges stay anti-aliased on any [shape].
 */
fun Modifier.glass(shape: Shape, focus: () -> Float = { 0f }, rim: Boolean = true): Modifier = composed {
    val backdrop = LocalGlassBackdrop.current
    val sampler = remember { BackdropSampler() }
    onGloballyPositioned { sampler.coordinates = it }
        .drawWithCache {
            val path = shape.createOutline(size, layoutDirection, this).asPath()
            val rtl = layoutDirection == LayoutDirection.Rtl
            val rimBrush = Brush.linearGradient(
                0f to GlassColors.GlassRim,
                0.65f to Color.Transparent,
                start = Offset(if (rtl) size.width else 0f, 0f),
                end = Offset(if (rtl) 0f else size.width, size.height),
            )
            val highlight = Brush.verticalGradient(
                0f to GlassColors.GlassHighlight,
                0.4f to Color.Transparent,
                startY = 0f,
                endY = size.height,
            )
            val stroke = Stroke(width = 1.dp.toPx())
            onDrawBehind {
                if (backdrop != null) sampler.draw(this, path, backdrop)
                drawPath(path, lerp(GlassColors.GlassTint, GlassColors.GlassTintFocused, focus().coerceIn(0f, 1f)))
                drawPath(path, highlight)
                if (rim) drawPath(path, rimBrush, style = stroke)
            }
        }
}

/** Soft outer shadow for glass surfaces that do not get one from the focus effect. */
fun Modifier.glassShadow(shape: Shape, elevation: Dp = 10.dp): Modifier =
    shadow(elevation, shape, clip = false, ambientColor = GlassColors.GlassShadow, spotColor = GlassColors.GlassShadow)

internal fun Outline.asPath(): Path = when (this) {
    is Outline.Generic -> path
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
    is Outline.Rectangle -> Path().apply { addRect(rect) }
}

/** Maps the screen-sized blurred bitmap onto one surface; keeps a shader per bitmap (at most two live). */
private class BackdropSampler {
    var coordinates: LayoutCoordinates? = null
    private val matrix = Matrix()
    private val slots = arrayOfNulls<Slot>(2)
    private var nextSlot = 0

    private class Slot(val bitmap: ImageBitmap, val shader: Shader, val brush: ShaderBrush)

    fun draw(scope: DrawScope, path: Path, backdrop: GlassBackdrop) {
        val coords = coordinates?.takeIf { it.isAttached } ?: return
        val root = coords.findRootCoordinates()
        val rootWidth = root.size.width.toFloat()
        val rootHeight = root.size.height.toFloat()
        if (rootWidth <= 0f || rootHeight <= 0f) return
        val origin = coords.positionInRoot()
        val previous = backdrop.previous
        val current = backdrop.current
        val fade = backdrop.fade.value
        if (previous != null && fade < 1f) sample(scope, path, previous, origin, rootWidth, rootHeight, 1f)
        if (current != null) sample(scope, path, current, origin, rootWidth, rootHeight, if (previous != null) fade else 1f)
        backdrop.shade?.invoke(Size(rootWidth, rootHeight), origin)?.forEach { scope.drawPath(path, it) }
    }

    private fun sample(
        scope: DrawScope, path: Path, bitmap: ImageBitmap, origin: Offset,
        rootWidth: Float, rootHeight: Float, alpha: Float,
    ) {
        val slot = slotFor(bitmap)
        matrix.reset()
        matrix.setScale(rootWidth / bitmap.width, rootHeight / bitmap.height)
        matrix.postTranslate(-origin.x, -origin.y)
        slot.shader.setLocalMatrix(matrix)
        scope.drawPath(path, slot.brush, alpha = alpha)
    }

    private fun slotFor(bitmap: ImageBitmap): Slot {
        slots.firstOrNull { it?.bitmap === bitmap }?.let { return it }
        val shader = ImageShader(bitmap, TileMode.Clamp, TileMode.Clamp)
        val slot = Slot(bitmap, shader, ShaderBrush(shader))
        slots[nextSlot] = slot
        nextSlot = (nextSlot + 1) % slots.size
        return slot
    }
}
