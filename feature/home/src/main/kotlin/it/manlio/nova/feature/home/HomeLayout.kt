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
enum class WidgetKind(val cols: Int, val rows: Int) { CLOCK(2, 2), CALENDAR(2, 2), BATTERY(2, 1), NOTE(2, 2), EXTERNAL(2, 2) }

/** [ref]: per i widget esterni l'identificativo di `AppWidgetHost`; per gli altri 0. */
@Immutable
data class WidgetItem(
    override val id: String,
    val kind: WidgetKind,
    override val cols: Int = kind.cols,
    override val rows: Int = kind.rows,
    val ref: Int = 0,
    /** Testo del widget Note. */
    val text: String = "",
) : HomeItem

/**
 * Come sono disposti gli elementi della Home: ordine di lettura (la griglia li impagina, vedi [pack]) e app
 * tolte dalla Home ([offHome]: restano nell'elenco completo). Il dock sono i preferiti e non è qui.
 * Funzioni pure: ogni modifica restituisce una nuova disposizione.
 */
@Immutable
data class HomeLayout(
    val items: List<HomeItem> = emptyList(),
    val offHome: Set<String> = emptySet(),
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
        val next = HomeLayout(kept + fresh, offHome.filterTo(HashSet()) { it in usable })
        return if (next == this) this else next
    }

    /** Sposta [id] prima di [beforeId], o in fondo se è null o non esiste. */
    fun move(
        id: String,
        beforeId: String?,
    ): HomeLayout {
        val item = items.firstOrNull { it.id == id } ?: return this
        if (id == beforeId) return this
        val rest = items - item
        val at = rest.indexOfFirst { it.id == beforeId }.takeIf { it >= 0 } ?: rest.size
        return copy(items = rest.toMutableList().apply { add(at, item) })
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
        val merged =
            without.map { item ->
                when {
                    item.id != ontoId -> item
                    item is AppItem -> FolderItem(newFolderId, folderName, listOf(item.key, draggedKey))
                    item is FolderItem -> item.copy(keys = item.keys + draggedKey)
                    else -> return this
                }
            }
        return copy(items = merged)
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
        return copy(items = items.toMutableList().apply { removeAt(index) }.also { it.addAll(index, out) })
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
        return HomeLayout(items - item, offHome + gone)
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
 * Impagina gli elementi in ordine di lettura su pagine di [columns]×[rows], senza tornare indietro:
 * ogni elemento va nella prima casella libera dove entra dopo il precedente, altrimenti nella pagina dopo.
 * Un widget più grande della pagina si riduce. Sempre almeno una pagina.
 */
fun pack(
    items: List<HomeItem>,
    columns: Int,
    rows: Int,
): List<List<Placement>> {
    val cols = columns.coerceAtLeast(1)
    val rws = rows.coerceAtLeast(1)
    val pages = mutableListOf(mutableListOf<Placement>())
    var taken = Array(rws) { BooleanArray(cols) }
    var cursor = 0

    fun fits(
        at: Int,
        w: Int,
        h: Int,
    ): Boolean {
        val c = at % cols
        val r = at / cols
        if (c + w > cols || r + h > rws) return false
        for (y in r until r + h) for (x in c until c + w) if (taken[y][x]) return false
        return true
    }
    items.forEach { item ->
        val w = item.cols.coerceIn(1, cols)
        val h = item.rows.coerceIn(1, rws)
        var at = cursor
        while (at < cols * rws && !fits(at, w, h)) at++
        if (at >= cols * rws) {
            pages.add(mutableListOf())
            taken = Array(rws) { BooleanArray(cols) }
            at = 0
        }
        val c = at % cols
        val r = at / cols
        for (y in r until r + h) for (x in c until c + w) taken[y][x] = true
        pages.last() += Placement(item, pages.lastIndex, c, r, w, h)
        cursor = at + 1
    }
    return pages
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
        }

    fun read(text: String): HomeLayout {
        val lines = text.lines()
        if (lines.firstOrNull() != MARK) return HomeLayout()
        val items = mutableListOf<HomeItem>()
        val off = mutableSetOf<String>()
        lines.drop(1).forEach { line ->
            runCatching {
                val p = line.split('|')
                when (p[0]) {
                    "app" -> items += AppItem(dec(p[1]))
                    "folder" -> items += FolderItem(dec(p[1]), dec(p[2]), p[3].split(',').filter { it.isNotEmpty() }.map(::dec))
                    "widget" -> items += widget(p)
                    "off" -> off += dec(p[1])
                }
            }
        }
        return HomeLayout(items, off)
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
