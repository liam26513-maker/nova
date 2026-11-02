package it.manlio.nova.core.system

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
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

    /** Interruttore del pannello rapido cambiato. [previous] è lo stato originale, prima della prima modifica di NOVA. */
    @Serializable
    data class Toggle(
        val toggle: QuickToggle,
        val previous: Boolean,
        val applied: Boolean,
        override val time: Long,
    ) : SystemChange

    /**
     * Densità dello schermo cambiata. [previous] null = quella di fabbrica. Finché [confirmed] è falso la modifica
     * si annulla da sola (`SystemTools`): se l'utente non vede più lo schermo, non resta così.
     */
    @Serializable
    data class Density(
        val previous: Int?,
        val applied: Int,
        val confirmed: Boolean = false,
        override val time: Long,
    ) : SystemChange
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

    /** Cambia la densità registrando quella precedente; resta «non confermata» finché non si chiama [confirmDensity]. */
    suspend fun setDensity(
        previous: Int?,
        dpi: Int,
    ): ShellResult {
        val result = runner.run(SystemCommand.SetDensity(dpi))
        if (result.ok) record(SystemChange.Density(previous, dpi, confirmed = false, time = clock()))
        return result
    }

    fun confirmDensity() {
        synchronized(changes) {
            changes.replaceAll { if (it is SystemChange.Density && !it.confirmed) it.copy(confirmed = true) else it }
        }
        save()
    }

    /** Annulla le densità non confermate (anche dopo un riavvio dell'app). @return true se tutto è tornato com'era. */
    suspend fun revertUnconfirmedDensity(): Boolean {
        val pending = entries().filterIsInstance<SystemChange.Density>().filter { !it.confirmed }
        if (pending.isEmpty()) return true
        val result = runner.run(SystemCommand.SetDensity(pending.first().previous))
        if (result.ok) {
            synchronized(changes) { changes.removeAll(pending.toSet()) }
            save()
        }
        return result.ok
    }

    /** Riattiva un pacchetto congelato e lo toglie dal registro. */
    suspend fun enablePackage(packageName: String): ShellResult {
        val result = runner.run(SystemCommand.EnablePackage(packageName))
        if (result.ok) {
            synchronized(changes) { changes.removeAll { it is SystemChange.PackageDisabled && it.packageName == packageName } }
            save()
        }
        return result
    }

    /** Riporta come prima le impostazioni [keys] cambiate da NOVA (la più vecchia delle sue modifiche ha il valore originale). */
    suspend fun revertSettings(
        namespace: SystemCommand.Namespace,
        keys: Set<String>,
    ): Boolean {
        val mine = entries().filterIsInstance<SystemChange.Setting>().filter { it.namespace == namespace && it.key in keys }
        var allOk = true
        for (key in mine.map { it.key }.distinct()) {
            val original = mine.first { it.key == key }.previous
            val result = runner.run(SystemCommand.PutSetting(namespace, key, original))
            if (result.ok) {
                synchronized(changes) { changes.removeAll { it is SystemChange.Setting && it.namespace == namespace && it.key == key } }
            } else {
                allOk = false
            }
        }
        save()
        return allOk
    }

    /**
     * Cambia un interruttore registrando com'era ([wasOn]). Se lo si cambia più volte il registro tiene una sola voce,
     * con lo stato originale; se si torna allo stato originale la voce sparisce (non c'è più niente da annullare).
     */
    suspend fun setToggle(
        toggle: QuickToggle,
        on: Boolean,
        wasOn: Boolean,
    ): ShellResult {
        val result = runner.run(SystemCommand.SetToggle(toggle, on))
        if (!result.ok) return result
        val original =
            synchronized(changes) {
                val first = changes.filterIsInstance<SystemChange.Toggle>().firstOrNull { it.toggle == toggle }
                changes.removeAll { it is SystemChange.Toggle && it.toggle == toggle }
                first?.previous ?: wasOn
            }
        if (original != on) record(SystemChange.Toggle(toggle, original, on, clock())) else save()
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
                        is SystemChange.Density -> runner.run(SystemCommand.SetDensity(change.previous))
                        is SystemChange.Toggle -> runner.run(SystemCommand.SetToggle(change.toggle, change.previous))
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
