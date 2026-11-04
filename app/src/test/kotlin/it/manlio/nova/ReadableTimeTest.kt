package it.manlio.nova

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadableTimeTest {
    @Test
    fun `minutes read as a person would say them`() {
        assertEquals("0 min", readableTime(0))
        assertEquals("45 min", readableTime(45))
        assertEquals("2 h", readableTime(120))
        assertEquals("2 h 5 min", readableTime(125))
    }
}
