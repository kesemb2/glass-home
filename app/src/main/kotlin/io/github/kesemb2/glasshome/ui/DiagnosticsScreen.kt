package io.github.kesemb2.glasshome.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kesemb2.glasshome.BuildConfig
import io.github.kesemb2.glasshome.R
import io.github.kesemb2.glasshome.diagnostics.DeviceDiagnostics
import io.github.kesemb2.glasshome.diagnostics.DiagnosticsFormat

// Stage-0 placeholders; stage 1 replaces these with the real design tokens (see DESIGN.md).
private val TextPrimary = Color(0xFFF5F5F7)
private val TextSecondary = Color(0x99EBEBF5)
private val PillShape = RoundedCornerShape(percent = 50)
private val CardShape = RoundedCornerShape(20.dp)
private val FocusSpring = spring<Float>(dampingRatio = 0.75f, stiffness = 400f)
private val ColorSpring = spring<Color>(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)

@Composable
fun DiagnosticsScreen(
    diagnostics: DeviceDiagnostics?,
    onRefresh: () -> Unit,
    onAppInfo: () -> Unit,
) {
    val initialFocus = remember { FocusRequester() }

    Box(
        Modifier
            .fillMaxSize()
            .backdrop()
            // tvOS-style safe area on the 960x540 dp canvas.
            .padding(horizontal = 48.dp, vertical = 27.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            BasicText(
                text = stringResource(R.string.app_name),
                style = TextStyle(color = TextPrimary, fontSize = 40.sp, fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = stringResource(R.string.stage_title),
                style = TextStyle(color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Medium),
            )
            BasicText(
                text = stringResource(R.string.stage_subtitle),
                style = TextStyle(color = TextSecondary, fontSize = 16.sp),
            )
            Spacer(Modifier.height(28.dp))

            if (diagnostics != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    InfoCard(
                        title = stringResource(R.string.section_display),
                        rows = listOf(
                            stringResource(R.string.label_ui_surface) to DiagnosticsFormat.uiSurface(
                                diagnostics.uiWidthPx, diagnostics.uiHeightPx, diagnostics.density,
                            ),
                            stringResource(R.string.label_display_mode) to DiagnosticsFormat.displayMode(
                                diagnostics.modeWidthPx, diagnostics.modeHeightPx, diagnostics.refreshRateHz,
                            ),
                            stringResource(R.string.label_density) to DiagnosticsFormat.density(
                                diagnostics.densityDpi, diagnostics.density,
                            ),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    InfoCard(
                        title = stringResource(R.string.section_device),
                        rows = listOf(
                            stringResource(R.string.label_android) to
                                "${diagnostics.androidRelease} (API ${diagnostics.sdkInt})",
                            stringResource(R.string.label_model) to diagnostics.model,
                            stringResource(R.string.label_memory) to DiagnosticsFormat.memory(
                                diagnostics.totalRamBytes, diagnostics.memoryClassMb, diagnostics.isLowRamDevice,
                            ),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    InfoCard(
                        title = stringResource(R.string.section_build),
                        rows = listOf(
                            stringResource(R.string.label_version) to
                                "${BuildConfig.VERSION_NAME} · ${BuildConfig.GIT_SHA}",
                            stringResource(R.string.label_app_memory) to
                                "${(diagnostics.appPssKb + 512) / 1024} MB",
                            stringResource(R.string.label_tmdb) to stringResource(
                                if (BuildConfig.TMDB_TOKEN.isNotEmpty()) R.string.tmdb_present else R.string.tmdb_missing,
                            ),
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PillButton(
                    text = stringResource(R.string.button_refresh),
                    onClick = onRefresh,
                    modifier = Modifier.focusRequester(initialFocus),
                )
                PillButton(text = stringResource(R.string.button_app_info), onClick = onAppInfo)
            }
        }
    }

    LaunchedEffect(Unit) { initialFocus.requestFocus() }
}

/** Static dark backdrop with two soft colour glows; brushes are built once per size. */
private fun Modifier.backdrop(): Modifier = drawWithCache {
    val base = Brush.verticalGradient(listOf(Color(0xFF0E1222), Color(0xFF040509)))
    val blueGlow = Brush.radialGradient(
        colors = listOf(Color(0x664C6FFF), Color.Transparent),
        center = Offset(size.width * 0.12f, size.height * 0.05f),
        radius = size.width * 0.55f,
    )
    val violetGlow = Brush.radialGradient(
        colors = listOf(Color(0x4D9B4DFF), Color.Transparent),
        center = Offset(size.width * 0.92f, size.height * 0.95f),
        radius = size.width * 0.5f,
    )
    onDrawBehind {
        drawRect(base)
        drawRect(blueGlow)
        drawRect(violetGlow)
    }
}

@Composable
private fun InfoCard(title: String, rows: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Color(0x14FFFFFF), CardShape)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(listOf(Color(0x59FFFFFF), Color(0x0DFFFFFF))),
                shape = CardShape,
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicText(
            text = title,
            style = TextStyle(color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        )
        rows.forEach { (label, value) ->
            Column {
                BasicText(text = label, style = TextStyle(color = TextSecondary, fontSize = 13.sp))
                // Technical values stay left-to-right even inside a Hebrew layout.
                BasicText(
                    text = value,
                    style = TextStyle(
                        color = TextPrimary,
                        fontSize = 16.sp,
                        textDirection = TextDirection.ContentOrLtr,
                    ),
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val scale = animateFloatAsState(if (focused) 1.1f else 1f, FocusSpring, label = "pillScale")
    val fill = animateColorAsState(
        if (focused) Color.White else Color(0x1FFFFFFF), ColorSpring, label = "pillFill",
    )
    val content = animateColorAsState(
        if (focused) Color(0xFF0A0A0F) else TextPrimary, ColorSpring, label = "pillContent",
    )

    Box(
        modifier
            // Animated values are read in the layer/draw phases only, so spring frames skip recomposition.
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .drawBehind {
                val radius = CornerRadius(size.height / 2f)
                drawRoundRect(color = fill.value, cornerRadius = radius)
            }
            .border(1.dp, Color(0x33FFFFFF), PillShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
            color = { content.value },
        )
    }
}
