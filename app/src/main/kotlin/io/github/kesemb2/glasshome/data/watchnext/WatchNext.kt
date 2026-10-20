package io.github.kesemb2.glasshome.data.watchnext

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.database.Cursor
import android.media.tv.TvContract
import org.json.JSONArray

/** One row of the system Watch Next list (TvContract.WatchNextPrograms), as published by the app that owns it. */
data class WatchNextEntry(
    val id: Long,
    val packageName: String,
    val title: String,
    val shortDescription: String?,
    val longDescription: String?,
    val posterArtUri: String?,
    val posterArtAspectRatio: Int?,
    val thumbnailUri: String?,
    val logoUri: String?,
    val seasonNumber: String?,
    val episodeNumber: String?,
    val episodeTitle: String?,
    val type: Int?,
    val durationMillis: Long,
    val positionMillis: Long,
    val intentUri: String?,
    val internalProviderId: String?,
    val contentId: String?,
    val lastEngagementMillis: Long,
    /** CONTINUE (watching it now), NEXT (the next episode), NEW or WATCHLIST; null when the app leaves it out. */
    val watchNextType: Int? = null,
) {
    /** Identifies "this entry at this point of watching": dismissing hides it until the app records new engagement. */
    val dismissKey: String get() = "$packageName|${internalProviderId ?: title}|$lastEngagementMillis"

    /** Identifies the entry across refreshes, for animations and caches. */
    val stableKey: String get() = "$packageName|${internalProviderId ?: id.toString()}"

    companion object {
        const val TYPE_MOVIE = 0
        const val TYPE_TV_SERIES = 1
        const val TYPE_TV_SEASON = 2
        const val TYPE_TV_EPISODE = 3
        const val ASPECT_RATIO_16_9 = 0
        const val WATCH_NEXT_CONTINUE = 0
    }
}

/** Where playback stands, in milliseconds. */
data class Playback(val positionMillis: Long, val durationMillis: Long)

/** Pure selection rules, unit-tested. */
object WatchNextRules {
    const val MAX_ENTRIES = 10

    /** Some apps write seconds instead of milliseconds. */
    fun normalizeEpochMillis(value: Long): Long = if (value in 1 until 10_000_000_000L) value * 1000 else value

    /** No video in Watch Next is shorter than this; a shorter duration was written in seconds. */
    private const val MIN_DURATION_MILLIS = 60_000L

    /** The runtime in milliseconds (some apps write seconds), or 0 when unknown. */
    fun durationMillis(entry: WatchNextEntry): Long {
        val d = entry.durationMillis
        return if (d in 1 until MIN_DURATION_MILLIS) d * 1000 else d.coerceAtLeast(0)
    }

    /**
     * Playback progress worth showing, or null. Only for rows the app marks as being watched (CONTINUE): a NEXT,
     * NEW or WATCHLIST row can still carry the position of the previous episode, which showed "1 min left" on an
     * episode just started. Durations written in seconds are converted (the position with them, when it fits);
     * a position past the end means the numbers do not belong together, and nothing is shown.
     */
    fun playback(entry: WatchNextEntry): Playback? {
        val type = entry.watchNextType
        if (type != null && type != WatchNextEntry.WATCH_NEXT_CONTINUE) return null
        var duration = entry.durationMillis
        var position = entry.positionMillis
        if (duration <= 0 || position <= 0) return null
        if (duration < MIN_DURATION_MILLIS) {
            duration *= 1000
            if (position <= entry.durationMillis) position *= 1000
        }
        if (position > duration + duration / 20) return null
        return Playback(position.coerceAtMost(duration), duration)
    }

    fun select(
        entries: List<WatchNextEntry>,
        ownPackage: String,
        dismissed: Set<String>,
        limit: Int = MAX_ENTRIES,
    ): List<WatchNextEntry> = entries.asSequence()
        .filter { it.packageName != ownPackage && it.title.isNotBlank() && it.dismissKey !in dismissed }
        .sortedWith(compareByDescending<WatchNextEntry> { it.lastEngagementMillis }.thenByDescending { it.id })
        .distinctBy { it.stableKey }
        .take(limit)
        .toList()
}

/** Which TV-listing permissions this device knows, and whether each is granted / can be requested at runtime. */
data class PermissionStatus(val name: String, val granted: Boolean, val protection: String)

object WatchNextAccess {
    /**
     * Read access to other apps' Watch Next rows differs between Android TV builds: newer TvProvider versions
     * gate it behind the runtime permission READ_TV_LISTINGS, older ones behind READ_EPG_DATA, and some
     * vendors add READ_WRITE_WATCH_NEXT_PROGRAMS. All three are declared; whichever exists is requested.
     */
    val PERMISSIONS = listOf(
        "android.permission.READ_TV_LISTINGS",
        "com.android.providers.tv.permission.READ_EPG_DATA",
        "com.android.providers.tv.permission.READ_WRITE_WATCH_NEXT_PROGRAMS",
    )

    fun status(context: Context): List<PermissionStatus> = PERMISSIONS.map { name ->
        val granted = context.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED
        val protection = try {
            when (context.packageManager.getPermissionInfo(name, 0).protection) {
                PermissionInfo.PROTECTION_DANGEROUS -> "runtime"
                PermissionInfo.PROTECTION_NORMAL -> "normal"
                PermissionInfo.PROTECTION_SIGNATURE -> "signature"
                else -> "internal"
            }
        } catch (_: PackageManager.NameNotFoundException) {
            "absent"
        }
        PermissionStatus(name, granted, protection)
    }

