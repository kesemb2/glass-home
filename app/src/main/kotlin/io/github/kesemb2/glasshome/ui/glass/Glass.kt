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
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
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
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.roundToInt

/**
 * Screen-space darkening behind glass (for example the hero's scrims): a horizontal ramp from [alphaX] at [fromX]
 * to 0 at [toX], a vertical ramp from 0 at [fromY] to [alphaY] at [toY], and an even [uniform] darkening, stacked
 * like three scrims of [color] (opaque; black or a dark tone).
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
    val color: Color = Color.Black,
) {
    /** The same ramps as brushes, in the coordinates of a surface whose origin is at ([dx], [dy]). */
    fun brushes(dx: Float = 0f, dy: Float = 0f): List<Brush> = listOfNotNull(
        Brush.horizontalGradient(
            0f to color.copy(alpha = alphaX),
            1f to Color.Transparent,
            startX = fromX - dx,
            endX = toX - dx,
        ),
        Brush.verticalGradient(
            0f to Color.Transparent,
            1f to color.copy(alpha = alphaY),
            startY = fromY - dy,
            endY = toY - dy,
        ),
        if (uniform > 0f) SolidColor(color.copy(alpha = uniform)) else null,
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

    /**
     * Cross-fades glass surfaces to [bitmap] (null shows tint only). A new image during a cross-fade that has not
     * got half way replaces the incoming one and the fade carries on, instead of starting over from a half-faded
     * mix; the first image shows at once.
     */
    suspend fun show(bitmap: ImageBitmap?) {
        if (bitmap == current) return
        if (current == null && previous == null) {
            current = bitmap
            return
        }
        if (previous == null || fade.value >= 0.5f) {
            previous = current
            fade.snapTo(0f)
        }
        current = bitmap
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
 * Live glass (Android 13+): draws what is behind the glass, in root coordinates — the recorded layers of the
 * screen under it. Glass renders a small, blurred copy of that region every frame it changes, so it follows the
 * content behind it in real time. Null: the glass uses the pre-blurred image of [LocalGlassBackdrop].
 */
val LocalGlassLive = staticCompositionLocalOf<(DrawScope.() -> Unit)?> { null }

/**
 * For glass inside a page that scrolls over a fixed backdrop (cached mode only): the page's current scroll offset
 * (px, the same rounded value its layout uses). Such glass samples the backdrop as if the page were at rest, so
 * scrolling does not render it again on every frame; the page only rests where that is exact or off screen.
 */
val LocalGlassScroll = staticCompositionLocalOf<(() -> Float)?> { null }

/** The glass look (Settings › Glass). */
@Immutable
data class GlassStyle(
    /** Live glass (follows the content behind it every frame); off renders it once per backdrop image. */
    val live: Boolean = true,
    /** Blur of the content behind live glass, dp (cached glass uses its pre-blurred image). */
    val blurDp: Float = 6f,
    /** Refraction strength, 1 = as designed. */
    val refraction: Float = 1f,
    /** -1..1, 0 = unchanged. */
    val brightness: Float = 0f,
    /** How frosted (tinted) the glass is; 0 = clear. */
    val frost: Float = 0.141f,
)

val LocalGlassStyle = compositionLocalOf { GlassStyle() }

/**
 * The liquid-glass shaders (AGSL, Android 13+; sources in res/raw): the cached lens (glass_lens) and the live
 * body and rim (glass_body, glass_rim). Each is compiled once; if that fails, or on older Android, surfaces fall
 * back to the layered look.
 */
object LiquidGlass {
    @Volatile
    private var failed = false
    private var lens: String? = null
    private var live: LiveSources? = null

    fun source(context: Context): String? {
        if (failed || Build.VERSION.SDK_INT < 33) return null
        lens?.let { return it }
        load { lens = read(context, R.raw.glass_lens).also { compile(it) } }
        return lens
    }

    fun liveSources(context: Context): LiveSources? {
        if (failed || Build.VERSION.SDK_INT < 33) return null
        live?.let { return it }
        load {
            val body = read(context, R.raw.glass_body).also { compile(it) }
            val rim = read(context, R.raw.glass_rim).also { compile(it) }
            live = LiveSources(body, rim)
        }
        return live
    }

    private inline fun load(block: () -> Unit) {
        try {
            block()
        } catch (_: Exception) {
            failed = true
        }
    }

    private fun read(context: Context, id: Int): String =
        context.resources.openRawResource(id).bufferedReader().use { it.readText() }

    /** A draw-time failure (driver, uniforms) switches every surface to the fallback for the rest of the run. */
    internal fun disable() {
        failed = true
    }

    internal val usable: Boolean get() = !failed

    private fun compile(source: String) {
        if (Build.VERSION.SDK_INT >= 33) RuntimeShader(source)
    }
}

class LiveSources(val body: String, val rim: String)

/**
 * Liquid-glass surface, drawn behind the content.
 *
 * Live (Android 13+, [GlassStyle.live] and a [LocalGlassLive] source): every frame the content behind it changes,
 * the region behind the surface is rendered into a small offscreen layer (half resolution for large surfaces),
 * blurred, and bent by the lens (glass_body.agsl): strong inward refraction in the outermost pixels, dispersion
 * there, vibrancy, an adaptive frost tint and brightness. The rim light (glass_rim.agsl) is drawn on top at full
 * resolution, only along the edge. Nothing runs while the screen is still.
 *
 * Cached: the screen's pre-blurred image seen through the same lens (glass_lens.agsl), rendered once into an
 * offscreen layer per image and re-rendered only when what it shows changes. Without shaders: the plain image
 * crop, the scrims, a tint (stronger when [focus] is 1), an inner top highlight and gradient rims.
 */
fun Modifier.glass(shape: Shape, focus: () -> Float = { 0f }, rim: Boolean = true): Modifier = composed {
    val context = LocalContext.current
    val backdrop = LocalGlassBackdrop.current
    val live = LocalGlassLive.current
    val style = LocalGlassStyle.current
    val scroll = LocalGlassScroll.current
    val liveSources = if (style.live && live != null) LiquidGlass.liveSources(context) else null
    val lens = LiquidGlass.source(context)
    val sampler = remember { BackdropSampler() }
    onGloballyPositioned { sampler.update(it, if (liveSources == null) scroll?.invoke() ?: 0f else 0f) }
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val rtl = layoutDirection == LayoutDirection.Rtl
            val radius = outline.cornerRadius(shape, this)
            val minSide = min(size.width, size.height)
            val bevel = max(2f, min(BEVEL_MAX_PX, minSide * BEVEL_RATIO))
            val rimBand = max(2f, min(RIM_BAND_MAX_PX, minSide * RIM_BAND_RATIO))
            val look = GlassLook(
                path = outline.asPath(),
                radius = radius,
                bevel = bevel,
                rimBand = rimBand,
                rimStroke = Stroke(width = 2f * (min(bevel * 0.3f, rimBand).coerceAtLeast(2f) + 4f)),
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
            val sample = obtainGraphicsLayer()
            onDrawBehind {
                val focusAmount = focus().coerceIn(0f, 1f)
                val drewLive = live != null && liveSources != null &&
                    sampler.drawLive(this, sample, look, live, liveSources, style, focusAmount)
                if (!drewLive) sampler.draw(this, slots, look, backdrop, lens, style, focusAmount)
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

/** Corner radius for the shader's rounded-rectangle distance field. */
private fun Outline.cornerRadius(shape: Shape, density: Density): Float = when (this) {
    is Outline.Rounded -> roundRect.topLeftCornerRadius.x
    is Outline.Rectangle -> 0f
    // Continuous corners start 1.5 x radius from the vertex; a circular corner of 1.25 x radius matches best.
    is Outline.Generic -> (shape as? ContinuousRoundedShape)?.radiusPx(density)?.times(1.25f) ?: 0f
}

// Lens geometry (px values were tuned against the reference implementation; see DESIGN.md). It scales with the
// surface's short side, so small pills are not all rim.
private const val BEVEL_MAX_PX = 48f
private const val BEVEL_RATIO = 0.3f
private const val REFRACT_MAX_PX = 160f
private const val REFRACT_RATIO = 0.7f
private const val REFERENCE_SIDE_DP = 110f
private const val DISPERSION = 0.12f
private const val LIGHT_X = 0.866f
private const val LIGHT_Y = 0.5f
private const val SPECULAR = 1.25f
private const val RIM_BAND_MAX_PX = 6f
private const val RIM_BAND_RATIO = 0.05f
private const val VIBRANCY = 1.5f
private const val FOCUS_TINT = 0.06f

/** Live glass renders surfaces larger than this (px²) at half resolution; the content behind is blurred anyway. */
private const val HALF_RES_AREA = 120_000f

private fun refractPx(minSide: Float, density: Float, strength: Float): Float =
    min(REFRACT_MAX_PX, REFRACT_RATIO * minSide * min(1f, minSide / (REFERENCE_SIDE_DP * density))) * strength

/** A surface's shape, lens geometry and the brushes of the fallback look; rebuilt only when its size changes. */
private class GlassLook(
    val path: Path,
    val radius: Float,
    val bevel: Float,
    val rimBand: Float,
    val rimStroke: Stroke,
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
    val style: GlassStyle,
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
 * Places one surface over what is behind it. The surface's position is observable state, so a surface that moves
 * (scrolling, focus scale) renders its sample again.
 */
private class BackdropSampler {
    private var origin by mutableStateOf(Offset.Zero)
    private var root by mutableStateOf(Size.Zero)
    private val matrix = Matrix()
    private var live: Any? = null // LiveGlass on Android 13+

    class Placement(val origin: Offset, val root: Size)

    fun update(coordinates: LayoutCoordinates, scroll: Float) {
        if (!coordinates.isAttached) return
        val rootSize = coordinates.findRootCoordinates().size
        origin = coordinates.positionInRoot() + Offset(0f, scroll)
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

    /** Live glass; false when it cannot draw (not placed yet, shader failure), so the caller falls back. */
    fun drawLive(
        scope: DrawScope, sample: GraphicsLayer, look: GlassLook, content: DrawScope.() -> Unit, sources: LiveSources,
        style: GlassStyle, focus: Float,
    ): Boolean {
        if (Build.VERSION.SDK_INT < 33 || !LiquidGlass.usable) return false
        val placement = placement() ?: return false
        return try {
            val glass = (live as? LiveGlass) ?: LiveGlass(sources).also { live = it }
            glass.draw(scope, sample, look, content, placement.origin, style, focus)
            true
        } catch (_: Exception) {
            LiquidGlass.disable()
            false
        }
    }

    fun draw(
        scope: DrawScope, slots: Array<GlassSlot>, look: GlassLook, backdrop: GlassBackdrop?, source: String?,
        style: GlassStyle, focus: Float,
    ) {
        val placement = placement()
        val current = backdrop?.current
        val previous = backdrop?.previous
        val fade = backdrop?.fade?.value ?: 1f
        val fading = previous != null && fade < 1f
        val shade = placement?.let { backdrop?.shade?.invoke(it.root) }
        val zoom = backdrop?.zoom ?: 1f
        val liquid = source != null && LiquidGlass.usable && Build.VERSION.SDK_INT >= 33
        val frame = Frame(placement, shade, zoom, focus, look, source, liquid, style)
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
        val style: GlassStyle,
    )

    private fun drawSlot(scope: DrawScope, slots: Array<GlassSlot>, bitmap: ImageBitmap?, other: ImageBitmap?, alpha: Float, frame: Frame) {
        val key = SlotKey(
            bitmap, frame.placement?.origin, frame.placement?.root, scope.size, frame.focus, frame.shade, frame.zoom,
            frame.liquid, frame.style,
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
                lens.draw(scope, look, bitmap, mapBitmap(bitmap, placement, frame.zoom), placement, frame.shade, frame.focus, frame.style)
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

/**
 * Live glass for one surface: the region behind it, recorded into [GraphicsLayer] `sample` at 1/1 or 1/2 scale
 * with a margin for the blur, gets a RenderEffect (blur, then the lens) and is drawn scaled back up; the rim light
 * follows at full resolution as a stroke along the edge. The recording only references the live layers, so it is
 * cheap; the GPU renders the sample whenever the content behind changes.
 */
@RequiresApi(33)
private class LiveGlass(sources: LiveSources) {
    private val body = RuntimeShader(sources.body)
    private val rim = RuntimeShader(sources.rim)
    private val rimBrush = ShaderBrush(rim)
    private var effectKey: Any? = null
    private var effect: androidx.compose.ui.graphics.RenderEffect? = null

    private data class EffectKey(
        val width: Float, val height: Float, val scale: Float, val pad: Float, val blur: Float, val radius: Float,
        val style: GlassStyle, val focus: Float,
    )

    fun draw(
        scope: DrawScope, sample: GraphicsLayer, look: GlassLook, content: DrawScope.() -> Unit, origin: Offset,
        style: GlassStyle, focus: Float,
    ) {
        val width = scope.size.width
        val height = scope.size.height
        if (width < 1f || height < 1f) return
        val k = if (width * height > HALF_RES_AREA) 2f else 1f
        val blur = style.blurDp * scope.density / k
        val pad = ceil(blur * 2f)
        val layerWidth = ceil(width / k + 2f * pad).toInt()
        val layerHeight = ceil(height / k + 2f * pad).toInt()

        sample.record(scope, scope.layoutDirection, IntSize(layerWidth, layerHeight)) {
            translate(pad, pad) {
                scale(1f / k, 1f / k, pivot = Offset.Zero) {
                    translate(-origin.x, -origin.y) { content() }
                }
            }
        }

        val key = EffectKey(width, height, k, pad, blur, look.radius, style, focus)
        if (key != effectKey) {
            val minSide = min(width, height)
            body.setFloatUniform("size", width / k, height / k)
            body.setFloatUniform("inset", pad, pad)
            body.setFloatUniform("radius", look.radius / k)
            body.setFloatUniform("bevel", look.bevel / k)
            body.setFloatUniform("refractPx", refractPx(minSide, scope.density, style.refraction) / k)
            body.setFloatUniform("dispersion", DISPERSION)
            body.setFloatUniform("saturation", VIBRANCY)
            body.setFloatUniform("frost", style.frost)
            body.setFloatUniform("brightness", style.brightness)
            body.setFloatUniform("focusTint", FOCUS_TINT * focus)
            val lens = android.graphics.RenderEffect.createRuntimeShaderEffect(body, "backdrop")
            val chained = if (blur >= 0.5f) {
                android.graphics.RenderEffect.createChainEffect(
                    lens,
                    android.graphics.RenderEffect.createBlurEffect(blur, blur, android.graphics.Shader.TileMode.CLAMP),
                )
            } else {
                lens
            }
            effect = chained.asComposeRenderEffect()

            rim.setFloatUniform("size", width, height)
            rim.setFloatUniform("radius", look.radius)
            rim.setFloatUniform("bevel", look.bevel)
            rim.setFloatUniform("lightDir", LIGHT_X, LIGHT_Y)
            rim.setFloatUniform("spec", SPECULAR * (1f + 0.3f * focus))
            rim.setFloatUniform("rimBandMax", look.rimBand)
            effectKey = key
        }

        sample.renderEffect = effect
        sample.pivotOffset = Offset.Zero
        sample.scaleX = k
        sample.scaleY = k
        sample.topLeft = IntOffset(-(pad * k).roundToInt(), -(pad * k).roundToInt())
        scope.drawLayer(sample)
        scope.drawPath(look.path, rimBrush, style = look.rimStroke)
    }
}

/** A compiled cached-mode lens for one layer; only its input bitmap and uniforms change, so nothing recompiles. */
@RequiresApi(33)
private class Lens(source: String) {
    private val shader = RuntimeShader(source)
    private val brush = ShaderBrush(shader)
    private var bitmap: ImageBitmap? = null
    private var sample: BitmapShader? = null

    fun draw(
        scope: DrawScope, look: GlassLook, image: ImageBitmap, map: Matrix, placement: BackdropSampler.Placement,
        shade: GlassShade?, focus: Float, style: GlassStyle,
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

        val width = scope.size.width
        val height = scope.size.height
        val minSide = min(width, height)
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("radius", look.radius)
        shader.setFloatUniform("bevel", look.bevel)
        shader.setFloatUniform("refractPx", refractPx(minSide, scope.density, style.refraction))
        shader.setFloatUniform("dispersion", DISPERSION)
        shader.setFloatUniform("lightDir", LIGHT_X, LIGHT_Y)
        shader.setFloatUniform("spec", SPECULAR * (1f + 0.3f * focus))
        shader.setFloatUniform("rimBandMax", look.rimBand)
        shader.setFloatUniform("saturation", VIBRANCY)
        shader.setFloatUniform("focusTint", FOCUS_TINT * focus)
        shader.setFloatUniform("frost", style.frost)
        shader.setFloatUniform("brightness", style.brightness)
        shader.setFloatUniform("origin", placement.origin.x, placement.origin.y)
        if (shade != null) {
            shader.setFloatUniform("shadeX", shade.fromX, shade.toX, shade.alphaX, shade.uniform)
            shader.setFloatUniform("shadeY", shade.fromY, shade.toY, shade.alphaY, 0f)
            shader.setFloatUniform("shadeColor", shade.color.red, shade.color.green, shade.color.blue)
        } else {
            shader.setFloatUniform("shadeX", 0f, 1f, 0f, 0f)
            shader.setFloatUniform("shadeY", 0f, 1f, 0f, 0f)
            shader.setFloatUniform("shadeColor", 0f, 0f, 0f)
        }
        scope.drawPath(look.path, brush)
    }
}
