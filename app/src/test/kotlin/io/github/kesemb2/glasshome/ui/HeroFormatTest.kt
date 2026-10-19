package io.github.kesemb2.glasshome.ui

import io.github.kesemb2.glasshome.ui.hero.HeroFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeroFormatTest {

    @Test
    fun remainingRoundsUpToWholeMinutes() {
        assertEquals(0 to 42, HeroFormat.remaining(41L * 60_000 + 1))
        assertEquals(0 to 42, HeroFormat.remaining(42L * 60_000))
        assertEquals(1 to 12, HeroFormat.remaining(72L * 60_000))
        assertEquals(2 to 0, HeroFormat.remaining(120L * 60_000))
    }

    @Test
    fun remainingNeverShowsZero() {
        assertEquals(0 to 1, HeroFormat.remaining(5_000))
        assertEquals(0 to 1, HeroFormat.remaining(0))
    }

    @Test
    fun durationRoundsToNearestMinute() {
        assertEquals(1 to 52, HeroFormat.duration(112L * 60_000 + 20_000))
        assertEquals(0 to 45, HeroFormat.duration(44L * 60_000 + 40_000))
        assertNull(HeroFormat.duration(0))
        assertNull(HeroFormat.duration(20_000))
    }
}
