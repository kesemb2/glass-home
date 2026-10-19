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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import coil3.ImageLoader
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
    private const val BLUR_GAIN = 1f

    const val LOGO_MAX_PX = 600

    /** Transparent margin added around every logo, room for the glow on dark logos. */
    const val LOGO_PAD_PX = 24

    fun backdrop(context: Context, url: String, widthPx: Int, heightPx: Int, hardware: Boolean = true): ImageRequest =
        ImageRequest.Builder(context)
            .data(url)
            .size(widthPx, heightPx)
            .scale(Scale.FILL)
            .allowHardware(hardware)
            .build()

    /** The sharp backdrop; retries once as a software bitmap if the hardware decode fails on this device. */
    suspend fun loadBackdrop(loader: ImageLoader, context: Context, url: String, widthPx: Int, heightPx: Int): ImageBitmap? =
        loader.loadImageBitmap(backdrop(context, url, widthPx, heightPx))
            ?: loader.loadImageBitmap(backdrop(context, url, widthPx, heightPx, hardware = false))

    fun blurred(context: Context, url: String): ImageRequest =
        ImageRequest.Builder(context)
            .data(url)
            .size(BLUR_WIDTH, BLUR_HEIGHT)
            .scale(Scale.FILL)
            .allowHardware(false)
            .transformations(BlurTransformation)
            .build()

    fun logo(context: Context, url: String): ImageRequest =
        ImageRequest.Builder(context)
            .data(url)
            .size(LOGO_MAX_PX, LOGO_MAX_PX)
            .scale(Scale.FIT)
            .allowHardware(false)
            .transformations(LogoTransformation)
            .build()

    /** Centre-crops to 480 x 270, blurs and saturates. Runs once per backdrop on Coil's decoder thread. */
    private object BlurTransformation : Transformation() {
        override val cacheKey: String = "glass-source-v3"

        override suspend fun transform(input: Bitmap, size: Size): Bitmap {
            val out = Bitmap.createBitmap(BLUR_WIDTH, BLUR_HEIGHT, Bitmap.Config.ARGB_8888)
            val scale = max(BLUR_WIDTH / input.width.toFloat(), BLUR_HEIGHT / input.height.toFloat())
            val srcWidth = BLUR_WIDTH / scale
            val srcHeight = BLUR_HEIGHT / scale
            val left = ((input.width - srcWidth) / 2f).roundToInt()
            val top = ((input.height - srcHeight) / 2f).roundToInt()
            val src = Rect(left, top, left + srcWidth.roundToInt(), top + srcHeight.roundToInt())
            Canvas(out).drawBitmap(input, src, Rect(0, 0, BLUR_WIDTH, BLUR_HEIGHT), Paint(Paint.FILTER_BITMAP_FLAG))
            val pixels = IntArray(BLUR_WIDTH * BLUR_HEIGHT)
            out.getPixels(pixels, 0, BLUR_WIDTH, 0, 0, BLUR_WIDTH, BLUR_HEIGHT)
            FastBlur.blur(pixels, BLUR_WIDTH, BLUR_HEIGHT, BLUR_RADIUS)
            FastBlur.saturate(pixels, BLUR_SATURATION, BLUR_GAIN)
            out.setPixels(pixels, 0, BLUR_WIDTH, 0, 0, BLUR_WIDTH, BLUR_HEIGHT)
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
    rememberLoadedImage(url) { loader, context, u -> loader.loadImageBitmap(request(context, u)) }

/** Loads [url] with [load]; restarts when the url changes. */
@Composable
fun rememberLoadedImage(
    url: String?,
    load: suspend (loader: ImageLoader, context: Context, url: String) -> ImageBitmap?,
): State<ImageState> {
    val context = LocalContext.current
    return produceState<ImageState>(initialValue = if (url == null) ImageState.Failed else ImageState.Loading, url) {
        if (url == null) {
            value = ImageState.Failed
            return@produceState
        }
        value = ImageState.Loading
        val loader = (context.applicationContext as GlassHomeApp).imageLoader
        value = load(loader, context, url)?.let { ImageState.Ready(it, url) } ?: ImageState.Failed
    }
}
