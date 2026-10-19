package io.github.kesemb2.glasshome.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.size.Scale
import coil3.size.Size
import coil3.toBitmap
import coil3.transform.Transformation
import io.github.kesemb2.glasshome.GlassHomeApp
import kotlin.math.max
import kotlin.math.roundToInt

/** Image requests for the hero, each decoded at the size it is shown at. */
object HeroImages {
    /**
     * The glass source: the backdrop at 1/4 of the 1920 x 1080 UI surface, lightly blurred and slightly more vivid
     * (the lens shader adds vibrancy on top). Liquid glass refracts it, so it keeps visible structure instead of
     * being a uniform haze. It is also the instant placeholder while the sharp backdrop decodes.
     */
    const val BLUR_WIDTH = 480
    const val BLUR_HEIGHT = 270
    private const val BLUR_RADIUS = 1
    private const val BLUR_SATURATION = 1.15f

    /** The backdrop behind the app grid: tiny and heavily blurred, then scaled up (bilinear keeps it smooth). */
    private const val DIM_WIDTH = 192
    private const val DIM_HEIGHT = 108
    private const val DIM_RADIUS = 3

    const val LOGO_MAX_PX = 600

    /**
     * The sharp backdrop is decoded at most this wide: TMDB's backdrops are 1280 px, so decoding them at the
     * screen's 1920 only upscales them in memory (8 MB instead of 3.7 per image, and more to read on every frame);
     * the GPU scales them up as it draws.
     */
    const val BACKDROP_MAX_WIDTH = 1280

    /** Transparent margin added around every logo, room for the glow on dark logos. */
    const val LOGO_PAD_PX = 24

    // Requests carry explicit memory-cache keys, so the first frame can take an image straight from the memory
    // cache (cachedImageBitmap) instead of drawing nothing until the load returns.

    fun backdrop(context: Context, url: String, widthPx: Int, heightPx: Int, hardware: Boolean = true): ImageRequest =
        ImageRequest.Builder(context)
            .data(url)
            .size(widthPx, heightPx)
            .scale(Scale.FILL)
            .allowHardware(hardware)
            .memoryCacheKey("backdrop:${widthPx}x$heightPx:$url")
            .build()

    /** The sharp backdrop; retries once as a software bitmap if the hardware decode fails on this device. */
    suspend fun loadBackdrop(loader: ImageLoader, context: Context, url: String, widthPx: Int, heightPx: Int): ImageBitmap? =
        loader.loadImageBitmap(backdrop(context, url, widthPx, heightPx))
            ?: loader.loadImageBitmap(backdrop(context, url, widthPx, heightPx, hardware = false))

    fun blurred(context: Context, url: String): ImageRequest = blurRequest(context, url, GlassSource)

    fun dimmed(context: Context, url: String): ImageRequest = blurRequest(context, url, GridSource)

    private fun blurRequest(context: Context, url: String, blur: BlurTransformation): ImageRequest =
        ImageRequest.Builder(context)
            .data(url)
            .size(blur.width, blur.height)
            .scale(Scale.FILL)
            .allowHardware(false)
            .transformations(blur)
            .memoryCacheKey("${blur.cacheKey}:$url")
            .build()

    fun logo(context: Context, url: String): ImageRequest =
        ImageRequest.Builder(context)
            .data(url)
            .size(LOGO_MAX_PX, LOGO_MAX_PX)
            .scale(Scale.FIT)
            .allowHardware(false)
            .transformations(LogoTransformation)
            .memoryCacheKey("${LogoTransformation.cacheKey}:$url")
            .build()

    private val GlassSource = BlurTransformation("glass-source-v3", BLUR_WIDTH, BLUR_HEIGHT, BLUR_RADIUS, BLUR_SATURATION)
    private val GridSource = BlurTransformation("grid-source-v1", DIM_WIDTH, DIM_HEIGHT, DIM_RADIUS, 1f)

    /** Centre-crops to [width] x [height], blurs and saturates. Runs once per backdrop on Coil's decoder thread. */
    private class BlurTransformation(
        key: String,
        val width: Int,
        val height: Int,
        private val radius: Int,
        private val saturation: Float,
    ) : Transformation() {
        override val cacheKey: String = key

        override suspend fun transform(input: Bitmap, size: Size): Bitmap {
            val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val scale = max(width / input.width.toFloat(), height / input.height.toFloat())
            val srcWidth = width / scale
            val srcHeight = height / scale
            val left = ((input.width - srcWidth) / 2f).roundToInt()
            val top = ((input.height - srcHeight) / 2f).roundToInt()
            val src = Rect(left, top, left + srcWidth.roundToInt(), top + srcHeight.roundToInt())
            Canvas(out).drawBitmap(input, src, Rect(0, 0, width, height), Paint(Paint.FILTER_BITMAP_FLAG))
            val pixels = IntArray(width * height)
            out.getPixels(pixels, 0, width, 0, 0, width, height)
            FastBlur.blur(pixels, width, height, radius)
            if (saturation != 1f) FastBlur.saturate(pixels, saturation)
            out.setPixels(pixels, 0, width, 0, 0, width, height)
            return out
        }
    }

