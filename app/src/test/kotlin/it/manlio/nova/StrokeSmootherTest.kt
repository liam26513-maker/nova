package it.manlio.nova

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeSmootherTest {
    private fun run(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        val s = StrokeSmoother()
        val out = ArrayList<Float>()
        points.forEach { (x, y) -> out += s.add(x, y, 3f).toList() }
        out += s.finish().toList()
        return out.chunked(3).map { it[0] to it[1] }
    }

    @Test
    fun `repeated positions from the screen add nothing`() {
        val s = StrokeSmoother()
        assertEquals(3, s.add(10f, 10f, 3f).size)
        assertEquals(0, s.add(10f, 10f, 3f).size)
    }

    @Test
    fun `the curve starts and ends on the real points`() {
        val out = run(listOf(0f to 0f, 4f to 0f, 8f to 4f, 12f to 4f))
        assertEquals(0f to 0f, out.first())
        assertEquals(12f to 4f, out.last())
    }

    @Test
    fun `a 4 pixel staircase becomes a gentle slope without corners`() {
        val stairs = (0 until 30).map { i -> (4f * ((i + 1) / 2)) to (4f * (i / 2)) }
        val out = run(stairs)
        // Lontano dai capi la linea è dritta: la distanza dalla diagonale varia meno di 1,5 pixel (gli scalini erano da 4).
        val offsets = out.drop(6).dropLast(6).map { (x, y) -> x - y }
        assertTrue("${offsets.minOrNull()}..${offsets.maxOrNull()}", offsets.max() - offsets.min() < 1.5f)
    }
}
