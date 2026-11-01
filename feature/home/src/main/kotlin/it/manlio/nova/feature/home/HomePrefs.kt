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
import java.io.StringReader
import java.io.StringWriter
import java.util.Properties

enum class TextSize { SMALL, MEDIUM, LARGE }

enum class HomeFont { MODERN, ELEGANT, TECHNICAL }

enum class TextWeight { LIGHT, REGULAR, BOLD }

enum class Side { LEFT, RIGHT }

enum class ScrimStrength { LIGHT, MEDIUM, STRONG }

enum class ThemeMode { DARK, LIGHT, AUTO }

/** Da dove viene lo sfondo della home. */
enum class WallpaperSource { SYSTEM, NOVA, PHOTO, SOLID }

enum class BlurLevel { NONE, LIGHT, STRONG, MAX }

enum class ClockStyle { THIN, BOLD, STACKED }

enum class IconSize { SMALL, MEDIUM, LARGE }

enum class IconShape { ORIGINAL, CIRCLE, SQUIRCLE, SQUARE }

/**
 * Disposizione della home: griglia di icone con dock (stile iPad), ibrida (elenco a sinistra e griglia a destra,
 * solo in orizzontale) oppure solo elenco di nomi (stile Niagara).
 */
enum class HomeLayoutStyle { IPAD, HYBRID, LIST }

/** Icone a colori o monocromatiche ("a tema", come le icone tematiche di Android 13+). */
enum class IconStyle { COLOR, MONO }

/**
 * Personalizzazione della home, sul modello di Niagara (temi, sfondo, colori, icone, caratteri,
 * orologio, elementi visibili, gesti) ma con grafica e nomi propri. Sono scelte del proprietario
 * sul tablet e restano sul dispositivo; l'aspetto (senza app nascoste e nomi) si può esportare come tema.
 */
@Immutable
data class HomePrefs(
    // Tema e colori
    val theme: ThemeMode = ThemeMode.DARK,
    /** Colore d'accento ARGB; null = preso dallo sfondo. */
    val accent: Int? = null,
    // Sfondo
    val wallpaper: WallpaperSource = WallpaperSource.SYSTEM,
    val novaWallpaper: Int = 0,
    val solidColor: Int = DEFAULT_SOLID,
    val blurLevel: BlurLevel = BlurLevel.STRONG,
    val scrim: ScrimStrength = ScrimStrength.MEDIUM,
    // Testo e orologio
    val textSize: TextSize = TextSize.MEDIUM,
    val font: HomeFont = HomeFont.MODERN,
    val weight: TextWeight = TextWeight.LIGHT,
    val showClock: Boolean = true,
    val clockStyle: ClockStyle = ClockStyle.THIN,
    val showDate: Boolean = true,
    // Icone
    val showIcons: Boolean = true,
    val iconSize: IconSize = IconSize.MEDIUM,
    val iconShape: IconShape = IconShape.SQUIRCLE,
    val iconStyle: IconStyle = IconStyle.COLOR,
    /** Pacchetto di icone di terzi (nome del pacchetto); null = icone delle app. */
    val iconPack: String? = null,
    // Disposizione e gesti
    val alphabetSide: Side = Side.RIGHT,
    /** Non fa parte dell'aspetto dei temi: cambiare tema non cambia la disposizione. */
    val layout: HomeLayoutStyle = HomeLayoutStyle.IPAD,
    val showStatusBar: Boolean = true,
    val swipeDownNotifications: Boolean = true,
    val doubleTapSleep: Boolean = false,
    /** Riquadri del centro di controllo a prima vista (ordine fisso di [ControlTileId]); gli altri stanno sotto «Altro». */
    val controlTiles: List<ControlTileId> = ControlTileId.DEFAULT,
    /** App nascoste: chiave → nome al momento in cui è stata nascosta (per mostrarle nelle Impostazioni). */
    val hidden: Map<String, String> = emptyMap(),
    val renamed: Map<String, String> = emptyMap(),
) {
    /** Le app come le vede la home: senza le nascoste, con i nomi scelti, in ordine alfabetico. */
    fun apply(apps: List<AppEntry>?): List<AppEntry>? =
        apps?.let { list ->
            val shown = list.filter { it.key !in hidden }.map { app -> renamed[app.key]?.let { app.copy(label = it) } ?: app }
            AppIndex.sort(shown) { it.label }
        }

    /** Solo l'aspetto (per i temi): senza app nascoste, nomi e disposizione. */
    fun appearance(): HomePrefs =
        copy(hidden = emptyMap(), renamed = emptyMap(), layout = HomeLayoutStyle.IPAD, controlTiles = ControlTileId.DEFAULT)

    /** Applica l'aspetto di [theme] tenendo app nascoste, nomi e disposizione di queste preferenze. */
    fun withLookOf(theme: HomePrefs): HomePrefs =
        theme.copy(hidden = hidden, renamed = renamed, layout = layout, controlTiles = controlTiles)

    /** Firma di ciò che cambia l'aspetto delle icone (per invalidare la cache). */
    val iconSignature: String get() = "$iconShape/$iconStyle/${iconPack.orEmpty()}"

    companion object {
        const val DEFAULT_SOLID = 0xFF1B2028.toInt()
    }
}

