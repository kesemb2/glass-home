package io.github.kesemb2.glasshome.ui.overlay

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.kesemb2.glasshome.image.FastBlur
import io.github.kesemb2.glasshome.ui.components.MenuRow
import io.github.kesemb2.glasshome.ui.glass.GlassBackdrop
import io.github.kesemb2.glasshome.ui.glass.LocalGlassBackdrop
import io.github.kesemb2.glasshome.ui.glass.glass
import io.github.kesemb2.glasshome.ui.glass.glassShadow
import io.github.kesemb2.glasshome.ui.motion.LocalFocusAllowed
import io.github.kesemb2.glasshome.ui.theme.GlassColors
import io.github.kesemb2.glasshome.ui.theme.LocalGlassType
import io.github.kesemb2.glasshome.ui.theme.Shapes
import io.github.kesemb2.glasshome.ui.theme.Springs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * One downscaled copy of the screen, taken when an overlay opens and blurred once: the overlay's backdrop and
 * what its glass refracts. Nothing is captured or blurred while the overlay is open.
 */
object ScreenSnapshot {
    private const val WIDTH = 480
    private const val HEIGHT = 270
    private const val RADIUS = 4
    private const val TIMEOUT_MS = 250L

    suspend fun capture(view: View): ImageBitmap? {
        val window = view.findWindow() ?: return null
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val copied = withTimeoutOrNull(TIMEOUT_MS) { copy(window, bitmap) } ?: false
        if (!copied) {
            bitmap.recycle()
            return null
        }
        return withContext(Dispatchers.Default) {
            val pixels = IntArray(WIDTH * HEIGHT)
            bitmap.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
            FastBlur.blur(pixels, WIDTH, HEIGHT, RADIUS)
            bitmap.setPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
            bitmap.asImageBitmap()
        }
    }

    private suspend fun copy(window: Window, into: Bitmap): Boolean = suspendCancellableCoroutine { cont ->
        try {
            PixelCopy.request(
                window,
                into,
                { result -> if (cont.isActive) cont.resume(result == PixelCopy.SUCCESS) },
                Handler(Looper.getMainLooper()),
            )
        } catch (_: IllegalArgumentException) {
            cont.resume(false) // The window has no surface yet.
        }
    }

    private fun View.findWindow(): Window? {
        var c = context
        while (c is ContextWrapper) {
            if (c is Activity) return c.window
            c = c.baseContext
        }
        return null
    }
}

/**
 * A full-screen layer over the home screen: the blurred snapshot, darkened by [dim], with glass inside it
 * refracting that snapshot. Only the [top] layer can take focus. The layer stays invisible until its snapshot is
 * taken (so the snapshot never contains the layer itself), then fades in; [content] gets the appear progress.
 */
@Composable
fun OverlayLayer(top: Boolean, dim: Float, content: @Composable BoxScope.(appear: () -> Float) -> Unit) {
    val view = LocalView.current
    val backdrop = remember { GlassBackdrop() }
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var ready by remember { mutableStateOf(false) }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        snapshot = ScreenSnapshot.capture(view)
        backdrop.set(snapshot)
        ready = true
        appear.animateTo(1f, Springs.overlay())
    }
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop, LocalFocusAllowed provides top) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (ready) appear.value.coerceIn(0f, 1f) else 0f }
                .drawBehind {
                    val image = snapshot
                    if (image != null) {
                        drawImage(
                            image = image,
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                            filterQuality = FilterQuality.Low,
                        )
                        drawRect(Color.Black.copy(alpha = dim))
                    } else {
                        drawRect(Color.Black.copy(alpha = max(dim, 0.7f)))
                    }
                },
        ) {
            if (ready) content { appear.value }
        }
    }
}

@Immutable
data class MenuAction(@DrawableRes val icon: Int, val label: String, val onClick: () -> Unit)

/**
 * A glass menu next to the card it belongs to (below it when there is room, else above), with a sharp copy of
 * the card drawn over the blur at its place. Scales in from the card's side.
 */
@Composable
fun AnchoredMenu(
    anchor: Rect,
    title: String?,
    actions: List<MenuAction>,
    appear: () -> Float,
    page: Any,
    card: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val type = LocalGlassType.current
    val firstFocus = remember { FocusRequester() }
    var below by remember { mutableStateOf(true) }
    LaunchedEffect(page) {
        try {
            firstFocus.requestFocus()
        } catch (_: IllegalStateException) {
        }
    }

    Box(Modifier.fillMaxSize()) {
        // The card, lifted as if focused.
        with(density) {
            Box(
                Modifier
                    .absoluteOffset { IntOffset(anchor.left.roundToInt(), anchor.top.roundToInt()) }
                    .size(anchor.width.toDp(), anchor.height.toDp())
                    .graphicsLayer {
                        scaleX = 1.12f
                        scaleY = 1.12f
                        shadowElevation = 18.dp.toPx()
                        shape = Shapes.Card
                        clip = false
                    },
            ) { card() }
        }

        Layout(
            content = {
                key(page) {
                    Column(
                        Modifier
                            .width(MENU_WIDTH)
                            .graphicsLayer {
                                val p = appear()
                                val s = 0.86f + 0.14f * p
                                scaleX = s
                                scaleY = s
                                transformOrigin = TransformOrigin(if (rtl) 1f else 0f, if (below) 0f else 1f)
                            }
                            .glassShadow(Shapes.Menu, 24.dp)
                            .glass(Shapes.Menu)
                            .padding(8.dp),
                    ) {
                        if (title != null) {
                            BasicText(
                                text = title,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp),
                                style = type.caption.copy(color = GlassColors.TextSecondary),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        actions.forEachIndexed { i, action ->
                            MenuRow(
                                text = action.label,
                                icon = action.icon,
                                onClick = action.onClick,
                                modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            )
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, constraints ->
            val menu = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
            val margin = SCREEN_V.roundToPx()
            val side = SCREEN_H.roundToPx()
            val gap = GAP.roundToPx() + (anchor.height * 0.06f).roundToInt()
            val fitsBelow = anchor.bottom + gap + menu.height <= constraints.maxHeight - margin
            below = fitsBelow
            val y = if (fitsBelow) {
                anchor.bottom.roundToInt() + gap
            } else {
                max(margin, anchor.top.roundToInt() - gap - menu.height)
            }
            val preferred = if (rtl) anchor.right.roundToInt() - menu.width else anchor.left.roundToInt()
            val x = preferred.coerceIn(side, max(side, constraints.maxWidth - side - menu.width))
            layout(constraints.maxWidth, constraints.maxHeight) { menu.place(x, min(y, constraints.maxHeight - margin - menu.height)) }
        }
    }
}

private val MENU_WIDTH: Dp = 300.dp
private val SCREEN_H: Dp = 48.dp
private val SCREEN_V: Dp = 27.dp
private val GAP: Dp = 14.dp
