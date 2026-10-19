package io.github.kesemb2.glasshome.ui.glass

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.kesemb2.glasshome.R
import io.github.kesemb2.glasshome.ui.theme.ContinuousRoundedShape
import io.github.kesemb2.glasshome.ui.theme.GlassColors
import io.github.kesemb2.glasshome.ui.theme.Springs
import kotlin.math.min

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

    /** How much larger than the screen the visible backdrop is drawn (centred), so samples line up with it. */
    var zoom: Float = 1f

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
 * Liquid-glass shaders (AGSL, Android 13+; sources in res/raw). They are compiled once; if that fails, or on
 * older Android, or when [enabled] is off, surfaces fall back to the layered look.
 */
object LiquidGlass {
    /** The "glass refraction" setting. */
    @Volatile
    var enabled: Boolean = true

    @Volatile
    private var failed = false
    private var sources: Sources? = null

    class Sources(val lens: String, val specular: String)

    fun sources(context: Context): Sources? {
        if (!enabled || failed || Build.VERSION.SDK_INT < 33) return null
        sources?.let { return it }
        return try {
            val res = context.resources
            val loaded = Sources(
                lens = res.openRawResource(R.raw.glass_lens).bufferedReader().use { it.readText() },
                specular = res.openRawResource(R.raw.glass_specular).bufferedReader().use { it.readText() },
            )
            compile(loaded)
            loaded.also { sources = it }
        } catch (_: Exception) {
            failed = true
            null
        }
    }

    /** A draw-time failure (driver, uniforms) switches every surface to the fallback for the rest of the run. */
    internal fun disable() {
        failed = true
    }

    @RequiresApi(33)
    private fun compile(sources: Sources) {
        RuntimeShader(sources.lens)
        RuntimeShader(sources.specular)
    }
}

/**
 * Liquid-glass surface, drawn behind the content. With the shaders (Android 13+): the pre-blurred backdrop seen
 * through a lens (slight magnification, refraction and dispersion towards the rim), the screen's scrims, a
 * light tint (stronger when [focus] is 1), and a crisp rim light with a softer reflection on the opposite edge.
 * Without them: the plain backdrop crop, tint, an inner top highlight and a gradient rim. All layers are fills
 * of the shape's path, so edges stay anti-aliased on any [shape].
 */
