package it.manlio.nova

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScaleTipTest {
    @Test
    fun `a slow game gets the next step, a fast one or one already at the minimum gets nothing`() {
        assertEquals(90, GameTools.suggestedScale(38f, null))
        assertEquals(70, GameTools.suggestedScale(30f, 80))
        assertNull(GameTools.suggestedScale(58f, null))
        assertNull(GameTools.suggestedScale(30f, 65))
        assertNull(GameTools.suggestedScale(null, null))
    }
}
