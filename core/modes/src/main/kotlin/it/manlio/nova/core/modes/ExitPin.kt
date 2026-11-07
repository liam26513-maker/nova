package it.manlio.nova.core.modes

import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Il PIN facoltativo per uscire dalla Presentazione (SPEC §8.6). Sul file resta solo un'impronta con un sale, mai il PIN.
 * Non è una protezione forte: serve a non far uscire dalla modalità chi ha il tablet in mano durante una presentazione.
 */
class ExitPin(private val file: File) {
    val isSet: Boolean get() = file.exists()

    fun set(pin: String) {
        require(valid(pin)) { "PIN non valido" }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes).toHex()
        file.parentFile?.mkdirs()
        file.writeText("$salt:${digest(salt, pin)}")
    }

    fun clear() {
        file.delete()
    }

    fun matches(pin: String): Boolean {
        val saved = runCatching { file.readText() }.getOrNull()?.split(':') ?: return false
        return saved.size == 2 && MessageDigest.isEqual(saved[1].toByteArray(), digest(saved[0], pin).toByteArray())
    }

    private fun digest(
        salt: String,
        pin: String,
    ) = MessageDigest.getInstance("SHA-256").digest("$salt$pin".toByteArray()).toHex()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    companion object {
        private const val SALT_BYTES = 16
        const val MIN_DIGITS = 4
        const val MAX_DIGITS = 8

        fun valid(pin: String) = pin.length in MIN_DIGITS..MAX_DIGITS && pin.all { it in '0'..'9' }
    }
}
