package it.manlio.nova

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameSuggestionTest {
    @Test
    fun `below the smooth threshold it proposes one step down`() {
        assertEquals(90, suggestedScale(46f, null))
        assertEquals(70, suggestedScale(50f, 80))
        assertEquals(65, suggestedScale(50f, 70))
    }

    @Test
    fun `no proposal without a measure, when it is smooth, or at the minimum`() {
        assertNull(suggestedScale(null, 90))
        assertNull(suggestedScale(55f, null))
        assertNull(suggestedScale(61.5f, 70))
        assertNull(suggestedScale(40f, 65))
    }
}
