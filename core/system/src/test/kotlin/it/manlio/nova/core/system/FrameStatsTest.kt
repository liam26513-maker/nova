package it.manlio.nova.core.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameStatsTest {
    private companion object {
        const val START = 1_000_000_000L
        const val STEP = 16_667_000L
    }

    /** Una lettura finta di `--latency`: ritmo 16,67 ms, [count] fotogrammi, uno ogni [stepNs] da [startNs] (numeri interi). */
    private fun sample(
        startNs: Long,
        count: Int,
        stepNs: Long = STEP,
        period: Long = 16_666_666,
    ): String =
        buildString {
            appendLine(period)
            repeat(count) {
                val t = startNs + it * stepNs
                appendLine("${t - 5_000_000}\t$t\t${t - 2_000_000}")
            }
        }

    @Test
    fun `a steady sixty fps game reads as sixty with no slow frames`() {
        val c = FrameCollector()
        c.add(sample(START, 120))
        val r = c.report()!!
        assertEquals(60.0, r.fps, 0.5)
        assertEquals(0.0, r.slowPercent, 0.0)
        assertEquals(60.0, r.lowFps, 1.0)
        assertEquals(0, r.pauses)
    }

    @Test
    fun `a game that stutters shows low fps and slow frames`() {
        val c = FrameCollector()
        // 100 fotogrammi regolari e 10 da 50 ms (scatti): la media cala, il peggiore 1% è molto basso
        val text =
            buildString {
                appendLine(16_666_666)
                var t = 1_000.0
                repeat(110) {
                    t += if (it % 11 == 10) 50.0 else 16.667
                    appendLine("0\t${(t * 1_000_000).toLong()}\t0")
                }
            }
        c.add(text)
        val r = c.report()!!
        assertTrue("fps ${r.fps}", r.fps in 45.0..55.0)
        assertTrue(r.lowFps < 25)
        assertTrue("slow ${r.slowPercent}", r.slowPercent in 5.0..15.0)
        assertEquals(50.0, r.worstMs, 0.5)
    }

    @Test
    fun `readings that overlap are not counted twice`() {
        val c = FrameCollector()
        assertEquals(60, c.add(sample(START, 60)))
        // la seconda lettura rivede gli ultimi 30 fotogrammi e ne aggiunge 30 nuovi
        val added = c.add(sample(START + 30 * STEP, 60))
        assertEquals(30, added)
        assertEquals(90, c.report()!!.frames)
    }

    @Test
    fun `frames not yet shown and stops like loading screens are not read as stutter`() {
        val c = FrameCollector()
        val text =
            buildString {
                appendLine(16_666_666)
                appendLine("0\t${Long.MAX_VALUE}\t0") // non ancora mostrato
                appendLine("0\t0\t0") // vuoto
                var t = 1_000.0
                repeat(40) {
                    t += 16.667
                    appendLine("0\t${(t * 1_000_000).toLong()}\t0")
                }
                t += 3_000.0 // uno stop di 3 secondi (menu)
                repeat(40) {
                    t += 16.667
                    appendLine("0\t${(t * 1_000_000).toLong()}\t0")
                }
            }
        c.add(text)
        val r = c.report()!!
        assertEquals(1, r.pauses)
        assertEquals(60.0, r.fps, 1.0) // lo stop di 3 secondi non abbassa la media
        assertEquals(0.0, r.slowPercent, 0.0)
    }

    @Test
    fun `too little data or garbage gives no report instead of a wrong one`() {
        assertNull(FrameCollector().report())
        val c = FrameCollector()
        assertEquals(0, c.add("non è un elenco di fotogrammi"))
        assertEquals(0, c.add(""))
        c.add(sample(START, 5))
        assertNull(c.report())
    }

    @Test
    fun `the game layer is picked from the list, SurfaceView first`() {
        val list =
            """
            Background for SurfaceView[com.supercell.brawlstars/com.supercell.titan.GameApp](BLAST)#7
            com.supercell.brawlstars/com.supercell.titan.GameApp#0
            SurfaceView[com.supercell.brawlstars/com.supercell.titan.GameApp](BLAST)#7
            StatusBar#0
            """.trimIndent()
        val game = "SurfaceView[com.supercell.brawlstars/com.supercell.titan.GameApp](BLAST)#7"
        assertEquals(game, LayerPicker.pick(list, "com.supercell.brawlstars"))
        assertEquals("com.altro/com.altro.Main#0", LayerPicker.pick("StatusBar#0\ncom.altro/com.altro.Main#0", "com.altro"))
        assertNull(LayerPicker.pick(list, "com.non.presente"))
        val android14 =
            "3fff21c ActivityRecordInputSink com.g/.GameApp#4047\nActivityRecord{3b69a36 u0 com.g/.GameApp#4046\n" +
                "715c3c4 com.g/com.g.GameApp#4048"
        assertEquals("715c3c4 com.g/com.g.GameApp#4048", LayerPicker.pick(android14, "com.g"))
    }

    @Test
    fun `the latency command accepts real layer names and refuses anything a shell could read`() {
        val ok = SystemCommand.SurfaceLatency("SurfaceView[com.supercell.brawlstars/com.supercell.titan.GameApp](BLAST)#7")
        assertEquals(listOf("dumpsys", "SurfaceFlinger", "--latency"), ok.argv.take(3))
        listOf("", "a;b", "a|b", "a`b`", "a\"b", "a'b", "a\nb", "a&b", "a>b", "a\\b", "x".repeat(251)).forEach {
            assertTrue(it, runCatching { SystemCommand.SurfaceLatency(it) }.isFailure)
        }
        assertNotNull(SystemCommand.ListSurfaceLayers.argv)
    }
}
