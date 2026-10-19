package io.github.kesemb2.glasshome.system

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils

/**
 * Glass Home as the TV's home screen. Two routes, because many Android TV builds do not let users change the home
 * app: the system HOME role where it can be granted, and the Home helper ([HomeButtonService]), an accessibility
 * service that catches the Home button and the TV's own home screen opening (also at startup) and opens Glass Home
 * instead.
 */
object HomeSystem {
    private const val PREFS = "settings"
    const val KEY_HOME_BUTTON = "home_button"
    const val KEY_REPLACE_HOME = "replace_home"
    private const val KEY_BYPASS_UNTIL = "home_bypass_until"

    /** After "Open the TV's home screen", the helper leaves it alone for this long (or until Home is pressed). */
    private const val BYPASS_MS = 10 * 60 * 1000L

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    /** True when the system already opens Glass Home for Home (the default home app). */
    fun isDefaultHome(context: Context): Boolean {
        val resolved = context.packageManager.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /** The system can be asked to make Glass Home the default home app (Android 10+, when the TV offers it). */
    fun canRequestHomeRole(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val roles = context.getSystemService(RoleManager::class.java) ?: return false
        return roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)
    }

    fun homeRoleRequest(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < 29) return null
        return context.getSystemService(RoleManager::class.java)?.createRequestRoleIntent(RoleManager.ROLE_HOME)
    }

    fun helperEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val ours = ComponentName(context, HomeButtonService::class.java)
        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
        return splitter.any { ComponentName.unflattenFromString(it) == ours }
    }

    /**
     * The other home screens on the TV (the TV's own launcher), as activities. Setup wizards and the settings app's
     * fallback home, which also answer the home intent on some builds, are left out.
     */
    fun otherHomes(context: Context): Set<ComponentName> {
        val pm = context.packageManager
        val infos = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(homeIntent(), PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(homeIntent(), 0)
        }
        return infos.mapNotNull { it.activityInfo }
            .filter { it.packageName != context.packageName && !isSystemPlumbing(it.packageName, it.name) }
            .mapTo(HashSet()) { ComponentName(it.packageName, it.name) }
    }

    private fun isSystemPlumbing(pkg: String, activity: String): Boolean =
        activity.contains("Fallback", ignoreCase = true) ||
            pkg.endsWith(".settings") ||
            pkg.contains("setup", ignoreCase = true) ||
            pkg.contains("provision", ignoreCase = true)

    fun homeButtonEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_HOME_BUTTON, true)

    fun replaceHomeEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_REPLACE_HOME, true)

    fun bypassed(context: Context): Boolean = SystemClock.elapsedRealtime() < prefs(context).getLong(KEY_BYPASS_UNTIL, 0L)

    fun clearBypass(context: Context) {
        prefs(context).edit().remove(KEY_BYPASS_UNTIL).apply()
    }

    /** Opens the TV's own home screen and keeps the helper from replacing it for a while. */
    fun openTvHome(context: Context): Boolean {
        val target = otherHomes(context).firstOrNull() ?: return false
        prefs(context).edit().putLong(KEY_BYPASS_UNTIL, SystemClock.elapsedRealtime() + BYPASS_MS).apply()
        return start(context, Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(target))
    }

    fun openTvSettings(context: Context): Boolean = start(context, Intent(Settings.ACTION_SETTINGS))

    /** The accessibility settings, where the Home helper is switched on. */
    fun openAccessibilitySettings(context: Context): Boolean =
        start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) || openTvSettings(context)

    /** The system's "default home app" page, on builds that have one. */
    fun openHomeSettings(context: Context): Boolean = start(context, Intent(Settings.ACTION_HOME_SETTINGS))

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
