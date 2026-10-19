package io.github.kesemb2.glasshome.ui.hero

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.kesemb2.glasshome.GlassHomeApp
import io.github.kesemb2.glasshome.R
import io.github.kesemb2.glasshome.data.hero.HeroItem
import io.github.kesemb2.glasshome.data.tmdb.MediaHint
import io.github.kesemb2.glasshome.image.HeroImages
import io.github.kesemb2.glasshome.image.ImageState
import io.github.kesemb2.glasshome.image.rememberImage
import io.github.kesemb2.glasshome.ui.components.GlassButton
import io.github.kesemb2.glasshome.ui.glass.LocalGlassBackdrop
import io.github.kesemb2.glasshome.ui.glass.glass
import io.github.kesemb2.glasshome.ui.glass.glassShadow
import io.github.kesemb2.glasshome.ui.home.AccessState
import io.github.kesemb2.glasshome.ui.home.HeroUiState
import io.github.kesemb2.glasshome.ui.theme.GlassColors
import io.github.kesemb2.glasshome.ui.theme.LocalGlassType
import io.github.kesemb2.glasshome.ui.theme.Shapes
import io.github.kesemb2.glasshome.ui.theme.Spacing
import io.github.kesemb2.glasshome.ui.theme.Springs
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val AUTO_ADVANCE_MS = 8_000L
private const val PAUSE_AFTER_KEY_MS = 20_000L
private const val STAGGER_MS = 40L
private val PARALLAX = 24.dp
private val CONTENT_BOTTOM = 64.dp
private val LOGO_MAX_WIDTH = 380.dp
private val LOGO_MAX_HEIGHT = 120.dp
private val LOGO_PLACEHOLDER = 72.dp
private val SYNOPSIS_WIDTH = 520.dp

/**
 * Continue Watching, full screen. Left/Right on Play changes the item (backdrop cross-fades with a parallax
 * slide, text staggers in); the hero auto-advances every 8 s only while visible and focused, and pauses for
 * 20 s after any key press. Hold OK on Play for "Remove from Continue Watching".
 */