    /** Pads the logo by [LOGO_PAD_PX]; a mostly dark logo also gets a soft white glow behind it. */
    private object LogoTransformation : Transformation() {
        override val cacheKey: String = "logo-v1-$LOGO_PAD_PX"

        override suspend fun transform(input: Bitmap, size: Size): Bitmap {
            val width = input.width
            val height = input.height
            val pixels = IntArray(width * height)
            input.getPixels(pixels, 0, width, 0, 0, width, height)
            val outWidth = width + 2 * LOGO_PAD_PX
            val outHeight = height + 2 * LOGO_PAD_PX
            val out = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            if (LogoAnalysis.isMostlyDark(pixels)) {
                val glow = IntArray(outWidth * outHeight)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val alpha = ((pixels[y * width + x] ushr 24) * 0.7f).toInt()
                        glow[(y + LOGO_PAD_PX) * outWidth + x + LOGO_PAD_PX] = (alpha shl 24) or 0xFFFFFF
                    }
                }
                FastBlur.blur(glow, outWidth, outHeight, LOGO_PAD_PX / 2)
                out.setPixels(glow, 0, outWidth, 0, 0, outWidth, outHeight)
            }
            Canvas(out).drawBitmap(input, LOGO_PAD_PX.toFloat(), LOGO_PAD_PX.toFloat(), null)
            return out
        }
    }
}

suspend fun ImageLoader.loadImageBitmap(request: ImageRequest): ImageBitmap? =
    (execute(request) as? SuccessResult)?.image?.toBitmap()?.asImageBitmap()

/** The image for [request] if it is in the memory cache right now, without suspending. */
fun ImageLoader.cachedImageBitmap(request: ImageRequest): ImageBitmap? {
    val key = request.memoryCacheKey ?: return null
    return memoryCache?.get(MemoryCache.Key(key))?.image?.toBitmap()?.asImageBitmap()
}

/**
 * The launcher went to the background: drops the large images (sharp backdrops) from the memory cache and keeps
 * a few MB of the small ones (blurred backdrops, logos), so coming back shows Continue Watching at once.
 */
fun ImageLoader.trimForBackground() {
    val cache = memoryCache ?: return
    for (key in cache.keys.toList()) {
        if ((cache.get(key)?.image?.size ?: 0L) > WARM_IMAGE_MAX_BYTES) cache.remove(key)
    }
    cache.trimToSize(WARM_CACHE_BYTES)
}

private const val WARM_IMAGE_MAX_BYTES = 2L * 1024 * 1024
private const val WARM_CACHE_BYTES = 12L * 1024 * 1024

@Immutable
sealed interface ImageState {
    data object Loading : ImageState
    data class Ready(val bitmap: ImageBitmap, val url: String) : ImageState
    data object Failed : ImageState
}

/** The bitmap if this state is a loaded image of [url] (a state can briefly still hold the previous url's image). */
fun ImageState.bitmapFor(url: String?): ImageBitmap? = (this as? ImageState.Ready)?.takeIf { it.url == url }?.bitmap

/** Loads [url] with the request built by [request]; restarts when the url changes. */
@Composable
fun rememberImage(url: String?, request: (Context, String) -> ImageRequest): State<ImageState> =
    rememberLoadedImage(url, cached = { loader, context, u -> loader.cachedImageBitmap(request(context, u)) }) { loader, context, u ->
        loader.loadImageBitmap(request(context, u))
    }

/**
 * Loads [url] with [load]; restarts when the url changes. An image [cached] finds in the memory cache is there
 * from the first frame, with no load at all.
 */
@Composable
fun rememberLoadedImage(
    url: String?,
    cached: (loader: ImageLoader, context: Context, url: String) -> ImageBitmap? = { _, _, _ -> null },
    load: suspend (loader: ImageLoader, context: Context, url: String) -> ImageBitmap?,
): State<ImageState> {
    val context = LocalContext.current
    val loader = (context.applicationContext as GlassHomeApp).imageLoader
    val initial: ImageState = remember {
        if (url == null) ImageState.Failed else cached(loader, context, url)?.let { ImageState.Ready(it, url) } ?: ImageState.Loading
    }
    return produceState(initialValue = initial, url) {
        if (url == null) {
            value = ImageState.Failed
            return@produceState
        }
        if ((value as? ImageState.Ready)?.url == url) return@produceState
        cached(loader, context, url)?.let {
            value = ImageState.Ready(it, url)
            return@produceState
        }
        value = ImageState.Loading
        value = load(loader, context, url)?.let { ImageState.Ready(it, url) } ?: ImageState.Failed
    }
}
