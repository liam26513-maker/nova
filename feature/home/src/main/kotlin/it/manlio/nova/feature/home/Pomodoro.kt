package it.manlio.nova.feature.home

import androidx.compose.runtime.Immutable

/**
 * Timer Pomodoro (25 minuti di concentrazione, 5 di pausa). Stato in forma di testo, così sta nel widget
 * e sopravvive al riavvio: se sta andando si ricorda l'istante di fine, altrimenti i millisecondi che restano.
 * Logica pura, provata con test.
 */
@Immutable
data class Pomodoro(
    val phase: Phase = Phase.FOCUS,
    val running: Boolean = false,
    /** Istante (ms, orologio di sistema) in cui finisce la fase, se [running]. */
    val endAt: Long = 0,
    /** Quanto resta, se in pausa o fermo. */
    val leftMs: Long = Phase.FOCUS.ms,
) {
    enum class Phase(val ms: Long) { FOCUS(FOCUS_MS), BREAK(BREAK_MS) }

    fun remaining(now: Long): Long = if (running) (endAt - now).coerceIn(0, phase.ms) else leftMs.coerceIn(0, phase.ms)

    fun start(now: Long): Pomodoro = if (running) this else copy(running = true, endAt = now + leftMs)

    fun pause(now: Long): Pomodoro = if (running) copy(running = false, leftMs = remaining(now)) else this

    fun reset(): Pomodoro = Pomodoro()

    /** Alla fine della fase passa all'altra, ferma e pronta a partire. */
    fun tick(now: Long): Pomodoro {
        if (!running || now < endAt) return this
        val next = if (phase == Phase.FOCUS) Phase.BREAK else Phase.FOCUS
        return Pomodoro(next, running = false, leftMs = next.ms)
    }

    /** Quanta parte della fase è passata, da 0 a 1. */
    fun progress(now: Long): Float = 1f - remaining(now).toFloat() / phase.ms

    fun encode(): String = "${phase.name}|$running|$endAt|$leftMs"

    companion object {
        const val FOCUS_MS = 25 * 60_000L
        const val BREAK_MS = 5 * 60_000L

        @Suppress("MagicNumber") // posizione dei campi nel testo
        fun decode(text: String): Pomodoro =
            runCatching {
                val parts = text.split('|')
                require(parts.size == FIELDS)
                Pomodoro(Phase.valueOf(parts[0]), parts[1].toBooleanStrict(), parts[2].toLong(), parts[3].toLong())
            }.getOrDefault(Pomodoro())

        /** "24:59" */
        fun clock(ms: Long): String {
            val total = (ms + MS_ROUND) / MS_PER_SECOND
            return "%02d:%02d".format(total / SECONDS_PER_MINUTE, total % SECONDS_PER_MINUTE)
        }

        private const val FIELDS = 4
        private const val MS_ROUND = 999L
        private const val MS_PER_SECOND = 1000L
        private const val SECONDS_PER_MINUTE = 60L
    }
}
