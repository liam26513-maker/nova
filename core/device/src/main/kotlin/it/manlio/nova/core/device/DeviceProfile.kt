package it.manlio.nova.core.device

import it.manlio.nova.core.design.GraphicsLevel
import kotlinx.serialization.Serializable

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
)

/** Esito del micro-benchmark: 90° percentile dei tempi di frame per livello, in millisecondi. */
@Serializable
data class BenchmarkResult(
    val p90MsByLevel: Map<GraphicsLevel, Float>,
    val jankPercentByLevel: Map<GraphicsLevel, Float>,
)

/**
 * Sceglie livello di partenza e tetto dai risultati del benchmark (SPEC §3.6, §5.3).
 * Un livello è "fluido" se il p90 resta nel budget di un frame e meno del [MAX_JANK_PERCENT] dei frame è lento.
 * Il tetto è il livello più ricco fluido; si parte dal tetto, ma mai sopra Bilanciato se la RAM è poca.
 */
object LevelAdvisor {
    const val FRAME_BUDGET_MS = 16.7f
    const val MAX_JANK_PERCENT = 5f
    const val LOW_RAM_MB = 3_000L

    fun advise(
        result: BenchmarkResult,
        ramMb: Long,
        realtimeBlur: Boolean,
    ): Pair<GraphicsLevel, GraphicsLevel> {
        val smooth =
            GraphicsLevel.entries.filter { level ->
                val p90 = result.p90MsByLevel[level] ?: return@filter false
                val jank = result.jankPercentByLevel[level] ?: 100f
                p90 <= FRAME_BUDGET_MS && jank < MAX_JANK_PERCENT
            }
        var ceiling = smooth.firstOrNull() ?: GraphicsLevel.PERFORMANCE
        if (!realtimeBlur && ceiling == GraphicsLevel.MAXIMUM) ceiling = GraphicsLevel.BALANCED
        val start = if (ramMb < LOW_RAM_MB && ceiling == GraphicsLevel.MAXIMUM) GraphicsLevel.BALANCED else ceiling
        return start to ceiling
    }

    /** p90 e percentuale di frame oltre il budget da una lista di durate in millisecondi. */
    fun summarize(frameMs: List<Float>): Pair<Float, Float> {
        if (frameMs.isEmpty()) return Float.MAX_VALUE to 100f
        val sorted = frameMs.sorted()
        val p90 = sorted[((sorted.size - 1) * 9) / 10]
        val jank = frameMs.count { it > FRAME_BUDGET_MS } * 100f / frameMs.size
        return p90 to jank
    }
}
