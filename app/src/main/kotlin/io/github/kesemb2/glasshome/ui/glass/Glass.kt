package io.github.kesemb2.glasshome.ui.glass

import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.kesemb2.glasshome.R
import io.github.kesemb2.glasshome.ui.theme.ContinuousRoundedShape
import io.github.kesemb2.glasshome.ui.theme.GlassColors
import io.github.kesemb2.glasshome.ui.theme.Springs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Screen-space darkening behind glass (for example the hero's scrims): a horizontal ramp from [alphaX] at [fromX]
 * to 0 at [toX], a vertical ramp from 0 at [fromY] to [alphaY] at [toY], and an even [uniform] darkening, stacked
 * like three scrims.
 */
@Immutable
data class GlassShade(
    val fromX: Float,
    val toX: Float,
    val alphaX: Float,
    val fromY: Float,
    val toY: Float,
    val alphaY: Float,
    val uniform: Float = 0f,
) {
    /** The ramps faded out by [amount] (0..1) while an even darkening of [dim] fades in. */
    fun fadeTo(amount: Float, dim: Float): GlassShade =
        copy(alphaX = alphaX * (1f - amount), alphaY = alphaY * (1f - amount), uniform = dim * amount)

    /** The same ramps as brushes, in the coordinates of a surface whose origin is at ([dx], [dy]). */
    fun brushes(dx: Float = 0f, dy: Float = 0f): List<Brush> = listOfNotNull(
        Brush.horizontalGradient(
            0f to Color.Black.copy(alpha = alphaX),
            1f to Color.Transparent,
            startX = fromX - dx,
            endX = toX - dx,
        ),
        Brush.verticalGradient(
            0f to Color.Transparent,
            1f to Color.Black.copy(alpha = alphaY),
            startY = fromY - dy,
            endY = toY - dy,
        ),
        if (uniform > 0f) SolidColor(Color.Black.copy(alpha = uniform)) else null,
    )
}

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

    /** The screen's scrims for a given root size, so glass carries the same darkening as the screen behind it. */
    var shade: ((root: Size) -> GlassShade)? = null

    /** How much larger than the screen the visible backdrop is drawn (centred), so samples line up with it. */
    var zoom: Float = 1f

    /** Shows [bitmap] at once, without a cross-fade (overlays, whose source never changes). */
    fun set(bitmap: ImageBitmap?) {
        previous = null
        current = bitmap
    }

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
 * The liquid-glass lens shader (AGSL, Android 13+; source in res/raw/glass_lens.agsl). Compiled once; if that
 * fails, or on older Android, or when [enabled] is off, surfaces fall back to the layered look.
 */
object LiquidGlass {
    /** The "glass refraction" setting; observable, so surfaces switch paths as soon as it changes. */
    var enabled: Boolean by mutableStateOf(true)

    @Volatile
    private var failed = false
    private var source: String? = null

    fun source(context: Context): String? {
        if (!enabled || failed || Build.VERSION.SDK_INT < 33) return null
        source?.let { return it }
        return try {
            val loaded = context.resources.openRawResource(R.raw.glass_lens).bufferedReader().use { it.readText() }
            compile(loaded)
            loaded.also { source = it }
        } catch (_: Exception) {
            failed = true
            null
        }
    }

    /** A draw-time failure (driver, uniforms) switches every surface to the fallback for the rest of the run. */
    internal fun disable() {
        failed = true
    }

    internal val usable: Boolean get() = !failed

    @RequiresApi(33)
    private fun compile(source: String) {
        RuntimeShader(source)
    }
}

/**
 * Liquid-glass surface, drawn behind the content.
 *
 * With the lens shader (Android 13+): the pre-blurred backdrop seen through a bevelled rounded-rectangle lens —
 * strong inward refraction in the outermost pixels (the rim shows a compressed mirror of what is inside), colour
 * dispersion there, vibrancy, an adaptive tint (lighter over dark content, darker over light), and two symmetric
 * rim-light lobes; the screen's scrims are refracted with the image. Without it: the plain backdrop crop, the
 * scrims, a tint (stronger when [focus] is 1), an inner top highlight and gradient rims.
 *
 * Each surface is rendered once into a cached offscreen layer per backdrop image and only re-rendered when what it
 * shows changes (its image, position, size, focus or the scrims). Everything else on screen can redraw around it
 * for the cost of copying that layer, and a cross-fade between two images only changes the layers' alpha.
 */
fun Modifier.glass(shape: Shape, focus: () -> Float = { 0f }, rim: Boolean = true): Modifier = composed {
    val backdrop = LocalGlassBackdrop.current
    val lens = LiquidGlass.source(LocalContext.current)
    val sampler = remember { BackdropSampler() }
    onGloballyPositioned { sampler.update(it) }
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val rtl = layoutDirection == LayoutDirection.Rtl
            val look = GlassLook(
                path = outline.asPath(),
                radius = outline.cornerRadius(shape, this),
                rimBrush = Brush.linearGradient(
                    0f to GlassColors.GlassRim,
                    0.65f to Color.Transparent,
                    start = Offset(if (rtl) size.width else 0f, 0f),
                    end = Offset(if (rtl) 0f else size.width, size.height),
                ),
                reflectionBrush = Brush.linearGradient(
                    0.6f to Color.Transparent,
                    1f to GlassColors.GlassRim.copy(alpha = 0.22f),
                    start = Offset(if (rtl) size.width else 0f, 0f),
                    end = Offset(if (rtl) 0f else size.width, size.height),
                ),
                highlight = Brush.verticalGradient(
                    0f to GlassColors.GlassHighlight,
                    0.4f to Color.Transparent,
                    startY = 0f,
                    endY = size.height,
                ),
                stroke = Stroke(width = 1.dp.toPx()),
                rim = rim,
            )
            val slots = arrayOf(GlassSlot(obtainGraphicsLayer()), GlassSlot(obtainGraphicsLayer()))
            onDrawBehind { sampler.draw(this, slots, look, backdrop, lens, focus().coerceIn(0f, 1f)) }
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

/** Corner radius for the shader's rounded-rectangle distance field. */
private fun Outline.cornerRadius(shape: Shape, density: Density): Float = when (this) {
    is Outline.Rounded -> roundRect.topLeftCornerRadius.x
    is Outline.Rectangle -> 0f
    // Continuous corners start 1.5 x radius from the vertex; a circular corner of 1.25 x radius matches best.
    is Outline.Generic -> (shape as? ContinuousRoundedShape)?.radiusPx(density)?.times(1.25f) ?: 0f
}

/** A surface's shape and the brushes of the fallback look, rebuilt only when its size or direction changes. */
private class GlassLook(
    val path: Path,
    val radius: Float,
    val rimBrush: Brush,
    val reflectionBrush: Brush,
    val highlight: Brush,
    val stroke: Stroke,
    val rim: Boolean,
)

/** Everything a cached rendering depends on; when any of it changes, the layer is rendered again. */
private data class SlotKey(
    val bitmap: ImageBitmap?,
    val origin: Offset?,
    val root: Size?,
    val size: Size,
    val focus: Float,
    val shade: GlassShade?,
    val zoom: Float,
    val liquid: Boolean,
)

/** One cached rendering of a surface (an offscreen layer) for one backdrop image. */
private class GlassSlot(val layer: GraphicsLayer) {
    var key: SlotKey? = null
    var lens: Any? = null // Lens on Android 13+, kept untyped so older runtimes never load the class

    init {
        layer.compositingStrategy = CompositingStrategy.Offscreen
    }
}

/**
 * Maps the screen-sized blurred bitmap onto one surface. The surface's position is observable state, so a
 * surface that moves (scrolling, focus scale) renders its sample again.
 */
private class BackdropSampler {
    private var origin by mutableStateOf(Offset.Zero)
    private var root by mutableStateOf(Size.Zero)
    private val matrix = Matrix()

    class Placement(val origin: Offset, val root: Size)

    fun update(coordinates: LayoutCoordinates) {
        if (!coordinates.isAttached) return
        val rootSize = coordinates.findRootCoordinates().size
        origin = coordinates.positionInRoot()
        root = Size(rootSize.width.toFloat(), rootSize.height.toFloat())
    }

    private fun placement(): Placement? {
        val root = root
        if (root.width <= 0f || root.height <= 0f) return null
        return Placement(origin, root)
    }

    fun mapBitmap(bitmap: ImageBitmap, placement: Placement, zoom: Float): Matrix = matrix.apply {
        reset()
        val root = placement.root
        setScale(root.width / bitmap.width * zoom, root.height / bitmap.height * zoom)
        postTranslate(
            -placement.origin.x - (zoom - 1f) * root.width / 2f,
            -placement.origin.y - (zoom - 1f) * root.height / 2f,
        )
    }

    fun draw(scope: DrawScope, slots: Array<GlassSlot>, look: GlassLook, backdrop: GlassBackdrop?, source: String?, focus: Float) {
        val placement = placement()
        val current = backdrop?.current
        val previous = backdrop?.previous
        val fade = backdrop?.fade?.value ?: 1f
        val fading = previous != null && fade < 1f
        val shade = placement?.let { backdrop?.shade?.invoke(it.root) }
        val zoom = backdrop?.zoom ?: 1f
        val liquid = source != null && LiquidGlass.usable && Build.VERSION.SDK_INT >= 33
        val frame = Frame(placement, shade, zoom, focus, look, source, liquid)
        if (fading) drawSlot(scope, slots, previous, current, 1f, frame)
        drawSlot(scope, slots, current, if (fading) previous else null, if (fading) fade else 1f, frame)
    }

    private class Frame(
        val placement: Placement?,
        val shade: GlassShade?,
        val zoom: Float,
        val focus: Float,
        val look: GlassLook,
        val source: String?,
        val liquid: Boolean,
    )

    private fun drawSlot(scope: DrawScope, slots: Array<GlassSlot>, bitmap: ImageBitmap?, other: ImageBitmap?, alpha: Float, frame: Frame) {
        val key = SlotKey(
            bitmap, frame.placement?.origin, frame.placement?.root, scope.size, frame.focus, frame.shade, frame.zoom, frame.liquid,
        )
        // The slot already showing this, else one with the same image, else one the other layer does not need.
        val slot = slots.firstOrNull { it.key == key }
            ?: slots.firstOrNull { it.key != null && it.key?.bitmap === bitmap }
            ?: slots.firstOrNull { it.key == null || it.key?.bitmap !== other }
            ?: slots[0]
        if (slot.key != key) {
            val width = ceil(scope.size.width).toInt().coerceAtLeast(1)
            val height = ceil(scope.size.height).toInt().coerceAtLeast(1)
            slot.layer.record(scope, scope.layoutDirection, IntSize(width, height)) { render(this, slot, bitmap, frame) }
            slot.key = key
        }
        slot.layer.alpha = alpha
        scope.drawLayer(slot.layer)
    }

    private fun render(scope: DrawScope, slot: GlassSlot, bitmap: ImageBitmap?, frame: Frame) {
        val placement = frame.placement
        val look = frame.look
        if (frame.liquid && bitmap != null && placement != null && frame.source != null && Build.VERSION.SDK_INT >= 33) {
            try {
                val lens = (slot.lens as? Lens) ?: Lens(frame.source).also { slot.lens = it }
                lens.draw(scope, look, bitmap, mapBitmap(bitmap, placement, frame.zoom), placement, frame.shade, frame.focus)
                return
            } catch (_: Exception) {
                LiquidGlass.disable()
            }
        }
        if (bitmap != null && placement != null) {
            val shader = ImageShader(bitmap, TileMode.Clamp, TileMode.Clamp)
            shader.setLocalMatrix(mapBitmap(bitmap, placement, frame.zoom))
            scope.drawPath(look.path, ShaderBrush(shader))
            frame.shade?.brushes(placement.origin.x, placement.origin.y)?.forEach { scope.drawPath(look.path, it) }
        }
        scope.drawPath(look.path, lerp(GlassColors.GlassTint, GlassColors.GlassTintFocused, frame.focus))
        scope.drawPath(look.path, look.highlight)
        if (look.rim) {
            scope.drawPath(look.path, look.rimBrush, style = look.stroke)
            scope.drawPath(look.path, look.reflectionBrush, style = look.stroke)
        }
    }
}

/** A compiled lens for one cached layer; only its input bitmap and uniforms change, so nothing recompiles. */
@RequiresApi(33)
private class Lens(source: String) {
    private val shader = RuntimeShader(source)
    private val brush = ShaderBrush(shader)
    private var bitmap: ImageBitmap? = null
    private var sample: BitmapShader? = null

    fun draw(
        scope: DrawScope, look: GlassLook, image: ImageBitmap, map: Matrix, placement: BackdropSampler.Placement,
        shade: GlassShade?, focus: Float,
    ) {
        val sample = sample?.takeIf { bitmap === image } ?: BitmapShader(
            image.asAndroidBitmap(),
            android.graphics.Shader.TileMode.CLAMP,
            android.graphics.Shader.TileMode.CLAMP,
        ).apply { filterMode = BitmapShader.FILTER_MODE_LINEAR }.also {
            bitmap = image
            sample = it
        }
        sample.setLocalMatrix(map)
        // The runtime shader keeps the child as it was when set, so set it again after moving it.
        shader.setInputShader("backdrop", sample)

        // Lens geometry scales with the surface's short side, so small pills are not all rim.
        val width = scope.size.width
        val height = scope.size.height
        val minSide = min(width, height)
        val reference = REFERENCE_SIDE_DP * scope.density
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("radius", look.radius)
        shader.setFloatUniform("bevel", max(2f, min(BEVEL_MAX_PX, minSide * BEVEL_RATIO)))
        shader.setFloatUniform("refractPx", min(REFRACT_MAX_PX, REFRACT_RATIO * minSide * min(1f, minSide / reference)))
        shader.setFloatUniform("dispersion", DISPERSION)
        shader.setFloatUniform("lightDir", LIGHT_X, LIGHT_Y)
        shader.setFloatUniform("spec", SPECULAR * (1f + 0.3f * focus))
        shader.setFloatUniform("rimBandMax", max(2f, min(RIM_BAND_MAX_PX, minSide * RIM_BAND_RATIO)))
        shader.setFloatUniform("saturation", VIBRANCY)
        shader.setFloatUniform("focusTint", FOCUS_TINT * focus)
        shader.setFloatUniform("origin", placement.origin.x, placement.origin.y)
        if (shade != null) {
            shader.setFloatUniform("shadeX", shade.fromX, shade.toX, shade.alphaX, shade.uniform)
            shader.setFloatUniform("shadeY", shade.fromY, shade.toY, shade.alphaY, 0f)
        } else {
            shader.setFloatUniform("shadeX", 0f, 1f, 0f, 0f)
            shader.setFloatUniform("shadeY", 0f, 1f, 0f, 0f)
        }
        scope.drawPath(look.path, brush)
    }

    private companion object {
        // Lens parameters (px values were tuned against the reference implementation; see DESIGN.md).
        const val BEVEL_MAX_PX = 48f
        const val BEVEL_RATIO = 0.3f
        const val REFRACT_MAX_PX = 160f
        const val REFRACT_RATIO = 0.7f
        const val REFERENCE_SIDE_DP = 110f
        const val DISPERSION = 0.12f
        const val LIGHT_X = 0.866f
        const val LIGHT_Y = 0.5f
        const val SPECULAR = 1.25f
        const val RIM_BAND_MAX_PX = 6f
        const val RIM_BAND_RATIO = 0.05f
        const val VIBRANCY = 1.5f
        const val FOCUS_TINT = 0.06f
    }
}
