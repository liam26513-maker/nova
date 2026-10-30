package it.manlio.nova.core.design

/** I tre livelli grafici adattivi (SPEC §5.3), dal più ricco al più leggero. */
enum class GraphicsLevel {
    /** Sfocatura in tempo reale con RenderEffect. */
    MAXIMUM,

    /** "Finto blur": sfondo sfocato calcolato una volta e riusato. */
    BALANCED,

    /** Superfici semi-opache piatte. */
    PERFORMANCE,
    ;

    fun lighter(): GraphicsLevel = entries.getOrElse(ordinal + 1) { this }

    fun richer(): GraphicsLevel = entries.getOrElse(ordinal - 1) { this }
}

/** Stato visibile all'utente: livello corrente e se è scelto automaticamente. */
data class GraphicsState(
    val level: GraphicsLevel,
    val automatic: Boolean,
)

/**
 * Regola automaticamente il livello grafico dai tempi di frame (SPEC §5.3):
 * - scende di un livello se il 90° percentile supera il budget per [BAD_SECONDS] secondi consecutivi;
 * - risale di un livello dopo [STABLE_NANOS] senza secondi lenti, mai oltre [ceiling].
 * In modalità manuale ([setManual]) il livello non cambia da solo; [setAutomatic] torna
 * al livello di partenza e riprende ad adattarsi.
 *
 * Non usa Android: riceve tempi in nanosecondi da FrameMetrics (o dai test).
 * Thread-safe: i frame arrivano da un thread dedicato, le scelte dell'utente dal thread principale.
 */
class GraphicsLevelController(
    startLevel: GraphicsLevel,
    ceiling: GraphicsLevel = GraphicsLevel.MAXIMUM,
    /**
     * Un secondo è "lento" se il 90° percentile supera questo valore. È il doppio di un frame a 60 Hz,
     * come l'euristica di JankStats: FrameMetrics.TOTAL_DURATION include la coda verso lo schermo,
     * quindi superare di poco 16,7 ms è normale anche quando nessun frame viene perso.
     */
    private val frameBudgetNanos: Long = 33_333_333L,
    private val onChange: (GraphicsState) -> Unit = {},
) {
    private val lock = Any()

    /** Livello da cui parte (e a cui torna) la modalità automatica. */
    private var startLevel = startLevel

    /** Il livello più ricco consentito (dal profilo del dispositivo). */
    private var ceiling = ceiling

    @Volatile
    var state: GraphicsState = GraphicsState(bounded(startLevel, ceiling), automatic = true)
        private set

    val level: GraphicsLevel get() = state.level

    private val second = LongArray(MAX_FRAMES_PER_SECOND)
    private var count = 0
    private var secondStart = -1L
    private var badSeconds = 0
    private var stableSince = -1L

    /** Registra un frame terminato all'istante [timestampNanos] e durato [durationNanos]. */
    fun onFrame(
        timestampNanos: Long,
        durationNanos: Long,
    ) = synchronized(lock) {
        if (secondStart < 0) {
            secondStart = timestampNanos
            stableSince = timestampNanos
        }
        while (timestampNanos - secondStart >= ONE_SECOND) {
            closeSecond(secondStart + ONE_SECOND)
            secondStart += ONE_SECOND
        }
        if (count < second.size) second[count++] = durationNanos
    }

    /**
     * L'utente fissa un livello: non cambierà più da solo. La scelta manuale non è limitata dal
     * tetto: il tetto vale solo per Automatico (l'utente decide sempre).
     */
    fun setManual(newLevel: GraphicsLevel) =
        synchronized(lock) {
            resetCounters()
            publish(GraphicsState(newLevel, automatic = false))
        }

    /** Nuovi limiti dal profilatore: in automatico si riparte subito dal nuovo livello; una scelta manuale resta com'è. */
    fun configure(
        start: GraphicsLevel,
        maxLevel: GraphicsLevel,
    ) = synchronized(lock) {
        startLevel = start
        ceiling = maxLevel
        resetCounters()
        if (state.automatic) publish(GraphicsState(bounded(start, maxLevel), automatic = true))
    }

    /** Torna alla scelta automatica, ripartendo dal livello di partenza. */
    fun setAutomatic() =
        synchronized(lock) {
            resetCounters()
            publish(GraphicsState(bounded(startLevel, ceiling), automatic = true))
        }

    private fun resetCounters() {
        badSeconds = 0
        count = 0
        stableSince = secondStart
    }

    private fun closeSecond(now: Long) {
        val slow = count > 0 && p90() > frameBudgetNanos
        count = 0
        if (!state.automatic) return
        if (slow) {
            badSeconds++
            stableSince = now
            if (badSeconds >= BAD_SECONDS) {
                badSeconds = 0
                publish(state.copy(level = bounded(level.lighter(), ceiling)))
            }
        } else {
            badSeconds = 0
            if (level.ordinal > ceiling.ordinal && now - stableSince >= STABLE_NANOS) {
                stableSince = now
                publish(state.copy(level = level.richer()))
            }
        }
    }

    private fun p90(): Long {
        second.sort(0, count)
        return second[((count - 1) * 9) / 10]
    }

    private fun publish(new: GraphicsState) {
        if (new != state) {
            state = new
            onChange(new)
        }
    }

    companion object {
        const val BAD_SECONDS = 5
        const val STABLE_NANOS = 10 * 60 * 1_000_000_000L
        private const val ONE_SECOND = 1_000_000_000L
        private const val MAX_FRAMES_PER_SECOND = 240

        private fun bounded(
            level: GraphicsLevel,
            ceiling: GraphicsLevel,
        ) = if (level.ordinal < ceiling.ordinal) ceiling else level
    }
}
