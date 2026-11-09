package it.manlio.nova.core.system

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.io.File

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

    /**
     * Installa [apk] (che deve stare nella cartella `updates` della cache esterna di NOVA, dove l'aggiornatore lo scarica
     * e lo verifica) passandolo alla shell in streaming: la shell non può leggere le cartelle private dell'app.
     */
    @Suppress("ReturnCount") // una uscita per ogni motivo per cui non si può installare
    suspend fun installApk(apk: File): ShellResult {
        if (_status.value != ShizukuStatus.READY) return ShellResult(NOT_READY, "Shizuku non è pronto")
        val updates = context.externalCacheDir?.let { File(it, "updates") }?.canonicalFile
        if (updates == null || apk.canonicalFile.parentFile != updates || !apk.name.endsWith(".apk")) {
            return ShellResult(-1, "File non consentito per l'installazione")
        }
        val command =
            runCatching {
                SystemCommand.InstallApk(apk.length(), context.packageName)
            }.getOrElse { return ShellResult(-1, it.message.orEmpty()) }
        val shell = connect() ?: return ShellResult(NOT_READY, "Servizio di sistema non disponibile")
        return withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    val raw = shell.execWithInput(command.argv.toTypedArray(), pfd)
                    ShellResult(raw.substringBefore('\n').toIntOrNull() ?: -1, raw.substringAfter('\n', ""))
                }
            }.getOrElse { ShellResult(-1, it.message.orEmpty()) }
        }
    }

    /** Le app recenti di Android, la più recente per prima; vuota se Shizuku non c'è. */
    suspend fun recentTasks(max: Int): List<RecentTask> = RecentTasksCodec.decode(shellCall("") { it.recentTasks(max) })

    /** L'anteprima JPEG di un task (vuota se manca). */
    suspend fun taskSnapshot(
        taskId: Int,
        maxSide: Int,
    ): ByteArray = shellCall(ByteArray(0)) { it.taskSnapshot(taskId, maxSide) }

    suspend fun openTask(taskId: Int): Boolean = shellCall(false) { it.openTask(taskId) }

    suspend fun removeTask(taskId: Int): Boolean = shellCall(false) { it.removeTask(taskId) }

    private suspend fun <T> shellCall(
        fallback: T,
        call: (IShellService) -> T,
    ): T {
        if (_status.value != ShizukuStatus.READY) return fallback
        val shell = connect() ?: return fallback
        return withContext(Dispatchers.IO) { runCatching { call(shell) }.getOrDefault(fallback) }
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
        const val SERVICE_VERSION = 3 // 3: le recenti; un servizio vecchio non ha i metodi nuovi
        const val CONNECT_TIMEOUT_MS = 5_000L
        const val NOT_READY = -100
    }
}
