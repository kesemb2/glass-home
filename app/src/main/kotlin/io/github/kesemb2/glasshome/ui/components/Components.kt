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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
 * with dark content. Hold OK for [onLongClick].
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
) {
    val type = LocalGlassType.current
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
            .padding(horizontal = 26.dp, vertical = 12.dp),
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
            Spacer(Modifier.width(10.dp))
        }
        BasicText(text = text, style = type.headline, color = { content.value })
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
