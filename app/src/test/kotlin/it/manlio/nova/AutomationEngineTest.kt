package it.manlio.nova

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class AutomationEngineTest {
    @Test
    fun `an edge fires only on changes, never on the starting state`() {
        val edge = Edge()
        assertEquals(listOf(false, false, true, false, true), listOf(true, true, false, false, true).map(edge::update))
    }

    @Test
    fun `the next time is today if still ahead, otherwise tomorrow`() {
        val now = ZonedDateTime.of(2026, 11, 9, 10, 30, 0, 0, ZoneOffset.UTC)
        assertEquals(now.withHour(22).withMinute(30 - 30), AutomationEngine.nextAt(now, 22))
        assertEquals(now.plusDays(1).withHour(8).withMinute(0), AutomationEngine.nextAt(now, 8))
    }
}
