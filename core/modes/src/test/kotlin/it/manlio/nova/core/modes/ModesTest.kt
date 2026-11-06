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
    fun `every mode has a unique id, no repeated action, no turbo outside gaming and a safety limit`() {
        assertEquals(Modes.all.size, Modes.all.map { it.id }.toSet().size)
        Modes.all.forEach { mode ->
            assertEquals(mode.id, mode.actions.size, mode.actions.toSet().size)
            assertTrue(mode.id, mode.maxMinutes in 1..24 * 60)
            assertTrue(mode.id, mode == Modes.GAMING || ModeAction.TURBO !in mode.actions)
        }
    }

    @Test
    fun `true colours are for creative and full brightness for presentation, never for gaming`() {
        assertTrue(ModeAction.NIGHT_LIGHT_OFF in Modes.CREATIVE.actions)
        assertTrue(ModeAction.FULL_BRIGHTNESS in Modes.PRESENTATION.actions)
        assertTrue(ModeAction.NIGHT_LIGHT_OFF !in Modes.GAMING.actions)
        assertTrue(ModeAction.FULL_BRIGHTNESS !in Modes.GAMING.actions)
    }

    @Test
    fun `the warm filter belongs to study only`() {
        assertEquals(listOf(Modes.STUDY), Modes.all.filter { ModeAction.NIGHT_LIGHT_ON in it.actions })
    }

    @Test
    fun `the config changes actions and duration, drops unknown names and keeps turbo in gaming only`() {
        val study = Modes.STUDY.withConfig(listOf("KEEP_SCREEN_ON", "DAL_FUTURO", "TURBO", "KEEP_SCREEN_ON"), 30)
        assertEquals(ModeDefinition("studio", listOf(ModeAction.KEEP_SCREEN_ON), 30), study)
        assertTrue(ModeAction.TURBO in Modes.GAMING.withConfig(listOf("TURBO"), 60).actions)
    }

    @Test
    fun `modes are found by id`() {
        assertNotNull(Modes.byId("gaming"))
        assertEquals(null, Modes.byId("inesistente"))
    }
}
