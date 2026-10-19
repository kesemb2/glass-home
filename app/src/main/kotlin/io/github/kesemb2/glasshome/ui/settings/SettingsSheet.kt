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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.kesemb2.glasshome.BuildConfig
import io.github.kesemb2.glasshome.R
import io.github.kesemb2.glasshome.data.settings.AppLanguage
import io.github.kesemb2.glasshome.data.settings.AppSettings
import io.github.kesemb2.glasshome.ui.apps.CardSize
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
fun SettingsSheet(
    page: SettingsPage,
    top: Boolean,
    apps: AppsController,
    onPage: (SettingsPage) -> Unit,
    onLanguage: (String) -> Unit,
    onCovering: (Boolean) -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val slidePx = with(LocalDensity.current) { 48.dp.toPx() } * (if (rtl) -1f else 1f)
    OverlayLayer(top = top, dim = 0.3f, onCovering = onCovering) { appear ->
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
                    SettingsPage.Language -> LanguagePage(apps.settings.language, onLanguage)
                    SettingsPage.Glass -> GlassPage(apps.settings)
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

    val sizeNames = mapOf(
        5 to stringResource(R.string.size_large),
        6 to stringResource(R.string.size_medium),
        7 to stringResource(R.string.size_small),
        8 to stringResource(R.string.size_extra_small),
    )

    Title(stringResource(R.string.settings_title))
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        MenuRow(
            text = stringResource(R.string.settings_language),
            trailing = languageName(settings.language),
            onClick = { onPage(SettingsPage.Language) },
            modifier = Modifier.focusRequester(first),
        )
        MenuRow(
            text = stringResource(R.string.settings_glass),
            secondary = stringResource(R.string.settings_glass_hint),
            onClick = { onPage(SettingsPage.Glass) },
        )
        ChoiceRow(
            text = stringResource(R.string.settings_app_size),
            secondary = stringResource(R.string.settings_app_size_hint),
            options = (CardSize.MIN_COLUMNS..CardSize.MAX_COLUMNS).toList(),
            selected = settings.appColumns,
            label = { sizeNames[it] ?: it.toString() },
            onSelect = apps::setAppColumns,
        )
        ChoiceRow(
            text = stringResource(R.string.settings_logo_size),
            secondary = stringResource(R.string.settings_logo_size_hint),
            options = AppSettings.LOGO_PERCENTS,
            selected = settings.logoPercent,
            label = { "$it%" },
            onSelect = { settings.logoPercent = it },
        )
        ChoiceRow(
            text = stringResource(R.string.settings_text_size),
            secondary = stringResource(R.string.settings_text_size_hint),
            options = AppSettings.TEXT_PERCENTS,
            selected = settings.textPercent,
            label = { "$it%" },
            onSelect = { settings.textPercent = it },
        )
        MenuRow(
            text = stringResource(R.string.settings_phone_apps),
            secondary = stringResource(R.string.settings_phone_apps_hint),
            trailing = state(settings.showPhoneApps),
            onClick = { apps.setShowPhoneApps(!settings.showPhoneApps) },
        )
        MenuRow(
            text = stringResource(R.string.settings_auto_advance),
            secondary = stringResource(R.string.settings_auto_advance_hint),
            trailing = state(settings.heroAutoAdvance),
            onClick = { settings.heroAutoAdvance = !settings.heroAutoAdvance },
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

/**
 * A row whose value steps with Left/Right (towards the end of the line = next) and cycles with OK. The value is
 * shown between angle marks as a hint that it can be changed.
 */
@Composable
private fun <T> ChoiceRow(
    text: String,
    secondary: String?,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val index = options.indexOf(selected).coerceAtLeast(0)
    MenuRow(
        text = text,
        secondary = secondary,
        trailing = "‹  ${label(options[index])}  ›",
        onClick = { onSelect(options[(index + 1) % options.size]) },
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val next = if (rtl) Key.DirectionLeft else Key.DirectionRight
            val previous = if (rtl) Key.DirectionRight else Key.DirectionLeft
            when (event.key) {
                next -> {
                    if (index < options.lastIndex) onSelect(options[index + 1])
                    true
                }
                previous -> {
                    if (index > 0) onSelect(options[index - 1])
                    true
                }
                else -> false
            }
        },
    )
}

@Composable
private fun languageName(language: String): String = when (language) {
    AppLanguage.HEBREW -> "עברית"
    AppLanguage.ENGLISH -> "English"
    else -> stringResource(R.string.settings_language_system)
}

/**
 * The glass look. This panel is glass itself, so every change shows on it at once (and on the screen behind it
 * when live glass is on).
 */
@Composable
private fun ColumnScope.GlassPage(settings: AppSettings) {
    val first = rememberFirstFocus()
    val on = stringResource(R.string.settings_on)
    val off = stringResource(R.string.settings_off)
    Title(stringResource(R.string.settings_glass))
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        MenuRow(
            text = stringResource(R.string.glass_live),
            secondary = stringResource(R.string.glass_live_hint),
            trailing = if (settings.liveGlass) on else off,
            onClick = { settings.liveGlass = !settings.liveGlass },
            modifier = Modifier.focusRequester(first),
        )
        ChoiceRow(
            text = stringResource(R.string.glass_blur),
            secondary = stringResource(R.string.glass_blur_hint),
            options = AppSettings.BLURS,
            selected = settings.blurDp,
            label = { it.toString() },
            onSelect = { settings.blurDp = it },
        )
        ChoiceRow(
            text = stringResource(R.string.glass_refraction),
            secondary = stringResource(R.string.glass_refraction_hint),
            options = AppSettings.REFRACTIONS,
            selected = settings.refractionPercent,
            label = { "$it%" },
            onSelect = { settings.refractionPercent = it },
        )
        ChoiceRow(
            text = stringResource(R.string.glass_brightness),
            secondary = null,
            options = AppSettings.BRIGHTNESSES,
            selected = settings.brightnessPercent,
            label = { if (it > 0) "+$it%" else "$it%" },
            onSelect = { settings.brightnessPercent = it },
        )
        ChoiceRow(
            text = stringResource(R.string.glass_transparency),
            secondary = stringResource(R.string.glass_transparency_hint),
            options = AppSettings.TRANSPARENCIES,
            selected = settings.transparencyPercent,
            label = { "$it%" },
            onSelect = { settings.transparencyPercent = it },
        )
        MenuRow(
            text = stringResource(R.string.glass_reset),
            onClick = { settings.resetGlass() },
        )
    }
}

/** The app's language, independent of the TV's; choosing one restarts the home screen in it. */
@Composable
private fun ColumnScope.LanguagePage(current: String, onLanguage: (String) -> Unit) {
    val first = rememberFirstFocus()
    Title(stringResource(R.string.settings_language))
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        AppLanguage.ALL.forEachIndexed { i, language ->
            MenuRow(
                text = languageName(language),
                trailing = if (language == current) "✓" else null,
                onClick = { if (language != current) onLanguage(language) },
                modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
            )
        }
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
