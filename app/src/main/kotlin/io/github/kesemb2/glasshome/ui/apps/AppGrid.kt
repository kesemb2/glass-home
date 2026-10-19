package io.github.kesemb2.glasshome.ui.apps

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.kesemb2.glasshome.R
import io.github.kesemb2.glasshome.data.apps.AppEntry
import io.github.kesemb2.glasshome.data.apps.GridItem
import io.github.kesemb2.glasshome.ui.glass.glass
import io.github.kesemb2.glasshome.ui.glass.glassShadow
import io.github.kesemb2.glasshome.ui.motion.animatePlacement
import io.github.kesemb2.glasshome.ui.theme.GlassColors
import io.github.kesemb2.glasshome.ui.theme.LocalGlassType
import io.github.kesemb2.glasshome.ui.theme.Shapes
import io.github.kesemb2.glasshome.ui.theme.Springs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Cells in rows of [columns] at a fixed pitch (start-aligned, mirrored in RTL). Cells are keyed by item, so a
 * reordered cell keeps its focus and slides to its new place. Each row wraps around: past its last cell focus goes
 * to its first ([focusItem] moves it), and back.
 */
@Composable
fun CardGrid(
    items: List<GridItem>,
    focusItem: (GridItem) -> Boolean,
    modifier: Modifier = Modifier,
    cell: @Composable (item: GridItem) -> Unit,
) {
    val size = LocalCardSize.current
    val columns = size.columns
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Layout(
        content = {
            items.forEachIndexed { index, item ->
                key(item.key) {
                    val rowStart = index / columns * columns
                    val rowEnd = min(rowStart + columns, items.size) - 1
                    Box(
                        Modifier
                            .animatePlacement()
                            .wrapAround(rtl, index == rowStart, index == rowEnd) { toEnd ->
                                focusItem(items[if (toEnd) rowEnd else rowStart])
                            },
                    ) { cell(item) }
                }
            }
        },
        modifier = modifier,
    ) { measurables, _ ->
        val cellWidth = size.width.roundToPx()
        val cellHeight = size.cellHeight.roundToPx()
        val gap = size.gap.roundToPx()
        val pitch = size.rowPitch.roundToPx()
        val placeables = measurables.map { it.measure(Constraints.fixed(cellWidth, cellHeight)) }
        val rows = (placeables.size + columns - 1) / columns
        val width = columns * cellWidth + (columns - 1) * gap
        val height = if (rows == 0) 0 else (rows - 1) * pitch + cellHeight
        layout(width, height) {
            placeables.forEachIndexed { i, p -> p.placeRelative((i % columns) * (cellWidth + gap), (i / columns) * pitch) }
        }
    }
}

/**
 * Wrap-around for a row of cards: "next" (Right, Left in RTL) on the [last] card calls [wrap] with false (go to
 * the first), "previous" on the [first] with true (go to the last).
 */
internal fun Modifier.wrapAround(rtl: Boolean, first: Boolean, last: Boolean, wrap: (toEnd: Boolean) -> Boolean): Modifier {
    if (!first && !last) return this
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val next = if (rtl) Key.DirectionLeft else Key.DirectionRight
        val previous = if (rtl) Key.DirectionRight else Key.DirectionLeft
        when {
            last && event.key == next -> wrap(false)
            first && event.key == previous -> wrap(true)
            else -> false
        }
    }
}

/** Grid rows needed for [count] cells. */
fun gridRows(count: Int, columns: Int): Int = (count + columns - 1) / columns

/** The shelf: as wide as the grid plus its padding, one card high. */
object ShelfMetrics {
    val Padding = 16.dp
    val Width = CardSize.CONTENT_WIDTH + Padding * 2

    fun height(size: CardSize): Dp = size.height + Padding * 2
}

/**
 * The favorites shelf: a glass tray holding a row of cards at a time; it scrolls when focus moves past either end.
 * Cards outside the window fade out instead of being clipped, so the focused card's lift is never cut off. Past
 * the last card focus wraps around to the first ([focusApp] moves it), and back.
 */
@Composable
fun FavoritesShelf(
    apps: List<AppEntry>,
    visuals: io.github.kesemb2.glasshome.image.AppVisuals,
    focusedPkg: String?,
    movingPkg: String?,
    focusModifier: (AppEntry) -> Modifier,
    focusApp: (AppEntry) -> Boolean,
    onFocused: (AppEntry) -> Unit,
    onClick: (AppEntry) -> Unit,
    onMenu: (AppEntry, Rect) -> Unit,
    onDown: (column: Int) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val type = LocalGlassType.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val window = remember { IntArray(1) }
    val size = LocalCardSize.current
    val visibleCount = size.columns
    val focusedIndex = apps.indexOfFirst { it.packageName == focusedPkg }
    var start = window[0]
    if (focusedIndex >= 0) {
        if (focusedIndex < start) start = focusedIndex
        if (focusedIndex >= start + visibleCount) start = focusedIndex - visibleCount + 1
    }
    start = start.coerceIn(0, max(0, apps.size - visibleCount))
    window[0] = start
    val pitchPx = with(LocalDensity.current) { (size.width + size.gap).toPx() }
    val scroll by animateFloatAsState(start * pitchPx, Springs.focus(), label = "shelfScroll")

    Box(
        modifier
            .size(ShelfMetrics.Width, ShelfMetrics.height(size))
            .glassShadow(Shapes.Shelf, 16.dp)
            .glass(Shapes.Shelf)
            // Down goes to the grid card under the focused one (the grid starts off screen).
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && focusedIndex >= 0 &&
                    onDown(focusedIndex - start)
            },
    ) {
        if (apps.isEmpty()) {
            BasicText(
                text = stringResource(R.string.shelf_empty),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                style = type.callout.copy(color = GlassColors.TextSecondary, textAlign = TextAlign.Center),
            )
            return@Box
        }
        Layout(
            content = {
                apps.forEachIndexed { index, app ->
                    key(app.packageName) {
                        val shown = index in start until start + visibleCount
                        val alpha by animateFloatAsState(if (shown) 1f else 0f, Springs.focus(), label = "shelfAlpha")
                        Box(
                            Modifier
                                .animatePlacement()
                                .graphicsLayer { this.alpha = alpha }
                                .wrapAround(rtl, index == 0, index == apps.lastIndex) { toEnd ->
                                    focusApp(if (toEnd) apps.last() else apps.first())
                                },
                        ) {
                            GridCard(
                                item = GridItem.App(app),
                                visuals = visuals,
                                focusModifier = focusModifier(app),
                                moving = app.packageName == movingPkg,
                                showLabel = false,
                                onFocused = { onFocused(app) },
                                onClick = { onClick(app) },
                                onMenu = { onMenu(app, it) },
                            )
                        }
                    }
                }
            },
            modifier = Modifier
                .padding(ShelfMetrics.Padding)
                .offset { IntOffset(-scroll.roundToInt(), 0) },
        ) { measurables, _ ->
            val cellWidth = size.width.roundToPx()
            val cellHeight = size.height.roundToPx()
            val gap = size.gap.roundToPx()
            val placeables = measurables.map { it.measure(Constraints.fixed(cellWidth, cellHeight)) }
            layout(visibleCount * cellWidth + (visibleCount - 1) * gap, cellHeight) {
                placeables.forEachIndexed { i, p -> p.placeRelative(i * (cellWidth + gap), 0) }
            }
        }
    }
}
