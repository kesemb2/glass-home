package io.github.kesemb2.glasshome.ui.motion

import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewConfiguration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import io.github.kesemb2.glasshome.ui.glass.asPath
import io.github.kesemb2.glasshome.ui.theme.Springs
import kotlinx.coroutines.launch

/**
 * Last D-pad direction, fed by the activity before Compose sees the key. Focus effects read it once when an
 * element gains focus to tilt toward the direction of travel; it is not observable state on purpose.
 */
object FocusTravel {
    enum class Direction { None, Left, Right, Up, Down }

    @Volatile
    var last: Direction = Direction.None
        private set

    fun record(keyCode: Int) {
        last = when (keyCode) {
            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> Direction.Left
            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> Direction.Right
            AndroidKeyEvent.KEYCODE_DPAD_UP -> Direction.Up
            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> Direction.Down
            else -> last
        }
    }
}

@Immutable
data class FocusStyle(
    val focusedScale: Float,
    val pressedScale: Float,
    val tiltDegrees: Float,
    val restElevation: Dp,
    val focusedElevation: Dp,
    val sweep: Boolean,
) {
    companion object {
        /** App cards and posters. */
        val Card = FocusStyle(1.12f, 1.06f, 2.5f, 0.dp, 18.dp, sweep = true)

        /** Glass pill buttons (Play, the settings button). */
        val Button = FocusStyle(1.08f, 1.0f, 1.5f, 0.dp, 12.dp, sweep = true)

        /** Rows inside menus and settings: barely grow, so they stay inside their panel. */
        val Row = FocusStyle(1.03f, 1.0f, 0f, 0.dp, 6.dp, sweep = false)
    }
}

/**
 * tvOS-style focus: scale up and lift with a spring, a one-shot specular sweep, and a brief tilt toward
 * the direction of travel that settles back to flat. Everything animates in the layer/draw phases.
 */
fun Modifier.focusEffect(focused: Boolean, pressed: Boolean, style: FocusStyle, shape: Shape): Modifier = composed {
    val density = LocalDensity.current
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> style.pressedScale
            focused -> style.focusedScale
            else -> 1f
        },
        animationSpec = Springs.focus(),
        label = "focusScale",
    )
    val lift by animateFloatAsState(if (focused) 1f else 0f, Springs.focus(), label = "focusLift")
    val tiltY = remember { Animatable(0f) }
    val tiltX = remember { Animatable(0f) }
    val sweep = remember { Animatable(1f) }
    // The tilt and the sweep run in the element's own scope, not the focus effect's: moving on before they end
    // lets them finish on this element instead of freezing half way.
    val scope = rememberCoroutineScope()

    LaunchedEffect(focused) {
        if (!focused) return@LaunchedEffect
        val (x, y) = when (FocusTravel.last) {
            FocusTravel.Direction.Right -> 0f to style.tiltDegrees
            FocusTravel.Direction.Left -> 0f to -style.tiltDegrees
            FocusTravel.Direction.Up -> style.tiltDegrees to 0f
            FocusTravel.Direction.Down -> -style.tiltDegrees to 0f
            FocusTravel.Direction.None -> 0f to 0f
        }
        scope.launch {
            tiltY.snapTo(y)
            tiltY.animateTo(0f, Springs.focus())
        }
        scope.launch {
            tiltX.snapTo(x)
            tiltX.animateTo(0f, Springs.focus())
        }
        if (style.sweep) {
            scope.launch {
                sweep.snapTo(0f)
                sweep.animateTo(1f, Springs.sweep())
            }
        }
    }

    graphicsLayer {
        scaleX = scale
        scaleY = scale
        rotationX = tiltX.value
        rotationY = tiltY.value
        cameraDistance = 16f * density.density
        val rest = style.restElevation.toPx()
        shadowElevation = rest + (style.focusedElevation.toPx() - rest) * lift
        this.shape = shape
        clip = false
    }.drawWithCache {
        val path = shape.createOutline(size, layoutDirection, this).asPath()
        val band = size.width * 0.45f
        onDrawWithContent {
            drawContent()
            val progress = sweep.value
            if (progress < 1f) {
                // A soft diagonal band of light travelling from the start edge to beyond the end edge.
                val x = -band + (size.width + 2 * band) * progress
                val brush = Brush.linearGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.28f * (1f - progress)),
                    1f to Color.Transparent,
                    start = Offset(x - band / 2, 0f),
                    end = Offset(x + band / 2, size.height),
                )
                drawPath(path, brush)
            }
        }
    }
}

/**
 * Click and hold-to-open-menu for the remote's OK key. Long press fires once after the system long-press
 * timeout; the matching key-up is swallowed so it never doubles as a click. A key-up without its own
 * key-down (for example, the release after a menu opened under the finger) is ignored.
 */
fun Modifier.tvClickable(
    interactionSource: MutableInteractionSource,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    val state = remember { PressState() }
    val allowed = LocalFocusAllowed.current

    onKeyEvent { event ->
        if (!event.isSelect()) return@onKeyEvent false
        val native = event.nativeKeyEvent
        when (event.type) {
            KeyEventType.KeyDown -> {
                if (native.repeatCount == 0) {
                    state.active = true
                    state.longFired = false
                    state.press = PressInteraction.Press(Offset.Zero).also { interactionSource.tryEmit(it) }
                } else if (state.active && !state.longFired && longClick != null &&
                    native.eventTime - native.downTime >= ViewConfiguration.getLongPressTimeout()
                ) {
                    state.longFired = true
                    state.release(interactionSource)
                    longClick?.invoke()
                }
                true
            }
            KeyEventType.KeyUp -> {
                val fire = state.active && !state.longFired
                state.active = false
                state.release(interactionSource)
                if (fire) click()
                true
            }
            else -> false
        }
    }.focusProperties { canFocus = allowed }.focusable(interactionSource = interactionSource)
}

/** False under an open overlay: nothing there can take focus, so the D-pad stays inside the overlay. */
val LocalFocusAllowed = compositionLocalOf { true }

/**
 * Animates an element to its new place when its parent lays it out somewhere else (reordering, move mode).
 * Works in absolute coordinates, so it is correct in RTL too.
 */
fun Modifier.animatePlacement(): Modifier = composed {
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<IntOffset?>(null) }
    val animatable = remember { mutableStateOf<Animatable<IntOffset, AnimationVector2D>?>(null) }
    onPlaced { target = it.positionInParent().round() }
        .absoluteOffset {
            val to = target ?: return@absoluteOffset IntOffset.Zero
            val anim = animatable.value ?: Animatable(to, IntOffset.VectorConverter).also { animatable.value = it }
            if (anim.targetValue != to) scope.launch { anim.animateTo(to, Springs.focus()) }
            anim.value - to
        }
}

/** The small wiggle of a card being moved; the only repeating animation, running only during move mode. */
fun Modifier.wiggle(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "wiggle")
    val angle = transition.animateFloat(
        initialValue = -WIGGLE_DEGREES,
        targetValue = WIGGLE_DEGREES,
        animationSpec = infiniteRepeatable(tween(WIGGLE_HALF_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "wiggleAngle",
    )
    graphicsLayer { rotationZ = angle.value }
}

private const val WIGGLE_DEGREES = 1.6f
private const val WIGGLE_HALF_MS = 140

private class PressState {
    var active = false
    var longFired = false
    var press: PressInteraction.Press? = null

    fun release(source: MutableInteractionSource) {
        press?.let { source.tryEmit(PressInteraction.Release(it)) }
        press = null
    }
}

private fun KeyEvent.isSelect(): Boolean =
    key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter
