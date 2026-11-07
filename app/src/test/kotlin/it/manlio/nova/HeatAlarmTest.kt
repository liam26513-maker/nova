package it.manlio.nova

import org.junit.Assert.assertEquals
import org.junit.Test

class HeatAlarmTest {
    @Test
    fun `warns once when hot and again only after cooling down`() {
        val alarm = HeatAlarm()
        assertEquals(listOf(false, true, false, false, false, true), listOf(40f, 45f, 47f, 43f, 41f, 46f).map(alarm::update))
    }
}
