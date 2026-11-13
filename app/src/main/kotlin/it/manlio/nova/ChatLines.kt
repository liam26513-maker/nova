package it.manlio.nova

/** Un messaggio da mostrare sopra la risposta: [sender] null = l'hai scritto tu. */
data class ChatLine(
    val sender: String?,
    val text: String,
)

/** Dai messaggi della notifica (chi, testo) alle righe da mostrare. Logica pura, provata con test. */
object ChatLines {
    const val MAX = 6

    /**
     * Gli ultimi [MAX] messaggi con testo, dal più vecchio. Se l'app non dà i messaggi uno per uno, basta il testo della notifica,
     * attribuito al titolo (di solito il nome di chi scrive).
     */
    fun from(
        messages: List<Pair<String?, String?>>,
        title: String?,
        text: String?,
    ): List<ChatLine> {
        val lines =
            messages.filter { !it.second.isNullOrBlank() }.map {
                    (who, what) ->
                ChatLine(who?.takeIf { it.isNotBlank() }, what!!.trim())
            }
        if (lines.isNotEmpty()) return lines.takeLast(MAX)
        return if (text.isNullOrBlank()) emptyList() else listOf(ChatLine(title?.takeIf { it.isNotBlank() }, text.trim()))
    }
}
