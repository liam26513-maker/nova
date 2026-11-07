package helium314.keyboard.keyboard

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import helium314.keyboard.latin.utils.ToolbarKey

/** Il colore d'accento scelto in NOVA e le modalità Lavoro e Studio (`AccentProvider`). Senza NOVA vale il tema. */
object NovaAccent {
    private val URI by lazy { Uri.parse("content://it.manlio.nova.accent") }

    /** La riga della modalità Lavoro (SPEC §10.5). */
    val WORK_KEYS = listOf(
        ToolbarKey.COPY, ToolbarKey.PASTE, ToolbarKey.CUT, ToolbarKey.UNDO, ToolbarKey.REDO, ToolbarKey.SELECT_ALL
    )

    fun read(context: Context): Int? = runCatching {
        context.contentResolver.query(URI, null, null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getInt(0) else null
        }
    }.getOrNull()

    fun working(context: Context) = flag(context, 1)

    fun studying(context: Context) = flag(context, 2)

    private fun flag(context: Context, column: Int): Boolean = runCatching {
        context.contentResolver.query(URI, null, null, null, null)?.use { it.moveToFirst() && it.getInt(column) == 1 }
    }.getOrNull() == true

    /** Android rifiuta l'osservatore se NOVA non è installata: la tastiera deve funzionare lo stesso. */
    fun watch(context: Context, reload: () -> Unit): ContentObserver? {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = reload()
        }
        return runCatching { context.contentResolver.registerContentObserver(URI, false, observer); observer }.getOrNull()
    }
}
