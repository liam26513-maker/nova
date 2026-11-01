package it.manlio.nova.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlSheetMathTest {
    @Test
    fun `without a fling the sheet goes to the nearer end`() {
        assertTrue(ControlSheetMath.shouldOpen(0.6f, 0f))
        assertFalse(ControlSheetMath.shouldOpen(0.4f, 0f))
        assertFalse(ControlSheetMath.shouldOpen(0.5f, 0f)) // a metà esatta resta chiusa: meglio non coprire la Home per sbaglio
    }

    @Test
    fun `a quick flick wins over the distance`() {
        assertTrue(ControlSheetMath.shouldOpen(0.1f, 3f)) // scatto verso il basso: apre anche a un decimo
        assertFalse(ControlSheetMath.shouldOpen(0.9f, -3f)) // scatto verso l'alto: chiude anche a nove decimi
    }

    @Test
    fun `a slow drag does not count as a flick`() {
        assertFalse(ControlSheetMath.shouldOpen(0.3f, ControlSheetMath.FLING - 0.1f))
        assertTrue(ControlSheetMath.shouldOpen(0.7f, -(ControlSheetMath.FLING - 0.1f)))
    }

    @Test
    fun `a slider value stays between 0 and 1`() {
        assertEquals(0f, ControlSheetMath.snap(-0.4f, 0), 0f)
        assertEquals(1f, ControlSheetMath.snap(1.7f, 0), 0f)
        assertEquals(0.37f, ControlSheetMath.snap(0.37f, 0), 0f) // senza gradini resta continuo (luminosità)
    }

    @Test
    fun `a slider with steps snaps to them`() {
        assertEquals(0.3f, ControlSheetMath.snap(0.34f, 10), 1e-6f)
        assertEquals(0.4f, ControlSheetMath.snap(0.36f, 10), 1e-6f)
        assertEquals(1f, ControlSheetMath.snap(2f, 15), 0f)
        assertEquals(0f, ControlSheetMath.snap(0.01f, 15), 0f)
    }
}
