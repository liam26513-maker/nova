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

    private fun jank(
        max: Float,
        balanced: Float,
        perf: Float,
    ) = BenchmarkResult(
        p90MsByLevel = mapOf(MAXIMUM to 20f, BALANCED to 20f, PERFORMANCE to 20f),
        jankPercentByLevel = mapOf(MAXIMUM to max, BALANCED to balanced, PERFORMANCE to perf),
    )

    @Test
    fun `fast device can use maximum`() {
        assertEquals(MAXIMUM to MAXIMUM, LevelAdvisor.advise(jank(1f, 0f, 0f), ramMb = 6000, realtimeBlur = true))
    }

    @Test
    fun `a p90 just above one vsync is still smooth if frames are not dropped`() {
        // Bug segnalato: con la soglia fissa di 16,7 ms ogni livello risultava "lento".
        assertEquals(MAXIMUM to MAXIMUM, LevelAdvisor.advise(result(19f, 18f, 17f, jank = 1f), 6000, true))
    }

    @Test
    fun `janky blur keeps the ceiling at balanced`() {
        assertEquals(BALANCED to BALANCED, LevelAdvisor.advise(jank(15f, 2f, 1f), ramMb = 6000, realtimeBlur = true))
    }

    @Test
    fun `nothing smooth falls back to performance`() {
        assertEquals(PERFORMANCE to PERFORMANCE, LevelAdvisor.advise(jank(40f, 30f, 25f), ramMb = 6000, realtimeBlur = true))
    }

    @Test
    fun `low ram starts at balanced even if maximum is smooth`() {
        assertEquals(BALANCED to MAXIMUM, LevelAdvisor.advise(jank(1f, 0f, 0f), ramMb = 2048, realtimeBlur = true))
    }

    @Test
    fun `without realtime blur the ceiling is balanced`() {
        assertEquals(BALANCED to BALANCED, LevelAdvisor.advise(jank(1f, 0f, 0f), ramMb = 6000, realtimeBlur = false))
    }

    @Test
    fun `summarize counts as slow only frames over twice their deadline`() {
        val frames =
            List(80) { FrameSample(totalMs = 12f, deadlineMs = 16.7f) } +
                List(10) { FrameSample(totalMs = 25f, deadlineMs = 16.7f) } + // sopra 1 vsync ma non perso
                List(10) { FrameSample(totalMs = 40f, deadlineMs = 16.7f) } // perso
        val (p90, jank) = LevelAdvisor.summarize(frames)
        assertEquals(25f, p90)
        assertEquals(10f, jank)
        assertEquals(Float.MAX_VALUE to 100f, LevelAdvisor.summarize(emptyList()))
    }

    @Test
    fun `profiles measured with the old method are discarded`() {
        val old = SAMPLE.copy(benchmark = jank(50f, 50f, 50f), ceiling = PERFORMANCE, startLevel = PERFORMANCE, benchmarkMethod = 1)
        val cleaned = old.withoutStaleBenchmark()
        assertEquals(null, cleaned.benchmark)
        assertEquals(MAXIMUM, cleaned.ceiling)
        val fresh = old.copy(benchmarkMethod = LevelAdvisor.METHOD)
        assertEquals(fresh, fresh.withoutStaleBenchmark())
    }

    private companion object {
        val SAMPLE =
            DeviceProfile(
                manufacturer = "UJJ",
                model = "U6_EEA",
                soc = "Allwinner A523",
                androidSdk = 34,
                cpuCores = 8,
                cpuClustersMhz = listOf(1800, 1416),
                ramMb = 5980,
                freeStorageMb = 98000,
                screenWidthPx = 1280,
                screenHeightPx = 800,
                densityDpi = 160,
                refreshRatesHz = listOf(60f),
                supportsRealtimeBlur = true,
                supportsFreeform = false,
                supportsMultipleUsers = false,
                hasStylusDigitizer = false,
            )
    }

    @Test
    fun `frames lost on screen count even when Android reports a huge deadline`() {
        // 60 Hz: un fotogramma ogni 16,67 ms; ogni 10 ne salta uno (buco di 2 periodi), con scadenza dichiarata enorme.
        var t = 0.0
        val frames =
            List(100) { i ->
                t += if (i % 10 == 9) 33.33 else 16.67
                FrameSample(totalMs = 20f, deadlineMs = 100f, vsyncMs = t)
            }
        val (_, jank) = LevelAdvisor.summarize(frames)
        assertEquals(9.0f, jank, 1.0f)
    }

    @Test
    fun `a steady stream of frames has no lost frames`() {
        val frames = List(60) { i -> FrameSample(totalMs = 12f, deadlineMs = 16.7f, vsyncMs = 1000.0 + i * 16.67) }
        assertEquals(0f, LevelAdvisor.lostFramesPercent(frames), 0.01f)
    }
}
