package io.github.kesemb2.glasshome.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

/**
 * The app's own language (Settings › Language), independent of the TV's: Hebrew, English, or the system's.
 * Applied by wrapping each activity's base context; changing it recreates the activity. The process-wide default
 * locale follows it too, so TMDB answers in the same language.
 */
object AppLanguage {
    const val SYSTEM = "system"
    const val HEBREW = "he"
    const val ENGLISH = "en"
    val ALL = listOf(SYSTEM, HEBREW, ENGLISH)

    private const val PREFS = "settings"
    private const val KEY = "language"

    internal fun stored(prefs: SharedPreferences): String =
        prefs.getString(KEY, SYSTEM)?.takeIf { it in ALL } ?: SYSTEM

    fun set(context: Context, language: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language).apply()
    }

    /** The locale for a language choice; null follows the system. */
    fun locale(language: String): Locale? = when (language) {
        HEBREW -> Locale.forLanguageTag("he-IL")
        ENGLISH -> Locale.forLanguageTag("en-US")
        else -> null
    }

    /** [base] with the chosen language applied (unchanged when following the system). */
    fun wrap(base: Context): Context {
        val chosen = locale(stored(base.getSharedPreferences(PREFS, Context.MODE_PRIVATE)))
        val locale = chosen ?: Resources.getSystem().configuration.locales[0]
        Locale.setDefault(locale)
        if (chosen == null) return base
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(chosen))
        config.setLayoutDirection(chosen)
        return base.createConfigurationContext(config)
    }
}
