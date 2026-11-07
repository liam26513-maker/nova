package it.manlio.nova

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekBarsTest {
    private val today = LocalDate.of(2026, 11, 7)

    @Test
    fun `seven days end today and the fullest day is the tallest`() {
        val bars =
            weekBars(today) {
                if (it == today) {
                    30
                } else if (it == today.minusDays(6)) {
                    60
                } else {
                    0
                }
            }

        assertEquals(7, bars.size)
        assertEquals(today.minusDays(6), bars.first().day)
        assertEquals(today, bars.last().day)
        assertEquals(1f, bars.first().height, 0f)
        assertEquals(0.5f, bars.last().height, 0f)
    }

    @Test
    fun `an empty week has no bars and no division by zero`() {
        assertEquals(listOf(0f), weekBars(today) { 0 }.map { it.height }.distinct())
    }
}
