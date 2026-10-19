package io.github.kesemb2.glasshome.data.apps

import androidx.compose.runtime.Immutable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A folder on the home grid. */
@Immutable
data class AppFolder(val id: String, val name: String, val apps: List<String>)

/**
 * The user's arrangement of the home screen: the favorites shelf, the grid order (apps and folders), folder
 * contents and hidden apps. Apps are identified by package name; grid entries are [GridKey]s.
 *
 * Every launchable app on the device has a place: in the grid, in exactly one folder, or hidden. Favorites are
 * a separate list of shortcuts, so a favorite also keeps its place in the grid. Pure and immutable: every edit
 * returns a new layout. Edits that take a `shown` filter work on what is on screen, so apps that are not
 * shown (phone apps while that setting is off) keep their place without getting in the way.
 */
@Immutable
data class HomeLayout(
    val favorites: List<String> = emptyList(),
    val grid: List<String> = emptyList(),
    val folders: Map<String, AppFolder> = emptyMap(),
    val hidden: Set<String> = emptySet(),
    val nextFolder: Int = 1,
    val seeded: Boolean = false,
) {
    fun isFavorite(pkg: String): Boolean = pkg in favorites

    fun folderOf(pkg: String): AppFolder? = folders.values.firstOrNull { pkg in it.apps }

    /**
     * Brings the layout in line with the installed apps ([installed], in display order): drops apps that are
     * gone, appends new ones to the end of the grid, and on the very first run fills the favorites shelf.
     */
    fun reconcile(installed: List<String>): HomeLayout {
        val present = installed.toHashSet()
        val hidden = hidden.filterTo(LinkedHashSet()) { it in present }
        val placed = HashSet<String>()
        val folders = LinkedHashMap<String, AppFolder>()
        for (folder in this.folders.values) {
            val apps = folder.apps.filter { it in present && it !in hidden && placed.add(it) }
            if (apps.isNotEmpty()) folders[folder.id] = folder.copy(apps = apps)
        }
        val seen = HashSet<String>()
        val grid = ArrayList<String>()
        for (key in this.grid) {
            val folderId = GridKey.folderId(key)
            val keep = if (folderId != null) {
                folderId in folders
            } else {
                val pkg = GridKey.packageOf(key)
                pkg != null && pkg in present && pkg !in hidden && placed.add(pkg)
            }
            if (keep && seen.add(key)) grid.add(key)
        }
        for (id in folders.keys) if (seen.add(GridKey.folder(id))) grid.add(GridKey.folder(id))
        for (pkg in installed) if (pkg !in hidden && placed.add(pkg)) grid.add(GridKey.app(pkg))

        var favorites = favorites.filter { it in present && it !in hidden }.distinct()
        var seeded = seeded
        if (!seeded && installed.isNotEmpty()) {
            favorites = seedFavorites(installed)
            seeded = true
        }
        return copy(favorites = favorites, grid = grid, folders = folders, hidden = hidden, seeded = seeded)
    }

    fun toggleFavorite(pkg: String): HomeLayout =
        copy(favorites = if (pkg in favorites) favorites - pkg else favorites + pkg)

    fun hide(pkg: String): HomeLayout = without(pkg).copy(favorites = favorites - pkg, hidden = hidden + pkg)

    fun unhide(pkg: String): HomeLayout =
        if (pkg !in hidden) this else copy(hidden = hidden - pkg, grid = grid + GridKey.app(pkg))

    /** A new folder holding [pkg], placed where the app was in the grid (or at the end). Returns it with its id. */
    fun newFolder(pkg: String, name: String): Pair<HomeLayout, String> {
        val id = nextFolder.toString()
        val index = grid.indexOf(GridKey.app(pkg))
        val base = without(pkg)
        val folderKey = GridKey.folder(id)
        val grid = base.grid.toMutableList().apply {
            if (index in 0..size) add(index, folderKey) else add(folderKey)
        }
        val folders = LinkedHashMap(base.folders).apply { put(id, AppFolder(id, name, listOf(pkg))) }
        return base.copy(grid = grid, folders = folders, nextFolder = nextFolder + 1) to id
    }

    fun addToFolder(pkg: String, folderId: String): HomeLayout {
        if (folderId !in folders || folders[folderId]?.apps?.contains(pkg) == true) return this
        val base = without(pkg)
        val folder = base.folders[folderId] ?: return this
        return base.copy(folders = base.folders + (folderId to folder.copy(apps = folder.apps + pkg)))
    }

    /** Takes [pkg] out of its folder and puts it in the grid right after the folder. */
    fun removeFromFolder(pkg: String): HomeLayout {
        val folder = folderOf(pkg) ?: return this
        val folderKey = GridKey.folder(folder.id)
        val at = grid.indexOf(folderKey)
        val base = without(pkg)
        val grid = base.grid.toMutableList()
        val folderAt = grid.indexOf(folderKey)
        val insertAt = if (folderAt >= 0) folderAt + 1 else if (at >= 0) at.coerceAtMost(grid.size) else grid.size
        grid.add(insertAt, GridKey.app(pkg))
        return base.copy(grid = grid)
    }

    /** Dissolves a folder; its apps take its place in the grid, in folder order. */
    fun removeFolder(folderId: String): HomeLayout {
        val folder = folders[folderId] ?: return this
        val key = GridKey.folder(folderId)
        val apps = folder.apps.map(GridKey::app)
        val grid = if (key in grid) grid.flatMap { if (it == key) apps else listOf(it) } else grid + apps
        return copy(grid = grid, folders = folders - folderId)
    }

    fun moveInGrid(key: String, delta: Int, shown: (String) -> Boolean): HomeLayout =
        copy(grid = grid.moveAmongShown(key, delta, shown))

    fun moveInFavorites(pkg: String, delta: Int, shown: (String) -> Boolean): HomeLayout =
        copy(favorites = favorites.moveAmongShown(pkg, delta, shown))

    fun moveInFolder(folderId: String, pkg: String, delta: Int, shown: (String) -> Boolean): HomeLayout {
        val folder = folders[folderId] ?: return this
        return copy(folders = folders + (folderId to folder.copy(apps = folder.apps.moveAmongShown(pkg, delta, shown))))
    }

    /** Removes [pkg] from the grid and from any folder; a folder left empty disappears. Favorites are untouched. */
    private fun without(pkg: String): HomeLayout {
        val emptied = HashSet<String>()
        val folders = LinkedHashMap<String, AppFolder>()
        for ((id, folder) in this.folders) {
            val apps = folder.apps - pkg
            if (apps.isEmpty()) emptied.add(GridKey.folder(id)) else folders[id] = folder.copy(apps = apps)
        }
        val appKey = GridKey.app(pkg)
        return copy(grid = grid.filter { it != appKey && it !in emptied }, folders = folders)
    }

    fun toJson(): String = JSONObject().apply {
        put("v", 1)
        put("favorites", JSONArray(favorites))
        put("grid", JSONArray(grid))
        put(
            "folders",
            JSONArray().apply {
                folders.values.forEach {
                    put(JSONObject().put("id", it.id).put("name", it.name).put("apps", JSONArray(it.apps)))
                }
            },
        )
        put("hidden", JSONArray(hidden.toList()))
        put("next", nextFolder)
        put("seeded", seeded)
    }.toString()

    companion object {
        /** Shown first on the favorites shelf on the first run, when installed. */
        private val PREFERRED_FAVORITES = listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom",
            "com.apple.atve.androidtv.appletv",
            "com.plexapp.android",
            "org.xbmc.kodi",
            "com.spotify.tv.android",
        )
        const val SEED_COUNT = 5

        internal fun seedFavorites(installed: List<String>): List<String> {
            val present = installed.toHashSet()
            val picked = PREFERRED_FAVORITES.filter { it in present }.take(SEED_COUNT).toMutableList()
            for (pkg in installed) {
                if (picked.size >= SEED_COUNT) break
                if (pkg !in picked) picked.add(pkg)
            }
            return picked
        }

        fun fromJson(json: String?): HomeLayout {
            if (json.isNullOrBlank()) return HomeLayout()
            return try {
                val o = JSONObject(json)
                val folders = LinkedHashMap<String, AppFolder>()
                o.optJSONArray("folders")?.let { array ->
                    for (i in 0 until array.length()) {
                        val f = array.optJSONObject(i) ?: continue
                        val id = f.optString("id").takeIf { it.isNotEmpty() } ?: continue
                        folders[id] = AppFolder(id, f.optString("name"), f.optJSONArray("apps").strings())
                    }
                }
                HomeLayout(
                    favorites = o.optJSONArray("favorites").strings(),
                    grid = o.optJSONArray("grid").strings(),
                    folders = folders,
                    hidden = o.optJSONArray("hidden").strings().toCollection(LinkedHashSet()),
                    nextFolder = o.optInt("next", folders.size + 1).coerceAtLeast(1),
                    seeded = o.optBoolean("seeded", false),
                )
            } catch (_: JSONException) {
                HomeLayout()
            }
        }

        private fun JSONArray?.strings(): List<String> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { i -> optString(i).takeIf { it.isNotEmpty() } }
        }
    }
}

/** Grid entries: `a:<package>` for an app, `f:<folder id>` for a folder. */
object GridKey {
    fun app(pkg: String): String = "a:$pkg"
    fun folder(id: String): String = "f:$id"
    fun packageOf(key: String): String? = key.takeIf { it.startsWith("a:") }?.substring(2)
    fun folderId(key: String): String? = key.takeIf { it.startsWith("f:") }?.substring(2)
}

/**
 * Moves [item] by [delta] places counted among the entries that are [shown]; hidden entries in between keep
 * their place. Moving past either end stops there.
 */
internal fun <T> List<T>.moveAmongShown(item: T, delta: Int, shown: (T) -> Boolean): List<T> {
    if (delta == 0) return this
    val visible = filter(shown)
    val from = visible.indexOf(item)
    if (from < 0) return this
    val to = (from + delta).coerceIn(0, visible.size - 1)
    if (to == from) return this
    val anchor = visible[to]
    val result = toMutableList()
    result.remove(item)
    val anchorAt = result.indexOf(anchor)
    result.add(if (delta > 0) anchorAt + 1 else anchorAt, item)
    return result
}
