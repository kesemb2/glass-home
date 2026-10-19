package io.github.kesemb2.glasshome.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.kesemb2.glasshome.BuildConfig
import io.github.kesemb2.glasshome.R
import io.github.kesemb2.glasshome.ui.components.MenuRow
import io.github.kesemb2.glasshome.ui.glass.glass
import io.github.kesemb2.glasshome.ui.glass.glassShadow
import io.github.kesemb2.glasshome.ui.home.AppsController
import io.github.kesemb2.glasshome.ui.home.SettingsPage
import io.github.kesemb2.glasshome.ui.overlay.OverlayLayer
import io.github.kesemb2.glasshome.ui.theme.GlassColors
import io.github.kesemb2.glasshome.ui.theme.LocalGlassType
import io.github.kesemb2.glasshome.ui.theme.Shapes
import io.github.kesemb2.glasshome.ui.theme.Spacing

/** Settings: a glass panel at the end side of the screen, over the blurred home screen. */
@Composable
fun SettingsSheet(page: SettingsPage, top: Boolean, apps: AppsController, onPage: (SettingsPage) -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val slidePx = with(LocalDensity.current) { 48.dp.toPx() } * (if (rtl) -1f else 1f)
    OverlayLayer(top = top, dim = 0.3f) { appear ->
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = Spacing.ScreenHorizontal, top = Spacing.ScreenVertical, bottom = Spacing.ScreenVertical)
                .width(PANEL_WIDTH)
                .fillMaxHeight()
                .graphicsLayer { translationX = (1f - appear()) * slidePx }
                .glassShadow(Shapes.Menu, 24.dp)
                .glass(Shapes.Menu)
                .padding(horizontal = 12.dp, vertical = 20.dp),
        ) {
            key(page) {
                when (page) {
                    SettingsPage.Main -> MainPage(apps, onPage)
                    SettingsPage.Hidden -> HiddenPage(apps)
                    SettingsPage.About -> AboutPage()
                }
            }
        }
    }
}

private val PANEL_WIDTH = 400.dp

@Composable
private fun ColumnScope.Title(text: String) {
    val type = LocalGlassType.current
    BasicText(
        text = text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        style = type.title2.copy(color = GlassColors.TextPrimary),
    )
}

@Composable
private fun rememberFirstFocus(key: Any = Unit): FocusRequester {
    val first = remember { FocusRequester() }
    LaunchedEffect(key) {
        try {
            first.requestFocus()
        } catch (_: IllegalStateException) {
            // Nothing focusable on this page.
        }
    }
    return first
}

@Composable
private fun ColumnScope.MainPage(apps: AppsController, onPage: (SettingsPage) -> Unit) {
    val settings = apps.settings
    val first = rememberFirstFocus()
    val on = stringResource(R.string.settings_on)
    val off = stringResource(R.string.settings_off)
    fun state(value: Boolean) = if (value) on else off

    Title(stringResource(R.string.settings_title))
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        MenuRow(
            text = stringResource(R.string.settings_phone_apps),
            secondary = stringResource(R.string.settings_phone_apps_hint),
            trailing = state(settings.showPhoneApps),
            onClick = { apps.setShowPhoneApps(!settings.showPhoneApps) },
            modifier = Modifier.focusRequester(first),
        )
        MenuRow(
            text = stringResource(R.string.settings_auto_advance),
            secondary = stringResource(R.string.settings_auto_advance_hint),
            trailing = state(settings.heroAutoAdvance),
            onClick = { settings.heroAutoAdvance = !settings.heroAutoAdvance },
        )
        MenuRow(
            text = stringResource(R.string.settings_glass),
            secondary = stringResource(R.string.settings_glass_hint),
            trailing = state(settings.glassRefraction),
            onClick = { settings.glassRefraction = !settings.glassRefraction },
        )
        MenuRow(
            text = stringResource(R.string.settings_perf),
            secondary = stringResource(R.string.settings_perf_hint),
            trailing = state(settings.perfOverlay),
            onClick = { settings.perfOverlay = !settings.perfOverlay },
        )
        MenuRow(
            text = stringResource(R.string.settings_hidden),
            trailing = apps.layout.hidden.count { it in apps.apps }.toString(),
            icon = R.drawable.ic_hide,
            onClick = { onPage(SettingsPage.Hidden) },
        )
        MenuRow(
            text = stringResource(R.string.settings_about),
            icon = R.drawable.ic_info,
            onClick = { onPage(SettingsPage.About) },
        )
    }
}

@Composable
private fun ColumnScope.HiddenPage(apps: AppsController) {
    val type = LocalGlassType.current
    val hidden = apps.layout.hidden.mapNotNull { apps.apps[it] }.sortedBy { it.label.lowercase() }
    // Refocus the list when a row leaves it.
    val first = rememberFirstFocus(hidden.size)
    Title(stringResource(R.string.settings_hidden))
    if (hidden.isEmpty()) {
        BasicText(
            text = stringResource(R.string.settings_hidden_empty),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = type.body.copy(color = GlassColors.TextSecondary),
        )
        return
    }
    BasicText(
        text = stringResource(R.string.settings_hidden_hint),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        style = type.caption.copy(color = GlassColors.TextSecondary),
    )
    val show = stringResource(R.string.settings_unhide)
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        hidden.forEachIndexed { i, app ->
            key(app.packageName) {
                MenuRow(
                    text = app.label,
                    trailing = show,
                    icon = R.drawable.ic_show,
                    onClick = { apps.unhide(app.packageName) },
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.AboutPage() {
    val type = LocalGlassType.current
    val first = rememberFirstFocus()
    Title(stringResource(R.string.settings_about))
    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        MenuRow(
            text = stringResource(R.string.app_name),
            secondary = stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.GIT_SHA),
            onClick = {},
            modifier = Modifier.focusRequester(first),
        )
        Spacer(Modifier.height(Spacing.XL))
        Image(
            painter = painterResource(R.drawable.tmdb_logo),
            contentDescription = "TMDB",
            modifier = Modifier.size(width = 56.dp, height = 40.dp),
        )
        Spacer(Modifier.height(Spacing.M))
        BasicText(stringResource(R.string.about_tmdb), style = type.callout.copy(color = GlassColors.TextPrimary))
        Spacer(Modifier.height(Spacing.XL))
        BasicText(stringResource(R.string.about_credits), style = type.caption.copy(color = GlassColors.TextSecondary))
    }
}
