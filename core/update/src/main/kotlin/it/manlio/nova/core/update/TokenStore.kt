package it.manlio.nova.core.update

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Conserva il token GitHub cifrato con una chiave dell'Android Keystore (non esportabile).
 * Il token non compare mai in log, report o configurazione (SPEC §13.4).
 */
class TokenStore(private val file: File) {
    @Volatile private var cached: String? = null

    fun get(): String? = cached ?: runCatching { decrypt(file.readBytes()) }.getOrNull()?.also { cached = it }

    fun isSet(): Boolean = file.exists()

    fun set(token: String) {
        val clean = token.trim()
        require(TOKEN.matches(clean)) { "Il token non sembra un token GitHub" }
        file.parentFile?.mkdirs()
        file.writeBytes(encrypt(clean))
        cached = clean
    }

    fun clear() {
        file.delete()
        cached = null
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec
                    .Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_BITS)
                    .build(),
            )
            generateKey()
        }
    }

    private fun encrypt(text: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return cipher.iv + cipher.doFinal(text.toByteArray())
    }

    private fun decrypt(data: ByteArray): String {
        val iv = data.copyOfRange(0, IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv)) }
        return String(cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES))
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "nova-github-token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val IV_BYTES = 12
        const val TAG_BITS = 128

        /** Token fine-grained (github_pat_…) o classici (ghp_…). */
        val TOKEN = Regex("^(github_pat_[A-Za-z0-9_]{20,255}|gh[pousr]_[A-Za-z0-9]{20,255})$")
    }
}
