package io.github.kesemb2.glasshome.data

import io.github.kesemb2.glasshome.data.watchnext.WatchNextEntry
import io.github.kesemb2.glasshome.data.watchnext.WatchNextRules
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchNextRulesTest {

    private fun entry(
        id: Long,
        engagement: Long,
        pkg: String = "com.example.tv",
        title: String = "Title $id",
        providerId: String? = "p$id",
    ) = WatchNextEntry(
        id = id, packageName = pkg, title = title, shortDescription = null, longDescription = null,
        posterArtUri = null, posterArtAspectRatio = null, thumbnailUri = null, logoUri = null,
        seasonNumber = null, episodeNumber = null, episodeTitle = null, type = null,
        durationMillis = 0, positionMillis = 0, intentUri = null, internalProviderId = providerId,
        contentId = null, lastEngagementMillis = engagement,
    )

    @Test
    fun secondsAreNormalisedToMilliseconds() {
        assertEquals(1_700_000_000_000L, WatchNextRules.normalizeEpochMillis(1_700_000_000L))
        assertEquals(1_700_000_000_000L, WatchNextRules.normalizeEpochMillis(1_700_000_000_000L))
        assertEquals(0L, WatchNextRules.normalizeEpochMillis(0L))
    }

    @Test
    fun mostRecentFirstAndLimited() {
        val entries = (1L..15L).map { entry(it, engagement = it * 1000) }
        val selected = WatchNextRules.select(entries, ownPackage = "me", dismissed = emptySet())
        assertEquals(10, selected.size)
        assertEquals(15L, selected.first().id)
        assertEquals(6L, selected.last().id)
    }

    @Test
    fun dismissedOwnAndDuplicateEntriesAreDropped() {
        val kept = entry(1, 5000)
        val dismissed = entry(2, 4000)
        val own = entry(3, 3000, pkg = "me")
        val olderDuplicate = entry(4, 1000, providerId = "p1")
        val untitled = entry(5, 2000, title = " ")
        val selected = WatchNextRules.select(
            listOf(olderDuplicate, dismissed, own, kept, untitled),
            ownPackage = "me",
            dismissed = setOf(dismissed.dismissKey),
        )
        assertEquals(listOf(kept), selected)
    }

    @Test
    fun dismissedEntryReturnsAfterNewEngagement() {
        val before = entry(1, 1000)
        val after = before.copy(lastEngagementMillis = 9000)
        val selected = WatchNextRules.select(listOf(after), ownPackage = "me", dismissed = setOf(before.dismissKey))
        assertEquals(listOf(after), selected)
    }
}
