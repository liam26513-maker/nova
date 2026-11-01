package it.manlio.nova

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PullDetectorTest {
    private fun detector() = PullDetector(slop = 10f)

    @Test
    fun `a pull down past the slop is recognised once`() {
        val d = detector()
        d.down(500f, 5f)
        assertFalse(d.move(502f, 12f)) // ancora dentro la soglia
        assertTrue(d.move(503f, 40f))
        assertFalse(d.move(503f, 90f)) // una volta sola per gesto
    }

    @Test
    fun `a sideways or upward movement is discarded for the whole gesture`() {
        val d = detector()
        d.down(500f, 50f)
        assertFalse(d.move(560f, 55f)) // di lato: non è il nostro gesto
        assertFalse(d.move(560f, 300f)) // anche se poi scende
        d.down(500f, 50f)
        assertFalse(d.move(500f, 20f)) // in su
    }

    @Test
    fun `a diagonal counts as a pull only when it goes down more than sideways`() {
        val d = detector()
        d.down(500f, 0f)
        assertTrue(d.move(520f, 40f))
        val e = detector()
        e.down(500f, 0f)
        assertFalse(e.move(540f, 30f))
    }

    @Test
    fun `a cancelled gesture does nothing until the next touch`() {
        val d = detector()
        d.down(500f, 0f)
        d.cancel()
        assertFalse(d.move(500f, 100f))
        d.down(500f, 0f)
        assertTrue(d.move(500f, 100f))
    }
}
