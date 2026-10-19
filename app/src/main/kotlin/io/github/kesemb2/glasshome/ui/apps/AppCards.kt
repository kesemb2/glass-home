package io.github.kesemb2.glasshome.ui.apps

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.kesemb2.glasshome.data.apps.AppEntry
import io.github.kesemb2.glasshome.data.apps.GridItem
import io.github.kesemb2.glasshome.image.AppVisual
import io.github.kesemb2.glasshome.image.AppVisuals
import io.github.kesemb2.glasshome.ui.glass.asPath
import io.github.kesemb2.glasshome.ui.glass.glass
import io.github.kesemb2.glasshome.ui.motion.FocusStyle
import io.github.kesemb2.glasshome.ui.motion.focusEffect
import io.github.kesemb2.glasshome.ui.motion.tvClickable
import io.github.kesemb2.glasshome.ui.motion.wiggle
import io.github.kesemb2.glasshome.ui.theme.GlassColors
import io.github.kesemb2.glasshome.ui.theme.LocalGlassType
import io.github.kesemb2.glasshome.ui.theme.Shapes
import io.github.kesemb2.glasshome.ui.theme.Springs
import kotlin.math.roundToInt

/**
 * Card geometry for a number of grid columns (Settings › App size). The grid always spans the 864 dp content
 * width with 16 dp gaps, so 5 columns give 160 x 90 dp cards and 8 give 94 x 53 dp. The focused card's name sits
 * below it.
 */
@Immutable
data class CardSize(val columns: Int) {
    val width: Dp get() = (CONTENT_WIDTH - GAP * (columns - 1)) / columns
    val height: Dp get() = width * (9f / 16f)
    val gap: Dp get() = GAP
    val labelGap: Dp get() = 10.dp
    val labelHeight: Dp get() = 18.dp
    val cellHeight: Dp get() = height + labelGap + labelHeight
    val rowPitch: Dp get() = cellHeight + 8.dp

    companion object {
        val CONTENT_WIDTH = 864.dp
        val GAP = 16.dp
        const val MIN_COLUMNS = 5
        const val MAX_COLUMNS = 8
        const val DEFAULT_COLUMNS = 6
    }
}

/** The card size chosen in Settings, for everything that lays out cards. */
val LocalCardSize = staticCompositionLocalOf { CardSize(CardSize.DEFAULT_COLUMNS) }

/** Remembers a card's layout coordinates so a menu can open next to it. */
private class Anchor {
    var coordinates: LayoutCoordinates? = null

    fun bounds(): Rect? = coordinates?.takeIf { it.isAttached }?.boundsInRoot()
}

/**
 * One app or folder in the grid, the shelf or a folder. OK opens it; holding OK calls [onMenu] with the card's
 * bounds on screen. While [moving], the card wiggles and stays highlighted.
 */
