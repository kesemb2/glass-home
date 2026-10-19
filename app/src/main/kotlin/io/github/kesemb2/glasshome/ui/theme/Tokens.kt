package io.github.kesemb2.glasshome.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Colour tokens. See DESIGN.md for the rules behind each value. */
object GlassColors {
    val Background = Color(0xFF05060A)
    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0x99EBEBF5)
    val TextTertiary = Color(0x4DEBEBF5)
    val FocusFill = Color.White
    val OnFocus = Color(0xFF0A0A0F)
    val ProgressTrack = Color(0x40FFFFFF)
    val ProgressFill = Color(0xE6FFFFFF)

    /** Glass layers. */
    val GlassTint = Color.White.copy(alpha = 0.10f)
    val GlassTintFocused = Color.White.copy(alpha = 0.18f)
    val GlassRim = Color.White.copy(alpha = 0.50f)
    val GlassHighlight = Color.White.copy(alpha = 0.12f)
    val GlassShadow = Color.Black.copy(alpha = 0.35f)
}

/** Spacing scale and the tvOS-style safe area on the 960 x 540 dp canvas. */
object Spacing {
    val XS = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 20.dp
    val XXL = 24.dp
    val XXXL = 32.dp
    val ScreenHorizontal = 48.dp
    val ScreenVertical = 27.dp
}

object Shapes {
    val Pill = RoundedCornerShape(percent = 50)
    val Card = ContinuousRoundedShape(16.dp)
    val Shelf = ContinuousRoundedShape(28.dp)
    val Menu = ContinuousRoundedShape(24.dp)
    val Row = ContinuousRoundedShape(14.dp)
}

/** Springs only; motion in this app is never a linear tween. */
object Springs {
    /** Focus scale, lift, tilt and neighbours shifting. */
    fun <T> focus(): SpringSpec<T> = spring(dampingRatio = 0.75f, stiffness = 400f)

    /** Hero cross-fade, parallax and text in/out. */
    fun <T> content(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(dampingRatio = 0.9f, stiffness = 200f, visibilityThreshold = visibilityThreshold)

    /** The home screen scrolling between the hero and the app grid; critically damped, so it never bounces. */
    fun <T> camera(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 300f)

    /** Menus and folders scaling in from their anchor. */
    fun <T> overlay(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 500f)

    /** Finishing a hero change quickly because the next one is waiting (fast key presses). */
    fun <T> hurry(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 900f)

    /** Quick, fully damped exits (outgoing hero text). */
    fun <T> exit(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 1500f)

    /** Colour changes (focus fills); critically damped so colours never overshoot. */
    fun <T> color(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 500f)

    /** The one-shot specular sweep across a newly focused element. */
    fun <T> sweep(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 40f)
}
