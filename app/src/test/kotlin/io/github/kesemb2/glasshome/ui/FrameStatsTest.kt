package io.github.kesemb2.glasshome.ui

import io.github.kesemb2.glasshome.ui.debug.FrameStats
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameStatsTest {

    private val ms = 1_000_000L

    @Test
    fun countsOnlyTheLastSecond() {
        val stats = FrameStats()
        val now = 10_000 * ms
        stats.add(now - 2_000 * ms, 5 * ms) // too old
        repeat(60) { stats.add(now - it * 16 * ms, 8 * ms) }
        val summary = stats.summary(now)
        assertEquals(60, summary.fps)
        assertEquals(0, summary.slow)
        assertEquals(8.0, summary.p95Millis, 0.001)
    }

    @Test
    fun slowFramesAndPercentile() {
        val stats = FrameStats()
        val now = 5_000 * ms
        repeat(19) { stats.add(now - it * ms, 10 * ms) }
        stats.add(now - 100 * ms, 40 * ms)
        val summary = stats.summary(now)
        assertEquals(20, summary.fps)
        assertEquals(1, summary.slow)
        assertEquals(10.0, summary.p95Millis, 0.001)
    }

    @Test
    fun idleShowsZero() {
        assertEquals(FrameStats.Summary(0, 0, 0.0), FrameStats().summary(1_000 * ms))
    }

    @Test
    fun ringBufferKeepsTheNewest() {
        val stats = FrameStats(capacity = 4)
        val now = 2_000 * ms
        repeat(10) { stats.add(now - it * ms, 1 * ms) }
        assertEquals(4, stats.summary(now).fps)
    }
}