@Composable
fun GridCard(
    item: GridItem,
    visuals: AppVisuals,
    focusModifier: Modifier,
    moving: Boolean,
    showLabel: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onMenu: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = LocalGlassType.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val anchor = remember { Anchor() }
    val label = when (item) {
        is GridItem.App -> item.app.label
        is GridItem.Folder -> item.folder.name
    }
    val labelAlpha by animateFloatAsState(if (showLabel && (focused || moving)) 1f else 0f, Springs.focus(), label = "label")

    val size = LocalCardSize.current
    Column(modifier.width(size.width), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size.width, size.height)
                .onPlaced { anchor.coordinates = it }
                .then(if (moving) Modifier.wiggle() else Modifier)
                .focusEffect(focused || moving, pressed, FocusStyle.Card, Shapes.Card)
                .then(focusModifier)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .tvClickable(interaction, onLongClick = { anchor.bounds()?.let(onMenu) }, onClick = onClick),
        ) {
            CardFace(item, visuals) { if (focused || moving) 1f else 0f }
        }
        if (showLabel) {
            Spacer(Modifier.height(size.labelGap))
            BasicText(
                text = label,
                modifier = Modifier
                    .height(size.labelHeight)
                    .graphicsLayer { alpha = labelAlpha },
                style = type.caption.copy(color = GlassColors.TextPrimary, textAlign = TextAlign.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** What a card shows, without any focus handling (also drawn over the blur next to an open menu). */
@Composable
fun CardFace(item: GridItem, visuals: AppVisuals, focus: () -> Float) {
    when (item) {
        is GridItem.App -> AppFace(item.app, visuals[item.app.packageName], visuals.placeholder(item.app.packageName))
        is GridItem.Folder -> FolderFace(item, visuals, focus)
    }
}

/** The card itself: the TV banner, or the icon and name on the icon's dominant colour. */
@Composable
fun AppFace(app: AppEntry, visual: AppVisual?, placeholder: Color, modifier: Modifier = Modifier) {
    val banner = visual?.banner
    if (banner != null) {
        Box(modifier.fillMaxSize().drawImageCard(banner))
        return
    }
    val type = LocalGlassType.current
    val color = visual?.color ?: placeholder
    // Sized from the card height, so the fallback reads the same at every card size.
    val cardHeight = LocalCardSize.current.height
    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val path = Shapes.Card.createOutline(size, layoutDirection, this).asPath()
                onDrawBehind { drawPath(path, color) }
            },
    ) {
        val icon = visual?.icon
        if (icon != null) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = cardHeight * 0.13f)
                    .size(cardHeight * 0.49f)
                    .drawWithCache { onDrawBehind { drawFitted(icon) } },
            )
        }
        BasicText(
            text = app.label,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 8.dp, end = 8.dp, bottom = cardHeight * 0.09f),
            style = type.caption.copy(
                color = GlassColors.TextPrimary,
                textAlign = TextAlign.Center,
                fontSize = type.caption.fontSize * (cardHeight / 90.dp).coerceIn(0.8f, 1f),
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A folder: glass with up to six of its apps in miniature (3 x 2). */
@Composable
private fun FolderFace(item: GridItem.Folder, visuals: AppVisuals, focus: () -> Float) {
    val minis = item.apps.take(6).map { it.packageName to (visuals[it.packageName]) }
    Box(
        Modifier
            .fillMaxSize()
            .glass(Shapes.Card, focus = focus)
            .drawWithCache {
                val columns = 3
                val gap = 6.dp.toPx()
                val padX = 12.dp.toPx()
                val w = (size.width - 2 * padX - (columns - 1) * gap) / columns
                val h = w * 9f / 16f
                val padY = (size.height - 2 * h - gap) / 2f
                val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
                val tiles = minis.mapIndexed { i, (pkg, visual) ->
                    val col = i % columns
                    val row = i / columns
                    val x = if (rtl) size.width - padX - (col + 1) * w - col * gap else padX + col * (w + gap)
                    val y = padY + row * (h + gap)
                    val rect = Rect(Offset(x, y), Size(w, h))
                    val path = Path().apply {
                        addRoundRect(androidx.compose.ui.geometry.RoundRect(rect, androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())))
                    }
                    Tile(rect, path, visual, visuals.placeholder(pkg))
                }
                onDrawBehind {
                    tiles.forEach { tile ->
                        val banner = tile.visual?.banner
                        if (banner != null) {
                            drawPath(tile.path, imageBrush(banner, tile.rect))
                        } else {
                            drawPath(tile.path, tile.visual?.color ?: tile.placeholder)
                            tile.visual?.icon?.let { icon ->
                                val s = tile.rect.height * 0.7f
                                drawImage(
                                    image = icon,
                                    dstOffset = IntOffset(
                                        (tile.rect.center.x - s / 2).roundToInt(),
                                        (tile.rect.center.y - s / 2).roundToInt(),
                                    ),
                                    dstSize = IntSize(s.roundToInt(), s.roundToInt()),
                                    filterQuality = FilterQuality.Low,
                                )
                            }
                        }
                    }
                }
            },
    )
}

private class Tile(val rect: Rect, val path: Path, val visual: AppVisual?, val placeholder: Color)

/** Draws [image] filling the card shape (anti-aliased edges, unlike a clip). */
private fun Modifier.drawImageCard(image: ImageBitmap): Modifier = drawWithCache {
    val path = Shapes.Card.createOutline(size, layoutDirection, this).asPath()
    val brush = imageBrush(image, Rect(Offset.Zero, size))
    onDrawBehind { drawPath(path, brush) }
}

private fun imageBrush(image: ImageBitmap, into: Rect): ShaderBrush {
    val shader = ImageShader(image)
    shader.setLocalMatrix(
        android.graphics.Matrix().apply {
            setScale(into.width / image.width, into.height / image.height)
            postTranslate(into.left, into.top)
        },
    )
    return ShaderBrush(shader)
}

private fun DrawScope.drawFitted(image: ImageBitmap) {
    drawImage(
        image = image,
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        filterQuality = FilterQuality.Medium,
    )
}