    fun anyGranted(context: Context): Boolean = status(context).any { it.granted }

    /** Runtime permissions not granted yet; asking for them shows the system dialog. */
    fun requestable(context: Context): Array<String> =
        status(context).filter { !it.granted && it.protection == "runtime" }.map { it.name }.toTypedArray()
}

class WatchNextRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("watch_next", Context.MODE_PRIVATE)

    /** Reads every Watch Next row visible to this app. Throws SecurityException when the provider refuses. */
    fun queryAll(): List<WatchNextEntry> {
        val cursor = context.contentResolver.query(TvContract.WatchNextPrograms.CONTENT_URI, null, null, null, null)
            ?: return emptyList()
        return cursor.use { c ->
            buildList {
                while (size < 100 && c.moveToNext()) {
                    readRow(c)?.let { add(it) }
                }
            }
        }
    }

    fun dismissedKeys(): Set<String> = readDismissed().toSet()

    /** Hides an entry locally; the provider is never modified. */
    fun dismiss(entry: WatchNextEntry) {
        val keys = readDismissed().filter { it != entry.dismissKey } + entry.dismissKey
        prefs.edit().putString(KEY_DISMISSED, JSONArray(keys.takeLast(MAX_DISMISSED)).toString()).apply()
    }

    private fun readDismissed(): List<String> {
        val raw = prefs.getString(KEY_DISMISSED, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            List(array.length()) { array.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun readRow(c: Cursor): WatchNextEntry? {
        fun index(column: String) = c.getColumnIndex(column)
        fun string(column: String): String? {
            val i = index(column)
            return if (i < 0 || c.isNull(i)) null else c.getString(i)?.trim()?.takeIf { it.isNotEmpty() }
        }
        fun long(column: String): Long? {
            val i = index(column)
            return if (i < 0 || c.isNull(i)) null else c.getLong(i)
        }
        fun int(column: String): Int? {
            val i = index(column)
            return if (i < 0 || c.isNull(i)) null else c.getInt(i)
        }

        if (int(COLUMN_BROWSABLE) == 0) return null
        val packageName = string(COLUMN_PACKAGE_NAME) ?: return null
        val episodeTitle = string(COLUMN_EPISODE_TITLE)
        val title = string(COLUMN_TITLE) ?: episodeTitle ?: return null
        return WatchNextEntry(
            id = long(COLUMN_ID) ?: 0L,
            packageName = packageName,
            title = title,
            shortDescription = string(COLUMN_SHORT_DESCRIPTION),
            longDescription = string(COLUMN_LONG_DESCRIPTION),
            posterArtUri = string(COLUMN_POSTER_ART_URI),
            posterArtAspectRatio = int(COLUMN_POSTER_ART_ASPECT_RATIO),
            thumbnailUri = string(COLUMN_THUMBNAIL_URI),
            logoUri = string(COLUMN_LOGO_URI),
            seasonNumber = string(COLUMN_SEASON_DISPLAY_NUMBER),
            episodeNumber = string(COLUMN_EPISODE_DISPLAY_NUMBER),
            episodeTitle = episodeTitle.takeIf { it != title },
            type = int(COLUMN_TYPE),
            durationMillis = long(COLUMN_DURATION_MILLIS)?.coerceAtLeast(0) ?: 0L,
            positionMillis = long(COLUMN_LAST_PLAYBACK_POSITION_MILLIS)?.coerceAtLeast(0) ?: 0L,
            intentUri = string(COLUMN_INTENT_URI),
            internalProviderId = string(COLUMN_INTERNAL_PROVIDER_ID),
            contentId = string(COLUMN_CONTENT_ID),
            lastEngagementMillis = WatchNextRules.normalizeEpochMillis(long(COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS) ?: 0L),
            watchNextType = int(COLUMN_WATCH_NEXT_TYPE),
        )
    }

    private companion object {
        const val KEY_DISMISSED = "dismissed"
        const val MAX_DISMISSED = 200

        // Column names of TvContract.WatchNextPrograms (stable provider schema).
        const val COLUMN_ID = "_id"
        const val COLUMN_PACKAGE_NAME = "package_name"
        const val COLUMN_TITLE = "title"
        const val COLUMN_SHORT_DESCRIPTION = "short_description"
        const val COLUMN_LONG_DESCRIPTION = "long_description"
        const val COLUMN_POSTER_ART_URI = "poster_art_uri"
        const val COLUMN_POSTER_ART_ASPECT_RATIO = "poster_art_aspect_ratio"
        const val COLUMN_THUMBNAIL_URI = "thumbnail_uri"
        const val COLUMN_LOGO_URI = "logo_uri"
        const val COLUMN_SEASON_DISPLAY_NUMBER = "season_display_number"
        const val COLUMN_EPISODE_DISPLAY_NUMBER = "episode_display_number"
        const val COLUMN_EPISODE_TITLE = "episode_title"
        const val COLUMN_TYPE = "type"
        const val COLUMN_DURATION_MILLIS = "duration_millis"
        const val COLUMN_LAST_PLAYBACK_POSITION_MILLIS = "last_playback_position_millis"
        const val COLUMN_INTENT_URI = "intent_uri"
        const val COLUMN_INTERNAL_PROVIDER_ID = "internal_provider_id"
        const val COLUMN_CONTENT_ID = "content_id"
        const val COLUMN_BROWSABLE = "browsable"
        const val COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS = "last_engagement_time_utc_millis"
        const val COLUMN_WATCH_NEXT_TYPE = "watch_next_type"
    }
}
