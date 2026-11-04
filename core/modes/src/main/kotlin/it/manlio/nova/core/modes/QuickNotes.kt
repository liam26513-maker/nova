package it.manlio.nova.core.modes

import java.io.File

/** Le note rapide della Studio (SPEC §8.4): un solo testo su file, scritto passando da un file temporaneo per non perderlo a metà. */
class QuickNotes(private val file: File) {
    /** Se il file esiste ma non si legge lancia un errore: chi chiama non deve sovrascrivere note che non ha visto. */
    fun read(): String = if (file.exists()) file.readText() else ""

    @Synchronized
    fun write(text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
