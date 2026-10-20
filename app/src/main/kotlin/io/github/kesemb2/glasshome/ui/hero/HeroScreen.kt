package io.github.kesemb2.glasshome.ui.hero

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kesemb2.glasshome.GlassHomeApp
import io.github.kesemb2.glasshome.R
import io.github.kesemb2.glasshome.data.hero.HeroItem
import io.github.kesemb2.glasshome.data.tmdb.MediaHint
import io.github.kesemb2.glasshome.image.HeroImages
import io.github.kesemb2.glasshome.image.ImageState
import io.github.kesemb2.glasshome.image.ScrimTone
import io.github.kesemb2.glasshome.image.bitmapFor
import io.github.kesemb2.glasshome.image.cachedImageBitmap
import io.github.kesemb2.glasshome.image.rememberImage
import io.github.kesemb2.glasshome.image.rememberLoadedImage
import io.github.kesemb2.glasshome.ui.components.GlassButton
import io.github.kesemb2.glasshome.ui.glass.GlassBackdrop
import io.github.kesemb2.glasshome.ui.glass.GlassShade
import io.github.kesemb2.glasshome.ui.glass.LocalGlassBackdrop
import io.github.kesemb2.glasshome.ui.glass.glass
import io.github.kesemb2.glasshome.ui.glass.glassShadow
import io.github.kesemb2.glasshome.ui.home.AccessState
import io.github.kesemb2.glasshome.ui.home.HeroUiState
import io.github.kesemb2.glasshome.ui.home.HomeBackHandler
import io.github.kesemb2.glasshome.ui.theme.GlassColors
import io.github.kesemb2.glasshome.ui.theme.LocalGlassType
import io.github.kesemb2.glasshome.ui.theme.Shapes
import io.github.kesemb2.glasshome.ui.theme.Spacing
import io.github.kesemb2.glasshome.ui.theme.Springs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val AUTO_ADVANCE_MS = 8_000L
private const val PAUSE_AFTER_KEY_MS = 20_000L

/** With no key pressed for this long (the TV left on), auto-advance stops until the next key press. */
private const val IDLE_STOP_MS = 5 * 60_000L
private const val STAGGER_MS = 40L

/** The selection rests this long before the neighbours' sharp backdrops are prefetched. */
private const val PREFETCH_REST_MS = 300L

/** An image that arrives this soon after the hero appears is shown without fading in. */
private const val INSTANT_MS = 300L
private val PARALLAX = 24.dp

private val LOGO_MAX_WIDTH = 380.dp
private val LOGO_MAX_HEIGHT = 96.dp
private val LOGO_PLACEHOLDER = 72.dp
private val SYNOPSIS_WIDTH = 480.dp
private val PROGRESS_WIDTH = 110.dp

// Hero detail type sizes at 100% (Settings › Text size scales them).
private val META_SIZE = 14.sp
private val BODY_SIZE = 14.sp
private val BODY_LINE = 19.sp
private val CAPTION_SIZE = 12.sp

/** Darkening over the blurred backdrop behind the app grid (and inside glass there). */
const val GRID_DIM = 0.4f

/**
 * Continue Watching, full screen. Left/Right on Play changes the item (backdrop cross-fades with a parallax
 * slide, text staggers in); the hero auto-advances every 8 s only while visible and focused, and pauses for
 * 20 s after any key press. Hold OK on Play for "Remove from Continue Watching".
 *
 * The backdrop stays fixed; the text moves up with [camera] (px) and fades out as [grid] goes to 1, while the
 * backdrop cross-fades to a heavily blurred, darker version for the app grid, which [gridGlass] also gets for the
 * glass there. The whole backdrop is recorded into [backdropLayer], which live glass draws again behind itself.
 * The text ends [contentBottom] above the screen's bottom edge (above the favorites shelf).
 * [scrimColor] (Settings › Gradient colour, one of [ScrimColors.KEYS]) is what the scrims and that darkening fade to.
 * [buttonScale] sizes Play (Settings › Play button size); [diagnostics] (with the performance overlay) adds the raw
 * Watch Next numbers of the item under its details.
 * [focusModifier] goes on Play (or Allow), before its focus target.
 */
