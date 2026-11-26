package it.manlio.nova.feature.home

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder

/** Un elemento della Home a icone: app, cartella o widget. [id] è stabile e identifica l'elemento. */
sealed interface HomeItem {
    val id: String

    /** Quante colonne e righe occupa nella griglia. */
    val cols: Int get() = 1
    val rows: Int get() = 1
}

@Immutable
data class AppItem(val key: String) : HomeItem {
    override val id: String get() = key
}

@Immutable
data class FolderItem(
    override val id: String,
    val name: String,
    val keys: List<String>,
) : HomeItem

/** I widget: quelli di NOVA (leggeri, disegnati in Compose) e quelli di altre app ([EXTERNAL], tramite `AppWidgetHost`). */
enum class WidgetKind(val cols: Int, val rows: Int) {
    CLOCK(2, 2),
    CALENDAR(2, 2),
    BATTERY(2, 1),
    NOTE(2, 2),
    WEATHER(2, 2),
    POMODORO(2, 1),
    MUSIC(2, 2),
    EXTERNAL(2, 2),
}

/** [ref]: per i widget esterni l'identificativo di `AppWidgetHost`; per gli altri 0. */
@Immutable
data class WidgetItem(
    override val id: String,
    val kind: WidgetKind,
    override val cols: Int = kind.cols,
    override val rows: Int = kind.rows,
    val ref: Int = 0,
    /** Testo del widget: la nota (Note), la città (Meteo), lo stato del timer (Pomodoro). */
    val text: String = "",
) : HomeItem

/** Una casella scelta a mano: pagina, colonna e riga (da 0) dell'angolo in alto a sinistra. */
@Immutable
data class Spot(val page: Int, val col: Int, val row: Int)

/**
 * Come sono disposti gli elementi della Home: ordine di lettura (la griglia li impagina, vedi [pack]), caselle scelte a mano
 * ([spots]: l'ultima scelta vince) e app tolte dalla Home ([offHome]: restano nell'elenco completo). Il dock sono i preferiti
 * e non è qui. Funzioni pure: ogni modifica restituisce una nuova disposizione.
 */
