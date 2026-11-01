package it.manlio.nova.feature.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Tutto ciò che la Home ricava da app, preferiti e disposizione; si ricalcola solo quando cambiano. */
@Immutable
@Suppress("LongParameterList") // raccolta di valori già pronti, non una funzione
internal class HomeData(
    val visible: List<AppEntry>?,
    val favorites: List<AppEntry>,
    val favoriteSet: Set<String>,
    val rows: List<AllRow>,
    val headerIndex: Map<Char, Int>,
    val dock: List<AppEntry>,
    val entries: Map<String, AppEntry>,
    val keys: List<String>,
    val dockKeys: Set<String>,
    val arranged: HomeLayout,
)

@Composable
internal fun rememberHomeData(
    apps: List<AppEntry>?,
    favoriteKeys: List<String>,
    prefs: HomePrefs,
    layout: HomeLayout,
): HomeData {
    val visible = remember(apps, prefs.hidden, prefs.renamed) { prefs.apply(apps) }
    val favorites = remember(visible, favoriteKeys) { favoritesOf(visible, favoriteKeys) }
    val rows = remember(visible) { allRows(visible.orEmpty()) }
    val dock = remember(favorites) { dockOf(favorites) }
    val keys = remember(visible) { visible.orEmpty().map { it.key } }
    val dockKeys = remember(dock) { dock.mapTo(HashSet()) { it.key } }
    return HomeData(
        visible = visible,
        favorites = favorites,
        favoriteSet = remember(favoriteKeys) { favoriteKeys.toSet() },
        rows = rows,
        headerIndex = remember(rows) { headerIndexOf(rows) },
        dock = dock,
        entries = remember(visible) { visible.orEmpty().associateBy { it.key } },
        keys = keys,
        dockKeys = dockKeys,
        arranged = remember(layout, keys, dockKeys) { layout.reconcile(keys, dockKeys) },
    )
}

/** Voci in più del menu di un'app nello stile a icone: modifica, togli dalla cartella, togli dalla Home, aggiungi alla Home. */
@Composable
internal fun menuExtras(
    app: AppEntry?,
    layout: HomeLayout,
    gridStyle: Boolean,
    ipad: IpadActions,
    close: () -> Unit,
): MenuExtras {
    if (app == null || !gridStyle) return MenuExtras()
    val folder = layout.folderOf(app.key)
    val placed = folder != null || layout.items.any { it is AppItem && it.key == app.key }
    return MenuExtras(
        onEdit = {
            ipad.onEditing(true)
            close()
        },
        onTakeOut =
            folder?.let { f ->
                {
                    ipad.onLayout { it.takeOut(f.id, app.key) }
                    close()
                }
            },
        onRemove =
            if (placed) {
                {
                    ipad.onLayout {
                            l ->
                        l.remove(app.key).let { r -> if (folder != null) l.takeOut(folder.id, app.key).remove(app.key) else r }
                    }
                    close()
                }
            } else {
                null
            },
        onAddToHome =
            if (app.key in layout.offHome) {
                {
                    ipad.onLayout { it.placeOnHome(app.key) }
                    close()
                }
            } else {
                null
            },
    )
}

/** Elenchi già pronti per la home ad elenco (preferiti e tutte le app) e il loro scorrimento. */
@Immutable
internal class HomeLists(
    val favorites: List<AppEntry>,
    val favoriteSet: Set<String>,
    val all: AllAppsData,
    val loaded: Boolean,
    val favoritesState: LazyListState,
) {
    val rows get() = all.rows
    val headerIndex get() = all.headerIndex
    val allState get() = all.state
}

/** L'elenco completo: righe, indice delle lettere e scorrimento. */
@Immutable
internal class AllAppsData(
    val rows: List<AllRow>,
    val headerIndex: Map<Char, Int>,
    val state: LazyListState,
)
