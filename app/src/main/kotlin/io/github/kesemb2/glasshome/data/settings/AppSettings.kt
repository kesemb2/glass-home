package io.github.kesemb2.glasshome.data.settings

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
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

    private val phoneApps = Flag("show_phone_apps", false)
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

    /** Frame rate and memory in a corner of the screen. */
    var perfOverlay: Boolean
        get() = perf.value
        set(v) {
            perf.value = v
        }
}