@Immutable
data class HomeLayout(
    val items: List<HomeItem> = emptyList(),
    val offHome: Set<String> = emptySet(),
    val spots: Map<String, Spot> = emptyMap(),
) {
    /**
     * Allinea la disposizione alle app che ci sono davvero ([visible]: chiavi in ordine alfabetico):
     * toglie quelle sparite o nascoste o finite nel [dock], scioglie le cartelle rimaste con una sola app
     * e mette in fondo le app nuove.
     */
    fun reconcile(
        visible: List<String>,
        dock: Set<String>,
    ): HomeLayout {
        val usable = visible.filterTo(HashSet()) { it !in dock }
        val seen = HashSet<String>()
        val kept =
            items.mapNotNull { item ->
                when (item) {
                    is AppItem -> item.takeIf { it.key in usable && seen.add(it.key) }
                    is FolderItem -> {
                        val keys = item.keys.filter { it in usable && seen.add(it) }
                        when (keys.size) {
                            0 -> null
                            1 -> AppItem(keys.single())
                            else -> item.copy(keys = keys)
                        }
                    }
                    is WidgetItem -> item
                }
            }
        val fresh = visible.filter { it in usable && it !in seen && it !in offHome }.map(::AppItem)
        val all = kept + fresh
        val ids = all.mapTo(HashSet()) { it.id }
        val next = HomeLayout(all, offHome.filterTo(HashSet()) { it in usable }, spots.filterKeys { it in ids })
        return if (next == this) this else next
    }

    /** Mette [id] nella casella [spot]; chi l'aveva scelta proprio lì la lascia e torna a scorrere con gli altri. */
    fun place(
        id: String,
        spot: Spot,
    ): HomeLayout {
        if (items.none { it.id == id }) return this
        return copy(spots = spots.filter { (k, s) -> k != id && s != spot } + (id to spot))
    }

    /** Trascina l'app [draggedKey] sull'elemento [ontoId]: app + app = nuova cartella, app + cartella = dentro la cartella. */
    fun merge(
        draggedKey: String,
        ontoId: String,
        newFolderId: String,
        folderName: String,
    ): HomeLayout {
        if (draggedKey == ontoId || items.none { it is AppItem && it.key == draggedKey }) return this
        val without = items.filterNot { it is AppItem && it.key == draggedKey }
        val ontoApp = items.any { it is AppItem && it.id == ontoId }
        val ontoSpot = spots[ontoId]
        val merged =
            without.map { item ->
                when {
                    item.id != ontoId -> item
                    item is AppItem -> FolderItem(newFolderId, folderName, listOf(item.key, draggedKey))
                    item is FolderItem -> item.copy(keys = item.keys + draggedKey)
                    else -> return this
                }
            }
        // La nuova cartella prende la casella dell'app su cui è nata.
        val kept = spots - draggedKey
        val next = if (ontoApp && ontoSpot != null) kept - ontoId + (newFolderId to ontoSpot) else kept
        return copy(items = merged, spots = next)
    }

    /** Toglie [key] dalla cartella [folderId] e la rimette subito dopo; una cartella con una sola app si scioglie. */
    fun takeOut(
        folderId: String,
        key: String,
    ): HomeLayout {
        val index = items.indexOfFirst { it.id == folderId }
        val folder = items.getOrNull(index) as? FolderItem ?: return this
        if (key !in folder.keys) return this
        val left = folder.keys - key
        val out = mutableListOf<HomeItem>()
        if (left.size >= 2) out += folder.copy(keys = left) else out += left.map(::AppItem)
        out += AppItem(key)
        // Una cartella che si scioglie lascia la sua casella all'app rimasta.
        val spot = spots[folderId]
        val next = if (left.size < 2 && spot != null) spots - folderId + (left.single() to spot) else spots
        return copy(items = items.toMutableList().apply { removeAt(index) }.also { it.addAll(index, out) }, spots = next)
    }

    fun renameFolder(
        id: String,
        name: String,
    ): HomeLayout =
        copy(
            items = items.map { if (it is FolderItem && it.id == id) it.copy(name = name.trim().take(MAX_FOLDER_NAME)) else it },
        )

    /** Toglie dalla Home: l'app resta nell'elenco completo, la cartella si scioglie nell'elenco, il widget sparisce. */
    fun remove(id: String): HomeLayout {
        val item = items.firstOrNull { it.id == id } ?: return this
        val gone =
            when (item) {
                is AppItem -> setOf(item.key)
                is FolderItem -> item.keys.toSet()
                is WidgetItem -> emptySet()
            }
        return HomeLayout(items - item, offHome + gone, spots - id)
    }

    /** Rimette [key] sulla Home (finisce in fondo alla prima occasione di [reconcile]). */
    fun placeOnHome(key: String): HomeLayout = copy(offHome = offHome - key)

    fun setNote(
        id: String,
        text: String,
    ): HomeLayout = copy(items = items.map { if (it is WidgetItem && it.id == id) it.copy(text = text.take(MAX_NOTE)) else it })

    fun add(widget: WidgetItem): HomeLayout = copy(items = items + widget)

    fun folderOf(key: String): FolderItem? = items.firstNotNullOfOrNull { (it as? FolderItem)?.takeIf { f -> key in f.keys } }

    companion object {
        const val MAX_FOLDER_NAME = 30
        const val MAX_NOTE = 500
    }
}

/** Un elemento messo in una casella della pagina [page]; [col] e [row] contano da 0. */
@Immutable
data class Placement(
    val item: HomeItem,
    val page: Int,
    val col: Int,
    val row: Int,
    val cols: Int,
    val rows: Int,
)

/**
 * Impagina gli elementi su pagine di [columns]×[rows]. Prima quelli con una casella scelta a mano ([spots], l'ultima scelta
 * vince se due si sovrappongono; una casella che non entra più nella griglia si ignora). Poi gli altri in ordine di lettura,
 * senza tornare indietro: ognuno nella prima casella libera dove entra dopo il precedente, anche nelle pagine dopo.
 * Un widget più grande della pagina si riduce. Sempre almeno una pagina.
 */
fun pack(
    items: List<HomeItem>,
    columns: Int,
    rows: Int,
    spots: Map<String, Spot> = emptyMap(),
): List<List<Placement>> {
    val grid = PageGrid(columns.coerceAtLeast(1), rows.coerceAtLeast(1))
    val byId = items.associateBy { it.id }
    val pinned = HashSet<String>()
    spots.entries.toList().asReversed().forEach { (id, s) ->
        val item = byId[id] ?: return@forEach
        // Mai più pagine che elementi: una casella assurda non crea pagine vuote all'infinito.
        if (s.page in items.indices && grid.fits(item, s.page, s.col, s.row)) {
            grid.put(item, s.page, s.col, s.row)
            pinned += id
        }
    }
    var cursor = 0
    items.forEach { item ->
        if (item.id in pinned) return@forEach
        var at = cursor
        while (!grid.fits(item, grid.pageOf(at), grid.colOf(at), grid.rowOf(at))) at++
        grid.put(item, grid.pageOf(at), grid.colOf(at), grid.rowOf(at))
        cursor = at + 1
    }
    return grid.pages
}