@Composable
fun HeroScreen(
    state: HeroUiState,
    autoAdvance: Boolean,
    lastKeyAt: () -> Long,
    onPlay: (HeroItem) -> Unit,
    onDismiss: (HeroItem) -> Unit,
    onRequestAccess: () -> Unit,
) {
    val items = state.items
    val context = LocalContext.current
    val metrics = context.resources.displayMetrics
    val widthPx = min(metrics.widthPixels, 1920)
    val heightPx = (widthPx * metrics.heightPixels / metrics.widthPixels.toFloat()).roundToInt()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    var selectedKey by remember { mutableStateOf<String?>(null) }
    val index = items.indexOfFirst { it.key == selectedKey }.takeIf { it >= 0 } ?: 0
    val item = items.getOrNull(index)
    var forward by remember { mutableStateOf(true) }
    var heroFocused by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val playFocus = remember { FocusRequester() }

    fun step(delta: Int) {
        if (items.size < 2) return
        forward = delta > 0
        selectedKey = items[(index + delta).mod(items.size)].key
    }

    // Glass surfaces sample the blurred version of whatever backdrop is showing.
    val glass = LocalGlassBackdrop.current
    val blurred by rememberImage(item?.backdropUrl) { ctx, url -> HeroImages.blurred(ctx, url) }
    LaunchedEffect(blurred) {
        when (val b = blurred) {
            is ImageState.Ready -> glass?.show(b.bitmap)
            ImageState.Failed -> glass?.show(null)
            ImageState.Loading -> Unit
        }
    }

    // Prefetch only the neighbours.
    LaunchedEffect(index, items) {
        if (items.size < 2) return@LaunchedEffect
        val loader = (context.applicationContext as GlassHomeApp).imageLoader
        for (delta in intArrayOf(1, -1)) {
            val neighbour = items[(index + delta).mod(items.size)]
            neighbour.backdropUrl?.let {
                loader.enqueue(HeroImages.backdrop(context, it, widthPx, heightPx))
                loader.enqueue(HeroImages.blurred(context, it))
            }
            neighbour.logoUrl?.let { loader.enqueue(HeroImages.logo(context, it)) }
        }
    }

    // Auto-advance while visible and focused: 8 s after the item appeared and 20 s after the last key press.
    LaunchedEffect(autoAdvance, heroFocused, menuOpen, index, items.size) {
        if (!autoAdvance || !heroFocused || menuOpen || items.size < 2) return@LaunchedEffect
        val shownAt = SystemClock.uptimeMillis()
        while (true) {
            val due = max(shownAt + AUTO_ADVANCE_MS, lastKeyAt() + PAUSE_AFTER_KEY_MS)
            val now = SystemClock.uptimeMillis()
            if (now >= due) break
            delay(due - now)
        }
        step(+1)
    }

    // Glass surfaces get the same scrims as the screen behind them.
    DisposableEffect(glass, rtl) {
        glass?.shade = { root, origin -> HeroScrim.brushes(root.width, root.height, rtl, origin.x, origin.y) }
        onDispose { glass?.shade = null }
    }

    LaunchedEffect(menuOpen, item != null) {
        if (!menuOpen && item != null) playFocus.requestFocus()
    }
    BackHandler(enabled = menuOpen) { menuOpen = false }

    Box(Modifier.fillMaxSize()) {
        HeroBackdrop(item, forward, rtl, widthPx, heightPx)
        Scrims(rtl)

        if (item != null) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = Spacing.ScreenHorizontal, bottom = CONTENT_BOTTOM),
            ) {
                AnimatedContent(
                    targetState = item,
                    transitionSpec = { (EnterTransition.None togetherWith fadeOut(Springs.exit())).using(null) },
                    contentAlignment = Alignment.BottomStart,
                    contentKey = { it.key },
                    label = "heroText",
                ) { target -> HeroText(target, rtl) }
                Spacer(Modifier.height(Spacing.XL))
                GlassButton(
                    text = stringResource(if (item.progress != null) R.string.hero_resume else R.string.hero_play),
                    icon = R.drawable.ic_play,
                    onClick = { onPlay(item) },
                    onLongClick = { menuOpen = true },
                    modifier = Modifier
                        .focusRequester(playFocus)
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
                        .align(Alignment.BottomCenter)
                        .padding(bottom = Spacing.ScreenVertical),
                )
            }
        } else if (state.loaded) {
            EmptyHero(state, onRequestAccess, Modifier.align(Alignment.BottomStart))
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
                    .padding(start = Spacing.ScreenHorizontal, bottom = CONTENT_BOTTOM + 60.dp),
            )
        }
    }
}

@Composable
private fun HeroBackdrop(item: HeroItem?, forward: Boolean, rtl: Boolean, widthPx: Int, heightPx: Int) {
    val parallax = with(LocalDensity.current) { PARALLAX.roundToPx() }
    AnimatedContent(
        targetState = item,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val sign = (if (forward) 1 else -1) * (if (rtl) -1 else 1)
            val offsetSpring = Springs.content(IntOffset.VisibilityThreshold)
            (fadeIn(Springs.content()) + slideInHorizontally(offsetSpring) { sign * parallax }) togetherWith
                (fadeOut(Springs.content()) + slideOutHorizontally(offsetSpring) { -sign * parallax })
        },
        contentKey = { it?.key },
        label = "heroBackdrop",
    ) { target -> BackdropImage(target?.backdropUrl, widthPx, heightPx) }
}

/** Full-screen, centre-cropped backdrop that cross-fades when its url changes (e.g. a TMDB backdrop arrives). */
@Composable
private fun BackdropImage(url: String?, widthPx: Int, heightPx: Int) {
    val image by rememberImage(url) { ctx, u -> HeroImages.backdrop(ctx, u, widthPx, heightPx) }
    var shown by remember { mutableStateOf<ImageBitmap?>(null) }
    var previous by remember { mutableStateOf<ImageBitmap?>(null) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(image) {
        val ready = (image as? ImageState.Ready)?.bitmap ?: return@LaunchedEffect
        if (ready == shown) return@LaunchedEffect
        previous = shown
        shown = ready
        fade.snapTo(0f)
        fade.animateTo(1f, Springs.content())
        previous = null
    }
    Canvas(Modifier.fillMaxSize()) {
        previous?.let { drawCover(it, 1f) }
        shown?.let { drawCover(it, fade.value) }
    }
}

private fun DrawScope.drawCover(image: ImageBitmap, alpha: Float) {
    val scale = max(size.width / image.width, size.height / image.height)
    val srcWidth = (size.width / scale).roundToInt().coerceIn(1, image.width)
    val srcHeight = (size.height / scale).roundToInt().coerceIn(1, image.height)
    drawImage(
        image = image,
        srcOffset = IntOffset((image.width - srcWidth) / 2, (image.height - srcHeight) / 2),
        srcSize = IntSize(srcWidth, srcHeight),
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.Low,
    )
}

/** Start-side and bottom scrims keep the text legible over any backdrop. */
@Composable
private fun Scrims(rtl: Boolean) {
    Spacer(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val brushes = HeroScrim.brushes(size.width, size.height, rtl)
                onDrawBehind { brushes.forEach { drawRect(it) } }
            },
    )
}

/** Start-side (85% black over 60% of the width) and bottom (90% black over 55% of the height) scrims. */
private object HeroScrim {
    /** Brushes in the coordinates of a surface whose origin is at ([dx], [dy]) on a [width] x [height] screen. */
    fun brushes(width: Float, height: Float, rtl: Boolean, dx: Float = 0f, dy: Float = 0f): List<Brush> = listOf(
        Brush.horizontalGradient(
            0f to Color.Black.copy(alpha = 0.85f),
            0.6f to Color.Transparent,
            startX = (if (rtl) width else 0f) - dx,
            endX = (if (rtl) 0f else width) - dx,
        ),
        Brush.verticalGradient(
            0.45f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.9f),
            startY = -dy,
            endY = height - dy,
        ),
    )
}

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

