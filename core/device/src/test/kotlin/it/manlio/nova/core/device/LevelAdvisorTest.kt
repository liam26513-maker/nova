package it.manlio.nova.core.device

import it.manlio.nova.core.design.GraphicsLevel.BALANCED
import it.manlio.nova.core.design.GraphicsLevel.MAXIMUM
import it.manlio.nova.core.design.GraphicsLevel.PERFORMANCE
import org.junit.Assert.assertEquals
import org.junit.Test

class LevelAdvisorTest {
    private fun result(
        max: Float,
        balanced: Float,
        perf: Float,
        jank: Float = 1f,
    ) = BenchmarkResult(
        p90MsByLevel = mapOf(MAXIMUM to max, BALANCED to balanced, PERFORMANCE to perf),
        jankPercentByLevel = mapOf(MAXIMUM to jank, BALANCED to jank, PERFORMANCE to jank),
    )

    @Test
    fun `fast device can use maximum`() {
        assertEquals(MAXIMUM to MAXIMUM, LevelAdvisor.advise(result(12f, 9f, 7f), ramMb = 6000, realtimeBlur = true))
    }

    @Test
    fun `slow blur keeps the ceiling at balanced`() {
        assertEquals(BALANCED to BALANCED, LevelAdvisor.advise(result(24f, 14f, 10f), ramMb = 6000, realtimeBlur = true))
    }

    @Test
    fun `nothing smooth falls back to performance`() {
        assertEquals(PERFORMANCE to PERFORMANCE, LevelAdvisor.advise(result(40f, 30f, 25f), ramMb = 6000, realtimeBlur = true))
    }

    @Test
    fun `too much jank disqualifies a level even with a good p90`() {
        assertEquals(PERFORMANCE to PERFORMANCE, LevelAdvisor.advise(result(12f, 12f, 12f, jank = 20f), 6000, true))
    }

    @Test
    fun `low ram starts at balanced even if maximum is smooth`() {
        assertEquals(BALANCED to MAXIMUM, LevelAdvisor.advise(result(12f, 9f, 7f), ramMb = 2048, realtimeBlur = true))
    }

    @Test
    fun `without realtime blur the ceiling is balanced`() {
        assertEquals(BALANCED to BALANCED, LevelAdvisor.advise(result(12f, 9f, 7f), ramMb = 6000, realtimeBlur = false))
    }

    @Test
    fun `summarize computes p90 and jank`() {
        val frames = List(90) { 10f } + List(10) { 30f }
        val (p90, jank) = LevelAdvisor.summarize(frames)
        assertEquals(10f, p90)
        assertEquals(10f, jank)
        assertEquals(Float.MAX_VALUE to 100f, LevelAdvisor.summarize(emptyList()))
    }
}