@Composable
fun HeroScreen(
    state: HeroUiState,
    autoAdvance: Boolean,
    lastKeyAt: () -> Long,
    camera: () -> Float,
    grid: () -> Float,
    contentBottom: Dp,
    logoScale: Float,
    textScale: Float,
    buttonScale: Float,
    scrimColor: String,
    diagnostics: Boolean,
    gridGlass: GlassBackdrop,
    backdropLayer: GraphicsLayer,
    focusModifier: Modifier,
    onPlay: (HeroItem) -> Unit,
    onDismiss: (HeroItem) -> Unit,
    onRequestAccess: () -> Unit,
) {
    val items = state.items
    val context = LocalContext.current
    val metrics = context.resources.displayMetrics
    val widthPx = min(metrics.widthPixels, HeroImages.BACKDROP_MAX_WIDTH)
    val heightPx = (widthPx * metrics.heightPixels / metrics.widthPixels.toFloat()).roundToInt()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    var selectedKey by remember { mutableStateOf<String?>(null) }
    val index = items.indexOfFirst { it.key == selectedKey }.takeIf { it >= 0 } ?: 0
    val item = items.getOrNull(index)
    var forward by remember { mutableStateOf(true) }
    var heroFocused by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    fun step(delta: Int) {
        if (items.size < 2) return
        forward = delta > 0
        selectedKey = items[(index + delta).mod(items.size)].key
    }

    // The current item's backdrop: the soft glass source (instant, also what glass surfaces refract) and the
    // sharp image.
    val glass = LocalGlassBackdrop.current
    val backdropUrl = item?.backdropUrl
    val soft by rememberImage(backdropUrl) { ctx, url -> HeroImages.blurred(ctx, url) }
    val sharp by rememberLoadedImage(
        backdropUrl,
        cached = { loader, ctx, url -> loader.cachedImageBitmap(HeroImages.backdrop(ctx, url, widthPx, heightPx)) },
    ) { loader, ctx, url -> HeroImages.loadBackdrop(loader, ctx, url, widthPx, heightPx) }
    val dim by rememberImage(backdropUrl) { ctx, url -> HeroImages.dimmed(ctx, url) }
    // Glass refracts what is behind it: the soft source over the hero, the grid's blur in the grid.
    LaunchedEffect(soft) {
        when (val b = soft) {
            is ImageState.Ready -> glass?.show(b.bitmap)
            ImageState.Failed -> glass?.show(null)
            ImageState.Loading -> Unit
        }
    }
    LaunchedEffect(dim) {
        when (val b = dim) {
            is ImageState.Ready -> gridGlass.show(b.bitmap)
            ImageState.Failed -> gridGlass.show(null)
            ImageState.Loading -> Unit
        }
    }

    // Prefetch: the small images (soft backdrop, the grid's, the logo) of two items either way at once, so fast key
    // presses have something to show; the sharp backdrops of the direct neighbours once the selection rests for a
    // moment, cancelled when it moves on (they are the expensive ones).
    LaunchedEffect(index, items) {
        if (items.size < 2) return@LaunchedEffect
        val loader = (context.applicationContext as GlassHomeApp).imageLoader
        fun around(vararg deltas: Int) =
            deltas.map { items[(index + it).mod(items.size)] }.filter { it.key != items[index].key }.distinctBy { it.key }
        for (neighbour in around(1, -1, 2, -2)) {
            neighbour.backdropUrl?.let {
                loader.enqueue(HeroImages.blurred(context, it))
                loader.enqueue(HeroImages.dimmed(context, it))
            }
            neighbour.logoUrl?.let { loader.enqueue(HeroImages.logo(context, it)) }
        }
        delay(PREFETCH_REST_MS)
        val large = around(1, -1).mapNotNull { n ->
            n.backdropUrl?.let { loader.enqueue(HeroImages.backdrop(context, it, widthPx, heightPx)) }
        }
        try {
            awaitCancellation()
        } finally {
            large.forEach { it.dispose() }
        }
    }

    // Auto-advance while visible and focused: 8 s after the item appeared and 20 s after the last key press.
    val visibleSince = remember { SystemClock.uptimeMillis() }
    LaunchedEffect(autoAdvance, heroFocused, menuOpen, index, items.size) {
        if (!autoAdvance || !heroFocused || menuOpen || items.size < 2) return@LaunchedEffect
        val shownAt = SystemClock.uptimeMillis()
        while (true) {
            val due = max(shownAt + AUTO_ADVANCE_MS, lastKeyAt() + PAUSE_AFTER_KEY_MS)
            val now = SystemClock.uptimeMillis()
            if (now >= due) break
            delay(due - now)
        }
        if (SystemClock.uptimeMillis() - max(lastKeyAt(), visibleSince) > IDLE_STOP_MS) return@LaunchedEffect
        step(+1)
    }

    // The scrims' colour: a fixed one, or a near-black tinted by this item's image (kept from the previous item
    // until this one's image is in). It changes with the backdrop's cross-fade.
    val tone by produceState(Color.Black, scrimColor, dim) {
        value = ScrimColors.fixed(scrimColor) ?: when (val b = dim) {
            is ImageState.Ready -> withContext(Dispatchers.Default) { Color(ScrimTone.of(b.bitmap.pixels())) }
            ImageState.Failed -> Color.Black
            ImageState.Loading -> return@produceState
        }
    }
    val scrimState = animateColorAsState(tone, Springs.content(), label = "scrim")
    val scrim = remember(scrimState) { { scrimState.value } }

    // Glass surfaces get the same scrims and the same overscan zoom as the backdrop behind them.
    val overscanPx = with(LocalDensity.current) { PARALLAX.toPx() }
    DisposableEffect(glass, gridGlass, rtl, overscanPx) {
        glass?.shade = { root -> HeroScrim.shade(root.width, root.height, rtl, scrim()) }
        glass?.zoom = 1f + 2f * overscanPx / metrics.widthPixels
        gridGlass.shade = { GlassShade(0f, 1f, 0f, 0f, 1f, 0f, uniform = GRID_DIM, color = scrim()) }
        onDispose {
            glass?.shade = null
            glass?.zoom = 1f
            gridGlass.shade = null
        }
    }

    HomeBackHandler(enabled = menuOpen) { menuOpen = false }

    Box(
        Modifier
            .fillMaxSize()
            // Its own layer, so the backdrop animating re-records only this.
            .graphicsLayer()
            .drawWithContent {
                backdropLayer.record { this@drawWithContent.drawContent() }
                drawLayer(backdropLayer)
            },
    ) {
        HeroBackdrop(item?.key, backdropUrl, sharp.bitmapFor(backdropUrl), soft.bitmapFor(backdropUrl), forward, rtl, overscanPx) {
            grid() >= 1f
        }
        Scrims(rtl, scrim) { 1f - grid() }
        GridBackdrop(dim.bitmapFor(backdropUrl), scrim, grid)
    }

    // The text scrolls away with the camera and fades out over the grid.
    Box(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, -camera().roundToInt()) }
            .graphicsLayer {
                alpha = (1f - grid()).coerceIn(0f, 1f)
                // Fade each element on its own: no full-screen offscreen buffer while fading.
                compositingStrategy = CompositingStrategy.ModulateAlpha
            },
    ) {
        if (item != null) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = Spacing.ScreenHorizontal, bottom = contentBottom),
            ) {
                AnimatedContent(
                    targetState = item,
                    transitionSpec = { (EnterTransition.None togetherWith fadeOut(Springs.exit())).using(null) },
                    // AnimatedContent aligns its children as if the layout ran left to right, so in Hebrew the
                    // incoming text sat on the left until the outgoing one left. Absolute alignment works both ways.
                    contentAlignment = if (rtl) AbsoluteAlignment.BottomRight else AbsoluteAlignment.BottomLeft,
                    contentKey = { it.key },
                    label = "heroText",
                ) { target -> HeroText(target, rtl, heroFocused, logoScale, textScale, diagnostics) }
                Spacer(Modifier.height(Spacing.XL))
                GlassButton(
                    text = stringResource(if (item.progress != null) R.string.hero_resume else R.string.hero_play),
                    icon = R.drawable.ic_play,
                    onClick = { onPlay(item) },
                    onLongClick = { menuOpen = true },
                    scale = buttonScale,
                    modifier = focusModifier
                        .onFocusChanged { heroFocused = it.isFocused }
                        .onKeyEvent { event ->
                            val next = if (rtl) Key.DirectionLeft else Key.DirectionRight
                            val previous = if (rtl) Key.DirectionRight else Key.DirectionLeft
                            when (event.key) {
                                next, previous -> {
                                    if (event.type == KeyEventType.KeyDown) step(if (event.key == next) 1 else -1)
                                    items.size > 1
                                }
                                else -> false
                            }
                        },
                )
            }
            if (items.size > 1) {
                PageDots(
                    count = items.size,
                    selected = index,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        // Level with Play's middle (Play is 48 dp × its size high, the dots 20 dp).
                        .padding(end = Spacing.ScreenHorizontal, bottom = contentBottom + 24.dp * buttonScale - 10.dp),
                )
            }
        } else if (state.loaded) {
            EmptyHero(state, onRequestAccess, focusModifier, contentBottom, Modifier.align(Alignment.BottomStart))
        }

        if (menuOpen && item != null) {
            HeroMenu(
                rtl = rtl,
                onRemove = {
                    menuOpen = false
                    onDismiss(item)
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = Spacing.ScreenHorizontal, bottom = contentBottom + 48.dp * buttonScale + 12.dp),
            )
        }
    }
}

