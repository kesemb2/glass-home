package io.github.kesemb2.glasshome

import android.app.Application
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import io.github.kesemb2.glasshome.data.tmdb.TmdbClient
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.util.concurrent.TimeUnit

/** Process-wide singletons, created lazily on first use (no DI framework). */
class GlassHomeApp : Application() {

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** Small memory cache (current + neighbouring hero images), capped disk cache. */
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(this)
            .memoryCache { MemoryCache.Builder().maxSizeBytes(MEMORY_CACHE_BYTES).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("images").toOkioPath())
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }
            .components { add(OkHttpNetworkFetcherFactory(httpClient)) }
            .build()
    }

    /** Null when no TMDB token was built in; the hero then uses provider data only. */
    val tmdb: TmdbClient? by lazy {
        BuildConfig.TMDB_TOKEN.takeIf { it.isNotBlank() }?.let { TmdbClient(httpClient, it, cacheDir.resolve("tmdb")) }
    }

    private companion object {
        const val MEMORY_CACHE_BYTES = 32L * 1024 * 1024
        const val DISK_CACHE_BYTES = 96L * 1024 * 1024
    }
}
