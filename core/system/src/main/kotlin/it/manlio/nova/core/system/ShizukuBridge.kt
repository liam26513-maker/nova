package it.manlio.nova.core.system

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/** Stato di Shizuku come lo vede l'utente. */
enum class ShizukuStatus {
    /** App Shizuku non installata o servizio non avviato (dopo un riavvio va riattivato). */
    NOT_RUNNING,

    /** Shizuku attivo, ma NOVA non ha ancora il permesso. */
    NEEDS_PERMISSION,

    /** Pronto. */
    READY,
}

/**
 * Ponte verso Shizuku (SPEC §7.1): segue lo stato del servizio, chiede il permesso ed esegue
 * i [SystemCommand] nel [ShellService] con privilegi di shell.
 */
class ShizukuBridge(private val context: Context) : CommandRunner {
    private val _status = MutableStateFlow(readStatus())
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private val service = MutableStateFlow<IShellService?>(null)

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead =
        Shizuku.OnBinderDeadListener {
            service.value = null
            refresh()
        }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    private val args =
        Shizuku
            .UserServiceArgs(ComponentName(context.packageName, ShellService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .version(SERVICE_VERSION)

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                binder: IBinder?,
            ) {
                service.value = binder?.takeIf { it.pingBinder() }?.let(IShellService.Stub::asInterface)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service.value = null
            }
        }

    fun start() {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()
    }

    /** Mostra la richiesta di permesso di Shizuku. */
    fun requestPermission() {
        if (Shizuku.pingBinder() && !Shizuku.isPreV11()) Shizuku.requestPermission(REQUEST_CODE)
    }

    override suspend fun run(command: SystemCommand): ShellResult {
        if (_status.value != ShizukuStatus.READY) return ShellResult(NOT_READY, "Shizuku non è pronto")
        val shell = connect() ?: return ShellResult(NOT_READY, "Servizio di sistema non disponibile")
        return withContext(Dispatchers.IO) {
            runCatching {
                val raw = shell.exec(command.argv.toTypedArray())
                val firstLine = raw.substringBefore('\n')
                ShellResult(firstLine.toIntOrNull() ?: -1, raw.substringAfter('\n', ""))
            }.getOrElse { ShellResult(-1, it.message.orEmpty()) }
        }
    }

    private suspend fun connect(): IShellService? {
        service.value?.let { return it }
        withContext(Dispatchers.Main) { Shizuku.bindUserService(args, connection) }
        return withTimeoutOrNull(CONNECT_TIMEOUT_MS) { service.first { it != null } }
    }

    private fun refresh() {
        _status.value = readStatus()
    }

    private fun readStatus(): ShizukuStatus =
        when {
            !runCatching { Shizuku.pingBinder() }.getOrDefault(false) -> ShizukuStatus.NOT_RUNNING
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShizukuStatus.NEEDS_PERMISSION
            else -> ShizukuStatus.READY
        }

    private companion object {
        const val REQUEST_CODE = 4721
        const val SERVICE_VERSION = 1
        const val CONNECT_TIMEOUT_MS = 5_000L
        const val NOT_READY = -100
    }
}
