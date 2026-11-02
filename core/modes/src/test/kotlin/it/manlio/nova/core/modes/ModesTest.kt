package it.manlio.nova.core.modes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModesTest {
    @Test
    fun `gaming has every action once, the slow turbo last, and a safety limit`() {
        val actions = Modes.GAMING.actions
        assertEquals(actions.size, actions.toSet().size)
        assertEquals(ModeAction.TURBO, actions.last())
        assertTrue(Modes.GAMING.maxMinutes in 1..24 * 60)
    }

    @Test
    fun `modes are found by id`() {
        assertNotNull(Modes.byId("gaming"))
        assertEquals(null, Modes.byId("inesistente"))
    }
}