/** Le caselle occupate di ogni pagina, per [pack]; le pagine nascono quando servono (sempre almeno una). */
private class PageGrid(
    val cols: Int,
    val rows: Int,
) {
    private val taken = mutableListOf<Array<BooleanArray>>()
    val pages = mutableListOf<MutableList<Placement>>()

    init {
        page(0)
    }

    fun pageOf(at: Int) = at / (cols * rows)

    fun colOf(at: Int) = at % (cols * rows) % cols

    fun rowOf(at: Int) = at % (cols * rows) / cols

    private fun page(index: Int): Array<BooleanArray> {
        while (taken.size <= index) {
            taken += Array(rows) { BooleanArray(cols) }
            pages += mutableListOf<Placement>()
        }
        return taken[index]
    }

    private fun HomeItem.w() = cols.coerceIn(1, this@PageGrid.cols)

    private fun HomeItem.h() = rows.coerceIn(1, this@PageGrid.rows)

    fun fits(
        item: HomeItem,
        index: Int,
        c: Int,
        r: Int,
    ): Boolean {
        val inside = c >= 0 && r >= 0 && c + item.w() <= cols
        if (!inside || r + item.h() > rows) return false
        val grid = page(index)
        for (y in r until r + item.h()) for (x in c until c + item.w()) if (grid[y][x]) return false
        return true
    }

    fun put(
        item: HomeItem,
        index: Int,
        c: Int,
        r: Int,
    ) {
        val grid = page(index)
        for (y in r until r + item.h()) for (x in c until c + item.w()) grid[y][x] = true
        pages[index] += Placement(item, index, c, r, item.w(), item.h())
    }
}

/** Salva [HomeLayout] in un file di testo, in modo atomico e fuori dal thread principale. */
class HomeLayoutStore(private val file: File) {
    private val _layout = MutableStateFlow(runCatching { LayoutCodec.read(file.readText()) }.getOrDefault(HomeLayout()))
    val layout: StateFlow<HomeLayout> = _layout.asStateFlow()
    private val mutex = Mutex()

    suspend fun update(change: (HomeLayout) -> HomeLayout) =
        mutex.withLock {
            val next = change(_layout.value)
            if (next == _layout.value) return@withLock
            withContext(Dispatchers.IO) {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, "${file.name}.tmp")
                tmp.writeText(LayoutCodec.write(next))
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
            }
            _layout.value = next
        }
}

/** Formato su file: una riga per elemento; nomi e chiavi codificati, righe illeggibili ignorate. */
@Suppress("MagicNumber") // posizioni dei campi di ogni riga
internal object LayoutCodec {
    private const val MARK = "nova-layout 1"

    fun write(layout: HomeLayout): String =
        buildString {
            appendLine(MARK)
            layout.items.forEach { item ->
                when (item) {
                    is AppItem -> appendLine("app|${enc(item.key)}")
                    is FolderItem -> appendLine("folder|${enc(item.id)}|${enc(item.name)}|${item.keys.joinToString(",") { enc(it) }}")
                    is WidgetItem ->
                        appendLine(
                            "widget|${enc(item.id)}|${item.kind.name}|${item.cols}|${item.rows}|${item.ref}|${enc(item.text)}",
                        )
                }
            }
            layout.offHome.sorted().forEach { appendLine("off|${enc(it)}") }
            val ids = layout.items.mapTo(HashSet()) { it.id }
            layout.spots.forEach { (id, s) -> if (id in ids) appendLine("spot|${enc(id)}|${s.page}|${s.col}|${s.row}") }
        }

    fun read(text: String): HomeLayout {
        val lines = text.lines()
        if (lines.firstOrNull() != MARK) return HomeLayout()
        val items = mutableListOf<HomeItem>()
        val off = mutableSetOf<String>()
        val spots = LinkedHashMap<String, Spot>()
        lines.drop(1).forEach { line ->
            runCatching {
                val p = line.split('|')
                when (p[0]) {
                    "app" -> items += AppItem(dec(p[1]))
                    "folder" -> items += FolderItem(dec(p[1]), dec(p[2]), p[3].split(',').filter { it.isNotEmpty() }.map(::dec))
                    "widget" -> items += widget(p)
                    "off" -> off += dec(p[1])
                    "spot" -> spots[dec(p[1])] = Spot(p[2].toInt(), p[3].toInt(), p[4].toInt())
                }
            }
        }
        return HomeLayout(items, off, spots)
    }

    private fun widget(p: List<String>): WidgetItem =
        WidgetItem(
            id = dec(p[1]),
            kind = WidgetKind.valueOf(p[2]),
            cols = p[3].toInt().coerceIn(1, MAX_SPAN),
            rows = p[4].toInt().coerceIn(1, MAX_SPAN),
            ref = p[5].toInt(),
            text = p.getOrNull(6)?.let(::dec).orEmpty(),
        )

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")

    private const val MAX_SPAN = 8
}
