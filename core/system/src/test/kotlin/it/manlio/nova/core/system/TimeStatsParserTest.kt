package it.manlio.nova.core.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeStatsParserTest {
    private fun layer(
        name: String,
        total: Int,
        fps: Double,
        bins: String,
    ) = "layerName = $name\ntotalFrames = $total\ndroppedFrames = 3\naverageFPS = $fps\n" +
        "presentToPresent histogram is as below:\n$bins\nframeDuration histogram is as below:\n 1ms=5\n"

    private val dump =
        "SurfaceFlinger TimeStats:\n" +
            layer("com.android.systemui.wallpapers.ImageWallpaper#66", 500, 60.0, "16ms=500") +
            layer("SurfaceView[com.game/com.game.Main]#7", 1000, 55.0, "16ms=900 17ms=60 33ms=30 50ms=9 900ms=1")

    @Test
    fun `reads the layer of the game and ignores the others`() {
        val r = TimeStatsParser.parse(dump, "com.game")!!
        assertEquals(1000, r.frames)
        assertEquals(55.0, r.fps, 0.01)
        assertEquals(1, r.pauses) // 900 ms è un caricamento, non uno scatto
        assertEquals(50.0, r.worstMs, 0.01)
        assertEquals(3.9, r.slowPercent, 0.2) // 39 di 999 fotogrammi oltre 25 ms
        assertTrue(r.lowFps < 25)
    }

    @Test
    fun `a missing game or an unknown format gives nothing`() {
        assertNull(TimeStatsParser.parse(dump, "com.altro"))
        assertNull(TimeStatsParser.parse("tutt'altro formato", "com.game"))
        assertNull(TimeStatsParser.parse(layer("SurfaceView[com.game/x]#1", 5, 60.0, "16ms=5"), "com.game"))
    }

    @Test
    fun `the timestats command takes only the four known steps`() {
        val flags = SystemCommand.TimeStats.Step.entries.map { SystemCommand.TimeStats(it).argv.last() }
        assertEquals(listOf("-enable", "-clear", "-dump", "-disable"), flags)
        assertNotNull(SystemCommand.TimeStats(SystemCommand.TimeStats.Step.DUMP).argv.first())
    }
}
