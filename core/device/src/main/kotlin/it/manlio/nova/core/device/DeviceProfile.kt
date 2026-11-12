package it.manlio.nova.core.device

import it.manlio.nova.core.design.GraphicsLevel
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** Capacità reali del tablet, misurate al primo avvio (SPEC §3.6). Tutte le decisioni leggono questo. */
@Serializable
data class DeviceProfile(
    val manufacturer: String,
    val model: String,
    val soc: String,
    val androidSdk: Int,
    val cpuCores: Int,
    /** Frequenze massime distinte dei cluster, in MHz, dalla più alta. */
    val cpuClustersMhz: List<Int>,
    val ramMb: Long,
    val freeStorageMb: Long,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val densityDpi: Int,
    val refreshRatesHz: List<Float>,
    val supportsRealtimeBlur: Boolean,
    val supportsFreeform: Boolean,
    val supportsMultipleUsers: Boolean,
    val hasStylusDigitizer: Boolean,
    val benchmark: BenchmarkResult? = null,
    /** Livello da cui parte la modalità automatica. */
    val startLevel: GraphicsLevel = GraphicsLevel.BALANCED,
    /** Il livello più ricco che il tablet regge. */
    val ceiling: GraphicsLevel = GraphicsLevel.MAXIMUM,
    val measuredAt: Long = 0,
    /** Versione del metodo di misura: i risultati di metodi più vecchi vanno rifatti. */
    val benchmarkMethod: Int = 0,
) {
    /** Il profilo senza un benchmark misurato con un metodo superato. */
    fun withoutStaleBenchmark(): DeviceProfile =
        if (benchmark != null && benchmarkMethod < LevelAdvisor.METHOD) {
            copy(benchmark = null, startLevel = GraphicsLevel.BALANCED, ceiling = GraphicsLevel.MAXIMUM)
        } else {
            this
        }
}

/** Un frame misurato: durata totale e scadenza in millisecondi; [vsyncMs] è l'istante del vsync (0 = sconosciuto). */
data class FrameSample(
    val totalMs: Float,
    val deadlineMs: Float,
    val vsyncMs: Double = 0.0,
)

/**
 * Esito del micro-benchmark per livello: 90° percentile dei tempi di frame (ms) e percentuale
 * di frame lenti (oltre il doppio della loro scadenza).
 */
@Serializable
data class BenchmarkResult(
    val p90MsByLevel: Map<GraphicsLevel, Float>,
    val jankPercentByLevel: Map<GraphicsLevel, Float>,
) {
    /** Percentuale di frame fluidi, 0–100. */
    fun smoothPercent(level: GraphicsLevel): Float = FULL - (jankPercentByLevel[level] ?: FULL)
}

/**
 * Sceglie livello di partenza e tetto dai risultati del benchmark (SPEC §3.6, §5.3).
 * Un frame è "lento" se dura più di [JANK_MULTIPLIER] volte la sua scadenza (euristica di JankStats:
 * FrameMetrics.TOTAL_DURATION include la coda verso lo schermo, quindi superare di poco la scadenza
 * non significa perdere un frame). Un livello è "fluido" se meno del [MAX_JANK_PERCENT] dei frame è lento.
 * Il tetto è il livello più ricco fluido; si parte dal tetto, ma mai sopra Bilanciato se la RAM è poca.
 */
object LevelAdvisor {
    /**
     * Versione del metodo di misura (1: soglia fissa 16,7 ms, troppo severa; 2: scadenza × 2; 3: fotogrammi persi sullo schermo,
     * dai buchi tra un vsync e l'altro, perché sul tablet di riferimento la scadenza data da Android è inaffidabile e il
     * metodo 2 diceva «fluido al 100%» con 9 fotogrammi su 10 entro 91 ms).
     */
    const val METHOD = 3
    const val JANK_MULTIPLIER = 2f
    const val MAX_JANK_PERCENT = 5f
    const val LOW_RAM_MB = 3_000L
    private const val ALL_PERCENT = 100f
    private const val P90_NUM = 9
    private const val P90_DEN = 10

    fun advise(
        result: BenchmarkResult,
        ramMb: Long,
        realtimeBlur: Boolean,
    ): Pair<GraphicsLevel, GraphicsLevel> {
        val smooth =
            GraphicsLevel.entries.filter { level ->
                val jank = result.jankPercentByLevel[level] ?: return@filter false
                jank < MAX_JANK_PERCENT
            }
        var ceiling = smooth.firstOrNull() ?: GraphicsLevel.PERFORMANCE
        if (!realtimeBlur && ceiling == GraphicsLevel.MAXIMUM) ceiling = GraphicsLevel.BALANCED
        val start = if (ramMb < LOW_RAM_MB && ceiling == GraphicsLevel.MAXIMUM) GraphicsLevel.BALANCED else ceiling
        return start to ceiling
    }

    /** p90 delle durate (ms) e percentuale di frame lenti. */
    fun summarize(frames: List<FrameSample>): Pair<Float, Float> {
        if (frames.isEmpty()) return Float.MAX_VALUE to ALL_PERCENT
        val sorted = frames.map { it.totalMs }.sorted()
        val p90 = sorted[((sorted.size - 1) * P90_NUM) / P90_DEN]
        val byDeadline = frames.count { it.totalMs > it.deadlineMs * JANK_MULTIPLIER } * ALL_PERCENT / frames.size
        return p90 to maxOf(byDeadline, lostFramesPercent(frames))
    }

    /**
     * Quanti fotogrammi sono andati persi sullo schermo, in percentuale: durante la misura l'animazione non si ferma mai, quindi
     * ogni vsync dovrebbe avere il suo fotogramma. Il periodo è la mediana dei buchi; un buco più lungo di [GAP_TOLERANCE] periodi
     * vuol dire che ne sono saltati (buco / periodo − 1). Non dipende dalla scadenza dichiarata da Android.
     */
    fun lostFramesPercent(frames: List<FrameSample>): Float {
        val times = frames.map { it.vsyncMs }.filter { it > 0.0 }.sorted()
        if (times.size < MIN_FRAMES_FOR_GAPS) return 0f
        val gaps = times.zipWithNext { a, b -> b - a }.filter { it > 0.0 }
        if (gaps.isEmpty()) return 0f
        val period = gaps.sorted()[gaps.size / 2]
        val lost = gaps.filter { it > period * GAP_TOLERANCE }.sumOf { (it / period).roundToInt() - 1 }
        return lost * ALL_PERCENT / (times.size + lost)
    }

    private const val MIN_FRAMES_FOR_GAPS = 10
    private const val GAP_TOLERANCE = 1.5
}

private const val FULL = 100f
