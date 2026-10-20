package io.github.kesemb2.glasshome.data

import io.github.kesemb2.glasshome.data.tmdb.TmdbArt
import io.github.kesemb2.glasshome.data.tmdb.TmdbParse
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbParseTest {

    @Test
    fun findsImdbIdsInProviderData() {
        assertEquals("tt0903747", TmdbParse.imdbIdIn(null, "series:tt0903747:2:5"))
        assertEquals("tt1234567", TmdbParse.imdbIdIn("tt1234567", "tt7654321"))
        assertNull(TmdbParse.imdbIdIn("12345", null, "movie-abc"))
    }

    @Test
    fun findPrefersMovieThenShowThenEpisode() {
        val episode = JSONObject(
            """{"movie_results":[],"tv_results":[],"tv_episode_results":[{"id":62085,"show_id":1396,"season_number":2,"episode_number":5}]}""",
        )
        assertEquals(TmdbParse.Target("tv", 1396, 2, 5), TmdbParse.fromFind(episode))
        val movie = JSONObject("""{"movie_results":[{"id":603}],"tv_results":[{"id":1}]}""")
        assertEquals(TmdbParse.Target("movie", 603), TmdbParse.fromFind(movie))
        assertNull(TmdbParse.fromFind(JSONObject("""{"movie_results":[]}""")))
    }

    @Test
    fun searchPrefersExactTitleMatch() {
        val json = JSONObject(
            """{"results":[
                {"id":1,"media_type":"person","name":"Dark"},
                {"id":2,"media_type":"movie","title":"Dark Waters"},
                {"id":3,"media_type":"tv","name":"Dark","original_name":"Dark"}
            ]}""",
        )
        assertEquals(TmdbParse.Target("tv", 3), TmdbParse.fromSearch(json, "DARK", type = null))
        assertEquals(TmdbParse.Target("movie", 2), TmdbParse.fromSearch(json, "Something else", type = null))
        assertEquals(TmdbParse.Target("tv", 1), TmdbParse.fromSearch(json, "Nope", type = "tv"))
    }

    @Test
    fun logoIsHighestVotedPng() {
        val images = JSONObject(
            """{"logos":[
                {"file_path":"/a.svg","vote_average":9.0,"vote_count":3,"iso_639_1":"en"},
                {"file_path":"/b.png","vote_average":5.3,"vote_count":2,"iso_639_1":"en"},
                {"file_path":"/c.png","vote_average":5.3,"vote_count":2,"iso_639_1":"he"},
                {"file_path":"/d.png","vote_average":0.0,"vote_count":0,"iso_639_1":null}
            ]}""",
        )
        assertEquals("https://image.tmdb.org/t/p/w500/c.png", TmdbParse.pickLogo(images, listOf("he", "en")))
        assertNull(TmdbParse.pickLogo(JSONObject("""{"logos":[]}"""), listOf("en")))
        assertNull(TmdbParse.pickLogo(null, listOf("en")))
    }

    @Test
    fun backdropPrefersTextFreeImage() {
        val details = JSONObject(
            """{"backdrop_path":"/main.jpg","images":{"backdrops":[
                {"file_path":"/en.jpg","iso_639_1":"en","vote_average":8.0},
                {"file_path":"/clean.jpg","iso_639_1":null,"vote_average":5.0}
            ]}}""",
        )
        assertEquals("https://image.tmdb.org/t/p/w1280/clean.jpg", TmdbParse.backdrop(details))
        assertEquals("https://image.tmdb.org/t/p/w1280/main.jpg", TmdbParse.backdrop(JSONObject("""{"backdrop_path":"/main.jpg"}""")))
        assertNull(TmdbParse.backdrop(JSONObject("""{"backdrop_path":null}""")))
    }

    @Test
    fun textTreatsJsonNullAndBlankAsMissing() {
        val json = JSONObject("""{"a":null,"b":"  ","c":" Hello "}""")
        assertNull(TmdbParse.text(json, "a"))
        assertNull(TmdbParse.text(json, "b"))
        assertNull(TmdbParse.text(json, "missing"))
        assertEquals("Hello", TmdbParse.text(json, "c"))
    }

    @Test
    fun yearAndCacheRoundTrip() {
        assertEquals("2008", TmdbParse.year(JSONObject("""{"first_air_date":"2008-01-20"}""")))
        assertNull(TmdbParse.year(JSONObject("""{"release_date":""}""")))
        val art = TmdbArt("tv", 1396, "https://l", null, "Overview", null, "2008", runtimeMinutes = 47)
        assertEquals(art, TmdbParse.artFromJson(TmdbParse.artToJson(art)))
        val noRuntime = art.copy(runtimeMinutes = null)
        assertEquals(noRuntime, TmdbParse.artFromJson(TmdbParse.artToJson(noRuntime)))
    }

    @Test
    fun runtimeFromMovieEpisodeOrShow() {
        assertEquals(100, TmdbParse.runtime(JSONObject("""{"runtime":100}""")))
        assertEquals(42, TmdbParse.runtime(JSONObject("""{"runtime":null,"episode_run_time":[0,42]}""")))
        assertEquals(38, TmdbParse.runtime(JSONObject("""{"episode_run_time":[],"last_episode_to_air":{"runtime":38}}""")))
        assertNull(TmdbParse.runtime(JSONObject("""{"runtime":0,"episode_run_time":[]}""")))
    }
}
