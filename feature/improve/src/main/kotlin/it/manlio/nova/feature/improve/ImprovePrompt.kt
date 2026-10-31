package it.manlio.nova.feature.improve

import java.net.URLEncoder
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Contesto che NOVA aggiunge a ogni richiesta (SPEC §4.2): versioni e dispositivo in breve. */
data class ImproveContext(
    val versionName: String,
    val versionCode: Int,
    val configVersion: String,
    val device: String,
    val shizuku: String,
)

/** Leggera = basta la configurazione (aggiornamento a caldo); profonda = serve codice. Solo un suggerimento. */
enum class RequestKind { LIGHT, DEEP }

/**
 * Costruzione dei testi per Claude Code (SPEC §4.2-4.3). Nessuna chiave API: il testo si apre in
 * Claude Code (app o sito) con il collegamento ufficiale `claude.ai/code/new?q=…&repo=…`.
 */
object ImprovePrompt {
    private val lightWords =
        listOf(
            "colore", "colori", "accento", "tema", "testo", "scritta", "nome", "titolo",
            "trasparen", "opacit", "arrotond", "spazi", "dimension",
        )

    fun classify(request: String): RequestKind {
        val lower = request.lowercase(Locale.ITALIAN)
        val deepWords = listOf("aggiung", "nuov", "widget", "modalit", "crash", "si blocca", "non funziona", "errore", "funzione")
        return if (lightWords.any { it in lower } && deepWords.none { it in lower }) RequestKind.LIGHT else RequestKind.DEEP
    }

    fun improve(
        request: String,
        context: ImproveContext,
    ): String =
        buildString {
            appendLine("Richiesta di Manlio dal tablet (pulsante Migliora):")
            appendLine("\"${request.trim()}\"")
            appendLine()
            appendContext(context)
            val kind = if (classify(request) == RequestKind.LIGHT) "leggera, forse basta config/" else "profonda, probabilmente codice"
            appendLine("- Tipo suggerito da NOVA: $kind (decidi tu)")
            appendLine()
            appendLine(RULES)
        }

    fun repair(
        description: String,
        reportPath: String?,
        context: ImproveContext,
    ): String =
        buildString {
            appendLine("Segnalazione di Manlio dal tablet (pulsante Ripara):")
            appendLine("\"${description.trim().ifEmpty { "(nessuna descrizione)" }}\"")
            appendLine()
            appendContext(context)
            appendLine()
            if (reportPath != null) {
                appendLine("Leggi `$reportPath` (report anonimizzato), individua la causa, correggi")
            } else {
                appendLine("Il report anonimizzato è negli appunti del tablet: Manlio lo incolla qui sotto. Individua la causa, correggi")
            }
            appendLine("e aggiungi un test che riproduca il problema prima della correzione.")
            appendLine()
            appendLine(RULES)
        }

    private fun StringBuilder.appendContext(c: ImproveContext) {
        appendLine("Contesto generato da NOVA:")
        appendLine("- App ${c.versionName} (${c.versionCode}), configurazione ${c.configVersion}")
        appendLine("- Dispositivo: ${c.device}")
        appendLine("- Shizuku: ${c.shizuku}")
    }

    /** Collegamento che apre Claude Code con il testo già scritto (va solo premuto Invia). */
    fun claudeLink(
        prompt: String,
        repo: String,
    ): String {
        val q = if (prompt.length > MAX_LINK_PROMPT) prompt.take(MAX_LINK_PROMPT) + "\n…(il resto è negli appunti)" else prompt
        return "https://claude.ai/code/new?q=${enc(q)}&repo=${enc(repo)}"
    }

    /** Nome del report: `reports/AAAA-MM-GG_HHMM_<slug>.md` (SPEC §4.3), solo caratteri sicuri. */
    fun reportPath(
        description: String,
        now: Long,
    ): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ROOT).apply { timeZone = TimeZone.getDefault() }.format(Date(now))
        return "reports/${stamp}_${slug(description)}.md"
    }

    fun slug(text: String): String {
        val ascii = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
        return ascii
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() }
            .take(SLUG_WORDS)
            .joinToString("-")
            .take(SLUG_MAX)
            .trimEnd('-')
            .ifEmpty { "segnalazione" }
    }

    private fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8).replace("+", "%20")

    private const val RULES =
        "Rispetta CLAUDE.md e SPEC.md del repository: branch richiesta/<slug>, PR verso main con test, " +
            "CHANGELOG in italiano semplice che cita la richiesta, nessun budget superato."
    private const val MAX_LINK_PROMPT = 6000
    private const val SLUG_WORDS = 6
    private const val SLUG_MAX = 40
}
