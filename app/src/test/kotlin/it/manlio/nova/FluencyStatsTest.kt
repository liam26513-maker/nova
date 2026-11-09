package it.manlio.nova

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FluencyStatsTest {
    @Test
    fun `too few frames give no figure, enough give the percentage`() {
        assertNull(FluencyStats.percent(FluencyStats.MIN_FRAMES - 1, 10))
        assertEquals(2.5f, FluencyStats.percent(400, 10)!!, 0.001f)
    }
}