/** Salva [HomePrefs] in un file di proprietà, in modo atomico e fuori dal thread principale. */
class HomePrefsStore(private val file: File) {
    private val _prefs = MutableStateFlow(runCatching { PrefsCodec.read(file.readText()) }.getOrDefault(HomePrefs()))
    val prefs: StateFlow<HomePrefs> = _prefs.asStateFlow()
    private val mutex = Mutex()

    suspend fun update(change: (HomePrefs) -> HomePrefs) =
        mutex.withLock {
            val next = change(_prefs.value)
            if (next == _prefs.value) return@withLock
            withContext(Dispatchers.IO) { write(next) }
            _prefs.value = next
        }

    /** Tema da condividere: solo l'aspetto, in testo leggibile. */
    fun exportTheme(): String = PrefsCodec.write(_prefs.value.appearance())

    /** @return false se il file non è un tema di NOVA. */
    suspend fun importTheme(text: String): Boolean {
        if (!PrefsCodec.isTheme(text)) return false
        val theme = PrefsCodec.read(text)
        update { it.withLookOf(theme) }
        return true
    }

    private fun write(prefs: HomePrefs) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(PrefsCodec.write(prefs))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}

/** Formato su file (proprietà Java): valori mancanti o non validi tornano al valore predefinito. */
internal object PrefsCodec {
    private const val MARK = "nova-home"
    private const val HIDDEN = "hidden."
    private const val RENAMED = "renamed."

    fun isTheme(text: String): Boolean = runCatching { props(text).getProperty(MARK) == "1" }.getOrDefault(false)

