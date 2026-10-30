package it.manlio.nova.core.telemetry

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro in memoria, a dimensione fissa (SPEC §4.3, §14.1). Non esce mai dal tablet:
 * finisce solo nei report di crash e, filtrato, nei report "Ripara".
 */
class LocalLog(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    enum class Level { INFO, WARN, ERROR }

    data class Entry(
        val time: Long,
        val level: Level,
        val tag: String,
        val message: String,
    ) {
        override fun toString(): String {
            val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(Date(time))
            return "$ts ${level.name.first()} $tag: $message"
        }
    }

    private val buffer = ArrayDeque<Entry>(capacity)

    fun i(
        tag: String,
        message: String,
    ) = add(Level.INFO, tag, message)

    fun w(
        tag: String,
        message: String,
    ) = add(Level.WARN, tag, message)

    fun e(
        tag: String,
        message: String,
        error: Throwable? = null,
    ) = add(Level.ERROR, tag, if (error == null) message else "$message: ${error::class.simpleName}: ${error.message}")

    fun snapshot(): List<Entry> = synchronized(buffer) { buffer.toList() }

    private fun add(
        level: Level,
        tag: String,
        message: String,
    ) = synchronized(buffer) {
        if (buffer.size == capacity) buffer.removeFirst()
        buffer.addLast(Entry(clock(), level, tag, message))
    }

    companion object {
        const val DEFAULT_CAPACITY = 500
    }
}
