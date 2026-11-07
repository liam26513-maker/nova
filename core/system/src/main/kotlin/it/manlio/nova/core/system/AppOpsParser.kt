package it.manlio.nova.core.system

/** Un accesso sensibile di un'app: quale permesso e quanto tempo fa (in millisecondi). */
data class SensitiveUse(
    val op: String,
    val agoMs: Long,
)

/**
 * Legge l'uscita di `appops get <pacchetto>` (SPEC §13.3), per esempio `RECORD_AUDIO: allow; time=+5d20h0m42s447ms ago`, e tiene
 * solo fotocamera, microfono e posizione con un orario. Pura, senza Android.
 */
object AppOpsParser {
    val WATCHED = setOf("CAMERA", "RECORD_AUDIO", "FINE_LOCATION", "COARSE_LOCATION")
    private val LINE = Regex("^(?:Uid mode: )?([A-Z_]+):.*?\\btime=\\+(\\S+) ago")
    private val PART = Regex("(\\d+)(ms|d|h|m|s)")

    @Suppress("MagicNumber") // millisecondi di ogni unità
    private val UNIT_MS = mapOf("d" to 86_400_000L, "h" to 3_600_000L, "m" to 60_000L, "s" to 1_000L, "ms" to 1L)

    fun parse(text: String): List<SensitiveUse> =
        text.lineSequence().mapNotNull { line ->
            val match = LINE.find(line.trim()) ?: return@mapNotNull null
            val op = match.groupValues[1].takeIf { it in WATCHED } ?: return@mapNotNull null
            SensitiveUse(op, duration(match.groupValues[2]))
        }.toList()

    /** «5d20h0m42s447ms» in millisecondi. */
    fun duration(text: String): Long = PART.findAll(text).sumOf { it.groupValues[1].toLong() * UNIT_MS.getValue(it.groupValues[2]) }
}
