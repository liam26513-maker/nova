package it.manlio.nova.core.update

import android.content.Context
import it.manlio.nova.core.system.ShizukuBridge
import it.manlio.nova.core.system.ShizukuStatus
import it.manlio.nova.core.telemetry.LocalLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** Ciò che l'utente vede nella sezione "Aggiornamenti". */
sealed interface UpdateState {
    data object Idle : UpdateState

    data object NoToken : UpdateState

    data object Checking : UpdateState

    data class UpToDate(val checkedAt: Long) : UpdateState

    data class AppAvailable(val update: AppUpdate) : UpdateState

    data class Downloading(val progress: Float) : UpdateState

    data object Installing : UpdateState

    /** Installazione avviata senza Shizuku: Android mostra la sua conferma. */
    data object AwaitingConfirmation : UpdateState

    data class ConfigApplied(val version: String) : UpdateState

    data class Failed(val reason: String) : UpdateState
}

/** Esito di una richiesta di ritorno a una versione precedente. */
sealed interface RollbackResult {
    /** APK di ripristino già pronto: download e installazione in corso (vedi [Updater.state]). */
    data object Installing : RollbackResult

    /** GitHub sta preparando la versione: arriverà da sola come aggiornamento. */
    data object Requested : RollbackResult

    data class Failed(val reason: String) : RollbackResult
}

/** Come l'aggiornatore applica una configurazione senza dipendere dal motore dell'app. */
interface ConfigTarget {
    /** Versione della configurazione attiva (meta.version). */
    fun currentVersion(): String

    /** Applica un pacchetto; null se applicato, altrimenti il motivo del rifiuto. */
    fun apply(json: String): String?

    /** Torna ai soli default compilati. */
    fun reset()
}

/**
 * Aggiornatore (SPEC §4.6): controlla le release, applica a caldo la configurazione e scarica,
 * verifica e installa l'app. Prima di ogni cambio salva un punto di ripristino.
 */
