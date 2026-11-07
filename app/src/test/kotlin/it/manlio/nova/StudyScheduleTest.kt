package it.manlio.nova

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class StudyScheduleTest {
    private fun at(
        day: Int,
        hour: Int,
    ) = ZonedDateTime.of(2026, 11, day, hour, 0, 0, 0, ZoneOffset.UTC)

    @Test
    fun `today if the hour has not passed, otherwise the next weekday`() {
        // 6 novembre 2026 è venerdì.
        assertEquals(at(6, 16), StudySchedule.next(at(6, 10), 16))
        assertEquals(at(9, 16), StudySchedule.next(at(6, 17), 16))
        assertEquals(at(9, 16), StudySchedule.next(at(7, 10), 16))
    }
}
