package io.github.kesemb2.glasshome.data

import io.github.kesemb2.glasshome.data.watchnext.Playback
import io.github.kesemb2.glasshome.data.watchnext.WatchNextEntry
import io.github.kesemb2.glasshome.data.watchnext.WatchNextRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun watched(position: Long, duration: Long, type: Int? = WatchNextEntry.WATCH_NEXT_CONTINUE) =
        entry(1, engagement = 1).copy(positionMillis = position, durationMillis = duration, watchNextType = type)

    private fun assertPlayback(fraction: Float, remaining: Long?, actual: Playback?) {
        requireNotNull(actual)
        assertEquals(fraction, actual.fraction, 0.0005f)
        assertEquals(remaining, actual.remainingMillis)
    }

    @Test
    fun playbackInMilliseconds() {
        assertPlayback(120f / 2700, 2_580_000, WatchNextRules.playback(watched(120_000, 2_700_000)))
        assertPlayback(120f / 2700, 2_580_000, WatchNextRules.playback(watched(120_000, 2_700_000, type = null)))
    }

    @Test
    fun playbackInSecondsIsConverted() {
        assertPlayback(120f / 2700, 2_580_000, WatchNextRules.playback(watched(120, 2_700)))
        // Duration in seconds, position already in milliseconds.
        assertPlayback(120f / 2700, 2_580_000, WatchNextRules.playback(watched(120_000, 2_700)))
        assertEquals(2_700_000L, WatchNextRules.durationMillis(watched(0, 2_700)))
        assertEquals(5_400_000L, WatchNextRules.durationMillis(watched(0, 5_400_000)))
    }

    @Test
    fun progressScaleGivesTheFractionOnly() {
        // As Nuvio writes episodes (photographed on the TV): 4.7% watched, no real runtime.
        assertPlayback(0.04683f, null, WatchNextRules.playback(watched(4_683, 100_000)))
        assertPlayback(0.70522f, null, WatchNextRules.playback(watched(70_522, 100_000)))
        assertEquals(0L, WatchNextRules.durationMillis(watched(4_683, 100_000)))
        assertNull(WatchNextRules.playback(watched(120_000, 100_000)))
        // Its movies carry real times.
        assertPlayback(72_683f / 6_000_000, 6_000_000L - 72_683, WatchNextRules.playback(watched(72_683, 6_000_000)))
    }

    @Test
    fun onlyRowsBeingWatchedShowProgress() {
        // A NEXT row (the next episode) can carry the previous episode's position.
        assertNull(WatchNextRules.playback(watched(2_640_000, 2_700_000, type = 1)))
        assertNull(WatchNextRules.playback(watched(2_640_000, 2_700_000, type = 3)))
    }

    @Test
    fun positionPastTheEndShowsNothing() {
        assertNull(WatchNextRules.playback(watched(5_000_000, 2_700_000)))
        assertPlayback(1f, 0, WatchNextRules.playback(watched(2_710_000, 2_700_000)))
        assertNull(WatchNextRules.playback(watched(0, 2_700_000)))
    }

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
