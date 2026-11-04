package it.manlio.nova.core.modes

import java.io.File
import java.time.LocalDate
import java.util.Properties

/** I minuti passati in ogni modalità, giorno per giorno (SPEC §12.7): solo statistiche, restano sul tablet. */
class ModeMinutes(private val file: File) {
    private val values = Properties().apply { runCatching { file.inputStream().use(::load) } }

    fun on(
        mode: String,
        day: LocalDate,
    ): Int = synchronized(values) { values.getProperty("$mode.$day")?.toIntOrNull() ?: 0 }

    /** I minuti degli ultimi 7 giorni, [today] compreso. */
    fun week(
        mode: String,
        today: LocalDate,
    ): Int = (0L until WEEK_DAYS).sumOf { on(mode, today.minusDays(it)) }

    fun add(
        mode: String,
        day: LocalDate,
        minutes: Int,
    ) {
        if (minutes <= 0) return
        synchronized(values) {
            values.setProperty("$mode.$day", (on(mode, day) + minutes).toString())
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.outputStream().use { values.store(it, "NOVA minuti per modalita") }
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    private companion object {
        const val WEEK_DAYS = 7L
    }
}
