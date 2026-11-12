@file:Suppress("MagicNumber") // misure del distintivo: sono il progetto grafico

package it.manlio.nova.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow

/** Una notifica come la dà il sistema, ridotta a ciò che serve. Solo in memoria: non viene mai salvata né inviata. */
data class RawNotification(
    val key: String,
    val pkg: String,
    val title: String,
    val text: String,
    val whenMs: Long,
    val ongoing: Boolean,
    val groupSummary: Boolean,
    val isMedia: Boolean,
    /** L'app permette di rispondere dalla notifica (un'azione con RemoteInput). */
    val canReply: Boolean = false,
)

/** Quel che si mostra di un'app: l'ultima notifica e quante sono in attesa. */
@Immutable
data class NotificationPreview(
    val pkg: String,
    val key: String,
    val title: String,
    val text: String,
    val count: Int,
    val whenMs: Long,
    val canReply: Boolean = false,
)

/** Da tutte le notifiche attive a un'anteprima per app (SPEC §6.2). Logica pura, provata con test. */
object NotificationIndex {
    /**
     * Le notifiche di servizio (in corso, riassunti di gruppo, comandi della musica) non contano: sono rumore sotto le icone.
     * Per ogni app vince la più recente; `count` le conta tutte.
     */
    fun build(raw: List<RawNotification>): Map<String, NotificationPreview> =
        raw
            .filter { !it.ongoing && !it.groupSummary && !it.isMedia && (it.title.isNotBlank() || it.text.isNotBlank()) }
            .groupBy { it.pkg }
            .mapValues { (pkg, list) ->
                val last = list.maxBy { it.whenMs }
                NotificationPreview(pkg, last.key, last.title.trim(), last.text.trim(), list.size, last.whenMs, last.canReply)
            }

    /** «Titolo: testo», o solo quello che c'è. */
    fun line(p: NotificationPreview): String = listOf(p.title, p.text).filter { it.isNotBlank() }.joinToString(": ")
}

/** Le notifiche lette dal sistema (`NotificationListenerService`); l'app le fornisce. null = senza notifiche in linea. */
interface NotificationSource {
    val previews: StateFlow<Map<String, NotificationPreview>>

    /** L'utente ha dato l'accesso alle notifiche a NOVA? */
    fun isEnabled(): Boolean

    /** Chiude la notifica (come «archivia»). */
    fun dismiss(key: String)

    /** Risponde alla notifica con [text], come dalla tendina di Android. @return vero se l'app l'ha ricevuta. */
    fun reply(
        key: String,
        text: String,
    ): Boolean = false
}

/** Le anteprime correnti, per disegnare distintivi e righe senza passarle a ogni funzione. */
internal val LocalNotifications = compositionLocalOf<Map<String, NotificationPreview>> { emptyMap() }

/** Il distintivo rosso con il numero, in alto a destra dell'icona. */
@Composable
internal fun CountBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    val text = if (count > 99) "99+" else count.toString()
    Box(
        modifier
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .background(Color(0xFFE5484D), CircleShape)
            .semantics { contentDescription = text }
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) { BasicText(text, style = TextStyle(Color.White, 12.sp, FontWeight.SemiBold)) }
}
