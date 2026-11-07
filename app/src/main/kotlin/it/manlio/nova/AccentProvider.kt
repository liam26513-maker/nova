package it.manlio.nova

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import it.manlio.nova.core.modes.Modes
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Dice alla tastiera il colore d'accento scelto in Personalizza la Home (SPEC §10.1) e se Lavoro o Studio sono accese (§10.5).
 * Solo lettura e solo questi valori: non sono dati personali, perciò non serve un permesso. Con l'accento automatico
 * il colore è vuoto e la tastiera usa il suo.
 */
class AccentProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val app = context?.applicationContext as? NovaApp
        // In modalità provvisoria le modalità non partono: la tastiera deve avere risposta lo stesso.
        val mode = runCatching { app?.modes?.engine?.active?.value?.modeId }.getOrNull()
        return accentCursor(app?.homePrefs?.prefs?.value?.accent, mode)
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0
}

internal fun accentCursor(
    accent: Int?,
    mode: String?,
): Cursor =
    MatrixCursor(arrayOf("accent", "work", "study")).apply {
        addRow(arrayOf(accent, if (mode == Modes.WORK.id) 1 else 0, if (mode == Modes.STUDY.id) 1 else 0))
    }

/** La tastiera si aggiorna subito quando cambia l'accento o si entra e si esce da una modalità. */
internal fun NovaApp.announceAccentChanges() {
    val uri = Uri.parse("content://$packageName.accent")
    appScope.launch {
        combine(homePrefs.prefs, modes.engine.active) { prefs, session -> prefs.accent to session?.modeId }
            .distinctUntilChanged()
            .drop(1)
            .collect { contentResolver.notifyChange(uri, null) }
    }
}
