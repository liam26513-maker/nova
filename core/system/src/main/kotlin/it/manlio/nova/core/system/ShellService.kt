package it.manlio.nova.core.system

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
        val process = ProcessBuilder(*argv).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val code = process.waitFor()
        return "$code\n$output"
    }
}
