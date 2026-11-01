package it.manlio.nova.feature.home

import java.text.Normalizer

/**
 * Ricerca nell'elenco delle app: senza maiuscole né accenti. Prima chi inizia con il testo,
 * poi chi ha una parola che inizia così, poi chi lo contiene.
 */
internal object AppSearch {
    private val MARKS = Regex("\\p{Mn}+")

    fun normalize(text: String): String = Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFD).replace(MARKS, "")

    /** 0 = inizia con la ricerca, 1 = una parola inizia, 2 = la contiene, null = non corrisponde. */
    fun score(
        query: String,
        label: String,
    ): Int? {
        val q = normalize(query)
        if (q.isEmpty()) return null
        val l = normalize(label)
        return when {
            l.startsWith(q) -> 0
            l.split(' ', '-', '.', '_').any { it.startsWith(q) } -> 1
            l.contains(q) -> 2
            else -> null
        }
    }

    fun <T> search(
        items: List<T>,
        query: String,
        label: (T) -> String,
    ): List<T> =
        items
            .mapNotNull { item -> score(query, label(item))?.let { it to item } }
            .sortedWith(compareBy({ it.first }, { normalize(label(it.second)) }))
            .map { it.second }
}
