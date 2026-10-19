package io.github.kesemb2.glasshome.ui.hero

import kotlin.math.ceil
import kotlin.math.roundToInt

/** Pure time formatting for the hero, unit-tested. Results are (hours, minutes). */
object HeroFormat {

    /** Time left, rounded up to the next whole minute (never shows "0 min left"). */
    fun remaining(millis: Long): Pair<Int, Int> {
        val minutes = ceil(millis.coerceAtLeast(0) / 60_000.0).toInt().coerceAtLeast(1)
        return minutes / 60 to minutes % 60
    }

    /** Runtime rounded to the nearest minute; null when unknown or under 30 seconds. */
    fun duration(millis: Long): Pair<Int, Int>? {
        val minutes = (millis / 60_000.0).roundToInt()
        return if (minutes <= 0) null else minutes / 60 to minutes % 60
    }
}