@Suppress("LongParameterList", "TooManyFunctions") // punto di assemblaggio: ogni dipendenza è esplicita e sostituibile nei test
class Updater(
    private val context: Context,
    private val versionCode: Int,
    private val versionName: String,
    private val client: GitHubClient,
    private val tokens: TokenStore,
    private val installer: ApkInstaller,
    private val shizuku: ShizukuBridge,
    private val config: ConfigTarget,
    private val history: LocalHistory,
    private val log: LocalLog,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow<UpdateState>(if (tokens.isSet()) UpdateState.Idle else UpdateState.NoToken)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private val mutex = Mutex()

    /**
     * Controlla le release. La configurazione si applica sempre da sola; l'app si installa da sola
     * solo se [automatic] e Shizuku è pronto (installazione silenziosa), altrimenti resta "disponibile".
     */
    suspend fun check(automatic: Boolean) =
        mutex.withLock {
            if (!tokens.isSet()) return@withLock publish(UpdateState.NoToken)
            publish(UpdateState.Checking)
            val releases = runCatching { client.releases() }.getOrElse { return@withLock fail("Controllo non riuscito", it) }
            val plan =
                runCatching { UpdatePlanner.plan(releases, versionCode, config.currentVersion()) }
                    .getOrElse { return@withLock fail("Controllo non riuscito", it) }
            plan.config?.let { applyConfig(it) }
            val app = plan.app
            when {
                app == null -> if (_state.value !is UpdateState.ConfigApplied) publish(UpdateState.UpToDate(clock()))
                automatic && shizuku.status.value == ShizukuStatus.READY -> installLocked(app, "app ${app.tag}")
                else -> publish(UpdateState.AppAvailable(app))
            }
            if (app == null) updateKeyboard(releases)
        }

    /** Installa l'aggiornamento disponibile (tasto "Installa"). */
    suspend fun install(update: AppUpdate) = mutex.withLock { installLocked(update, "app ${update.tag}") }

    /** Torna istantaneamente alla configurazione precedente (o ai default). */
    fun rollbackConfig(): String? {
        val previous = history.previousBundle()
        if (previous == null) {
            history.setActive(null)
            config.reset()
            log.i(TAG, "configurazione riportata ai valori di fabbrica")
            return null
        }
        val (version, json) = previous
        config.apply(json)?.let { return it }
        history.setActive(version)
        log.i(TAG, "configurazione riportata alla $version")
        return null
    }

    /** Versioni dell'app più vecchie di quella installata (per il ritorno indietro d'emergenza). */
    suspend fun previousVersions(): Result<List<PreviousVersion>> =
        runCatching { UpdatePlanner.previousVersions(client.releases(), versionCode) }

    /**
     * Ritorno d'emergenza a una versione precedente (SPEC §4.6). Android non installa versioni più
     * vecchie sopra quelle nuove, quindi serve lo stesso codice con un numero di versione più alto:
     * - se la release installata ha già pronto l'APK di ripristino ([PreviousVersion.standby]),
     *   si scarica e si installa subito (pochi secondi);
     * - altrimenti GitHub lo ricompila (`rollback.yml`) e [UpdateWorker.watchRollback] lo installa appena c'è.
     */
    suspend fun requestRollback(target: PreviousVersion): RollbackResult {
        val tag = target.update.tag
        if (!TAG_FORMAT.matches(tag) || target.update.versionCode >= versionCode) return RollbackResult.Failed("Versione non valida: $tag")
        target.standby?.let { standby ->
            log.i(TAG, "ritorno immediato a $tag (${standby.versionCode})")
            mutex.withLock { installLocked(standby, "ritorno a $tag") }
            return (_state.value as? UpdateState.Failed)?.let { RollbackResult.Failed(it.reason) } ?: RollbackResult.Installing
        }
        return runCatching {
            history.addRestorePoint(restorePoint("richiesta di ritorno a $tag"))
            client.dispatchWorkflow(ROLLBACK_WORKFLOW, "main", mapOf("tag" to tag))
            log.i(TAG, "chiesto il ritorno a $tag")
        }.fold(
            onSuccess = { RollbackResult.Requested },
            onFailure = {
                val reason =
                    if (it is GitHubClient.HttpException && it.code in FORBIDDEN_CODES) {
                        "Il token non ha il permesso \"Actions: Read and write\": rigeneralo con quel permesso"
                    } else {
                        reasonFor(it)
                    }
                log.w(TAG, "ritorno a $tag non avviato: $reason")
                RollbackResult.Failed(reason)
            },
        )
    }

    /** C'è un token salvato (serve anche per caricare i report di Ripara). */
    fun hasToken(): Boolean = tokens.isSet()

    fun setToken(token: String): String? =
        runCatching { tokens.set(token) }
            .fold(
                onSuccess = {
                    publish(UpdateState.Idle)
                    null
                },
                onFailure = { it.message },
            )

    private suspend fun applyConfig(update: ConfigUpdate) {
        runCatching {
            val expected = Checksums.parse(client.downloadText(update.checksum)) ?: error("Impronta mancante")
            val json = client.downloadText(update.bundle)
            kotlin.check(Checksums.sha256(json.toByteArray()) == expected) { "L'impronta della configurazione non corrisponde" }
            history.addRestorePoint(restorePoint("configurazione ${update.version}"))
            config.apply(json)?.let { error(it) }
            history.saveBundle(update.version, json)
            log.i(TAG, "configurazione ${update.version} applicata")
            publish(UpdateState.ConfigApplied(update.version))
        }.onFailure { fail("Configurazione ${update.version} rifiutata", it) }
    }

    private suspend fun installLocked(
        update: AppUpdate,
        reason: String,
    ) {
        runCatching {
            val apk = fetch(update, context.packageName) { publish(UpdateState.Downloading(it)) }
            history.addRestorePoint(restorePoint(reason))
            publish(UpdateState.Installing)
            log.i(TAG, "installo ${update.tag} (${update.versionCode})")
            val silent = shizuku.status.value == ShizukuStatus.READY
            installer.install(apk)?.let { error(it) }
            if (!silent) publish(UpdateState.AwaitingConfirmation)
            // Con Shizuku il processo viene sostituito subito dalla nuova versione.
        }.onFailure { fail("Aggiornamento non riuscito", it) }
    }

    /** Scarica e verifica un APK (impronta, pacchetto, versione, firma): se qualcosa non torna lancia un errore. */
    private suspend fun fetch(
        update: AppUpdate,
        packageName: String,
        progress: (Float) -> Unit,
    ): File {
        val dir = File(context.externalCacheDir ?: error("Memoria esterna non disponibile"), "updates")
        dir.listFiles()?.forEach(File::delete) // niente avanzi di download vecchi
        val apk = File(dir, update.apk.name)
        val expected = Checksums.parse(client.downloadText(update.checksum)) ?: error("Impronta mancante")
        progress(0f)
        client.download(update.apk, apk, progress)
        kotlin.check(Checksums.sha256(apk) == expected) { "L'impronta del file scaricato non corrisponde" }
        (installer.check(apk, update.versionCode, packageName) as? ApkCheck.Rejected)?.let { error(it.reason) }
        return apk
    }

    /**
     * Aggiorna la tastiera, se è installata e Shizuku è pronto. Non cambia lo stato mostrato e un errore finisce solo nel registro:
     * un problema della tastiera non deve mai disturbare l'aggiornamento di NOVA.
     */
    private suspend fun updateKeyboard(releases: List<Release>) {
        if (shizuku.status.value != ShizukuStatus.READY) return
        val installed = installer.installedVersion(UpdatePlanner.KEYBOARD_PACKAGE) ?: return
        val update = UpdatePlanner.keyboard(releases, installed) ?: return
        runCatching {
            val result = shizuku.installApk(fetch(update, UpdatePlanner.KEYBOARD_PACKAGE) {})
            kotlin.check(result.ok) { result.output }
            log.i(TAG, "tastiera aggiornata a ${update.tag}")
        }.onFailure { log.e(TAG, "tastiera non aggiornata", it) }
    }

    private fun restorePoint(reason: String) = RestorePoint(clock(), reason, versionCode, versionName, config.currentVersion())

    private fun fail(
        what: String,
        error: Throwable,
    ) {
        val reason = reasonFor(error)
        log.w(TAG, "$what: $reason")
        publish(UpdateState.Failed("$what: $reason"))
    }

    /** Motivo comprensibile di un errore di rete o di GitHub. */
    fun reasonFor(error: Throwable): String = failureReason(error)

    private fun publish(state: UpdateState) {
        _state.value = state
    }

    private companion object {
        const val TAG = "update"
        val FORBIDDEN_CODES = setOf(403, 404)
        const val ROLLBACK_WORKFLOW = "rollback.yml"
        val TAG_FORMAT = Regex("^v\\d+\\.\\d+\\.\\d+$")
    }
}

private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_NOT_FOUND = 404

/** Motivo comprensibile di un errore di rete o di GitHub. */
internal fun failureReason(error: Throwable): String =
    when {
        error is GitHubClient.HttpException && error.code == HTTP_UNAUTHORIZED -> "Token GitHub non valido o scaduto"
        error is GitHubClient.HttpException && error.code == HTTP_NOT_FOUND -> "Repository non trovato: controlla i permessi del token"
        error is java.net.UnknownHostException -> "Il tablet non trova i siti. Se è acceso, spegni «Blocca pubblicità» in Prestazioni"
        error is java.io.IOException -> "Nessuna connessione o GitHub non raggiungibile"
        else -> error.message ?: error::class.simpleName.orEmpty()
    }
