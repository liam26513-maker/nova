package it.manlio.nova.core.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Fotografia salvata prima di ogni aggiornamento (SPEC §14.3). */
@Serializable
data class RestorePoint(
    val time: Long,
    val reason: String,
    val versionCode: Int,
    val versionName: String,
    val configVersion: String,
)

/**
 * Storia locale degli aggiornamenti:
 * - punti di ripristino (ultimi [KEEP]);
 * - pacchetti di configurazione applicati (ultimi [KEEP]), con quello attivo: il ritorno
 *   indietro della configurazione è istantaneo e non richiede rete (SPEC §4.6).
 */
class LocalHistory(private val dir: File) {
    private val pointsFile = File(dir, "restore-points.json")
    private val bundles = File(dir, "config-bundles")
    private val activeFile = File(dir, "config-active")

    fun restorePoints(): List<RestorePoint> =
        runCatching { JSON.decodeFromString<List<RestorePoint>>(pointsFile.readText()) }.getOrDefault(emptyList())

    fun addRestorePoint(point: RestorePoint) {
        val all = (restorePoints() + point).takeLast(KEEP)
        write(pointsFile, JSON.encodeToString(all))
    }

    /** Salva un pacchetto di configurazione appena applicato e lo rende attivo. */
    fun saveBundle(
        version: String,
        json: String,
    ) {
        require(VERSION.matches(version)) { "Versione non valida: $version" }
        bundles.mkdirs()
        write(File(bundles, "$version.json"), json)
        write(activeFile, version)
        bundleVersions().dropLast(KEEP).forEach { File(bundles, "$it.json").delete() }
    }

    /** Versioni salvate, dalla più vecchia alla più nuova. */
    fun bundleVersions(): List<String> =
        bundles
            .listFiles { f -> f.extension == "json" }
            .orEmpty()
            .map { it.nameWithoutExtension }
            .filter { VERSION.matches(it) }
            .sortedWith { a, b -> UpdatePlanner.compareVersions(a, b) }

    fun activeVersion(): String? = activeFile.takeIf { it.exists() }?.readText()?.trim()?.takeIf { VERSION.matches(it) }

    fun activeBundle(): String? = activeVersion()?.let { File(bundles, "$it.json") }?.takeIf { it.exists() }?.readText()

    /** Rende attivo il pacchetto precedente a quello attivo. @return il testo da applicare, o null se non c'è. */
    fun previousBundle(): Pair<String, String>? {
        val versions = bundleVersions()
        val index = versions.indexOf(activeVersion())
        val previous = if (index > 0) versions[index - 1] else return null
        return previous to File(bundles, "$previous.json").readText()
    }

    fun setActive(version: String?) {
        if (version == null) activeFile.delete() else write(activeFile, version)
    }

    private fun write(
        file: File,
        text: String,
    ) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private companion object {
        const val KEEP = 10
        val VERSION = Regex("^\\d+\\.\\d+\\.\\d+$")
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
