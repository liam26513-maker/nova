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

/**
 * Regola automaticamente il livello grafico dai tempi di frame (SPEC §5.3):
 * - scende di un livello se il 90° percentile supera il budget per [BAD_SECONDS] secondi consecutivi;
 * - risale di un livello dopo [STABLE_NANOS] senza secondi lenti, mai oltre [ceiling].
 *
 * Non usa Android: riceve tempi in nanosecondi da FrameMetrics (o dai test).
 * Non è thread-safe: chiamarlo da un solo thread (quello del listener dei frame).
 */
class GraphicsLevelController(
    initial: GraphicsLevel,
    /** Il livello più ricco consentito (dal profilo del dispositivo o fissato dall'utente). */
    var ceiling: GraphicsLevel = GraphicsLevel.MAXIMUM,
    private val frameBudgetNanos: Long = 16_666_667L,
    private val onChange: (GraphicsLevel) -> Unit = {},
) {
    var level: GraphicsLevel = initial
        private set

    /** Se true il livello è fissato dall'utente e non cambia da solo. */
    var locked: Boolean = false

    private val second = LongArray(MAX_FRAMES_PER_SECOND)
    private var count = 0
    private var secondStart = -1L
    private var badSeconds = 0
    private var stableSince = -1L

    /** Registra un frame terminato all'istante [timestampNanos] e durato [durationNanos]. */
    fun onFrame(
        timestampNanos: Long,
        durationNanos: Long,
    ) {
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

    /** Imposta il livello a mano (es. dal centro di controllo). */
    fun set(
        newLevel: GraphicsLevel,
        lock: Boolean,
    ) {
        locked = lock
        change(newLevel)
        badSeconds = 0
        stableSince = secondStart
    }

    private fun closeSecond(now: Long) {
        val slow = count > 0 && p90() > frameBudgetNanos
        count = 0
        if (locked) return
        if (slow) {
            badSeconds++
            stableSince = now
            if (badSeconds >= BAD_SECONDS) {
                badSeconds = 0
                change(level.lighter())
            }
        } else {
            badSeconds = 0
            if (level.ordinal > ceiling.ordinal && now - stableSince >= STABLE_NANOS) {
                stableSince = now
                change(level.richer())
            }
        }
    }

    private fun p90(): Long {
        second.sort(0, count)
        return second[((count - 1) * 9) / 10]
    }

    private fun change(newLevel: GraphicsLevel) {
        val bounded = if (newLevel.ordinal < ceiling.ordinal) ceiling else newLevel
        if (bounded != level) {
            level = bounded
            onChange(bounded)
        }
    }

    companion object {
        const val BAD_SECONDS = 5
        const val STABLE_NANOS = 10 * 60 * 1_000_000_000L
        private const val ONE_SECOND = 1_000_000_000L
        private const val MAX_FRAMES_PER_SECOND = 240
    }
}
