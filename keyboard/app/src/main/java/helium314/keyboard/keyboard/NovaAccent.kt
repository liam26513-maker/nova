package helium314.keyboard.keyboard

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper

/** Il colore d'accento scelto in NOVA (`AccentProvider`). Senza NOVA, o con l'accento automatico, non c'è e vale quello del tema. */
object NovaAccent {
    private val URI = Uri.parse("content://it.manlio.nova.accent")

    fun read(context: Context): Int? = runCatching {
        context.contentResolver.query(URI, null, null, null, null)?.use { if (it.moveToFirst()) it.getInt(0) else null }
    }.getOrNull()

    /** Android rifiuta l'osservatore se NOVA non è installata: la tastiera deve funzionare lo stesso. */
    fun watch(context: Context, reload: () -> Unit): ContentObserver? {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = reload()
        }
        return runCatching { context.contentResolver.registerContentObserver(URI, false, observer); observer }.getOrNull()
    }
}
