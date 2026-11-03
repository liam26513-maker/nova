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
    fun `reads the real format of the UJJ U6 on Android 14`() {
        val real =
            "displayRefreshRate = 60 fps\nrenderRate = 60 fps\nuid = 10182\n" +
                "layerName = SurfaceView[com.supercell.brawlstars/com.supercell.brawlstars.GameApp](BLAST)#4373\n" +
                "packageName = \ngameMode = Unsupported\ntotalFrames = 5848\ndroppedFrames = 0\naverageFPS = 48.694\n" +
                "present2present histogram is as below:\n16ms=4000 17ms=1500 33ms=300 34ms=40 50ms=8\n" +
                "frameDuration histogram is as below:\n1ms=5848\n"
        val r = TimeStatsParser.parse(real, "com.supercell.brawlstars")!!
        assertEquals(5848, r.frames)
        assertEquals(48.694, r.fps, 0.001)
        assertEquals(50.0, r.worstMs, 0.01)
    }

    @Test
    fun `finds the busiest game layer when nobody said which app is in front`() {
        val (pkg, r) = TimeStatsParser.busiest(dump) { it != "com.android.systemui" }!!
        assertEquals("com.game", pkg)
        assertEquals(1000, r.frames)
        assertNull(TimeStatsParser.busiest(dump) { false })
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
