package it.manlio.nova

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Dice alla tastiera il colore d'accento scelto in Personalizza la Home (SPEC §10.1). Solo lettura e solo quel colore: non è un
 * dato personale, perciò non serve un permesso. Con l'accento automatico non risponde niente e la tastiera usa il suo.
 */
class AccentProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor = accentCursor((context?.applicationContext as? NovaApp)?.homePrefs?.prefs?.value?.accent)

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

internal fun accentCursor(accent: Int?): Cursor = MatrixCursor(arrayOf("accent")).apply { accent?.let { addRow(arrayOf(it)) } }

/** La tastiera si ricolora subito quando l'accento cambia. */
internal fun NovaApp.announceAccentChanges() {
    val uri = Uri.parse("content://$packageName.accent")
    appScope.launch {
        homePrefs.prefs.map { it.accent }.distinctUntilChanged().drop(1).collect { contentResolver.notifyChange(uri, null) }
    }
}
