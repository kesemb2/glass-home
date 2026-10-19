package io.github.kesemb2.glasshome.data

import io.github.kesemb2.glasshome.data.apps.AppEntry
import io.github.kesemb2.glasshome.data.apps.GridKey
import io.github.kesemb2.glasshome.data.apps.HomeView
import io.github.kesemb2.glasshome.data.apps.HomeLayout
import io.github.kesemb2.glasshome.data.apps.moveAmongShown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {

    private val installed = listOf("a", "b", "c", "d", "e", "f", "g")

    private fun fresh(): HomeLayout = HomeLayout(seeded = true).reconcile(installed)

    private fun apps(vararg pkgs: String) = pkgs.map(GridKey::app)

    @Test
    fun firstReconcilePlacesEveryAppAndSeedsFavorites() {
        val layout = HomeLayout().reconcile(installed)
        assertEquals(apps(*installed.toTypedArray()), layout.grid)
        assertEquals(listOf("a", "b", "c", "d", "e"), layout.favorites)
        assertTrue(layout.seeded)
    }

    @Test
    fun seedPrefersKnownStreamingApps() {
        val seeded = HomeLayout.seedFavorites(listOf("x", "com.netflix.ninja", "y"))
        assertEquals(listOf("com.netflix.ninja", "x", "y"), seeded)
    }

    @Test
    fun seedingHappensOnlyOnce() {
        val layout = fresh().copy(favorites = emptyList()).reconcile(installed)
        assertTrue(layout.favorites.isEmpty())
    }

    @Test
    fun noSeedWhileNothingIsInstalled() {
        val layout = HomeLayout().reconcile(emptyList())
        assertFalse(layout.seeded)
    }

    @Test
    fun reconcileDropsRemovedAppsAndAppendsNewOnes() {
        val layout = fresh().toggleFavorite("c").reconcile(listOf("a", "c", "z"))
        assertEquals(apps("a", "c", "z"), layout.grid)
        assertEquals(listOf("c"), layout.favorites)
    }

    @Test
    fun reconcileDropsFoldersThatLostAllApps() {
        val (withFolder, id) = fresh().newFolder("b", "F")
        val layout = withFolder.reconcile(installed - "b")
        assertNull(layout.folders[id])
        assertFalse(GridKey.folder(id) in layout.grid)
    }

    @Test
    fun hideRemovesEverywhereAndUnhideAppends() {
        val (withFolder, id) = fresh().toggleFavorite("b").newFolder("b", "F")
        val hidden = withFolder.hide("b")
        assertTrue("b" in hidden.hidden)
        assertFalse("b" in hidden.favorites)
        assertNull(hidden.folders[id])
        assertFalse(GridKey.app("b") in hidden.grid)
        // Hidden apps stay hidden across reconciles.
        assertEquals(hidden.grid, hidden.reconcile(installed).grid)
        val back = hidden.unhide("b")
        assertEquals(GridKey.app("b"), back.grid.last())
    }

    @Test
    fun newFolderTakesTheAppsPlace() {
        val (layout, id) = fresh().newFolder("c", "Movies")
        assertEquals(listOf(GridKey.app("a"), GridKey.app("b"), GridKey.folder(id)), layout.grid.take(3))
        assertEquals(listOf("c"), layout.folders.getValue(id).apps)
        assertEquals("Movies", layout.folders.getValue(id).name)
        assertEquals(2, layout.nextFolder)
    }

    @Test
    fun addToFolderMovesTheAppOutOfTheGridAndOtherFolders() {
        val (one, first) = fresh().newFolder("a", "1")
        val (two, second) = one.newFolder("b", "2")
        val layout = two.addToFolder("c", first).addToFolder("b", first)
        assertEquals(listOf("a", "c", "b"), layout.folders.getValue(first).apps)
        assertNull(layout.folders[second]) // Lost its only app.
        assertFalse(GridKey.app("c") in layout.grid)
        assertEquals(layout, layout.reconcile(installed))
    }

    @Test
    fun removeFromFolderPutsTheAppAfterTheFolder() {
        val (one, id) = fresh().newFolder("b", "F")
        val layout = one.addToFolder("d", id).removeFromFolder("d")
        val at = layout.grid.indexOf(GridKey.folder(id))
        assertEquals(GridKey.app("d"), layout.grid[at + 1])
        assertEquals(listOf("b"), layout.folders.getValue(id).apps)
    }

    @Test
    fun removingTheLastAppPutsItWhereTheFolderWas() {
        val (one, id) = fresh().newFolder("b", "F")
        val layout = one.removeFromFolder("b")
        assertEquals(apps(*installed.toTypedArray()), layout.grid)
        assertNull(layout.folders[id])
    }

    @Test
    fun removeFolderSpillsItsAppsInPlace() {
        val (one, id) = fresh().newFolder("b", "F")
        val layout = one.addToFolder("e", id).removeFolder(id)
        assertEquals(apps("a", "b", "e", "c", "d", "f", "g"), layout.grid)
        assertTrue(layout.folders.isEmpty())
    }

    @Test
    fun moveSkipsEntriesThatAreNotShown() {
        val list = listOf("a", "B", "c", "D", "e")
        val shown = { s: String -> s.first().isLowerCase() }
        assertEquals(listOf("a", "B", "D", "e", "c"), list.moveAmongShown("c", 1, shown))
        assertEquals(listOf("c", "a", "B", "D", "e"), list.moveAmongShown("c", -1, shown))
        assertEquals(listOf("B", "c", "D", "e", "a"), list.moveAmongShown("a", 5, shown))
        assertEquals(list, list.moveAmongShown("a", -1, shown))
        assertEquals(list, list.moveAmongShown("B", 1, shown))
    }

    @Test
    fun moveInGridByARow() {
        val layout = fresh().moveInGrid(GridKey.app("a"), 5) { true }
        assertEquals(apps("b", "c", "d", "e", "f", "a", "g"), layout.grid)
    }

    @Test
    fun favoritesAreNotRepeatedInTheGrid() {
        val entries = installed.associateWith { AppEntry(it, "Main", it, tv = true, updatedAt = 0L) }
        val starred = fresh().toggleFavorite("b")
        val view = HomeView.of(starred, entries) { true }
        assertEquals(listOf("b"), view.favorites.map { it.packageName })
        assertEquals(apps("a", "c", "d", "e", "f", "g"), view.grid.map { it.key })
        // Off the shelf, it is back where it was.
        val back = HomeView.of(starred.toggleFavorite("b"), entries) { true }
        assertEquals(apps("a", "b", "c", "d", "e", "f", "g"), back.grid.map { it.key })
    }

    @Test
    fun jsonRoundTrip() {
        val (one, id) = fresh().toggleFavorite("g").newFolder("b", "Kids \"TV\"")
        val layout = one.addToFolder("c", id).hide("f")
        assertEquals(layout, HomeLayout.fromJson(layout.toJson()))
    }

    @Test
    fun badJsonGivesAnEmptyLayout() {
        assertEquals(HomeLayout(), HomeLayout.fromJson("{not json"))
        assertEquals(HomeLayout(), HomeLayout.fromJson(null))
    }
}
