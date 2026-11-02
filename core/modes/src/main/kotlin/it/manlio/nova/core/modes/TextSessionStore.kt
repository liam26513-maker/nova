package it.manlio.nova.core.modes

import java.io.File

/**
 * Salva la sessione in un file di testo semplice (una riga per voce): niente librerie, si legge a occhio.
 * La scrittura passa da un file temporaneo, così un blocco a metà non lascia un file rotto.
 */
class TextSessionStore(private val file: File) : SessionStore {
    override fun load(): Session? =
        runCatching {
            if (!file.exists()) return null
            val lines = file.readLines()
            if (lines.firstOrNull() != HEADER || lines.size < FIXED_LINES) return null
            val tokens =
                lines.drop(FIXED_LINES).filter { it.isNotEmpty() }.map {
                    val kind = it.substringBefore('\t')
                    UndoToken(unescape(kind), unescape(it.substringAfter('\t', "")))
                }
            Session(unescape(lines[1]), lines[2].toLong(), tokens, lines[3].toBoolean())
        }.getOrNull()

    override fun save(session: Session?) {
        if (session == null) {
            file.delete()
            return
        }
        file.parentFile?.mkdirs()
        val body =
            buildList {
                add(HEADER)
                add(escape(session.modeId))
                add(session.startedAt.toString())
                add(session.leftover.toString())
                session.tokens.forEach { add(escape(it.kind) + "\t" + escape(it.value)) }
            }.joinToString("\n", postfix = "\n")
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(body)
        tmp.renameTo(file)
    }

    private fun escape(text: String) = text.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    private fun unescape(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                out.append(
                    when (text[i + 1]) {
                        't' -> '\t'
                        'n' -> '\n'
                        else -> text[i + 1]
                    },
                )
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    private companion object {
        const val HEADER = "nova-modo-1"
        const val FIXED_LINES = 4
    }
}
