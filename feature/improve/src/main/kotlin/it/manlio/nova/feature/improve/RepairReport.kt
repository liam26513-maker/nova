package it.manlio.nova.feature.improve

/** Materiale grezzo per il report di "Ripara": viene anonimizzato tutto prima di uscire dal tablet. */
data class RepairData(
    val description: String,
    val context: ImproveContext,
    val crashes: List<String>,
    val appLog: List<String>,
    val systemLog: List<String>,
    val notes: List<String> = emptyList(),
)

/** Report in Markdown per `reports/` (SPEC §4.3): anonimizzato e di dimensione limitata. */
object RepairReport {
    fun build(data: RepairData): String {
        val text =
            buildString {
                appendLine("# Segnalazione da NOVA")
                appendLine()
                appendLine("**Descrizione:** ${data.description.trim().ifEmpty { "(nessuna)" }}")
                appendLine()
                appendLine("## Contesto")
                appendLine("- App ${data.context.versionName} (${data.context.versionCode}), configurazione ${data.context.configVersion}")
                appendLine("- Dispositivo: ${data.context.device}")
                appendLine("- Shizuku: ${data.context.shizuku}")
                data.notes.forEach { appendLine("- $it") }
                section("Ultimi crash", data.crashes.take(MAX_CRASHES).joinToString("\n\n---\n\n"))
                section("Log di NOVA (ultimi eventi)", data.appLog.takeLast(MAX_APP_LOG).joinToString("\n"))
                section("Log di sistema dell'app (logcat)", data.systemLog.takeLast(MAX_SYSTEM_LOG).joinToString("\n"))
            }
        val redacted = Redactor.redact(text)
        return if (redacted.length <= MAX_CHARS) redacted else redacted.take(MAX_CHARS) + "\n\n…(report tagliato)\n"
    }

    private fun StringBuilder.section(
        title: String,
        body: String,
    ) {
        appendLine()
        appendLine("## $title")
        appendLine()
        if (body.isBlank()) {
            appendLine("(niente)")
        } else {
            appendLine("```")
            appendLine(body.replace("```", "'''"))
            appendLine("```")
        }
    }

    private const val MAX_CRASHES = 3
    private const val MAX_APP_LOG = 200
    private const val MAX_SYSTEM_LOG = 400
    private const val MAX_CHARS = 180_000
}
