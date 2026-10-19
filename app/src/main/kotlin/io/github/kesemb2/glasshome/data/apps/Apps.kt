package io.github.kesemb2.glasshome.data.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Immutable
import java.text.Collator

/** A launchable app. [tv] is true when it has a TV (Leanback) launcher activity, false for phone-only apps. */
@Immutable
data class AppEntry(
    val packageName: String,
    val activity: String,
    val label: String,
    val tv: Boolean,
    val updatedAt: Long,
)

/** One entry of the home grid as shown: an app, or a folder with the apps it shows. */
@Immutable
sealed interface GridItem {
    val key: String

    data class App(val app: AppEntry) : GridItem {
        override val key: String get() = GridKey.app(app.packageName)
    }

    data class Folder(val folder: AppFolder, val apps: List<AppEntry>) : GridItem {
        override val key: String get() = GridKey.folder(folder.id)
    }
}

/** What the home screen shows for a layout: apps that are not installed or not [shown] are left out. */
@Immutable
data class HomeView(val favorites: List<AppEntry>, val grid: List<GridItem>) {
    companion object {
        fun of(layout: HomeLayout, apps: Map<String, AppEntry>, shown: (AppEntry) -> Boolean): HomeView {
            fun visible(pkg: String) = apps[pkg]?.takeIf(shown)
            val grid = layout.grid.mapNotNull { key ->
                val folderId = GridKey.folderId(key)
                if (folderId != null) {
                    val folder = layout.folders[folderId] ?: return@mapNotNull null
                    val inside = folder.apps.mapNotNull(::visible)
                    if (inside.isEmpty()) null else GridItem.Folder(folder, inside)
                } else {
                    GridKey.packageOf(key)?.let(::visible)?.let { GridItem.App(it) }
                }
            }
            return HomeView(layout.favorites.mapNotNull(::visible), grid)
        }
    }
}

/** Reads the launchable apps and starts them. */
class AppRepository(private val context: Context) {

    /** TV apps and phone apps, one entry per package (its TV activity when it has one), sorted by label. */
    fun query(): List<AppEntry> {
        val pm = context.packageManager
        val byPackage = LinkedHashMap<String, AppEntry>()
        for ((category, tv) in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER to true, Intent.CATEGORY_LAUNCHER to false)) {
            for (info in pm.launchers(category)) {
                val activity = info.activityInfo ?: continue
                val pkg = activity.packageName
                if (pkg == context.packageName || pkg in byPackage) continue
                val label = info.loadLabel(pm).toString().trim().ifEmpty { pkg }
                byPackage[pkg] = AppEntry(pkg, activity.name, label, tv, pm.updatedAt(pkg))
            }
        }
        val collator = Collator.getInstance()
        return byPackage.values.sortedWith(compareBy(collator) { it.label })
    }

    fun launch(app: AppEntry): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(if (app.tv) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
            .setClassName(app.packageName, app.activity)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        if (start(intent)) return true
        val pm = context.packageManager
        val fallback = pm.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: pm.getLaunchIntentForPackage(app.packageName)
            ?: return false
        return start(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openAppInfo(pkg: String): Boolean = start(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

private fun PackageManager.launchers(category: String): List<ResolveInfo> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
    return if (Build.VERSION.SDK_INT >= 33) {
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        queryIntentActivities(intent, 0)
    }
}

private fun PackageManager.updatedAt(pkg: String): Long = try {
    if (Build.VERSION.SDK_INT >= 33) {
        getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).lastUpdateTime
    } else {
        @Suppress("DEPRECATION")
        getPackageInfo(pkg, 0).lastUpdateTime
    }
} catch (_: PackageManager.NameNotFoundException) {
    0L
}
