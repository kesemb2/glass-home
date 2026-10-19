package io.github.kesemb2.glasshome.data.settings

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import io.github.kesemb2.glasshome.system.HomeSystem
import io.github.kesemb2.glasshome.ui.apps.CardSize
import io.github.kesemb2.glasshome.ui.glass.GlassStyle
import io.github.kesemb2.glasshome.ui.hero.ScrimColors

/** The launcher's settings, observable by Compose and saved as soon as they change. */
@Stable
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private inner class Flag(private val key: String, default: Boolean) {
        val state = mutableStateOf(prefs.getBoolean(key, default))
        var value: Boolean
            get() = state.value
            set(v) {
                state.value = v
                prefs.edit().putBoolean(key, v).apply()
            }
    }

    private inner class Choice(private val key: String, default: Int, private val range: IntRange) {
        val state = mutableStateOf(prefs.getInt(key, default).coerceIn(range))
        var value: Int
            get() = state.value
            set(v) {
                state.value = v.coerceIn(range)
                prefs.edit().putInt(key, state.value).apply()
            }
    }

    private inner class Text(private val key: String, default: String) {
        val state = mutableStateOf(prefs.getString(key, null) ?: default)
        var value: String
            get() = state.value
            set(v) {
                state.value = v
                prefs.edit().putString(key, v).apply()
            }
    }

    private val phoneApps = Flag("show_phone_apps", false)
    private val columns = Choice("app_columns", CardSize.DEFAULT_COLUMNS, CardSize.MIN_COLUMNS..CardSize.MAX_COLUMNS)
    private val logo = Choice("logo_percent", DEFAULT_LOGO_PERCENT, LOGO_PERCENTS.first()..LOGO_PERCENTS.last())
    private val text = Choice("text_percent", DEFAULT_TEXT_PERCENT, TEXT_PERCENTS.first()..TEXT_PERCENTS.last())
    private val autoAdvance = Flag("hero_auto_advance", true)
    private val perf = Flag("perf_overlay", false)
    private val homeButtonFlag = Flag(HomeSystem.KEY_HOME_BUTTON, true)
    private val replaceHomeFlag = Flag(HomeSystem.KEY_REPLACE_HOME, true)
    private val glassLive = Flag("glass_live", true)
    private val glassBlur = Choice("glass_blur_dp", DEFAULT_BLUR, BLURS.first()..BLURS.last())
    private val glassRefraction = Choice("glass_refraction_pct", DEFAULT_REFRACTION, REFRACTIONS.first()..REFRACTIONS.last())
    private val glassBrightness = Choice("glass_brightness_pct", DEFAULT_BRIGHTNESS, BRIGHTNESSES.first()..BRIGHTNESSES.last())
    private val glassTransparency =
        Choice("glass_transparency_pct", DEFAULT_TRANSPARENCY, TRANSPARENCIES.first()..TRANSPARENCIES.last())
    private val scrim = Text("scrim_color", ScrimColors.BLACK)

    /** Also show apps that only have a phone/tablet launcher activity. */
    var showPhoneApps: Boolean
        get() = phoneApps.value
        set(v) {
            phoneApps.value = v
        }

    /** Continue Watching moves to the next item every 8 s while the hero is focused. */
    var heroAutoAdvance: Boolean
        get() = autoAdvance.value
        set(v) {
            autoAdvance.value = v
        }

    /** Live glass: follows the content behind it every frame (off: rendered once per backdrop image). */
    var liveGlass: Boolean
        get() = glassLive.value
        set(v) {
            glassLive.value = v
        }

    /** Blur behind live glass, dp. */
    var blurDp: Int
        get() = glassBlur.value
        set(v) {
            glassBlur.value = v
        }

    /** Refraction strength, percent of the designed lens. */
    var refractionPercent: Int
        get() = glassRefraction.value
        set(v) {
            glassRefraction.value = v
        }

    /** Glass brightness, -30..30 percent. */
    var brightnessPercent: Int
        get() = glassBrightness.value
        set(v) {
            glassBrightness.value = v
        }

    /** Glass transparency, percent: 100 is clear glass, lower is more frosted. */
    var transparencyPercent: Int
        get() = glassTransparency.value
        set(v) {
            glassTransparency.value = v
        }

    /** The glass look for [io.github.kesemb2.glasshome.ui.glass.LocalGlassStyle]. */
    val glassStyle: GlassStyle
        get() = GlassStyle(
            live = liveGlass,
            blurDp = blurDp.toFloat(),
            refraction = refractionPercent / 100f,
            brightness = brightnessPercent / 100f,
            frost = (100 - transparencyPercent) / 100f * MAX_FROST,
        )

    fun resetGlass() {
        liveGlass = true
        blurDp = DEFAULT_BLUR
        refractionPercent = DEFAULT_REFRACTION
        brightnessPercent = DEFAULT_BRIGHTNESS
        transparencyPercent = DEFAULT_TRANSPARENCY
    }

    /** App cards per grid row (5 = largest cards). */
    var appColumns: Int
        get() = columns.value
        set(v) {
            columns.value = v
        }

    /** Hero logo (and title) size, percent of the largest. */
    var logoPercent: Int
        get() = logo.value
        set(v) {
            logo.value = v
        }

    /** Hero details (episode line, synopsis, time left) size, percent. */
    var textPercent: Int
        get() = text.value
        set(v) {
            text.value = v
        }

    /** The hero's gradient colour, one of [ScrimColors.KEYS]. */
    var scrimColor: String
        get() = scrim.value
        set(v) {
            scrim.value = v
        }

    /** The app's language:[AppLanguage.SYSTEM], [AppLanguage.HEBREW] or [AppLanguage.ENGLISH]. */
    val language: String get() = AppLanguage.stored(prefs)

    /** With the Home helper on: the Home button opens Glass Home. */
    var homeButton: Boolean
        get() = homeButtonFlag.value
        set(v) {
            homeButtonFlag.value = v
        }

    /** With the Home helper on: when the TV's own home screen opens (also at startup), Glass Home replaces it. */
    var replaceHome: Boolean
        get() = replaceHomeFlag.value
        set(v) {
            replaceHomeFlag.value = v
        }

    /** Frame rate and memory in a corner of the screen. */
    var perfOverlay: Boolean
        get() = perf.value
        set(v) {
            perf.value = v
        }

    companion object {
        val LOGO_PERCENTS = listOf(50, 60, 70, 80, 90, 100)
        const val DEFAULT_LOGO_PERCENT = 70
        val TEXT_PERCENTS = listOf(80, 90, 100, 110, 120, 130)
        const val DEFAULT_TEXT_PERCENT = 100

        val BLURS = listOf(0, 2, 4, 6, 8, 12, 16, 24, 32)
        const val DEFAULT_BLUR = 6
        val REFRACTIONS = listOf(0, 25, 50, 75, 100, 125, 150, 200)
        const val DEFAULT_REFRACTION = 100
        val BRIGHTNESSES = listOf(-30, -20, -10, 0, 10, 20, 30)
        const val DEFAULT_BRIGHTNESS = 0
        val TRANSPARENCIES = listOf(0, 20, 40, 50, 60, 70, 80, 90, 100)
        const val DEFAULT_TRANSPARENCY = 70

        /** Frost at 0% transparency; 70% gives the designed 0.14. */
        private const val MAX_FROST = 0.47f
    }
}
