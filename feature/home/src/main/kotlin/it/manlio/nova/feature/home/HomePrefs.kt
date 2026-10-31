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
import java.util.Properties

enum class TextSize { SMALL, MEDIUM, LARGE }

enum class HomeFont { MODERN, ELEGANT, TECHNICAL }

enum class Side { LEFT, RIGHT }

enum class ScrimStrength { LIGHT, MEDIUM, STRONG }

/**
 * Personalizzazione della home, ispirata alle opzioni migliori di Niagara: icone sì/no, misura e
 * carattere del testo, orologio, lato dell'alfabeto, intensità della sfumatura e sfocatura,
 * app nascoste e rinominate. Sono scelte del proprietario sul tablet: restano sul dispositivo.
 */
@Immutable
data class HomePrefs(
    val showIcons: Boolean = true,
    val textSize: TextSize = TextSize.MEDIUM,
    val font: HomeFont = HomeFont.MODERN,
    val showClock: Boolean = true,
    val alphabetSide: Side = Side.RIGHT,
    val scrim: ScrimStrength = ScrimStrength.MEDIUM,
    val blur: Boolean = true,
    /** App nascoste: chiave → nome al momento in cui è stata nascosta (per mostrarle nelle Impostazioni). */
    val hidden: Map<String, String> = emptyMap(),
    val renamed: Map<String, String> = emptyMap(),
) {
    /** Le app come le vede la home: senza le nascoste, con i nomi scelti, in ordine alfabetico. */
    fun apply(apps: List<AppEntry>?): List<AppEntry>? =
        apps?.let { list ->
            AppIndex.sort(
                list.filter { it.key !in hidden }.map {
                        app ->
                    renamed[app.key]?.let { app.copy(label = it) } ?: app
                },
            ) { it.label }
        }
}

/** Salva [HomePrefs] in un file di proprietà, in modo atomico e fuori dal thread principale. */
class HomePrefsStore(private val file: File) {
    private val _prefs = MutableStateFlow(read())
    val prefs: StateFlow<HomePrefs> = _prefs.asStateFlow()
    private val mutex = Mutex()

    suspend fun update(change: (HomePrefs) -> HomePrefs) =
        mutex.withLock {
            val next = change(_prefs.value)
            if (next == _prefs.value) return@withLock
            withContext(Dispatchers.IO) { write(next) }
            _prefs.value = next
        }

    private fun read(): HomePrefs {
        val p = Properties()
        runCatching { file.inputStream().use(p::load) }.onFailure { return HomePrefs() }
        val d = HomePrefs()
        return HomePrefs(
            showIcons = p.getProperty(ICONS)?.toBooleanStrictOrNull() ?: d.showIcons,
            textSize = enumOr(p.getProperty(TEXT), d.textSize),
            font = enumOr(p.getProperty(FONT), d.font),
            showClock = p.getProperty(CLOCK)?.toBooleanStrictOrNull() ?: d.showClock,
            alphabetSide = enumOr(p.getProperty(SIDE), d.alphabetSide),
            scrim = enumOr(p.getProperty(SCRIM), d.scrim),
            blur = p.getProperty(BLUR)?.toBooleanStrictOrNull() ?: d.blur,
            hidden = p.stringPropertyNames().filter { it.startsWith(HIDDEN) }.associate { it.removePrefix(HIDDEN) to p.getProperty(it) },
            renamed =
                p
                    .stringPropertyNames()
                    .filter { it.startsWith(RENAMED) }
                    .associate { it.removePrefix(RENAMED) to p.getProperty(it) }
                    .filterValues { it.isNotBlank() },
        )
    }

    private fun write(prefs: HomePrefs) {
        val p = Properties()
        p[ICONS] = prefs.showIcons.toString()
        p[TEXT] = prefs.textSize.name
        p[FONT] = prefs.font.name
        p[CLOCK] = prefs.showClock.toString()
        p[SIDE] = prefs.alphabetSide.name
        p[SCRIM] = prefs.scrim.name
        p[BLUR] = prefs.blur.toString()
        prefs.hidden.forEach { (key, label) -> p["$HIDDEN$key"] = label }
        prefs.renamed.forEach { (key, name) -> p["$RENAMED$key"] = name }
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.outputStream().use { p.store(it, null) }
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private inline fun <reified E : Enum<E>> enumOr(
        value: String?,
        default: E,
    ): E = enumValues<E>().firstOrNull { it.name == value } ?: default

    private companion object {
        const val ICONS = "icons"
        const val TEXT = "text"
        const val FONT = "font"
        const val CLOCK = "clock"
        const val SIDE = "side"
        const val SCRIM = "scrim"
        const val BLUR = "blur"
        const val HIDDEN = "hidden."
        const val RENAMED = "renamed."
    }
}
