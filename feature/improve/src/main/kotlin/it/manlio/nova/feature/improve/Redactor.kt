package it.manlio.nova.feature.improve

/**
 * Anonimizza i testi prima che lascino il tablet (SPEC §4.3, §13.4, CLAUDE.md "Privacy"):
 * token e chiavi, email, numeri di telefono, indirizzi IP, parametri degli indirizzi web e
 * percorsi con nomi di persona. Meglio togliere un dato innocuo che lasciarne uno personale.
 */
object Redactor {
    private val rules =
        listOf(
            // Token GitHub (fine-grained e classici) e intestazioni di autorizzazione.
            Regex("""github_pat_[A-Za-z0-9_]{10,}""") to "[TOKEN]",
            Regex("""\bgh[pousr]_[A-Za-z0-9]{20,}""") to "[TOKEN]",
            Regex("""(?i)\b(bearer|token|authorization)\s*[:=]?\s*[A-Za-z0-9._~+/=-]{12,}""") to "$1 [TOKEN]",
            // Chiavi lunghe in genere (base64/esadecimale), ma non i nomi di classi Java (hanno punti).
            Regex("""\b[A-Za-z0-9_\-+/]{40,}={0,2}""") to "[CHIAVE]",
            Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""") to "[EMAIL]",
            // Telefoni: +39 333 1234567, 0774-123456, 3331234567…
            Regex("""(?<![\w.:-])(?:\+\d{1,3}[ -]?)?(?:3\d{2}|0\d{1,4})[ -]?\d{3,4}[ -]?\d{3,4}(?![\w.:-])""") to "[TELEFONO]",
            Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""") to "[IP]",
            // Indirizzi web: si tiene il sito, si tolgono percorso e parametri (possono contenere dati).
            Regex("""(https?://[A-Za-z0-9.-]+)[^\s)"'<>]*""") to "$1/…",
            // Nomi utente nei percorsi di Windows o Linux.
            Regex("""(?i)([/\\](?:Users|home)[/\\])[^/\\\s]+""") to "$1[UTENTE]",
        )

    fun redact(text: String): String = rules.fold(text) { acc, (pattern, replacement) -> pattern.replace(acc, replacement) }
}