/** One item on the backdrop stage: its soft image, shown first, and its sharp one fading in over it. */
@Stable
private class BackdropLayer(val key: String?, val url: String?, soft: ImageBitmap?, sharp: ImageBitmap?, offset: Float) {
    var soft by mutableStateOf(soft)
    var sharp by mutableStateOf(sharp)
    val sharpness = Animatable(if (sharp != null) 1f else 0f)
    var alpha by mutableFloatStateOf(0f)
    var offset by mutableFloatStateOf(offset)
}

/** What the stage should show: an item's images, as far as they have loaded. */
private data class StageTarget(
    val key: String?,
    val url: String?,
    val soft: ImageBitmap?,
    val sharp: ImageBitmap?,
    val forward: Boolean,
) {
    val ready: Boolean get() = soft != null || sharp != null

    fun shownBy(layer: BackdropLayer?): Boolean = layer != null && layer.key == key && layer.url == url
}

/**
 * Full-screen backdrop stage. A new item slides in by [PARALLAX] in the direction of travel while cross-fading
 * over the old one, which slides out; its soft image appears first and the sharp one fades in over it once
 * decoded. Until the new item has any image the previous one stays, so the screen never flashes black. Images
 * are drawn [overscanPx] wider than the screen on each side so the slide never uncovers an edge.
 *
 * Item changes run one at a time. When a newer item is waiting, the running change finishes quickly and only the
 * newest waiting item is shown next, so fast key presses never stack half-faded images (each one a full-screen
 * pass) and the stage keeps up smoothly.
 */
