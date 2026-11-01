package it.manlio.nova.feature.home

import androidx.compose.runtime.Immutable

/** Quante colonne e righe di icone entrano in una pagina della griglia (stile iPad). */
@Immutable
internal data class GridMetrics(
    val columns: Int,
    val rows: Int,
) {
    val perPage: Int get() = columns * rows

    companion object {
        /** [widthDp] e [heightDp]: lo spazio che resta alla griglia, tra intestazione e dock. */
        fun of(
            widthDp: Float,
            heightDp: Float,
        ) = GridMetrics(
            columns = (widthDp / MIN_CELL_WIDTH_DP).toInt().coerceIn(MIN_COLUMNS, MAX_COLUMNS),
            rows = (heightDp / MIN_CELL_HEIGHT_DP).toInt().coerceIn(MIN_ROWS, MAX_ROWS),
        )
    }
}

/** Divide [items] in pagine da [perPage]; senza elementi resta comunque una pagina vuota. */
internal fun <T> paginate(
    items: List<T>,
    perPage: Int,
): List<List<T>> = if (items.isEmpty()) listOf(emptyList()) else items.chunked(perPage.coerceAtLeast(1))

/** Le app del dock: i primi preferiti (SPEC §6.4: da 4 a 8, qui al massimo [DOCK_MAX]). */
internal fun dockOf(favorites: List<AppEntry>): List<AppEntry> = favorites.take(DOCK_MAX)

/** Le app della griglia: tutte le visibili tranne quelle già nel dock, come sull'iPad. */
internal fun gridAppsOf(
    visible: List<AppEntry>?,
    dock: List<AppEntry>,
): List<AppEntry> {
    val inDock = dock.mapTo(HashSet()) { it.key }
    return visible.orEmpty().filter { it.key !in inDock }
}

internal const val DOCK_MAX = 6
private const val MIN_CELL_WIDTH_DP = 150f
private const val MIN_CELL_HEIGHT_DP = 128f
private const val MIN_COLUMNS = 4
private const val MAX_COLUMNS = 8
private const val MIN_ROWS = 2
private const val MAX_ROWS = 6
