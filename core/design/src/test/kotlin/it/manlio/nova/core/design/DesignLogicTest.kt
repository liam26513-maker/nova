package it.manlio.nova.core.design

import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphicsLevelControllerTest {
    private val second = 1_000_000_000L
    private val slowFrame = 30_000_000L
    private val fastFrame = 8_000_000L

    private fun GraphicsLevelController.feed(
        startSec: Long,
        seconds: Long,
        duration: Long,
    ) {
        for (s in startSec until startSec + seconds) {
            for (f in 0 until 60) onFrame(s * second + f * (second / 60), duration)
        }
    }

    @Test
    fun `five slow seconds downgrade one level`() {
        val changes = mutableListOf<GraphicsLevel>()
        val c = GraphicsLevelController(GraphicsLevel.MAXIMUM) { changes += it }
        c.feed(0, 5, slowFrame) // il quinto secondo si chiude al primo frame del sesto
        assertEquals(GraphicsLevel.MAXIMUM, c.level)
        c.feed(5, 1, fastFrame)
        assertEquals(GraphicsLevel.BALANCED, c.level)
        assertEquals(listOf(GraphicsLevel.BALANCED), changes)
    }

    @Test
    fun `a fast second in between resets the count`() {
        val c = GraphicsLevelController(GraphicsLevel.MAXIMUM)
        c.feed(0, 4, slowFrame)
        c.feed(4, 1, fastFrame)
        c.feed(5, 4, slowFrame)
        c.feed(9, 1, fastFrame)
        assertEquals(GraphicsLevel.MAXIMUM, c.level)
    }

    @Test
    fun `upgrades after ten stable minutes but never above the ceiling`() {
        val c = GraphicsLevelController(GraphicsLevel.PERFORMANCE, ceiling = GraphicsLevel.BALANCED)
        c.feed(0, 599, fastFrame)
        assertEquals(GraphicsLevel.PERFORMANCE, c.level)
        c.feed(599, 3, fastFrame)
        assertEquals(GraphicsLevel.BALANCED, c.level)
        c.feed(602, 1300, fastFrame)
        assertEquals(GraphicsLevel.BALANCED, c.level) // tetto
    }

    @Test
    fun `locked level never changes by itself`() {
        val c = GraphicsLevelController(GraphicsLevel.MAXIMUM)
        c.set(GraphicsLevel.MAXIMUM, lock = true)
        c.feed(0, 30, slowFrame)
        assertEquals(GraphicsLevel.MAXIMUM, c.level)
    }

    @Test
    fun `lightest level does not go further`() {
        val c = GraphicsLevelController(GraphicsLevel.PERFORMANCE)
        c.feed(0, 30, slowFrame)
        assertEquals(GraphicsLevel.PERFORMANCE, c.level)
    }
}

class BoxBlurTest {
    @Test
    fun `uniform image is unchanged`() {
        val px = IntArray(16 * 9) { 0xFF336699.toInt() }
        assertArrayEquals(px, BoxBlur.blur(px, 16, 9, radius = 3))
    }

    @Test
    fun `a bright pixel spreads to its neighbours and the original is untouched`() {
        val w = 9
        val px = IntArray(w * w) { 0xFF000000.toInt() }
        px[4 * w + 4] = 0xFFFFFFFF.toInt()
        val copy = px.copyOf()
        val out = BoxBlur.blur(px, w, w, radius = 1, passes = 1)
        assertArrayEquals(copy, px)
        val centre = out[4 * w + 4] and 0xFF
        val neighbour = out[4 * w + 5] and 0xFF
        val far = out[0] and 0xFF
        assertTrue(centre in 1..254)
        assertEquals(centre, neighbour)
        assertEquals(0, far)
        assertEquals(0xFF, out[0] ushr 24) // alfa conservato
    }

    @Test
    fun `zero radius returns a copy`() {
        val px = intArrayOf(1, 2, 3, 4)
        assertArrayEquals(px, BoxBlur.blur(px, 2, 2, radius = 0))
    }
}

class SquircleTest {
    @Test
    fun `radius is clamped so corners fit in small shapes`() {
        val r = effectiveRadius(Size(40f, 20f), requested = 100f)
        assertTrue(r * 1.528665f * 2 <= 20f + 0.001f)
        assertEquals(10f, effectiveRadius(Size(400f, 400f), 10f))
    }

    @Test
    fun `colors from config are parsed`() {
        assertEquals(0xFF0A84FF.toInt(), parseNovaColor("#0A84FF").value.shr(32).toInt())
        assertEquals(0x800A84FF.toInt(), parseNovaColor("#800A84FF").value.shr(32).toInt())
    }
}
