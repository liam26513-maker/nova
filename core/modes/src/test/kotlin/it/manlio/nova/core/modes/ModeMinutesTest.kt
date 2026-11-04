package it.manlio.nova.core.modes

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

class ModeMinutesTest {
    @get:Rule val folder = TemporaryFolder()

    private val today = LocalDate.of(2026, 11, 4)

    private fun minutes() = ModeMinutes(folder.root.resolve("sotto/minuti.properties"))

    @Test
    fun `minutes add up per mode and day and survive a restart`() {
        minutes().add("studio", today, 25)
        minutes().add("studio", today, 5)
        minutes().add("gaming", today, 40)
        assertEquals(30, minutes().on("studio", today))
        assertEquals(40, minutes().on("gaming", today))
    }

    @Test
    fun `the week counts the last seven days only`() {
        val store = minutes()
        store.add("studio", today, 10)
        store.add("studio", today.minusDays(6), 20)
        store.add("studio", today.minusDays(7), 100)
        assertEquals(30, store.week("studio", today))
    }

    @Test
    fun `less than a minute is not written`() {
        val store = minutes()
        store.add("studio", today, 0)
        assertEquals(0, store.on("studio", today))
    }
}