@Composable
private fun HeroBackdrop(
    key: String?,
    url: String?,
    sharp: ImageBitmap?,
    soft: ImageBitmap?,
    forward: Boolean,
    rtl: Boolean,
    overscanPx: Float,
    covered: () -> Boolean,
) {
    val layers = remember { mutableStateListOf<BackdropLayer>() }
    val target by rememberUpdatedState(StageTarget(key, url, soft, sharp, forward))
    val direction by rememberUpdatedState(if (rtl) -1f else 1f)
    val overscan by rememberUpdatedState(overscanPx)
    val scope = rememberCoroutineScope()
    val composedAt = remember { SystemClock.uptimeMillis() }

    // The shown item's sharp image (or a soft one that came late) fills in on its own layer.
    LaunchedEffect(Unit) {
        snapshotFlow { target }.collect { t ->
            val layer = layers.lastOrNull()?.takeIf { t.shownBy(it) } ?: return@collect
            if (layer.soft == null) layer.soft = t.soft
            if (layer.sharp == null && t.sharp != null) {
                layer.sharp = t.sharp
                scope.launch { layer.sharpness.animateTo(1f, Springs.content()) }
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val t = snapshotFlow { target }.first { it.ready && !it.shownBy(layers.lastOrNull()) }
            val top = layers.lastOrNull()
            val layer = BackdropLayer(t.key, t.url, t.soft, t.sharp, 0f)
            // The first image right as the screen appears (back from an app, from the memory cache): no fade in.
            if (top == null && SystemClock.uptimeMillis() - composedAt < INSTANT_MS) {
                layer.alpha = 1f
                layers.add(layer)
                continue
            }
            val newItem = top != null && top.key != t.key
            val travel = if (newItem) (if (t.forward) 1f else -1f) * direction * overscan else 0f
            val below = layers.toList()
            val starts = FloatArray(below.size) { below[it].offset }
            layer.offset = travel
            layers.add(layer)
            val progress = Animatable(0f)
            val step: Animatable<Float, AnimationVector1D>.() -> Unit = {
                layer.alpha = value
                layer.offset = travel * (1f - value)
                for (i in below.indices) below[i].offset = starts[i] + (-travel - starts[i]) * value
            }
            coroutineScope {
                val hurry = launch {
                    snapshotFlow { target }.first { it.ready && it.key != t.key }
                    progress.animateTo(1f, Springs.hurry(), block = step)
                }
                try {
                    progress.animateTo(1f, Springs.content(), block = step)
                    hurry.cancel()
                } catch (e: CancellationException) {
                    ensureActive() // Cancelled ourselves: stop. Otherwise the hurry took over; let it finish.
                    hurry.join()
                }
            }
            layers.removeAll(below)
        }
    }

    Canvas(Modifier.fillMaxSize()) {
        // Behind the grid the blurred backdrop covers this completely; skip the full-screen draws.
        if (covered()) return@Canvas
        layers.forEach { layer ->
            val alpha = layer.alpha.coerceIn(0f, 1f)
            if (alpha <= 0f) return@forEach
            val sharpImage = layer.sharp
            val sharpness = if (sharpImage != null) layer.sharpness.value.coerceIn(0f, 1f) else 0f
            val softImage = layer.soft
            if (sharpness < 1f && softImage != null) drawCover(softImage, alpha, layer.offset, overscanPx)
            if (sharpImage != null && sharpness > 0f) drawCover(sharpImage, alpha * sharpness, layer.offset, overscanPx)
        }
    }
}

private fun DrawScope.drawCover(image: ImageBitmap, alpha: Float, offsetX: Float, overscan: Float) {
    if (alpha <= 0f) return
    val scale = max((size.width + 2 * overscan) / image.width, size.height / image.height)
    val width = image.width * scale
    val height = image.height * scale
    drawImage(
        image = image,
        dstOffset = IntOffset(((size.width - width) / 2f + offsetX).roundToInt(), ((size.height - height) / 2f).roundToInt()),
        dstSize = IntSize(width.roundToInt(), height.roundToInt()),
        alpha = alpha.coerceIn(0f, 1f),
        filterQuality = FilterQuality.Low,
    )
}

/**
 * Start-side and bottom scrims keep the text legible over any backdrop; they fade out over the grid. Both are
 * baked into one small bitmap drawn stretched (smooth ramps survive the scaling), so they cost one pass instead
 * of two full-screen gradients: the TV's memory bandwidth, not its GPU, is the limit at 1080p. The bitmap is
 * black; another [color] tints it as it is drawn.
 */
@Composable
private fun Scrims(rtl: Boolean, color: () -> Color, alpha: () -> Float) {
    Spacer(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val scrim = HeroScrim.bitmap(HeroScrim.shade(size.width, size.height, rtl), size)
                val dst = IntSize(size.width.roundToInt(), size.height.roundToInt())
                onDrawBehind {
                    val a = alpha().coerceIn(0f, 1f)
                    if (a <= 0f) return@onDrawBehind
                    val c = color()
                    val tint = if (c == Color.Black) null else ColorFilter.tint(c, BlendMode.SrcIn)
                    drawImage(scrim, dstSize = dst, alpha = a, colorFilter = tint, filterQuality = FilterQuality.Low)
                }
            },
    )
}

