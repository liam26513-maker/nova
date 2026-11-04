package it.manlio.nova.core.modes

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PomodoroTest {
    private val minute = 60_000L

    @Test
    fun `focus and break follow each other and each says when it ends`() =
        runTest {
            val seen = mutableListOf<PomodoroPhase>()
            val timer = Pomodoro(backgroundScope, { currentTime }, { seen += it })

            timer.start()
            runCurrent()
            assertEquals(PomodoroState(PomodoroPhase.FOCUS, 25 * minute), timer.state.value)

            advanceTimeBy(25 * minute + 1)
            assertEquals(PomodoroState(PomodoroPhase.BREAK, 30 * minute), timer.state.value)

            advanceTimeBy(5 * minute)
            assertEquals(PomodoroPhase.FOCUS, timer.state.value?.phase)
            assertEquals(listOf(PomodoroPhase.FOCUS, PomodoroPhase.BREAK, PomodoroPhase.FOCUS), seen)
        }

    @Test
    fun `stop ends the cycle and nothing is announced afterwards`() =
        runTest {
            val seen = mutableListOf<PomodoroPhase>()
            val timer = Pomodoro(backgroundScope, { currentTime }, { seen += it })

            timer.start()
            runCurrent()
            timer.stop()
            advanceTimeBy(60 * minute)

            assertNull(timer.state.value)
            assertEquals(listOf(PomodoroPhase.FOCUS), seen)
        }
}
