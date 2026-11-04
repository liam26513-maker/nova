package it.manlio.nova.core.modes

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class PomodoroTest {
    @get:Rule val folder = TemporaryFolder()

    private val minute = 60_000L
    private val seen = mutableListOf<PomodoroPhase>()
    private val alarms = mutableListOf<Long?>()

    private fun store() = StudyStore(folder.root.resolve("studio.properties"))

    private fun TestScope.timer(
        store: StudyStore = store(),
        now: () -> Long = { currentTime },
    ) = Pomodoro(backgroundScope, now, store, { alarms += it }, ZoneOffset.UTC) { phase, _ -> seen += phase }

    @Test
    fun `focus and break follow each other, each with its alarm`() =
        runTest {
            val timer = timer()
            timer.start()
            runCurrent()
            assertEquals(PomodoroState(PomodoroPhase.FOCUS, 25 * minute), timer.state.value)

            advanceTimeBy(25 * minute + 1)
            assertEquals(PomodoroState(PomodoroPhase.BREAK, 30 * minute), timer.state.value)

            advanceTimeBy(5 * minute)
            assertEquals(listOf(PomodoroPhase.FOCUS, PomodoroPhase.BREAK, PomodoroPhase.FOCUS), seen)
            assertEquals(listOf<Long?>(25 * minute, 30 * minute, 55 * minute), alarms)
        }

    @Test
    fun `stop ends the cycle, cancels the alarm and forgets the session`() =
        runTest {
            val store = store()
            val timer = timer(store)
            timer.start()
            runCurrent()
            timer.stop()
            advanceTimeBy(60 * minute)

            assertNull(timer.state.value)
            assertNull(store.session)
            assertNull(alarms.last())
            assertEquals(listOf(PomodoroPhase.FOCUS), seen)
        }

    @Test
    fun `chosen durations are used and survive a restart`() =
        runTest {
            store().setDurations(PomodoroDurations(focus = 50, pause = 10))
            val timer = timer()
            timer.start()
            runCurrent()
            assertEquals(50 * minute, timer.state.value?.endsAt)
            assertEquals(PomodoroDurations(50, 10), store().durations.value)
        }

    @Test
    fun `a session goes on after the app was closed, without announcing the same phase again`() =
        runTest {
            store().session = PomodoroState(PomodoroPhase.FOCUS, 10 * minute)
            val timer = timer()
            timer.restore()
            runCurrent()
            assertEquals(PomodoroState(PomodoroPhase.FOCUS, 10 * minute), timer.state.value)
            assertTrue(seen.isEmpty())
        }

    @Test
    fun `phases ended while the app was closed are counted and the new one announced`() =
        runTest {
            val store = store()
            store.session = PomodoroState(PomodoroPhase.FOCUS, 1 * minute)
            val timer = timer(store) { 3 * minute }
            timer.restore()
            runCurrent()

            assertEquals(PomodoroState(PomodoroPhase.BREAK, 6 * minute), timer.state.value)
            assertEquals(listOf(PomodoroPhase.BREAK), seen)
            assertEquals(25 to 25, timer.minutes())
        }

    @Test
    fun `the week adds up the last seven days only`() =
        runTest {
            val store = store()
            val timer = timer(store)
            val today = java.time.LocalDate.of(1970, 1, 1)
            store.addMinutes(today, 25)
            store.addMinutes(today.minusDays(6), 50)
            store.addMinutes(today.minusDays(7), 100)
            assertEquals(25 to 75, timer.minutes())
        }

    @Test
    fun `a blocked app stays free only for the unlock time, and the list is remembered`() {
        var now = 0L
        val store = store()
        store.setBlocked("com.social", on = true)
        val gate = StudyGate({ now }, StudyStore(folder.root.resolve("studio.properties")))

        assertTrue(gate.blocks("com.social"))
        assertFalse(gate.blocks("com.lavoro"))
        gate.unlock("com.social")
        assertFalse(gate.blocks("com.social"))
        now = StudyGate.UNLOCK_MS
        assertTrue(gate.blocks("com.social"))
    }
}
