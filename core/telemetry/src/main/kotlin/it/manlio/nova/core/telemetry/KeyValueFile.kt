package it.manlio.nova.core.telemetry

import java.io.File
import java.util.Properties

/**
 * Piccolo archivio chiave→valore su file, scritto in modo atomico (file temporaneo + rinomina)
 * e in modo sincrono: deve funzionare anche dentro il gestore dei crash, poco prima che il
 * processo muoia, quindi niente coroutine né DataStore.
 */
class KeyValueFile(private val file: File) {
    private val props = Properties()

    init {
        if (file.exists()) {
            // Un file corrotto vale come vuoto: meglio perdere i contatori che bloccare l'avvio.
            runCatching { file.inputStream().use(props::load) }.onFailure { props.clear() }
        }
    }

    @Synchronized
    fun getLong(
        key: String,
        default: Long = 0,
    ): Long = props.getProperty(key)?.toLongOrNull() ?: default

    @Synchronized
    fun getBoolean(
        key: String,
        default: Boolean = false,
    ): Boolean = props.getProperty(key)?.toBooleanStrictOrNull() ?: default

    @Synchronized
    fun edit(block: Editor.() -> Unit) {
        Editor().block()
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.outputStream().use { out ->
            props.store(out, null)
            out.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    inner class Editor {
        fun put(
            key: String,
            value: Long,
        ) {
            props.setProperty(key, value.toString())
        }

        fun put(
            key: String,
            value: Boolean,
        ) {
            props.setProperty(key, value.toString())
        }
    }
}