/** Source app, logo, metadata, synopsis and progress, fading/sliding in with a short stagger. */
@Composable
private fun HeroText(item: HeroItem, rtl: Boolean) {
    val type = LocalGlassType.current
    val offsetPx = with(LocalDensity.current) { 18.dp.toPx() } * (if (rtl) -1f else 1f)

    Column {
        if (item.appLabel != null) {
            Row(
                Modifier.stagger(rememberStagger(0), offsetPx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item.appIcon?.let {
                    Image(bitmap = it, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(Spacing.S))
                }
                BasicText(item.appLabel, style = type.caption.copy(color = GlassColors.TextSecondary))
            }
            Spacer(Modifier.height(Spacing.M))
        }

        HeroLogo(item, Modifier.stagger(rememberStagger(1), offsetPx))

        val meta = buildList {
            item.season?.let { add(stringResource(R.string.hero_season, it)) }
            item.episode?.let { add(stringResource(R.string.hero_episode, it)) }
            item.episodeTitle?.let { add(it) }
            if (item.season == null && item.episode == null) {
                item.year?.let { add(it) }
                if (item.media == MediaHint.MOVIE) {
                    HeroFormat.duration(item.entry.durationMillis)?.let { (h, m) ->
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
            Spacer(Modifier.height(14.dp))
            BasicText(
                text = meta.joinToString("  ·  "),
                modifier = Modifier.stagger(rememberStagger(2), offsetPx).widthIn(max = SYNOPSIS_WIDTH),
                style = type.callout.copy(color = GlassColors.TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        item.synopsis?.let {
            Spacer(Modifier.height(Spacing.S))
            BasicText(
                text = it,
                modifier = Modifier.stagger(rememberStagger(3), offsetPx).width(SYNOPSIS_WIDTH),
                style = type.body.copy(color = GlassColors.TextPrimary.copy(alpha = 0.85f)),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val progress = item.progress
        val remaining = item.remainingMillis
        if (progress != null && remaining != null) {
            Spacer(Modifier.height(Spacing.L))
            Row(
                Modifier.stagger(rememberStagger(4), offsetPx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProgressBar(progress, rtl)
                Spacer(Modifier.width(10.dp))
                val (h, m) = HeroFormat.remaining(remaining)
                BasicText(
                    text = when {
                        h > 0 && m > 0 -> stringResource(R.string.hero_left_h_min, h, m)
                        h > 0 -> stringResource(R.string.hero_left_h, h)
                        else -> stringResource(R.string.hero_left_min, m)
                    },
                    style = type.caption.copy(color = GlassColors.TextSecondary),
                )
            }
        }
    }
}

@Composable
private fun ProgressBar(progress: Float, rtl: Boolean) {
    Spacer(
        Modifier
            .width(140.dp)
            .height(4.dp)
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

/** Logo fitted into 380 x 120 dp, start-aligned; the title as large text when there is no logo. */
@Composable
private fun HeroLogo(item: HeroItem, modifier: Modifier) {
    val type = LocalGlassType.current
    val logo by rememberImage(item.logoUrl) { ctx, url -> HeroImages.logo(ctx, url) }
    when (val state = logo) {
        is ImageState.Ready -> LogoImage(state.bitmap, modifier)
        ImageState.Loading -> Spacer(modifier.height(LOGO_PLACEHOLDER))
        // Until TMDB has answered, keep the logo's space instead of flashing the title.
        ImageState.Failed -> if (!item.enriched) Spacer(modifier.height(LOGO_PLACEHOLDER)) else BasicText(
            text = item.title,
            modifier = modifier.widthIn(max = SYNOPSIS_WIDTH),
            style = type.display.copy(color = GlassColors.TextPrimary),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LogoImage(bitmap: ImageBitmap, modifier: Modifier) {
    val density = LocalDensity.current
    val pad = HeroImages.LOGO_PAD_PX
    val contentWidth = (bitmap.width - 2 * pad).coerceAtLeast(1)
    val contentHeight = (bitmap.height - 2 * pad).coerceAtLeast(1)
    val scale = with(density) {
        min(LOGO_MAX_WIDTH.toPx() / contentWidth, LOGO_MAX_HEIGHT.toPx() / contentHeight)
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
private fun EmptyHero(state: HeroUiState, onRequestAccess: () -> Unit, modifier: Modifier) {
    val type = LocalGlassType.current
    val (title, body) = when (state.access) {
        AccessState.Requestable -> R.string.empty_access_title to R.string.empty_access_body
        AccessState.Blocked -> R.string.empty_blocked_title to R.string.empty_blocked_body
        else -> R.string.empty_none_title to R.string.empty_none_body
    }
    val allowFocus = remember { FocusRequester() }
    Column(modifier.padding(start = Spacing.ScreenHorizontal, bottom = CONTENT_BOTTOM).width(SYNOPSIS_WIDTH)) {
        BasicText(stringResource(title), style = type.title1.copy(color = GlassColors.TextPrimary))
        Spacer(Modifier.height(Spacing.S))
        BasicText(stringResource(body), style = type.body.copy(color = GlassColors.TextSecondary))
        if (state.access == AccessState.Requestable) {
            Spacer(Modifier.height(Spacing.XL))
            GlassButton(
                text = stringResource(R.string.empty_allow),
                icon = null,
                onClick = onRequestAccess,
                modifier = Modifier.focusRequester(allowFocus),
            )
            LaunchedEffect(Unit) { allowFocus.requestFocus() }
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
