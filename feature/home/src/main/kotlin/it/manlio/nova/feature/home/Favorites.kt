package it.manlio.nova.feature.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** App preferite della home, nell'ordine in cui sono state aggiunte. Una chiave per riga, scrittura atomica. */
class Favorites(private val file: File) {
    private val _keys = MutableStateFlow(read())
    val keys: StateFlow<List<String>> = _keys.asStateFlow()
    private val mutex = Mutex()

    /** Aggiunge o toglie [key]. Scrive su disco fuori dal thread principale. */
    suspend fun toggle(key: String) =
        mutex.withLock {
            val current = _keys.value
            val next = if (key in current) current - key else current + key
            withContext(Dispatchers.IO) { write(next) }
            _keys.value = next
        }

    private fun read(): List<String> =
        runCatching { file.readLines().map(String::trim).filter(String::isNotEmpty).distinct() }.getOrDefault(emptyList())

    private fun write(keys: List<String>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(keys.joinToString("\n"))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
