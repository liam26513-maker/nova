package it.manlio.nova.core.modes

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class PomodoroPhase { FOCUS, BREAK }

/** La fase in corso e quando finisce: l'ora di fine basta a mostrarla, senza nessun conto alla rovescia da ridisegnare. */
data class PomodoroState(val phase: PomodoroPhase, val endsAt: Long)

/**
 * Il timer Pomodoro della Studio (SPEC §8.4): studio e pausa a ciclo, finché non lo fermi. Ogni fase è salvata in [store], così
 * [restore] riprende il conto anche se Android ha chiuso l'app; [onSchedule] tiene una sveglia sulla fine della fase (null = nessuna).
 */
class Pomodoro(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val store: StudyStore,
    private val onSchedule: (Long?) -> Unit,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val onPhase: (PomodoroPhase, Int) -> Unit,
) {
    private val current = MutableStateFlow<PomodoroState?>(null)
    private var job: Job? = null

    val state: StateFlow<PomodoroState?> = current.asStateFlow()

    fun start() = run(PomodoroState(PomodoroPhase.FOCUS, clock() + length(PomodoroPhase.FOCUS)), announce = true)

    /** Riprende la sessione salvata, contando le fasi finite mentre l'app era chiusa. */
    fun restore() {
        val saved = store.session ?: return
        var state: PomodoroState = saved
        while (state.endsAt <= clock()) state = next(state)
        run(state, announce = state.phase != saved.phase)
    }

    fun stop() {
        if (job == null && store.session == null) return
        job?.cancel()
        job = null
        current.value = null
        store.session = null
        onSchedule(null)
    }

    /** I minuti di studio di oggi e degli ultimi 7 giorni, oggi compreso. */
    fun minutes(): Pair<Int, Int> {
        val today = day(clock())
        return store.minutes(today) to (0L until WEEK_DAYS).sumOf { store.minutes(today.minusDays(it)) }
    }

    private fun run(
        first: PomodoroState,
        announce: Boolean,
    ) {
        job?.cancel()
        job =
            scope.launch {
                var state = first
                var say = announce
                while (true) {
                    current.value = state
                    store.session = state
                    onSchedule(state.endsAt)
                    if (say) onPhase(state.phase, minutesOf(state.phase))
                    say = true
                    delay(state.endsAt - clock())
                    state = next(state)
                }
            }
    }

    private fun next(state: PomodoroState): PomodoroState {
        if (state.phase == PomodoroPhase.FOCUS) store.addMinutes(day(state.endsAt), store.durations.value.focus)
        val phase = if (state.phase == PomodoroPhase.FOCUS) PomodoroPhase.BREAK else PomodoroPhase.FOCUS
        return PomodoroState(phase, state.endsAt + length(phase))
    }

    private fun minutesOf(phase: PomodoroPhase) = store.durations.value.let { if (phase == PomodoroPhase.FOCUS) it.focus else it.pause }

    private fun length(phase: PomodoroPhase) = minutesOf(phase) * MS_PER_MINUTE

    private fun day(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private companion object {
        const val MS_PER_MINUTE = 60_000L
        const val WEEK_DAYS = 7L
    }
}

/**
 * Il blocco delle app che distraggono durante la Studio (SPEC §8.4). Un'app sbloccata resta libera per [UNLOCK_MS].
 * Lo sblocco sta solo in memoria: se l'app si chiude, si richiede.
 */
class StudyGate(
    private val clock: () -> Long,
    private val store: StudyStore,
) {
    private val unlocked = mutableMapOf<String, Long>()

    fun blocks(pkg: String): Boolean = pkg in store.blocked.value && clock() >= (unlocked[pkg] ?: 0L)

    fun unlock(pkg: String) {
        unlocked[pkg] = clock() + UNLOCK_MS
    }

    companion object {
        const val UNLOCK_MS = 5 * 60_000L
    }
}
