package it.manlio.nova.core.system

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Esito di un comando: codice d'uscita e output. */
data class ShellResult(val exitCode: Int, val output: String) {
    val ok get() = exitCode == 0
}

/** Esegue un comando consentito (in produzione: via Shizuku; nei test: un finto). */
fun interface CommandRunner {
    suspend fun run(command: SystemCommand): ShellResult
}

/** Una modifica di sistema fatta da NOVA, con ciò che serve per annullarla (SPEC §7.1). */
@Serializable
sealed interface SystemChange {
    val time: Long

    @Serializable
    data class Setting(
        val namespace: SystemCommand.Namespace,
        val key: String,
        /** null = l'impostazione prima non esisteva. */
        val previous: String?,
        val applied: String,
        override val time: Long,
    ) : SystemChange

    @Serializable
    data class PackageDisabled(val packageName: String, override val time: Long) : SystemChange
}

/**
 * Applica modifiche reversibili e ne tiene il registro in un file. [revertAll] le annulla tutte,
 * dalla più recente alla più vecchia; un'impostazione modificata più volte torna al valore originale.
 */
class SystemChangeLog(
    private val file: File,
    private val runner: CommandRunner,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val changes = mutableListOf<SystemChange>()

    init {
        if (file.exists()) {
            runCatching { changes += JSON.decodeFromString<List<SystemChange>>(file.readText()) }
        }
    }

    fun entries(): List<SystemChange> = synchronized(changes) { changes.toList() }

    /** Imposta un valore registrando quello precedente. */
    suspend fun putSetting(
        namespace: SystemCommand.Namespace,
        key: String,
        value: String,
    ): ShellResult {
        val read = runner.run(SystemCommand.GetSetting(namespace, key))
        if (!read.ok) return read
        val previous = read.output.trim().takeUnless { it.isEmpty() || it == "null" }
        val result = runner.run(SystemCommand.PutSetting(namespace, key, value))
        if (result.ok) record(SystemChange.Setting(namespace, key, previous, value, clock()))
        return result
    }

    suspend fun disablePackage(packageName: String): ShellResult {
        val result = runner.run(SystemCommand.DisablePackage(packageName))
        if (result.ok) record(SystemChange.PackageDisabled(packageName, clock()))
        return result
    }

    /** Annulla tutto. Le voci che non si riesce ad annullare restano nel registro. @return quante ne restano. */
    suspend fun revertAll(): Int {
        val failed = mutableListOf<SystemChange>()
        for (change in entries().asReversed()) {
            // Un valore precedente non rappresentabile come comando consentito resta nel registro.
            val result =
                runCatching {
                    when (change) {
                        is SystemChange.Setting -> runner.run(SystemCommand.PutSetting(change.namespace, change.key, change.previous))
                        is SystemChange.PackageDisabled -> runner.run(SystemCommand.EnablePackage(change.packageName))
                    }
                }.getOrElse { ShellResult(-1, it.message.orEmpty()) }
            if (!result.ok) failed += change
        }
        synchronized(changes) {
            changes.clear()
            changes += failed.asReversed()
        }
        save()
        return failed.size
    }

    private fun record(change: SystemChange) {
        synchronized(changes) { changes += change }
        save()
    }

    private fun save() {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(JSON.encodeToString(entries()))
        tmp.renameTo(file)
    }

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }

        /** File del registro, condiviso da app e modalità provvisoria. */
        fun fileIn(filesDir: File) = File(filesDir, "system-changes.json")
    }
}
