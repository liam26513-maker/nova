package it.manlio.nova

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceRuleTest {
    @Test
    fun `enters on connect and leaves on disconnect only when it entered`() {
        val rule = DeviceRule()
        assertEquals(true, rule.connected(anyModeOn = false))
        assertEquals(true, rule.disconnected(stillOn = true))
    }

    @Test
    fun `never replaces another mode and never leaves a mode started by hand`() {
        val rule = DeviceRule()
        assertEquals(false, rule.connected(anyModeOn = true))
        assertEquals(false, rule.disconnected(stillOn = true))
    }

    @Test
    fun `does nothing on disconnect if the user already left`() {
        val rule = DeviceRule()
        rule.connected(anyModeOn = false)
        assertEquals(false, rule.disconnected(stillOn = false))
    }
}
