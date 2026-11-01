package it.manlio.nova.core.system

import android.os.ParcelFileDescriptor
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Gira in un processo separato con i privilegi della shell, avviato da Shizuku (UserService).
 * Riceve solo liste di argomenti già validate da [SystemCommand]: niente shell, niente interpretazione.
 */
class ShellService : IShellService.Stub() {
    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(argv: Array<String>): String {
        val process = ProcessBuilder(argv.toList()).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        return "$code\n$output"
    }

    override fun execWithInput(
        argv: Array<String>,
        input: ParcelFileDescriptor,
    ): String {
        val process = ProcessBuilder(argv.toList()).redirectErrorStream(true).start()
        // Si scrive in un altro thread: il comando potrebbe rispondere prima di aver letto tutto.
        val writer =
            thread {
                runCatching {
                    ParcelFileDescriptor.AutoCloseInputStream(input).use { source -> process.outputStream.use { source.copyTo(it) } }
                }
            }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        writer.join()
        return "$code\n$output"
    }
}
