package io.github.kesemb2.glasshome.data.tmdb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

enum class MediaHint { MOVIE, TV, UNKNOWN }

data class TmdbQuery(
    val packageName: String,
    val imdbId: String?,
    val title: String,
    val media: MediaHint,
    val season: Int?,
    val episode: Int?,
    /** BCP-47 tag for TMDB's `language` parameter, e.g. "he-IL". */
    val languageTag: String,
    /** ISO 639-1 code for `include_image_language`, e.g. "he". */
    val languageIso: String,
) {
    val cacheKey: String
        get() = listOf("v1", packageName, imdbId ?: title.lowercase(), media, season, episode, languageTag).joinToString("|")
}

data class TmdbArt(
    val mediaType: String,
    val id: Int,
    val logoUrl: String?,
    val backdropUrl: String?,
    val overview: String?,
    val episodeOverview: String?,
    val year: String?,
)

/**
 * Minimal TMDB v3 client (Read Access Token as Bearer). Every lookup is cached on disk as a small JSON file
 * keyed by package + id/title, so each title costs network calls once per two weeks at most.
 */
class TmdbClient(
    private val http: OkHttpClient,
    private val token: String,
    private val cacheDir: File,
) {

    suspend fun lookup(query: TmdbQuery): TmdbArt? = withContext(Dispatchers.IO) {
        val file = File(cacheDir, sha1(query.cacheKey) + ".json")
        readCache(file)?.let { return@withContext it.art }
        val art = try {
            fetch(query)
        } catch (_: IOException) {
            return@withContext null // Offline or server error: try again next time, cache nothing.
        } catch (_: JSONException) {
            null
        }
        writeCache(file, art)
        art
    }

    private fun fetch(q: TmdbQuery): TmdbArt? {
        val target = resolve(q) ?: return null
        val base = "3/${target.type}/${target.id}"
        val details = get(
            base,
            "language" to q.languageTag,
            "append_to_response" to "images",
            "include_image_language" to "${q.languageIso},en,null",
        ) ?: return null

        var overview = TmdbParse.text(details, "overview")
        if (overview == null && q.languageIso != "en") {
            overview = get(base, "language" to "en-US")?.let { TmdbParse.text(it, "overview") }
        }

        val season = q.season ?: target.season
        val episode = q.episode ?: target.episode
        var episodeOverview: String? = null
        if (target.type == "tv" && season != null && episode != null) {
            val path = "3/tv/${target.id}/season/$season/episode/$episode"
            episodeOverview = get(path, "language" to q.languageTag)?.let { TmdbParse.text(it, "overview") }
            if (episodeOverview == null && q.languageIso != "en") {
                episodeOverview = get(path, "language" to "en-US")?.let { TmdbParse.text(it, "overview") }
            }
        }

        return TmdbArt(
            mediaType = target.type,
            id = target.id,
            logoUrl = TmdbParse.pickLogo(details.optJSONObject("images"), listOf(q.languageIso, "en")),
            backdropUrl = TmdbParse.backdrop(details),
            overview = overview,
            episodeOverview = episodeOverview,
            year = TmdbParse.year(details),
        )
    }

    private fun resolve(q: TmdbQuery): TmdbParse.Target? {
        q.imdbId?.let { imdb ->
            get("3/find/$imdb", "external_source" to "imdb_id")?.let(TmdbParse::fromFind)?.let { return it }
        }
        if (q.title.isBlank()) return null
        val type = when (q.media) {
            MediaHint.MOVIE -> "movie"
            MediaHint.TV -> "tv"
            MediaHint.UNKNOWN -> null
        }
        val results = get(
            "3/search/${type ?: "multi"}",
            "query" to q.title,
            "include_adult" to "false",
            "language" to q.languageTag,
        ) ?: return null
        return TmdbParse.fromSearch(results, q.title, type)
    }

    private fun get(path: String, vararg params: Pair<String, String>): JSONObject? {
        val url = API.newBuilder()
            .addPathSegments(path)
            .apply { params.forEach { (key, value) -> addQueryParameter(key, value) } }
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw IOException("TMDB HTTP ${response.code}")
            return JSONObject(response.body.string())
        }
    }

    private class Cached(val art: TmdbArt?)

    private fun readCache(file: File): Cached? {
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            val art = json.optJSONObject("art")?.let(TmdbParse::artFromJson)
            val maxAge = if (art != null) HIT_TTL_MILLIS else MISS_TTL_MILLIS
            if (System.currentTimeMillis() - json.getLong("t") > maxAge) null else Cached(art)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeCache(file: File, art: TmdbArt?) {
        try {
            cacheDir.mkdirs()
            val json = JSONObject().put("t", System.currentTimeMillis()).put("art", art?.let(TmdbParse::artToJson) ?: JSONObject.NULL)
            file.writeText(json.toString())
        } catch (_: IOException) {
            // A missing cache entry only costs another lookup.
        }
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        val API = "https://api.themoviedb.org/".toHttpUrl()
        const val HIT_TTL_MILLIS = 14L * 24 * 60 * 60 * 1000
        const val MISS_TTL_MILLIS = 24L * 60 * 60 * 1000
    }
}

