package io.github.kesemb2.glasshome.data.hero

import android.content.Intent
import androidx.compose.runtime.Immutable
import io.github.kesemb2.glasshome.data.tmdb.MediaHint
import io.github.kesemb2.glasshome.data.tmdb.TmdbArt
import io.github.kesemb2.glasshome.data.tmdb.TmdbClient
import io.github.kesemb2.glasshome.data.tmdb.TmdbParse
import io.github.kesemb2.glasshome.data.tmdb.TmdbQuery
import io.github.kesemb2.glasshome.data.watchnext.WatchNextEntry
import io.github.kesemb2.glasshome.data.watchnext.WatchNextRules
import java.util.Locale

/** Everything the hero shows for one Watch Next entry, after merging the provider row, Nuvio extras and TMDB. */
@Immutable
data class HeroItem(
    val key: String,
    val entry: WatchNextEntry,
    val title: String,
    val season: Int?,
    val episode: Int?,
    val episodeTitle: String?,
    val synopsis: String?,
    val logoUrl: String?,
    val backdropUrl: String?,
    val progress: Float?,
    val remainingMillis: Long?,
    /** The runtime in milliseconds, 0 when unknown. */
    val runtimeMillis: Long,
    val year: String?,
    val media: MediaHint,
    val imdbId: String?,
    val enriched: Boolean,
)

/** Extras Nuvio (com.nuvio.tv*) packs into its Watch Next intent URI. */
data class NuvioExtras(
    val logo: String?,
    val backdrop: String?,
    val contentId: String?,
    val contentType: String?,
    val season: Int?,
    val episode: Int?,
) {
    companion object {
        fun isNuvio(packageName: String) = packageName.startsWith("com.nuvio.tv")

        @Suppress("DEPRECATION") // Bundle.get: the extras' types are not documented (string or int).
        fun parse(intentUri: String?): NuvioExtras? {
            if (intentUri.isNullOrBlank()) return null
            val extras = try {
                Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME).extras
            } catch (_: Exception) {
                null
            } ?: return null
            fun value(key: String): String? =
                extras.get(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
            return NuvioExtras(
                logo = value("logo"),
                backdrop = value("backdrop"),
                contentId = value("contentId"),
                contentType = value("contentType"),
                season = value("season")?.toIntOrNull(),
                episode = value("episode")?.toIntOrNull(),
            )
        }
    }
}

/**
 * Builds [HeroItem]s. [base] is immediate (provider data and Nuvio extras); [enrich] fills
 * gaps from TMDB in the documented order:
 * - logo: entry logo URI, Nuvio logo, TMDB logo, else the title is drawn as text;
 * - backdrop: 16:9 poster art, Nuvio backdrop, TMDB backdrop, poster of unknown shape, thumbnail;
 * - synopsis: short description, long description, TMDB (episode overview when season/episode are known).
 */
class HeroResolver(private val tmdb: TmdbClient?) {

    fun base(entry: WatchNextEntry): HeroItem {
        val nuvio = if (NuvioExtras.isNuvio(entry.packageName)) NuvioExtras.parse(entry.intentUri) else null
        val season = entry.seasonNumber?.toIntOrNull() ?: nuvio?.season
        val episode = entry.episodeNumber?.toIntOrNull() ?: nuvio?.episode
        val contentType = nuvio?.contentType?.lowercase()
        val type = entry.type
        val media = when {
            (type != null && type in TV_TYPES) || (contentType != null && contentType in TV_CONTENT_TYPES) ||
                season != null -> MediaHint.TV
            entry.type == WatchNextEntry.TYPE_MOVIE || contentType == "movie" -> MediaHint.MOVIE
            else -> MediaHint.UNKNOWN
        }
        val playback = WatchNextRules.playback(entry)
        return HeroItem(
            key = entry.stableKey,
            entry = entry,
            title = entry.title,
            season = season,
            episode = episode,
            episodeTitle = entry.episodeTitle,
            synopsis = listOf(entry.shortDescription, entry.longDescription)
                .firstOrNull { !it.isNullOrBlank() && !it.equals(entry.title, ignoreCase = true) },
            logoUrl = entry.logoUri ?: nuvio?.logo,
            backdropUrl = entry.posterArtUri.takeIf { entry.posterArtAspectRatio == WatchNextEntry.ASPECT_RATIO_16_9 }
                ?: nuvio?.backdrop,
            progress = playback?.let { (it.positionMillis.toFloat() / it.durationMillis).coerceIn(0f, 1f) },
            remainingMillis = playback?.let { it.durationMillis - it.positionMillis },
            runtimeMillis = WatchNextRules.durationMillis(entry),
            year = null,
            media = media,
            imdbId = TmdbParse.imdbIdIn(nuvio?.contentId, entry.contentId, entry.internalProviderId),
            enriched = false,
        )
    }

    suspend fun enrich(item: HeroItem): HeroItem {
        val needsTmdb = item.logoUrl == null || item.backdropUrl == null || item.synopsis == null ||
            (item.media == MediaHint.MOVIE && item.year == null)
        val art: TmdbArt? = if (needsTmdb && tmdb != null) tmdb.lookup(query(item)) else null
        val entry = item.entry
        val unknownShapePoster = entry.posterArtUri.takeIf { entry.posterArtAspectRatio == null }
        val knowsEpisode = item.season != null && item.episode != null
        return item.copy(
            logoUrl = item.logoUrl ?: art?.logoUrl,
            backdropUrl = item.backdropUrl ?: art?.backdropUrl ?: unknownShapePoster ?: entry.thumbnailUri,
            synopsis = item.synopsis ?: art?.episodeOverview?.takeIf { knowsEpisode } ?: art?.overview,
            year = item.year ?: art?.year,
            enriched = true,
        )
    }

    private fun query(item: HeroItem): TmdbQuery {
        val tag = Locale.getDefault().toLanguageTag() // Modern codes ("he-IL"), unlike Locale.language ("iw").
        return TmdbQuery(
            packageName = item.entry.packageName,
            imdbId = item.imdbId,
            title = item.title,
            media = item.media,
            season = item.season,
            episode = item.episode,
            languageTag = tag,
            languageIso = tag.substringBefore('-').lowercase(),
        )
    }

    private companion object {
        val TV_TYPES = setOf(WatchNextEntry.TYPE_TV_SERIES, WatchNextEntry.TYPE_TV_SEASON, WatchNextEntry.TYPE_TV_EPISODE)
        val TV_CONTENT_TYPES = setOf("series", "tv", "show", "episode")
    }
}
