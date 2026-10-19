package io.github.kesemb2.glasshome.image

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/** What an app card draws: the TV banner, or the icon on its dominant colour. */
@Immutable
class AppVisual(val banner: ImageBitmap?, val icon: ImageBitmap?, val color: Color, val updatedAt: Long, val width: Int)

/**
 * Card images for the launcher's apps, rendered once at the size they are shown at (banners at card size,
 * icons at 48 dp) and moved to GPU memory. Rendered cards are also saved as small PNGs per app version and size,
 * so after a restart they decode straight into GPU memory instead of being drawn again from each app's resources
 * (the slow part). Dominant colours are cached per app version, so a card has its colour immediately while its
 * image loads. Everything but the colours is dropped by [clear] when the system is short of memory.
 */
@Stable
class AppVisuals(private val context: Context) {
    private val loaded = mutableStateMapOf<String, AppVisual>()
    private val prefs = context.getSharedPreferences("app_colors", Context.MODE_PRIVATE)
    private val colors = ConcurrentHashMap<String, Int>()
    private val dir = File(context.cacheDir, "cards")

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
        // Saved cards of apps that are gone (or not shown) go too.
        withContext(Dispatchers.IO) {
            val shown = apps.mapTo(HashSet()) { it.packageName }
            dir.listFiles()?.forEach { if (it.name.substringBefore(SEPARATOR) !in shown) it.delete() }
        }
    }

    fun clear() {
        loaded.clear()
    }

    private fun render(app: AppEntry, width: Int, height: Int, iconSize: Int): Pair<String, AppVisual>? {
        val stamp = app.updatedAt.toString()
        val storedColor = prefs.getString(app.packageName, null)
            ?.takeIf { it.substringBefore(':') == stamp }?.substringAfter(':')?.toLongOrNull(16)?.toInt()

        // Rendered before at this size: straight from the saved PNG into GPU memory.
        if (storedColor != null) {
            val saved = savedCard(app, BANNER, width, height)?.let { AppVisual(it, null, Color(storedColor), app.updatedAt, width) }
                ?: savedCard(app, ICON, iconSize, iconSize)?.let { AppVisual(null, it, Color(storedColor), app.updatedAt, width) }
            if (saved != null) return app.packageName to saved
        }

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
        val color = storedColor
            ?: dominant(source).also { c ->
                colors[app.packageName] = c
                prefs.edit().putString(app.packageName, "$stamp:${Integer.toHexString(c)}").apply()
            }
        if (banner != null) saveCard(app, BANNER, banner) else saveCard(app, ICON, source)
        return app.packageName to AppVisual(
            banner = banner?.toGpu()?.asImageBitmap(),
            icon = icon?.toGpu()?.asImageBitmap(),
            color = Color(color),
            updatedAt = app.updatedAt,
            width = width,
        )
    }

    private fun cardFile(app: AppEntry, kind: String, width: Int, height: Int) =
        File(dir, "${app.packageName}$SEPARATOR${app.updatedAt}$SEPARATOR${width}x$height.$kind.png")

    private fun savedCard(app: AppEntry, kind: String, width: Int, height: Int): ImageBitmap? {
        val file = cardFile(app, kind, width, height)
        if (!file.exists()) return null
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.HARDWARE }
        return BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
    }

    /** Saves a rendered card, replacing any saved before for that app (another version or size). */
    private fun saveCard(app: AppEntry, kind: String, bitmap: Bitmap) {
        try {
            dir.mkdirs()
            dir.listFiles()?.forEach { if (it.name.startsWith(app.packageName + SEPARATOR)) it.delete() }
            val file = cardFile(app, kind, bitmap.width, bitmap.height)
            val partial = File(dir, file.name + ".tmp")
            FileOutputStream(partial).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            partial.renameTo(file)
        } catch (_: IOException) {
            // Only a cache: the card is drawn from the app's resources again next time.
        }
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
        private const val BANNER = "b"
        private const val ICON = "i"

        /** Between the parts of a saved card's file name; package names never contain it. */
        private const val SEPARATOR = '@'
    }
}
