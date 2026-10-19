package io.github.kesemb2.glasshome.ui.home

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.node.ModifierNodeElement
import io.github.kesemb2.glasshome.data.apps.AppEntry
import io.github.kesemb2.glasshome.data.apps.GridItem

/** Which part of the home screen has focus; decides where the camera looks. */
enum class Area { Hero, Top, Shelf, Grid }

/** What a hold-OK menu is about. */
@Immutable
sealed interface MenuTarget {
    data class ShelfApp(val app: AppEntry) : MenuTarget
    data class GridApp(val app: AppEntry) : MenuTarget
    data class GridFolder(val folder: GridItem.Folder) : MenuTarget
    data class FolderApp(val app: AppEntry, val folderId: String) : MenuTarget
}

enum class MenuPage { Main, Folders }

enum class SettingsPage { Main, Home, Language, Glass, Hidden, About }

/** Layers above the home screen, newest last. [id] keeps a layer's state when it changes page. */
@Immutable
sealed interface Overlay {
    val id: Int

    /** Focus key to return to when this layer closes. */
    val restoreKey: String

    data class Menu(
        override val id: Int,
        val target: MenuTarget,
        val anchor: Rect,
        override val restoreKey: String,
        val page: MenuPage = MenuPage.Main,
    ) : Overlay

    data class Folder(override val id: Int, val folderId: String, override val restoreKey: String) : Overlay

    data class Settings(
        override val id: Int,
        override val restoreKey: String,
        val page: SettingsPage = SettingsPage.Main,
    ) : Overlay
}

/** An app or folder in move mode. */
@Immutable
data class Moving(val area: MoveArea, val key: String, val focusKey: String)

/**
 * Navigation state of the home screen. It lives outside the part of the UI that is disposed while the launcher
 * is in the background, so returning from an app puts focus (and the camera) back where it was.
 *
 * Focus keys: `hero` (Play, or Allow), `settings`, `shelf:<pkg>`, `grid:<grid key>`, `folder:<pkg>`.
 */
@Stable
class HomeNav {
    var area by mutableStateOf(Area.Hero)

    /** Last focused grid entry and favorite (where Up/Down from elsewhere return to). */
    var gridKey by mutableStateOf<String?>(null)
    var shelfPkg by mutableStateOf<String?>(null)

    /** Focused app inside the open folder. */
    var folderPkg by mutableStateOf<String?>(null)

    /** The element that had focus last on the home screen itself (not in an overlay). */
    var focusKey: String? = null

    /** The same, ignoring the settings button: where Down from the top bar goes back to. */
    var contentKey: String? = null

    val overlays = mutableStateListOf<Overlay>()
    var moving by mutableStateOf<Moving?>(null)

    /** A settings page to open again when the home screen comes back (after a trip to the TV's own settings). */
    var reopenSettings: SettingsPage? = null

    private var nextId = 1
    private val requesters = HashMap<String, FocusRequester>()
    private val attached = HashMap<String, Int>()

    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    /** Moves focus to [key] if an element with that key is on screen. */
    fun focus(key: String?): Boolean {
        if (key == null || (attached[key] ?: 0) <= 0) return false
        return try {
            requester(key).requestFocus()
            true
        } catch (_: IllegalStateException) {
            false
        }
    }

    fun focused(key: String, area: Area) {
        focusKey = key
        if (area != Area.Top) contentKey = key
        this.area = area
    }

    fun newId(): Int = nextId++

    fun open(overlay: Overlay) {
        moving = null
        overlays.add(overlay)
    }

    /** Closes the top layer; focus then returns to [restoreKey] (by default the layer's own). */
    fun closeTop(restoreKey: String? = null) {
        val top = overlays.removeLastOrNull() ?: return
        val key = restoreKey ?: top.restoreKey
        if (overlays.isEmpty()) focusKey = key else if (key.startsWith(FOLDER_PREFIX)) folderPkg = key.removePrefix(FOLDER_PREFIX)
    }

    /** Replaces the top layer (a menu or settings page change) without closing it. */
    fun replaceTop(overlay: Overlay) {
        if (overlays.isNotEmpty()) overlays[overlays.lastIndex] = overlay
    }

    fun closeAll() {
        overlays.clear()
        moving = null
    }

    internal fun attach(key: String) {
        attached[key] = (attached[key] ?: 0) + 1
    }

    internal fun detach(key: String) {
        val count = (attached[key] ?: 0) - 1
        if (count <= 0) attached.remove(key) else attached[key] = count
    }

    companion object {
        const val HERO = "hero"
        const val SETTINGS = "settings"
        const val SHELF_PREFIX = "shelf:"
        const val GRID_PREFIX = "grid:"
        const val FOLDER_PREFIX = "folder:"
    }
}

/** Gives an element its focus key: [HomeNav.focus] can then move focus to it while it is on screen. */
fun Modifier.focusKey(nav: HomeNav, key: String): Modifier =
    this then FocusKeyElement(nav, key) then Modifier.focusRequester(nav.requester(key))

private data class FocusKeyElement(val nav: HomeNav, val key: String) : ModifierNodeElement<FocusKeyNode>() {
    override fun create() = FocusKeyNode(nav, key)

    override fun update(node: FocusKeyNode) {
        if (node.nav === nav && node.key == key) return
        if (node.isAttached) node.nav.detach(node.key)
        node.nav = nav
        node.key = key
        if (node.isAttached) nav.attach(key)
    }
}

private class FocusKeyNode(var nav: HomeNav, var key: String) : Modifier.Node() {
    override fun onAttach() = nav.attach(key)

    override fun onDetach() = nav.detach(key)
}
