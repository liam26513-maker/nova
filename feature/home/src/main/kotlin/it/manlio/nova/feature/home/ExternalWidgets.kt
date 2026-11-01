package it.manlio.nova.feature.home

import android.content.Context
import android.view.View
import androidx.compose.runtime.Immutable

/** Un widget offerto da un'altra app; [cols] e [rows] sono la sua misura consigliata in caselle. */
@Immutable
data class WidgetProvider(
    val id: String,
    val label: String,
    val cols: Int,
    val rows: Int,
)

/**
 * Il ponte verso `AppWidgetHost` (SPEC §6.5): lo fornisce l'app, così la Home resta provabile senza Android vero.
 * [add] guida l'utente nell'autorizzazione e nell'eventuale configurazione e poi chiama [onAdded].
 */
interface ExternalWidgets {
    fun providers(): List<WidgetProvider>

    /** La vista del widget [ref], o null se non si può più mostrare. */
    fun view(
        context: Context,
        ref: Int,
    ): View?

    fun add(
        provider: WidgetProvider,
        onAdded: (ref: Int, cols: Int, rows: Int) -> Unit,
    )

    fun delete(ref: Int)
}
