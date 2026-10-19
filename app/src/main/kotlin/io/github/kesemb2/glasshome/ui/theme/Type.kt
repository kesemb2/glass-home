package io.github.kesemb2.glasshome.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontStyle
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.kesemb2.glasshome.R
import androidx.compose.ui.text.font.Font as ComposeFont
import androidx.compose.ui.text.font.Typeface as ComposeTypeface
import android.graphics.fonts.FontFamily as PlatformFontFamily

/**
 * Type scale (see DESIGN.md). Inter covers Latin; Heebo is chained as a per-glyph fallback so Hebrew in any
 * string (UI or content titles) renders in Heebo, then the system font for anything else.
 */
@Immutable
class GlassTypography(family: (weight: Int) -> FontFamily) {
    private val regular = family(400)
    private val medium = family(500)
    private val semiBold = family(600)
    private val bold = family(700)

    val display = TextStyle(
        fontFamily = bold, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 54.sp,
        letterSpacing = (-0.01).em,
    )
    val title1 = TextStyle(fontFamily = semiBold, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp)
    val title2 = TextStyle(fontFamily = semiBold, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp)
    val headline = TextStyle(fontFamily = semiBold, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 24.sp)
    val body = TextStyle(fontFamily = regular, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 23.sp)
    val callout = TextStyle(fontFamily = medium, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp)
    val caption = TextStyle(fontFamily = medium, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp)

    companion object {
        fun create(context: Context): GlassTypography {
            val cache = HashMap<Int, FontFamily>()
            return GlassTypography { weight -> cache.getOrPut(weight) { fontFamily(context, weight) } }
        }

        private fun fontFamily(context: Context, weight: Int): FontFamily {
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    val res = context.resources
                    fun family(resId: Int) = PlatformFontFamily.Builder(
                        Font.Builder(res, resId)
                            .setWeight(weight)
                            .setFontVariationSettings("'wght' $weight")
                            .build(),
                    ).build()
                    val typeface = Typeface.CustomFallbackBuilder(family(R.font.inter))
                        .addCustomFallback(family(R.font.heebo))
                        .setSystemFallback("sans-serif")
                        .setStyle(FontStyle(weight, FontStyle.FONT_SLANT_UPRIGHT))
                        .build()
                    return FontFamily(ComposeTypeface(typeface))
                } catch (_: Exception) {
                    // Fall through to the resource font below.
                }
            }
            return FontFamily(ComposeFont(R.font.inter, FontWeight(weight)))
        }
    }
}

val LocalGlassType = staticCompositionLocalOf<GlassTypography> { error("GlassTypography not provided") }
