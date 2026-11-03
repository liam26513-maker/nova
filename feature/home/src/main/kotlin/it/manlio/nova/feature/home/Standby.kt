@file:Suppress("MagicNumber") // misure e colori del progetto grafico

package it.manlio.nova.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Cosa mostra il StandBy oltre all'ora: batteria, se è in carica, se è notte (rosso scuro) e la prossima sveglia, se c'è. */
@Immutable
data class StandbyInfo(
    val battery: Int,
    val charging: Boolean,
    val night: Boolean,
    val alarm: String?,
)

/** Le regole del StandBy (SPEC §12.6), senza Android, così si provano. */
object StandbyRules {
    /** Dopo quanto tempo senza toccare lo schermo parte il StandBy automatico. */
    const val IDLE_MS = 30_000L
    private const val NIGHT_FROM = 21
    private const val NIGHT_TO = 7

    /** Di notte (dalle 21 alle 7) i colori diventano rosso scuro e lo schermo si abbassa quasi al minimo. */
    fun isNight(hour: Int): Boolean = hour >= NIGHT_FROM || hour < NIGHT_TO

    /** Il StandBy automatico parte solo se acceso nelle impostazioni, in carica e in orizzontale. */
    fun shouldStart(
        auto: Boolean,
        charging: Boolean,
        landscape: Boolean,
    ): Boolean = auto && charging && landscape

    /** Luminosità della finestra mentre il StandBy è visibile (0..1); poi si torna a quella di sistema. */
    fun brightness(night: Boolean): Float = if (night) 0.03f else 0.3f
}

/**
 * StandBy (SPEC §12.6): schermo nero a tutto campo con l'ora grande, la data, la batteria e la sveglia. Un tocco, o Indietro, esce.
 * Colori sobri, e di notte rosso scuro per non disturbare. È dentro la Home: non apre finestre sopra le altre app.
 */
@Composable
fun StandbyScreen(
    info: StandbyInfo,
    nowMillis: Long,
    onExit: () -> Unit,
) {
    BackHandler(onBack = onExit)
    val look = LocalHomeLook.current
    val ink = if (info.night) Color(0xFFB02A2A) else Color(0xFFF2F2F2)
    val muted = ink.copy(alpha = 0.62f)
    val time = remember(nowMillis) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(nowMillis)) }
    val date =
        remember(nowMillis) {
            SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(nowMillis)).replaceFirstChar { it.uppercase() }
        }
    val label = stringResource(R.string.standby_description)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onExit()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(time, style = TextStyle(ink, 220.sp, FontWeight.Thin, fontFamily = look.family))
            BasicText(date, style = TextStyle(muted, 36.sp, FontWeight.Light, fontFamily = look.family))
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                val battery = stringResource(R.string.standby_battery, info.battery)
                val charging = if (info.charging) " · " + stringResource(R.string.standby_charging) else ""
                BasicText(battery + charging, style = TextStyle(muted, 26.sp, fontFamily = look.family))
                info.alarm?.let {
                    BasicText(stringResource(R.string.standby_alarm, it), style = TextStyle(muted, 26.sp, fontFamily = look.family))
                }
            }
        }
    }
}

/** Avvisa a ogni tocco, senza consumarlo (passaggio iniziale): serve a ricominciare il conto del StandBy automatico. */
internal fun Modifier.onAnyTouch(onTouch: State<() -> Unit>): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            onTouch.value()
        }
    }
