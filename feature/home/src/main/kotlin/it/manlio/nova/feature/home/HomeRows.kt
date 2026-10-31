package it.manlio.nova.feature.home

/** La home mostra i preferiti oppure tutte le app (si passa all'elenco completo con l'alfabeto). */
internal enum class HomeMode { FAVORITES, ALL }

/** Righe dell'elenco completo: intestazione di lettera o app. */
internal sealed interface AllRow {
    val key: String

    data class Header(val letter: Char) : AllRow {
        override val key = "header-$letter"
    }

    data class App(val entry: AppEntry) : AllRow {
        override val key = entry.key
    }
}

internal fun allRows(apps: List<AppEntry>): List<AllRow> =
    buildList {
        var last: Char? = null
        apps.forEach { app ->
            if (app.letter != last) {
                add(AllRow.Header(app.letter))
                last = app.letter
            }
            add(AllRow.App(app))
        }
    }

/** Indice dell'intestazione di ogni lettera nell'elenco completo. */
internal fun headerIndexOf(rows: List<AllRow>): Map<Char, Int> =
    rows.withIndex().mapNotNull { (i, row) -> (row as? AllRow.Header)?.let { it.letter to i } }.toMap()

/** I preferiti nell'ordine scelto, saltando quelli disinstallati. */
internal fun favoritesOf(
    apps: List<AppEntry>?,
    keys: List<String>,
): List<AppEntry> {
    val byKey = apps.orEmpty().associateBy { it.key }
    return keys.mapNotNull(byKey::get)
}
