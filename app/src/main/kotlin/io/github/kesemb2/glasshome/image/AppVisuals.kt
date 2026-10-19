package io.github.kesemb2.glasshome.image

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.kesemb2.glasshome.data.apps.AppEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/** What an app card draws: the TV banner, or the icon on its dominant colour. */
@Immutable
class AppVisual(val banner: ImageBitmap?, val icon: ImageBitmap?, val color: Color, val updatedAt: Long, val width: Int)

/**
 * Card images for the launcher's apps, rendered once at the size they are shown at (banners at card size,
 * icons at 48 dp) and moved to GPU memory. Dominant colours are cached on disk per app version, so a card has
 * its colour immediately while its image loads. Everything but the colours is dropped by [clear] when the
 * home screen is hidden.
 */
@Stable
class AppVisuals(private val context: Context) {
    private val loaded = mutableStateMapOf<String, AppVisual>()
    private val prefs = context.getSharedPreferences("app_colors", Context.MODE_PRIVATE)
    private val colors = ConcurrentHashMap<String, Int>()

    init {
        prefs.all.forEach { (pkg, value) ->
            val color = (value as? String)?.substringAfter(':')?.toLongOrNull(16)?.toInt()
            if (color != null) colors[pkg] = color
        }
    }

    operator fun get(pkg: String): AppVisual? = loaded[pkg]

    /** The cached dominant colour while the image is still loading. */
    fun placeholder(pkg: String): Color = Color(colors[pkg] ?: DominantColor.FALLBACK)

    /**
     * Loads what is missing, outdated or rendered at another card size ([cardWidth] x [cardHeight] px), in the
     * order given (the favorites first), publishing in small batches. Until a card is re-rendered it keeps its old
     * image, scaled.
     */
    suspend fun load(apps: List<AppEntry>, cardWidth: Int, cardHeight: Int) {
        val pending = apps.filter { app ->
            loaded[app.packageName]?.let { it.updatedAt != app.updatedAt || it.width != cardWidth } ?: true
        }
        if (pending.isEmpty()) return
        val density = context.resources.displayMetrics.density
        val iconSize = (ICON_DP * density).roundToInt()
        for (batch in pending.chunked(BATCH)) {
            val rendered = withContext(Dispatchers.IO) {
                batch.mapNotNull { app ->
                    try {
                        render(app, cardWidth, cardHeight, iconSize)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null // A broken resource must not stop the other cards.
                    }
                }
            }
            rendered.forEach { (pkg, visual) -> loaded[pkg] = visual }
        }
    }

    fun clear() {
        loaded.clear()
    }

    private fun render(app: AppEntry, width: Int, height: Int, iconSize: Int): Pair<String, AppVisual>? {
        val pm = context.packageManager
        val info = try {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getActivityInfo(
                    android.content.ComponentName(app.packageName, app.activity),
                    PackageManager.ComponentInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getActivityInfo(android.content.ComponentName(app.packageName, app.activity), 0)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        val bannerDrawable = if (app.tv) info.loadBanner(pm) ?: info.applicationInfo.loadBanner(pm) else null
        val banner = bannerDrawable?.let { draw(it, width, height) }
        val icon = if (banner == null) draw(info.loadIcon(pm), iconSize, iconSize) else null
        val source = banner ?: icon ?: return null
        val cached = prefs.getString(app.packageName, null)
        val stamp = app.updatedAt.toString()
        val color = cached?.takeIf { it.substringBefore(':') == stamp }?.substringAfter(':')?.toLongOrNull(16)?.toInt()
            ?: dominant(source).also { c ->
                colors[app.packageName] = c
                prefs.edit().putString(app.packageName, "$stamp:${Integer.toHexString(c)}").apply()
            }
        return app.packageName to AppVisual(
            banner = banner?.toGpu()?.asImageBitmap(),
            icon = icon?.toGpu()?.asImageBitmap(),
            color = Color(color),
            updatedAt = app.updatedAt,
            width = width,
        )
    }

    private fun draw(drawable: Drawable, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun dominant(bitmap: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(bitmap, SAMPLE, SAMPLE, true)
        val pixels = IntArray(SAMPLE * SAMPLE)
        small.getPixels(pixels, 0, SAMPLE, 0, 0, SAMPLE, SAMPLE)
        if (small !== bitmap) small.recycle()
        return DominantColor.of(pixels)
    }

    /** A hardware copy keeps the pixels in GPU memory only (no second, CPU-side copy). */
    private fun Bitmap.toGpu(): Bitmap {
        val gpu = copy(Bitmap.Config.HARDWARE, false) ?: return this
        recycle()
        return gpu
    }

    companion object {
        const val ICON_DP = 48f
        private const val SAMPLE = 24
        private const val BATCH = 8
    }
}