/**
 * Behind the app grid: the backdrop heavily blurred and darkened towards [color] (one pass), faded in by [amount].
 */
@Composable
private fun GridBackdrop(image: ImageBitmap?, color: () -> Color, amount: () -> Float) {
    Spacer(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val a = amount().coerceIn(0f, 1f)
                if (a <= 0f) return@drawBehind
                val c = color()
                if (image != null) {
                    // image * (1 - dim) + colour * dim
                    val darken = ColorFilter.lighting(
                        multiply = Color(1f - GRID_DIM, 1f - GRID_DIM, 1f - GRID_DIM),
                        add = Color(c.red * GRID_DIM, c.green * GRID_DIM, c.blue * GRID_DIM),
                    )
                    drawImage(
                        image = image,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        alpha = a,
                        colorFilter = darken,
                        filterQuality = FilterQuality.Low,
                    )
                } else {
                    drawRect(c, alpha = GRID_DIM * a)
                }
            },
    )
}

/** Start-side (85% over 60% of the width) and bottom (90% over 55% of the height) scrims of [GlassShade.color]. */
private object HeroScrim {
    private const val BITMAP_WIDTH = 192

    fun shade(width: Float, height: Float, rtl: Boolean, color: Color = Color.Black) = GlassShade(
        fromX = if (rtl) width else 0f,
        toX = if (rtl) width * 0.4f else width * 0.6f,
        alphaX = 0.85f,
        fromY = height * 0.45f,
        toY = height,
        alphaY = 0.9f,
        color = color,
    )

