package io.github.kesemb2.glasshome.ui.home

import android.content.Context
import io.github.kesemb2.glasshome.R
import io.github.kesemb2.glasshome.data.apps.AppEntry
import io.github.kesemb2.glasshome.data.apps.GridItem
import io.github.kesemb2.glasshome.data.apps.GridKey
import io.github.kesemb2.glasshome.data.apps.HomeView
import io.github.kesemb2.glasshome.ui.home.HomeNav.Companion.FOLDER_PREFIX
import io.github.kesemb2.glasshome.ui.home.HomeNav.Companion.GRID_PREFIX
import io.github.kesemb2.glasshome.ui.home.HomeNav.Companion.SHELF_PREFIX
import io.github.kesemb2.glasshome.ui.overlay.MenuAction

/** A menu's title and entries for its target and page. */
class MenuContent(val title: String?, val actions: List<MenuAction>)

/**
 * Builds the hold-OK menus. Every entry closes the menu itself and says where focus goes next, so focus never
 * falls back to the hero after the focused card disappears (hidden, moved into a folder).
 */
fun menuContent(context: Context, menu: Overlay.Menu, view: HomeView, apps: AppsController, nav: HomeNav): MenuContent {
    fun s(id: Int) = context.getString(id)
    val layout = apps.layout

    fun favorite(app: AppEntry, restore: String? = null) = MenuAction(
        if (layout.isFavorite(app.packageName)) R.drawable.ic_star_filled else R.drawable.ic_star,
        s(if (layout.isFavorite(app.packageName)) R.string.menu_remove_favorite else R.string.menu_add_favorite),
    ) {
        nav.closeTop(restore)
        apps.toggleFavorite(app.packageName)
    }

    fun info(app: AppEntry) = MenuAction(R.drawable.ic_info, s(R.string.menu_app_info)) {
        nav.closeTop()
        apps.openAppInfo(app)
    }

    fun move(area: MoveArea, key: String, focusKey: String) = MenuAction(R.drawable.ic_move, s(R.string.menu_move)) {
        nav.closeTop(focusKey)
        nav.moving = Moving(area, key, focusKey)
    }

    // Where focus goes when the card disappears from the grid: its next neighbour, else the previous one.
    fun gridNeighbour(key: String): String? {
        val i = view.grid.indexOfFirst { it.key == key }
        if (i < 0) return null
        return (view.grid.getOrNull(i + 1) ?: view.grid.getOrNull(i - 1))?.let { GRID_PREFIX + it.key }
    }

    fun shelfNeighbour(pkg: String): String? {
        val i = view.favorites.indexOfFirst { it.packageName == pkg }
        if (i < 0) return null
        return (view.favorites.getOrNull(i + 1) ?: view.favorites.getOrNull(i - 1))?.let { SHELF_PREFIX + it.packageName }
    }

    return when (val target = menu.target) {
        is MenuTarget.GridApp -> {
            val app = target.app
            val key = GridKey.app(app.packageName)
            when (menu.page) {
                MenuPage.Main -> MenuContent(
                    app.label,
                    listOf(
                        move(MoveArea.Grid, key, GRID_PREFIX + key),
                        favorite(app),
                        MenuAction(R.drawable.ic_folder_new, s(R.string.menu_add_to_folder)) {
                            nav.replaceTop(menu.copy(page = MenuPage.Folders))
                        },
                        MenuAction(R.drawable.ic_hide, s(R.string.menu_hide)) {
                            nav.closeTop(gridNeighbour(key) ?: HomeNav.HERO)
                            apps.hide(app.packageName)
                        },
                        info(app),
                    ),
                )
                MenuPage.Folders -> MenuContent(
                    s(R.string.menu_add_to_folder),
                    view.grid.filterIsInstance<GridItem.Folder>().map { folder ->
                        MenuAction(R.drawable.ic_folder, folder.folder.name) {
                            nav.closeTop(GRID_PREFIX + folder.key)
                            apps.addToFolder(app.packageName, folder.folder.id)
                        }
                    } + MenuAction(R.drawable.ic_folder_new, s(R.string.menu_new_folder)) {
                        val id = apps.newFolder(app.packageName)
                        nav.closeTop(GRID_PREFIX + GridKey.folder(id))
                    },
                )
            }
        }

        is MenuTarget.ShelfApp -> {
            val app = target.app
            val pkg = app.packageName
            val away = shelfNeighbour(pkg) ?: HomeNav.HERO
            MenuContent(
                app.label,
                listOf(
                    move(MoveArea.Favorites, pkg, SHELF_PREFIX + pkg),
                    favorite(app, restore = away),
                    MenuAction(R.drawable.ic_hide, s(R.string.menu_hide)) {
                        nav.closeTop(away)
                        apps.hide(pkg)
                    },
                    info(app),
                ),
            )
        }

        is MenuTarget.GridFolder -> {
            val folder = target.folder
            MenuContent(
                folder.folder.name,
                listOf(
                    move(MoveArea.Grid, folder.key, GRID_PREFIX + folder.key),
                    MenuAction(R.drawable.ic_folder_remove, s(R.string.menu_remove_folder)) {
                        nav.closeTop(folder.apps.firstOrNull()?.let { GRID_PREFIX + GridKey.app(it.packageName) })
                        apps.removeFolder(folder.folder.id)
                    },
                ),
            )
        }

        is MenuTarget.FolderApp -> {
            val app = target.app
            val pkg = app.packageName
            val inFolder = view.grid.filterIsInstance<GridItem.Folder>().firstOrNull { it.folder.id == target.folderId }
            val others = inFolder?.apps.orEmpty().filter { it.packageName != pkg }
            val i = inFolder?.apps?.indexOfFirst { it.packageName == pkg } ?: -1
            val neighbour = (inFolder?.apps?.getOrNull(i + 1) ?: inFolder?.apps?.getOrNull(i - 1))
                ?.let { FOLDER_PREFIX + it.packageName }
            // Taking out the last app dissolves the folder: close it too and land on the app in the grid.
            fun leave(gridKey: String, action: () -> Unit) {
                if (others.isEmpty()) {
                    nav.closeTop()
                    nav.closeTop(gridKey)
                } else {
                    nav.closeTop(neighbour)
                }
                action()
            }
            MenuContent(
                app.label,
                listOf(
                    move(MoveArea.Folder(target.folderId), pkg, FOLDER_PREFIX + pkg),
                    MenuAction(R.drawable.ic_folder_remove, s(R.string.menu_remove_from_folder)) {
                        leave(GRID_PREFIX + GridKey.app(pkg)) { apps.removeFromFolder(pkg) }
                    },
                    favorite(app),
                    MenuAction(R.drawable.ic_hide, s(R.string.menu_hide)) {
                        leave(gridNeighbour(GridKey.folder(target.folderId)) ?: HomeNav.HERO) { apps.hide(pkg) }
                    },
                    info(app),
                ),
            )
        }
    }
}
