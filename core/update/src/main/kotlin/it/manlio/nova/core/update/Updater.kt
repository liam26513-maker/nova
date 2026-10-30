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
            val plan =
                runCatching { UpdatePlanner.plan(client.releases(), versionCode, config.currentVersion()) }
                    .getOrElse { return@withLock fail("Controllo non riuscito", it) }
            plan.config?.let { applyConfig(it) }
            val app = plan.app
            when {
                app == null -> if (_state.value !is UpdateState.ConfigApplied) publish(UpdateState.UpToDate(clock()))
                automatic && shizuku.status.value == ShizukuStatus.READY -> installLocked(app)
                else -> publish(UpdateState.AppAvailable(app))
            }
        }

    /** Installa l'aggiornamento disponibile (tasto "Installa"). */
    suspend fun install(update: AppUpdate) = mutex.withLock { installLocked(update) }

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
            history.addRestorePoint(RestorePoint(clock(), "configurazione ${update.version}", versionCode, versionName, config.currentVersion()))
            config.apply(json)?.let { error(it) }
            history.saveBundle(update.version, json)
            log.i(TAG, "configurazione ${update.version} applicata")
            publish(UpdateState.ConfigApplied(update.version))
        }.onFailure { fail("Configurazione ${update.version} rifiutata", it) }
    }

    private suspend fun installLocked(update: AppUpdate) {
        runCatching {
            val dir = File(context.externalCacheDir ?: error("Memoria esterna non disponibile"), "updates")
            dir.listFiles()?.forEach(File::delete) // niente avanzi di download vecchi
            val apk = File(dir, update.apk.name)
            val expected = Checksums.parse(client.downloadText(update.checksum)) ?: error("Impronta mancante")
            publish(UpdateState.Downloading(0f))
            client.download(update.apk, apk) { publish(UpdateState.Downloading(it)) }
            kotlin.check(Checksums.sha256(apk) == expected) { "L'impronta del file scaricato non corrisponde" }
            (installer.check(apk, update.versionCode) as? ApkCheck.Rejected)?.let { error(it.reason) }
            history.addRestorePoint(RestorePoint(clock(), "app ${update.tag}", versionCode, versionName, config.currentVersion()))
            publish(UpdateState.Installing)
            log.i(TAG, "installo ${update.tag} (${update.versionCode})")
            val silent = shizuku.status.value == ShizukuStatus.READY
            installer.install(apk)?.let { error(it) }
            if (!silent) publish(UpdateState.AwaitingConfirmation)
            // Con Shizuku il processo viene sostituito subito dalla nuova versione.
        }.onFailure { fail("Aggiornamento non riuscito", it) }
    }

    private fun fail(
        what: String,
        error: Throwable,
    ) {
        val reason =
            when {
                error is GitHubClient.HttpException && error.code == UNAUTHORIZED -> "Token GitHub non valido o scaduto"
                error is GitHubClient.HttpException && error.code == NOT_FOUND -> "Repository non trovato: controlla i permessi del token"
                error is java.io.IOException -> "Nessuna connessione o GitHub non raggiungibile"
                else -> error.message ?: error::class.simpleName.orEmpty()
            }
        log.w(TAG, "$what: $reason")
        publish(UpdateState.Failed("$what: $reason"))
    }

    private fun publish(state: UpdateState) {
        _state.value = state
    }

    private companion object {
        const val TAG = "update"
        const val UNAUTHORIZED = 401
        const val NOT_FOUND = 404
    }
}