    /** Both ramps of [shade] as one black bitmap with the combined alpha, at a small size with [size]'s shape. */
    fun bitmap(shade: GlassShade, size: Size): ImageBitmap {
        val w = BITMAP_WIDTH
        val h = max(1, (BITMAP_WIDTH * size.height / size.width).roundToInt())
        val sx = size.width / w
        val sy = size.height / h
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            // Sample at pixel centres in screen coordinates, the same formula the glass shader uses.
            val ay = shade.alphaY * ((((y + 0.5f) * sy) - shade.fromY) / (shade.toY - shade.fromY)).coerceIn(0f, 1f)
            for (x in 0 until w) {
                val ax = shade.alphaX * ((((x + 0.5f) * sx) - shade.toX) / (shade.fromX - shade.toX)).coerceIn(0f, 1f)
                val a = 1f - (1f - ax) * (1f - ay)
                pixels[y * w + x] = (a * 255f).roundToInt().coerceIn(0, 255) shl 24
            }
        }
        return android.graphics.Bitmap.createBitmap(pixels, w, h, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

private fun ImageBitmap.pixels(): IntArray = IntArray(width * height).also { readPixels(it) }

@Composable
private fun rememberStagger(order: Int): Animatable<Float, AnimationVector1D> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(order * STAGGER_MS)
        progress.animateTo(1f, Springs.content())
    }
    return progress
}

private fun Modifier.stagger(progress: Animatable<Float, AnimationVector1D>, offsetPx: Float): Modifier =
    graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationX = (1f - progress.value) * offsetPx
    }

/**
 * Logo and the episode or movie line; the synopsis and time left appear under them only while Play is focused
 * ([showDetails]). Everything fades/slides in with a short stagger when the item changes. Sizes follow Settings
 * ([logoScale] for the logo or title, [textScale] for the rest).
 */
