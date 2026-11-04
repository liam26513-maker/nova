package it.manlio.nova.core.modes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.LocalDate
import java.util.Properties

/** Le durate del Pomodoro, in minuti. */
data class PomodoroDurations(val focus: Int = DEFAULT_FOCUS, val pause: Int = DEFAULT_PAUSE) {
    companion object {
        const val DEFAULT_FOCUS = 25
        const val DEFAULT_PAUSE = 5
    }
}

/** Ciò che la Modalità Studio ricorda: sessione del Pomodoro, durate, minuti studiati per giorno e app bloccate. */
class StudyStore(private val file: File) {
    private val values = Properties().apply { runCatching { file.inputStream().use(::load) } }
    private val durationsNow =
        MutableStateFlow(
            PomodoroDurations(
                values.getProperty(FOCUS)?.toIntOrNull()?.takeIf { it > 0 } ?: PomodoroDurations.DEFAULT_FOCUS,
                values.getProperty(PAUSE)?.toIntOrNull()?.takeIf { it > 0 } ?: PomodoroDurations.DEFAULT_PAUSE,
            ),
        )
    private val blockedNow =
        MutableStateFlow(values.getProperty(BLOCKED).orEmpty().split(',').filter { it.isNotEmpty() }.toSet())

    val durations: StateFlow<PomodoroDurations> = durationsNow.asStateFlow()
    val blocked: StateFlow<Set<String>> = blockedNow.asStateFlow()

    /** La sessione in corso; un valore illeggibile vale come nessuna sessione. */
    var session: PomodoroState?
        get() =
            synchronized(values) {
                val phase = PomodoroPhase.entries.firstOrNull { it.name == values.getProperty(PHASE) }
                val end = values.getProperty(ENDS_AT)?.toLongOrNull()
                if (phase != null && end != null) PomodoroState(phase, end) else null
            }
        set(value) =
            change {
                if (value == null) {
                    remove(PHASE)
                    remove(ENDS_AT)
                } else {
                    setProperty(PHASE, value.phase.name)
                    setProperty(ENDS_AT, value.endsAt.toString())
                }
            }

    fun setDurations(value: PomodoroDurations) {
        durationsNow.value = value
        change {
            setProperty(FOCUS, value.focus.toString())
            setProperty(PAUSE, value.pause.toString())
        }
    }

    fun setBlocked(
        pkg: String,
        on: Boolean,
    ) {
        val next = if (on) blockedNow.value + pkg else blockedNow.value - pkg
        blockedNow.value = next
        change { setProperty(BLOCKED, next.joinToString(",")) }
    }

    fun minutes(day: LocalDate): Int = synchronized(values) { values.getProperty(DAY + day)?.toIntOrNull() ?: 0 }

    fun addMinutes(
        day: LocalDate,
        minutes: Int,
    ) {
        val total = minutes(day) + minutes
        change { setProperty(DAY + day, total.toString()) }
    }

    private fun change(edit: Properties.() -> Unit) {
        synchronized(values) {
            values.edit()
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.outputStream().use { values.store(it, "NOVA studio") }
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    private companion object {
        const val PHASE = "phase"
        const val ENDS_AT = "endsAt"
        const val FOCUS = "focus"
        const val PAUSE = "pause"
        const val BLOCKED = "blocked"
        const val DAY = "day."
    }
}
