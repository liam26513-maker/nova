package it.manlio.nova.feature.home

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/**
 * Ordinamento e lettere dell'alfabeto della home (SPEC §6.3): ordine italiano che ignora maiuscole
 * e accenti, lettere accentate raggruppate sotto quella base, numeri e simboli in fondo sotto "#".
 */
object AppIndex {
    const val OTHER = '#'

    private val collator = Collator.getInstance(Locale.ITALIAN).apply { strength = Collator.PRIMARY }

    fun letterOf(label: String): Char {
        val first =
            Normalizer
                .normalize(label.trim(), Normalizer.Form.NFD)
                .firstOrNull { it.isLetterOrDigit() }
                ?.uppercaseChar() ?: return OTHER
        return if (first in 'A'..'Z') first else OTHER
    }

    fun <T> sort(
        items: List<T>,
        label: (T) -> String,
    ): List<T> =
        items.sortedWith(
            compareBy<T> { letterOf(label(it)) == OTHER }.thenComparator { a, b -> collator.compare(label(a), label(b)) },
        )

    /** Prima posizione di ogni lettera in una lista già ordinata. */
    fun firstIndexByLetter(letters: List<Char>): Map<Char, Int> {
        val result = LinkedHashMap<Char, Int>()
        letters.forEachIndexed { i, c -> result.putIfAbsent(c, i) }
        return result
    }
}