@Composable
private fun HeroText(
    item: HeroItem,
    rtl: Boolean,
    showDetails: Boolean,
    logoScale: Float,
    textScale: Float,
    diagnostics: Boolean,
) {
    val type = LocalGlassType.current
    val offsetPx = with(LocalDensity.current) { 18.dp.toPx() } * (if (rtl) -1f else 1f)
    val metaStyle = type.callout.copy(color = GlassColors.TextSecondary, fontSize = META_SIZE * textScale)
    // Aligned to the layout's start edge even when the text runs the other way (an English synopsis in Hebrew).
    val edge = if (rtl) TextAlign.Right else TextAlign.Left
    val bodyStyle = type.body.copy(
        color = GlassColors.TextPrimary.copy(alpha = 0.8f),
        fontSize = BODY_SIZE * textScale,
        lineHeight = BODY_LINE * textScale,
        textAlign = edge,
    )
    val captionStyle = type.caption.copy(color = GlassColors.TextSecondary, fontSize = CAPTION_SIZE * textScale)

    Column {
        HeroLogo(item, logoScale, edge, Modifier.stagger(rememberStagger(0), offsetPx))

        val meta = buildList {
            item.season?.let { add(stringResource(R.string.hero_season, it)) }
            item.episode?.let { add(stringResource(R.string.hero_episode, it)) }
            item.episodeTitle?.let { add(it) }
            if (item.season == null && item.episode == null) {
                item.year?.let { add(it) }
                if (item.media == MediaHint.MOVIE) {
                    HeroFormat.duration(item.runtimeMillis)?.let { (h, m) ->
                        add(
                            when {
                                h > 0 && m > 0 -> stringResource(R.string.hero_duration_h_min, h, m)
                                h > 0 -> stringResource(R.string.hero_duration_h, h)
                                else -> stringResource(R.string.hero_duration_min, m)
                            },
                        )
                    }
                }
            }
        }
        if (meta.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            BasicText(
                text = meta.joinToString("  ·  "),
                modifier = Modifier.stagger(rememberStagger(1), offsetPx).widthIn(max = SYNOPSIS_WIDTH),
                style = metaStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (diagnostics) {
            // What the app wrote to Watch Next, as is (to tell a wrong "time left" from the app's own numbers).
            val e = item.entry
            BasicText(
                text = "${e.packageName} · type ${e.watchNextType ?: "-"} · position ${e.positionMillis} · " +
                    "duration ${e.durationMillis}",
                modifier = Modifier.widthIn(max = SYNOPSIS_WIDTH),
                style = type.caption.copy(color = GlassColors.TextTertiary, textAlign = edge, textDirection = TextDirection.Ltr),
            )
        }

        val progress = item.progress
        val remaining = item.remainingMillis
        val hasDetails = item.synopsis != null || progress != null
        AnimatedVisibility(
            visible = showDetails && hasDetails,
            enter = fadeIn(Springs.content()) + expandVertically(Springs.content(IntSize.VisibilityThreshold)),
            exit = fadeOut(Springs.exit()) + shrinkVertically(Springs.content(IntSize.VisibilityThreshold)),
        ) {
            Column {
                item.synopsis?.let {
                    Spacer(Modifier.height(Spacing.S))
                    BasicText(
                        text = it,
                        modifier = Modifier.stagger(rememberStagger(2), offsetPx).width(SYNOPSIS_WIDTH),
                        style = bodyStyle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // The bar whenever the fraction watched is known; the time left only when the runtime is too.
                if (progress != null) {
                    Spacer(Modifier.height(Spacing.M))
                    Row(
                        Modifier.stagger(rememberStagger(3), offsetPx),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProgressBar(progress, rtl, PROGRESS_WIDTH * textScale)
                        if (remaining != null) {
                            Spacer(Modifier.width(8.dp))
                            val (h, m) = HeroFormat.remaining(remaining)
                            BasicText(
                                text = when {
                                    h > 0 && m > 0 -> stringResource(R.string.hero_left_h_min, h, m)
                                    h > 0 -> stringResource(R.string.hero_left_h, h)
                                    else -> stringResource(R.string.hero_left_min, m)
                                },
                                style = captionStyle,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressBar(progress: Float, rtl: Boolean, width: Dp) {
    Spacer(
        Modifier
            .width(width)
            .height(3.dp)
            .drawBehind {
                val radius = CornerRadius(size.height / 2f)
                drawRoundRect(GlassColors.ProgressTrack, cornerRadius = radius)
                val filled = size.width * progress
                drawRoundRect(
                    GlassColors.ProgressFill,
                    topLeft = androidx.compose.ui.geometry.Offset(if (rtl) size.width - filled else 0f, 0f),
                    size = androidx.compose.ui.geometry.Size(filled, size.height),
                    cornerRadius = radius,
                )
            },
    )
}

/** Logo fitted into 380 x 96 dp x [scale], start-aligned; the title as large text when there is no logo. */
@Composable
private fun HeroLogo(item: HeroItem, scale: Float, edge: TextAlign, modifier: Modifier) {
    val type = LocalGlassType.current
    val logo by rememberImage(item.logoUrl) { ctx, url -> HeroImages.logo(ctx, url) }
    when (val state = logo) {
        is ImageState.Ready -> LogoImage(state.bitmap, LOGO_MAX_WIDTH * scale, LOGO_MAX_HEIGHT * scale, modifier)
        ImageState.Loading -> Spacer(modifier.height(LOGO_PLACEHOLDER * scale))
        // Until TMDB has answered, keep the logo's space instead of flashing the title.
        ImageState.Failed -> if (!item.enriched) Spacer(modifier.height(LOGO_PLACEHOLDER * scale)) else BasicText(
            text = item.title,
            modifier = modifier.widthIn(max = SYNOPSIS_WIDTH),
            style = type.display.copy(color = GlassColors.TextPrimary, fontSize = type.display.fontSize * scale, textAlign = edge),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LogoImage(bitmap: ImageBitmap, maxWidth: Dp, maxHeight: Dp, modifier: Modifier) {
    val density = LocalDensity.current
    val pad = HeroImages.LOGO_PAD_PX
    val contentWidth = (bitmap.width - 2 * pad).coerceAtLeast(1)
    val contentHeight = (bitmap.height - 2 * pad).coerceAtLeast(1)
    val scale = with(density) {
        min(maxWidth.toPx() / contentWidth, maxHeight.toPx() / contentHeight)
    }
    val width = with(density) { (contentWidth * scale).toDp() }
    val height = with(density) { (contentHeight * scale).toDp() }
    // The padded bitmap (with its glow) overflows the layout box by `pad` on every side.
    Canvas(modifier.size(width, height)) {
        drawImage(
            image = bitmap,
            dstOffset = IntOffset((-pad * scale).roundToInt(), (-pad * scale).roundToInt()),
            dstSize = IntSize((bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt()),
            filterQuality = FilterQuality.Medium,
        )
    }
}

@Composable
private fun PageDots(count: Int, selected: Int, modifier: Modifier) {
    Row(
        modifier
            .glassShadow(Shapes.Pill, 6.dp)
            .glass(Shapes.Pill)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val isSelected = i == selected
            val width by animateDpAsState(if (isSelected) 16.dp else 6.dp, Springs.focus(), label = "dotWidth")
            val alpha by animateFloatAsState(if (isSelected) 1f else 0.4f, Springs.focus(), label = "dotAlpha")
            Box(
                Modifier
                    .size(width = width, height = 6.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .background(Color.White, Shapes.Pill),
            )
        }
    }
}

/** Glass menu that scales in from the Play button. */
@Composable
private fun HeroMenu(rtl: Boolean, onRemove: () -> Unit, modifier: Modifier) {
    val focus = remember { FocusRequester() }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        appear.animateTo(1f, Springs.overlay())
    }
    Column(
        modifier
            .graphicsLayer {
                val s = 0.85f + 0.15f * appear.value
                scaleX = s
                scaleY = s
                alpha = appear.value.coerceIn(0f, 1f)
                transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 1f)
            }
            .glassShadow(Shapes.Menu, 24.dp)
            .glass(Shapes.Menu)
            .padding(8.dp)
            // Keep focus inside the menu while it is open.
            .onPreviewKeyEvent {
                it.key == Key.DirectionLeft || it.key == Key.DirectionRight ||
                    it.key == Key.DirectionUp || it.key == Key.DirectionDown
            },
    ) {
        GlassButton(
            text = stringResource(R.string.hero_remove),
            icon = R.drawable.ic_close,
            onClick = onRemove,
            modifier = Modifier.focusRequester(focus),
            shape = Shapes.Menu,
            withGlass = false,
        )
    }
}

@Composable
private fun EmptyHero(
    state: HeroUiState,
    onRequestAccess: () -> Unit,
    focusModifier: Modifier,
    contentBottom: Dp,
    modifier: Modifier,
) {
    val type = LocalGlassType.current
    val (title, body) = when (state.access) {
        AccessState.Requestable -> R.string.empty_access_title to R.string.empty_access_body
        AccessState.Blocked -> R.string.empty_blocked_title to R.string.empty_blocked_body
        else -> R.string.empty_none_title to R.string.empty_none_body
    }
    Column(modifier.padding(start = Spacing.ScreenHorizontal, bottom = contentBottom).width(SYNOPSIS_WIDTH)) {
        BasicText(stringResource(title), style = type.title1.copy(color = GlassColors.TextPrimary))
        Spacer(Modifier.height(Spacing.S))
        BasicText(stringResource(body), style = type.body.copy(color = GlassColors.TextSecondary))
        if (state.access == AccessState.Requestable) {
            Spacer(Modifier.height(Spacing.XL))
            GlassButton(
                text = stringResource(R.string.empty_allow),
                icon = null,
                onClick = onRequestAccess,
                modifier = focusModifier,
            )
        }
        Spacer(Modifier.height(Spacing.XL))
        // Diagnostics for the first runs on a new TV: which TV-listing permissions exist and are granted.
        val details = buildString {
            append(stringResource(R.string.diag_rows, state.providerRows))
            state.error?.let { append(" · ").append(it) }
            state.permissions.forEach { p ->
                append('\n').append(p.name.substringAfterLast('.')).append(": ")
                    .append(if (p.granted) "granted" else "not granted").append(" (").append(p.protection).append(')')
            }
        }
        BasicText(details, style = type.caption.copy(color = GlassColors.TextTertiary))
    }
}
