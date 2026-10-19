package io.github.kesemb2.glasshome.ui.hero

import androidx.compose.ui.graphics.Color

/**
 * Settings › Gradient colour: what the hero's scrims and the darkening behind the app grid fade to. Black, a
 * near-black tinted with the hero image's colour ([IMAGE], see [io.github.kesemb2.glasshome.image.ScrimTone]), or
 * one of a few fixed tones dark enough that white text stays legible over them.
 */
object ScrimColors {
    const val BLACK = "black"
    const val IMAGE = "image"

    private val fixed = linkedMapOf(
        BLACK to Color.Black,
        "blue" to Color(0xFF0B1630),
        "purple" to Color(0xFF24112E),
        "green" to Color(0xFF0C2117),
        "red" to Color(0xFF2A0C14),
        "teal" to Color(0xFF08242A),
        "brown" to Color(0xFF22160C),
    )

    val KEYS: List<String> = listOf(BLACK, IMAGE) + fixed.keys.drop(1)

    /** The colour for [key]; null for [IMAGE]. Unknown keys are black. */
    fun fixed(key: String): Color? = if (key == IMAGE) null else fixed[key] ?: Color.Black
}
