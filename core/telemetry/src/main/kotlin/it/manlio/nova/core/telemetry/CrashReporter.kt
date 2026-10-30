package it.manlio.nova.core.telemetry

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Gestore globale dei crash (SPEC §14.1): salva un report leggibile in [dir], avvisa il
 * [BootGuard] e poi passa l'errore al gestore precedente (che chiude il processo).
 * I report restano sul tablet; escono solo con "Ripara".
 */
class CrashReporter(
    private val dir: File,
    private val log: LocalLog,
    private val guard: BootGuard,
    private val versionName: String,
    private val clock: () -> Long = System::currentTimeMillis,
) : Thread.UncaughtExceptionHandler {
    private var previous: Thread.UncaughtExceptionHandler? = null

    fun install() {
        previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(
        thread: Thread,
        error: Throwable,
    ) {
        // Ogni passo è protetto: un errore qui non deve impedire la chiusura del processo.
        runCatching { write(thread, error) }
        runCatching { guard.onCrash() }
        previous?.uncaughtException(thread, error)
    }

    /** Report non ancora gestiti, dal più recente. */
    fun pending(): List<File> =
        dir.listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(".txt") }
            ?.sortedByDescending { it.name }
            .orEmpty()

    fun clear() = pending().forEach(File::delete)

    internal fun write(
        thread: Thread,
        error: Throwable,
    ): File {
        dir.mkdirs()
        val now = clock()
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val text =
            buildString {
                appendLine("NOVA $versionName — crash")
                appendLine("ora: $now")
                appendLine("thread: ${thread.name}")
                appendLine()
                appendLine(trace)
                appendLine("--- ultimi eventi ---")
                log.snapshot().takeLast(LOG_LINES).forEach { appendLine(it.toString()) }
            }
        val file = File(dir, "$PREFIX$now.txt")
        file.writeText(text)
        pending().drop(KEEP).forEach(File::delete)
        return file
    }

    companion object {
        const val PREFIX = "crash-"
        const val KEEP = 20
        const val LOG_LINES = 80
    }
}
