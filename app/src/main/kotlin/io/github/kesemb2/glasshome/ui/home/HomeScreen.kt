package io.github.kesemb2.glasshome.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.kesemb2.glasshome.data.hero.HeroItem
import io.github.kesemb2.glasshome.ui.components.ClockPill
import io.github.kesemb2.glasshome.ui.glass.GlassBackdrop
import io.github.kesemb2.glasshome.ui.glass.LocalGlassBackdrop
import io.github.kesemb2.glasshome.ui.hero.HeroScreen
import io.github.kesemb2.glasshome.ui.theme.GlassColors
import io.github.kesemb2.glasshome.ui.theme.Spacing

/**
 * The home screen. While the launcher is in the background ([visible] false) nothing but the window colour
 * is composed, so every bitmap it held can be reclaimed.
 */
@Composable
fun HomeScreen(
    controller: HomeController,
    visible: Boolean,
    resumed: Boolean,
    lastKeyAt: () -> Long,
    onPlay: (HeroItem) -> Unit,
    onRequestAccess: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(GlassColors.Background),
    ) {
        if (visible) {
            val backdrop = remember { GlassBackdrop() }
            CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                HeroScreen(
                    state = controller.hero,
                    autoAdvance = resumed,
                    lastKeyAt = lastKeyAt,
                    onPlay = onPlay,
                    onDismiss = controller::dismiss,
                    onRequestAccess = onRequestAccess,
                )
                ClockPill(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = Spacing.ScreenVertical, end = Spacing.ScreenHorizontal),
                )
            }
        }
    }
}
