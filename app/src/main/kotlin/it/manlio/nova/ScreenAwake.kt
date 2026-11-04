package it.manlio.nova

import android.content.Context
import android.content.Intent
import android.provider.Settings
import it.manlio.nova.feature.home.MoreItem

/**
 * Schermo sempre acceso: spento di serie, si accende da tendina → Altro. Allunga al massimo il tempo di spegnimento e ricorda
 * quello di prima per rimetterlo. Lo stato si legge sempre da Android, così resta vero anche se lo cambia una modalità uscendo.
 */
internal class ScreenAwake(private val context: Context) {
    private val saved = context.getSharedPreferences("schermo-acceso", Context.MODE_PRIVATE)

    val on: Boolean get() = timeout() == ALWAYS_MS

    /** Vero se Android ha obbedito. */
    fun set(next: Boolean): Boolean {
        if (next == on) return true
        if (next) saved.edit().putInt(BEFORE, timeout()).apply()
        val value = if (next) ALWAYS_MS else saved.getInt(BEFORE, DEFAULT_MS)
        val resolver = context.contentResolver
        return runCatching { Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, value) }.getOrDefault(false)
    }

    private fun timeout() = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, DEFAULT_MS)

    private companion object {
        const val ALWAYS_MS = Int.MAX_VALUE
        const val DEFAULT_MS = 60_000
        const val BEFORE = "prima"
    }
}

/** La riga di «Altro»: senza il permesso di modificare le impostazioni apre la schermata di Android che lo concede. */
internal fun screenAwakeItem(context: Context): MoreItem {
    val awake = ScreenAwake(context)
    val text = { on: Boolean -> context.getString(if (on) R.string.more_on else R.string.more_off) }
    return MoreItem(context.getString(R.string.more_screen_awake), text(awake.on)) {
        if (Settings.System.canWrite(context)) {
            awake.set(!awake.on)
            text(awake.on)
        } else {
            val ask = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
            runCatching { context.startActivity(ask.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            null
        }
    }
}