    @Suppress("CyclomaticComplexMethod") // una riga per campo: lunga ma lineare
    fun read(text: String): HomePrefs {
        val p = props(text)
        val d = HomePrefs()
        // File scritto prima dello stile iPad: l'unica forma allora predefinita era "Originale";
        // la portiamo alle icone morbide, come il resto della nuova interfaccia (si rimette dalle impostazioni).
        val beforeIpad = p.getProperty("layout") == null

        fun bool(
            key: String,
            def: Boolean,
        ) = p.getProperty(key)?.toBooleanStrictOrNull() ?: def

        fun int(
            key: String,
            def: Int,
        ) = p.getProperty(key)?.toLongOrNull()?.toInt() ?: def
        return HomePrefs(
            theme = enumOr(p.getProperty("theme"), d.theme),
            accent = p.getProperty("accent")?.toLongOrNull()?.toInt(),
            wallpaper = enumOr(p.getProperty("wallpaper"), d.wallpaper),
            novaWallpaper = int("novaWallpaper", d.novaWallpaper).coerceIn(0, NovaWallpapers.all.lastIndex),
            solidColor = int("solidColor", d.solidColor),
            // "blur=false" delle versioni precedenti vale come "nessuna sfocatura".
            blurLevel = enumOr(p.getProperty("blurLevel"), if (p.getProperty("blur") == "false") BlurLevel.NONE else d.blurLevel),
            scrim = enumOr(p.getProperty("scrim"), d.scrim),
            textSize = enumOr(p.getProperty("text"), d.textSize),
            font = enumOr(p.getProperty("font"), d.font),
            weight = enumOr(p.getProperty("weight"), d.weight),
            showClock = bool("clock", d.showClock),
            clockStyle = enumOr(p.getProperty("clockStyle"), d.clockStyle),
            showDate = bool("date", d.showDate),
            showIcons = bool("icons", d.showIcons),
            iconSize = enumOr(p.getProperty("iconSize"), d.iconSize),
            iconShape =
                enumOr(
                    p.getProperty("iconShape"),
                    d.iconShape,
                ).let { if (beforeIpad && it == IconShape.ORIGINAL) IconShape.SQUIRCLE else it },
            iconStyle = enumOr(p.getProperty("iconStyle"), d.iconStyle),
            iconPack = p.getProperty("iconPack")?.takeIf { it.isNotBlank() },
            layout = enumOr(p.getProperty("layout"), d.layout),
            alphabetSide = enumOr(p.getProperty("side"), d.alphabetSide),
            showStatusBar = bool("statusBar", d.showStatusBar),
            swipeDownNotifications = bool("swipeDown", d.swipeDownNotifications),
            doubleTapSleep = bool("doubleTapSleep", d.doubleTapSleep),
            controlTiles = tiles(p.getProperty("controlTiles"), d.controlTiles),
            hidden = p.stringPropertyNames().filter { it.startsWith(HIDDEN) }.associate { it.removePrefix(HIDDEN) to p.getProperty(it) },
            renamed =
                p
                    .stringPropertyNames()
                    .filter { it.startsWith(RENAMED) }
                    .associate { it.removePrefix(RENAMED) to p.getProperty(it) }
                    .filterValues { it.isNotBlank() },
        )
    }

    fun write(prefs: HomePrefs): String {
        val p = Properties()
        p[MARK] = "1"
        p["theme"] = prefs.theme.name
        prefs.accent?.let { p["accent"] = it.toString() }
        p["wallpaper"] = prefs.wallpaper.name
        p["novaWallpaper"] = prefs.novaWallpaper.toString()
        p["solidColor"] = prefs.solidColor.toString()
        p["blurLevel"] = prefs.blurLevel.name
        p["scrim"] = prefs.scrim.name
        p["text"] = prefs.textSize.name
        p["font"] = prefs.font.name
        p["weight"] = prefs.weight.name
        p["clock"] = prefs.showClock.toString()
        p["clockStyle"] = prefs.clockStyle.name
        p["date"] = prefs.showDate.toString()
        p["icons"] = prefs.showIcons.toString()
        p["iconSize"] = prefs.iconSize.name
        p["iconShape"] = prefs.iconShape.name
        p["iconStyle"] = prefs.iconStyle.name
        prefs.iconPack?.let { p["iconPack"] = it }
        p["layout"] = prefs.layout.name
        p["side"] = prefs.alphabetSide.name
        p["statusBar"] = prefs.showStatusBar.toString()
        p["swipeDown"] = prefs.swipeDownNotifications.toString()
        p["doubleTapSleep"] = prefs.doubleTapSleep.toString()
        p["controlTiles"] = prefs.controlTiles.joinToString(",") { it.name }
        prefs.hidden.forEach { (key, label) -> p["$HIDDEN$key"] = label }
        prefs.renamed.forEach { (key, name) -> p["$RENAMED$key"] = name }
        return StringWriter().also { p.store(it, "NOVA") }.toString()
    }

    /** Chiave assente = predefiniti; presente ma vuota = nessun riquadro a prima vista; nomi sconosciuti o doppi si ignorano. */
    private fun tiles(
        value: String?,
        default: List<ControlTileId>,
    ): List<ControlTileId> {
        value ?: return default
        val chosen = value.split(',').mapNotNull { name -> ControlTileId.entries.firstOrNull { it.name == name.trim() } }.toSet()
        return ControlTileId.entries.filter { it in chosen }
    }

    private fun props(text: String) = Properties().apply { load(StringReader(text)) }

    private inline fun <reified E : Enum<E>> enumOr(
        value: String?,
        default: E,
    ): E = enumValues<E>().firstOrNull { it.name == value } ?: default
}
