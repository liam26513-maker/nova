package it.manlio.nova.core.modes

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val FOCUS_MINUTES = 25
private const val BREAK_MINUTES = 5

enum class PomodoroPhase(val minutes: Int) { FOCUS(FOCUS_MINUTES), BREAK(BREAK_MINUTES) }

/** La fase in corso e quando finisce: l'ora di fine basta a mostrarla, senza nessun conto alla rovescia da ridisegnare. */
data class PomodoroState(val phase: PomodoroPhase, val endsAt: Long)

/**
 * Il timer Pomodoro della Studio (SPEC §8.4): 25 minuti di studio e 5 di pausa, a ciclo, finché non lo fermi.
 * shortcut: durate fisse e tempo tenuto in memoria, se Android chiude l'app il timer si ferma; passare a un allarme di sistema
 * e a durate scelte dall'utente quando servirà.
 */
class Pomodoro(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val onPhase: (PomodoroPhase) -> Unit,
) {
    private val current = MutableStateFlow<PomodoroState?>(null)
    private var job: Job? = null

    val state: StateFlow<PomodoroState?> = current.asStateFlow()

    fun start() {
        job?.cancel()
        job =
            scope.launch {
                var phase = PomodoroPhase.FOCUS
                while (true) {
                    val length = phase.minutes * MS_PER_MINUTE
                    current.value = PomodoroState(phase, clock() + length)
                    onPhase(phase)
                    delay(length)
                    phase = if (phase == PomodoroPhase.FOCUS) PomodoroPhase.BREAK else PomodoroPhase.FOCUS
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
        current.value = null
    }

    private companion object {
        const val MS_PER_MINUTE = 60_000L
    }
}