/** Pure JSON handling, unit-tested with recorded TMDB responses. */
object TmdbParse {
    private const val IMAGE_BASE = "https://image.tmdb.org/t/p/"
    private val IMDB_ID = Regex("tt\\d{5,10}")

    data class Target(val type: String, val id: Int, val season: Int? = null, val episode: Int? = null)

    /** First IMDb id ("tt" + digits) found in any of the candidates. */
    fun imdbIdIn(vararg candidates: String?): String? =
        candidates.firstNotNullOfOrNull { candidate -> candidate?.let { IMDB_ID.find(it)?.value } }

    fun fromFind(json: JSONObject): Target? {
        json.optJSONArray("movie_results")?.optJSONObject(0)?.let { return Target("movie", it.getInt("id")) }
        json.optJSONArray("tv_results")?.optJSONObject(0)?.let { return Target("tv", it.getInt("id")) }
        json.optJSONArray("tv_episode_results")?.optJSONObject(0)?.let {
            return Target("tv", it.getInt("show_id"), it.intOrNull("season_number"), it.intOrNull("episode_number"))
        }
        return null
    }

    /** Prefers an exact (normalised) title match among the top results, else TMDB's first result. */
    fun fromSearch(json: JSONObject, title: String, type: String?): Target? {
        val results = json.optJSONArray("results") ?: return null
        val wanted = normalize(title)
        var first: Target? = null
        for (i in 0 until minOf(results.length(), 10)) {
            val item = results.optJSONObject(i) ?: continue
            val mediaType = type ?: item.optString("media_type").takeIf { it == "movie" || it == "tv" } ?: continue
            val target = Target(mediaType, item.optInt("id").takeIf { it > 0 } ?: continue)
            if (first == null) first = target
            val names = listOf("title", "name", "original_title", "original_name").mapNotNull { text(item, it) }
            if (names.any { normalize(it) == wanted }) return target
        }
        return first
    }

    /** Highest-voted PNG logo (TMDB also serves SVGs, which the image loader cannot decode). */
    fun pickLogo(images: JSONObject?, languages: List<String>): String? {
        val logos = images?.optJSONArray("logos") ?: return null
        val best = objects(logos)
            .filter { it.optString("file_path").endsWith(".png", ignoreCase = true) }
            .sortedWith(
                compareByDescending<JSONObject> { it.optDouble("vote_average", 0.0) }
                    .thenByDescending { it.optInt("vote_count", 0) }
                    .thenBy { languageRank(it, languages) },
            )
            .firstOrNull() ?: return null
        return IMAGE_BASE + "w500" + best.getString("file_path")
    }

    /** A text-free backdrop if TMDB has one (best for a logo overlay), otherwise the main backdrop. */
    fun backdrop(details: JSONObject): String? {
        val textless = details.optJSONObject("images")?.optJSONArray("backdrops")?.let { objects(it) }
            ?.filter { it.isNull("iso_639_1") }
            ?.maxWithOrNull(compareBy<JSONObject> { it.optDouble("vote_average", 0.0) }.thenBy { it.optInt("vote_count", 0) })
            ?.let { text(it, "file_path") }
        val path = textless ?: text(details, "backdrop_path") ?: return null
        return IMAGE_BASE + "w1280" + path
    }

    fun year(details: JSONObject): String? =
        (text(details, "release_date") ?: text(details, "first_air_date"))?.take(4)?.takeIf { it.length == 4 }

    /** org.json returns the string "null" for JSON null; this returns Kotlin null and drops blanks. */
    fun text(obj: JSONObject?, key: String): String? {
        if (obj == null || obj.isNull(key)) return null
        return obj.optString(key).trim().takeIf { it.isNotEmpty() }
    }

    fun artToJson(art: TmdbArt): JSONObject = JSONObject()
        .put("mediaType", art.mediaType)
        .put("id", art.id)
        .put("logo", art.logoUrl ?: JSONObject.NULL)
        .put("backdrop", art.backdropUrl ?: JSONObject.NULL)
        .put("overview", art.overview ?: JSONObject.NULL)
        .put("episodeOverview", art.episodeOverview ?: JSONObject.NULL)
        .put("year", art.year ?: JSONObject.NULL)

    fun artFromJson(json: JSONObject): TmdbArt = TmdbArt(
        mediaType = json.getString("mediaType"),
        id = json.getInt("id"),
        logoUrl = text(json, "logo"),
        backdropUrl = text(json, "backdrop"),
        overview = text(json, "overview"),
        episodeOverview = text(json, "episodeOverview"),
        year = text(json, "year"),
    )

    private fun languageRank(item: JSONObject, languages: List<String>): Int {
        if (item.isNull("iso_639_1")) return languages.size
        val index = languages.indexOf(item.optString("iso_639_1"))
        return if (index >= 0) index else languages.size + 1
    }

    private fun objects(array: JSONArray): List<JSONObject> = List(array.length()) { array.optJSONObject(it) }.filterNotNull()

    private fun normalize(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)
}