fun Modifier.glass(shape: Shape, focus: () -> Float = { 0f }, rim: Boolean = true): Modifier = composed {
    val backdrop = LocalGlassBackdrop.current
    val liquid = LiquidGlass.sources(LocalContext.current)
    val sampler = remember { BackdropSampler() }
    onGloballyPositioned { sampler.coordinates = it }
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = outline.asPath()
            val radius = outline.cornerRadius(shape, this)
            val rtl = layoutDirection == LayoutDirection.Rtl
            val rimBrush = Brush.linearGradient(
                0f to GlassColors.GlassRim,
                0.65f to Color.Transparent,
                start = Offset(if (rtl) size.width else 0f, 0f),
                end = Offset(if (rtl) 0f else size.width, size.height),
            )
            val reflectionBrush = Brush.linearGradient(
                0.6f to Color.Transparent,
                1f to GlassColors.GlassRim.copy(alpha = 0.22f),
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
                val focusAmount = focus().coerceIn(0f, 1f)
                val lit = backdrop != null && liquid != null &&
                    sampler.drawLiquid(this, path, backdrop, liquid, radius, rtl, focusAmount)
                if (!lit) {
                    if (backdrop != null) sampler.drawPlain(this, path, backdrop)
                    drawPath(path, lerp(GlassColors.GlassTint, GlassColors.GlassTintFocused, focusAmount))
                    drawPath(path, highlight)
                    if (rim) {
                        drawPath(path, rimBrush, style = stroke)
                        drawPath(path, reflectionBrush, style = stroke)
                    }
                }
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

/** Corner radius for the shaders' rounded-rectangle distance field. */
private fun Outline.cornerRadius(shape: Shape, density: Density): Float = when (this) {
    is Outline.Rounded -> roundRect.topLeftCornerRadius.x
    is Outline.Rectangle -> 0f
    // Continuous corners start 1.5 x radius from the vertex; a circular corner of 1.25 x radius matches best.
    is Outline.Generic -> (shape as? ContinuousRoundedShape)?.radiusPx(density)?.times(1.25f) ?: 0f
}

/** Maps the screen-sized blurred bitmap onto one surface; keeps shaders per bitmap (at most two live). */
private class BackdropSampler {
    var coordinates: LayoutCoordinates? = null
    private val matrix = Matrix()
    private val plainSlots = arrayOfNulls<PlainSlot>(2)
    private var nextPlain = 0
    private var liquid: Any? = null // LiquidSlots on Android 13+, kept untyped so older runtimes never load it

    private class PlainSlot(val bitmap: ImageBitmap, val shader: Shader, val brush: ShaderBrush)

    private class Placement(val origin: Offset, val root: Size)

    private fun placement(): Placement? {
        val coords = coordinates?.takeIf { it.isAttached } ?: return null
        val root = coords.findRootCoordinates()
        if (root.size.width <= 0 || root.size.height <= 0) return null
        return Placement(coords.positionInRoot(), Size(root.size.width.toFloat(), root.size.height.toFloat()))
    }

    private fun mapBitmap(bitmap: ImageBitmap, placement: Placement, zoom: Float): Matrix = matrix.apply {
        reset()
        val root = placement.root
        setScale(root.width / bitmap.width * zoom, root.height / bitmap.height * zoom)
        postTranslate(
            -placement.origin.x - (zoom - 1f) * root.width / 2f,
            -placement.origin.y - (zoom - 1f) * root.height / 2f,
        )
    }

    fun drawPlain(scope: DrawScope, path: Path, backdrop: GlassBackdrop) {
        val placement = placement() ?: return
        val previous = backdrop.previous
        val current = backdrop.current
        val fade = backdrop.fade.value
        if (previous != null && fade < 1f) plain(scope, path, previous, placement, backdrop.zoom, 1f)
        if (current != null) plain(scope, path, current, placement, backdrop.zoom, if (previous != null) fade else 1f)
        backdrop.shade?.invoke(placement.root, placement.origin)?.forEach { scope.drawPath(path, it) }
    }

    private fun plain(scope: DrawScope, path: Path, bitmap: ImageBitmap, placement: Placement, zoom: Float, alpha: Float) {
        val slot = plainSlots.firstOrNull { it?.bitmap === bitmap } ?: run {
            val shader = ImageShader(bitmap, TileMode.Clamp, TileMode.Clamp)
            PlainSlot(bitmap, shader, ShaderBrush(shader)).also {
                plainSlots[nextPlain] = it
                nextPlain = (nextPlain + 1) % plainSlots.size
            }
        }
        slot.shader.setLocalMatrix(mapBitmap(bitmap, placement, zoom))
        scope.drawPath(path, slot.brush, alpha = alpha)
    }

    /** Returns false when the liquid path cannot draw, so the caller falls back to the layered look. */
    fun drawLiquid(
        scope: DrawScope, path: Path, backdrop: GlassBackdrop, sources: LiquidGlass.Sources,
        radius: Float, rtl: Boolean, focus: Float,
    ): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        val placement = placement() ?: return false
        return try {
            val slots = (liquid as? LiquidSlots) ?: LiquidSlots(sources).also { liquid = it }
            slots.draw(scope, path, backdrop, placement, radius, rtl, focus)
            true
        } catch (_: Exception) {
            LiquidGlass.disable()
            false
        }
    }

    @RequiresApi(33)
    private inner class LiquidSlots(sources: LiquidGlass.Sources) {
        /** A compiled lens; only its input bitmap changes when the hero moves on, so nothing recompiles. */
        private inner class Lens(source: String) {
            val shader = RuntimeShader(source)
            val brush = ShaderBrush(shader)
            var bitmap: ImageBitmap? = null
            var sample: BitmapShader? = null

            fun use(image: ImageBitmap): BitmapShader {
                sample?.takeIf { bitmap === image }?.let { return it }
                return BitmapShader(
                    image.asAndroidBitmap(),
                    android.graphics.Shader.TileMode.CLAMP,
                    android.graphics.Shader.TileMode.CLAMP,
                ).apply { filterMode = BitmapShader.FILTER_MODE_LINEAR }.also {
                    bitmap = image
                    sample = it
                }
            }
        }

        // Two lenses: the current glass source and, during a cross-fade, the previous one.
        private val lenses = arrayOf(Lens(sources.lens), Lens(sources.lens))
        private val specular = RuntimeShader(sources.specular)
        private val specularBrush = ShaderBrush(specular)

        fun draw(
            scope: DrawScope, path: Path, backdrop: GlassBackdrop, placement: Placement,
            radius: Float, rtl: Boolean, focus: Float,
        ) {
            val size = scope.size
            val minSide = min(size.width, size.height)
            val lightX = if (rtl) 0.6f else -0.6f
            val previous = backdrop.previous
            val current = backdrop.current
            val fade = backdrop.fade.value
            val zoom = backdrop.zoom
            if (previous != null && fade < 1f) {
                lens(scope, path, previous, current, placement, zoom, size, minSide, radius, lightX, 1f)
            }
            if (current != null) {
                lens(scope, path, current, previous, placement, zoom, size, minSide, radius, lightX, if (previous != null) fade else 1f)
            }
            backdrop.shade?.invoke(placement.root, placement.origin)?.forEach { scope.drawPath(path, it) }
            scope.drawPath(path, lerp(LIQUID_TINT, LIQUID_TINT_FOCUSED, focus))
            specular.setFloatUniform("size", size.width, size.height)
            specular.setFloatUniform("radius", radius)
            specular.setFloatUniform("light", lightX, -0.8f)
            specular.setFloatUniform("px", scope.density)
            specular.setFloatUniform("strength", 1f + 0.3f * focus)
            scope.drawPath(path, specularBrush)
        }

        private fun lens(
            scope: DrawScope, path: Path, bitmap: ImageBitmap, other: ImageBitmap?, placement: Placement, zoom: Float,
            size: Size, minSide: Float, radius: Float, lightX: Float, alpha: Float,
        ) {
            // Reuse the lens already showing this bitmap, else one not needed by the other cross-fade layer.
            val lens = lenses.firstOrNull { it.bitmap === bitmap }
                ?: lenses.firstOrNull { it.bitmap !== other }
                ?: lenses[0]
            val sample = lens.use(bitmap)
            sample.setLocalMatrix(mapBitmap(bitmap, placement, zoom))
            // The runtime shader keeps the child as it was when set, so set it again after moving it.
            lens.shader.setInputShader("backdrop", sample)
            lens.shader.setFloatUniform("size", size.width, size.height)
            lens.shader.setFloatUniform("radius", radius)
            lens.shader.setFloatUniform("depth", minSide * 0.18f)
            lens.shader.setFloatUniform("band", minSide * 0.38f)
            lens.shader.setFloatUniform("zoom", 0.05f)
            lens.shader.setFloatUniform("chroma", 1.6f * scope.density)
            lens.shader.setFloatUniform("light", lightX, -0.8f)
            scope.drawPath(path, lens.brush, alpha = alpha)
        }
    }

    private companion object {
        val LIQUID_TINT = Color.White.copy(alpha = 0.08f)
        val LIQUID_TINT_FOCUSED = Color.White.copy(alpha = 0.16f)
    }
}
