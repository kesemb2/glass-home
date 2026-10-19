package io.github.kesemb2.glasshome.data.settings

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import io.github.kesemb2.glasshome.ui.apps.CardSize
import io.github.kesemb2.glasshome.ui.glass.LiquidGlass

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

    private val phoneApps = Flag("show_phone_apps", false)
    private val columns = Choice("app_columns", CardSize.DEFAULT_COLUMNS, CardSize.MIN_COLUMNS..CardSize.MAX_COLUMNS)
    private val logo = Choice("logo_percent", DEFAULT_LOGO_PERCENT, LOGO_PERCENTS.first()..LOGO_PERCENTS.last())
    private val text = Choice("text_percent", DEFAULT_TEXT_PERCENT, TEXT_PERCENTS.first()..TEXT_PERCENTS.last())
    private val autoAdvance = Flag("hero_auto_advance", true)
    private val refraction = Flag("glass_refraction", true)
    private val perf = Flag("perf_overlay", false)

    init {
        LiquidGlass.enabled = refraction.value
    }

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

    /** The lens shader for glass surfaces; off draws the simpler layered glass. */
    var glassRefraction: Boolean
        get() = refraction.value
        set(v) {
            refraction.value = v
            LiquidGlass.enabled = v
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

    /** The app's language: [AppLanguage.SYSTEM], [AppLanguage.HEBREW] or [AppLanguage.ENGLISH]. */
    val language: String get() = AppLanguage.stored(prefs)

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
    }
}
