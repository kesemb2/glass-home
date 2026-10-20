package io.github.kesemb2.glasshome.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kesemb2.glasshome.ui.glass.asPath
import io.github.kesemb2.glasshome.ui.glass.glass
import io.github.kesemb2.glasshome.ui.glass.glassShadow
import io.github.kesemb2.glasshome.ui.motion.FocusStyle
import io.github.kesemb2.glasshome.ui.motion.focusEffect
import io.github.kesemb2.glasshome.ui.motion.tvClickable
import io.github.kesemb2.glasshome.ui.theme.GlassColors
import io.github.kesemb2.glasshome.ui.theme.LocalGlassType
import io.github.kesemb2.glasshome.ui.theme.Shapes
import io.github.kesemb2.glasshome.ui.theme.Springs
import java.util.Date

/**
 * tvOS-style glass button: translucent glass at rest; when focused it scales up, lifts, and fills white
 * with dark content. Hold OK for [onLongClick]. [scale] sizes everything (48 dp high at 1).
 */
@Composable
fun GlassButton(
    text: String,
    @DrawableRes icon: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = Shapes.Pill,
    withGlass: Boolean = true,
    scale: Float = 1f,
) {
    val type = LocalGlassType.current
    val style = if (scale == 1f) type.headline else {
        type.headline.copy(fontSize = type.headline.fontSize * scale, lineHeight = type.headline.lineHeight * scale)
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val fill = animateColorAsState(
        if (focused) GlassColors.FocusFill else Color.Transparent, Springs.color(), label = "buttonFill",
    )
    val content = animateColorAsState(
        if (focused) GlassColors.OnFocus else GlassColors.TextPrimary, Springs.color(), label = "buttonContent",
    )
    val iconPainter = icon?.let { painterResource(it) }

    Row(
        modifier
            .focusEffect(focused, pressed, FocusStyle.Button, shape)
            .then(if (withGlass) Modifier.glass(shape) else Modifier)
            .drawWithCache {
                val path = shape.createOutline(size, layoutDirection, this).asPath()
                onDrawBehind { drawPath(path, fill.value) }
            }
            .tvClickable(interaction, onLongClick, onClick)
            .padding(horizontal = 26.dp * scale, vertical = 12.dp * scale),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconPainter != null) {
            Spacer(
                Modifier
                    .size(20.dp * scale)
                    .drawBehind {
                        with(iconPainter) { draw(size, colorFilter = ColorFilter.tint(content.value)) }
                    },
            )
            Spacer(Modifier.width(10.dp * scale))
        }
        BasicText(text = text, style = style, color = { content.value })
    }
}

/** Round glass button with an icon only (the settings button); white with a dark icon when focused. */
@Composable
fun GlassIconButton(
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val fill = animateColorAsState(
        if (focused) GlassColors.FocusFill else Color.Transparent, Springs.color(), label = "iconFill",
    )
    val content = animateColorAsState(
        if (focused) GlassColors.OnFocus else GlassColors.TextPrimary, Springs.color(), label = "iconContent",
    )
    val painter = painterResource(icon)
    Box(
        modifier
            .size(size)
            .glassShadow(Shapes.Pill, 6.dp)
            .focusEffect(focused, pressed, FocusStyle.Button, Shapes.Pill)
            .glass(Shapes.Pill)
            .drawBehind {
                drawCircle(fill.value)
                val iconSize = this.size.minDimension * 0.56f
                translate((this.size.width - iconSize) / 2f, (this.size.height - iconSize) / 2f) {
                    with(painter) { draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(content.value)) }
                }
            }
            .tvClickable(interaction, onClick = onClick),
    )
}

/**
 * A full-width row in a menu or settings panel: optional icon, text and a trailing value. Translucent at rest,
 * white with dark content when focused.
 */
@Composable
fun MenuRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    trailing: String? = null,
    secondary: String? = null,
    onFocused: (() -> Unit)? = null,
) {
    val type = LocalGlassType.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val fill = animateColorAsState(
        if (focused) GlassColors.FocusFill else Color.Transparent, Springs.color(), label = "rowFill",
    )
    val content = animateColorAsState(
        if (focused) GlassColors.OnFocus else GlassColors.TextPrimary, Springs.color(), label = "rowContent",
    )
    val dim = animateColorAsState(
        if (focused) GlassColors.OnFocus.copy(alpha = 0.6f) else GlassColors.TextSecondary, Springs.color(), label = "rowDim",
    )
    val iconPainter = icon?.let { painterResource(it) }
    val shape = Shapes.Row
    LaunchedEffect(focused) { if (focused) onFocused?.invoke() }
    Row(
        modifier
            .fillMaxWidth()
            .focusEffect(focused, pressed, FocusStyle.Row, shape)
            .drawWithCache {
                val path = shape.createOutline(size, layoutDirection, this).asPath()
                onDrawBehind { drawPath(path, fill.value) }
            }
            .tvClickable(interaction, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconPainter != null) {
            Spacer(
                Modifier
                    .size(20.dp)
                    .drawBehind {
                        with(iconPainter) { draw(size, colorFilter = ColorFilter.tint(content.value)) }
                    },
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            BasicText(text = text, style = type.headline, color = { content.value }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (secondary != null) {
                BasicText(text = secondary, style = type.caption, color = { dim.value }, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            BasicText(text = trailing, style = type.callout, color = { dim.value }, maxLines = 1)
        }
    }
}

/** Small glass pill with the time; redraws once a minute from the system TIME_TICK broadcast (no polling). */
@Composable
fun ClockPill(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val type = LocalGlassType.current
    var time by remember { mutableStateOf(formatTime(context)) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                time = formatTime(context)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        time = formatTime(context)
        onDispose { context.unregisterReceiver(receiver) }
    }

    Box(
        modifier
            .glassShadow(Shapes.Pill, 6.dp)
            .glass(Shapes.Pill)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        BasicText(
            text = time,
            style = type.callout.copy(color = GlassColors.TextPrimary, fontFeatureSettings = "tnum"),
        )
    }
}

private fun formatTime(context: Context): String = DateFormat.getTimeFormat(context).format(Date())
