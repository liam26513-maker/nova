package it.manlio.nova.feature.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import it.manlio.nova.core.design.Nova
import it.manlio.nova.core.design.NovaHaptic
import it.manlio.nova.core.design.perform
import java.text.SimpleDateFormat
import java.util.Date

/** Ora corrente, aggiornata dal "tic" di sistema di ogni minuto solo mentre la home è visibile (niente timer). */
@Composable
fun rememberMinuteClock(): Long {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LifecycleStartEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context,
                    intent: Intent,
                ) {
                    now = System.currentTimeMillis()
                }
            }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        now = System.currentTimeMillis()
        onStopOrDispose { context.unregisterReceiver(receiver) }
    }
    return now
}

/** Ora grande e leggera, data per esteso e, a destra, il pulsante delle impostazioni di NOVA. */
@Composable
internal fun ClockHeader(
    nowMillis: Long,
    onOpenSettings: () -> Unit,
) {
    val t = Nova.tokens
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val time = remember(nowMillis, locale) { android.text.format.DateFormat.getTimeFormat(context).format(Date(nowMillis)) }
    val date =
        remember(nowMillis, locale) {
            SimpleDateFormat("EEEE d MMMM", locale).format(Date(nowMillis)).replaceFirstChar { it.titlecase(locale) }
        }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            BasicText(time, style = TextStyle(t.colors.onSurface, 72.sp, FontWeight.ExtraLight, letterSpacing = (-2).sp))
            BasicText(date, style = TextStyle(t.colors.onSurfaceMuted, 18.sp))
        }
        SettingsButton(onOpenSettings)
    }
}

/** Pulsante tondo con tre cursori disegnati (nessuna icona di terzi). */
@Composable
private fun SettingsButton(onClick: () -> Unit) {
    val t = Nova.tokens
    val view = LocalView.current
    val label = stringResource(R.string.home_settings)
    Box(
        Modifier
            .size(BUTTON)
            .background(t.colors.surface.copy(alpha = t.materialOpacity), CircleShape)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                view.perform(NovaHaptic.TAP)
                onClick()
            }.semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val ink = t.colors.onSurface
        Canvas(Modifier.size(GLYPH)) {
            val stroke = 1.6.dp.toPx()
            SLIDERS.forEach { (y, knob) ->
                val yy = size.height * y
                drawLine(ink, Offset(0f, yy), Offset(size.width, yy), stroke, StrokeCap.Round)
                drawCircle(t.colors.surface, radius = 3.2.dp.toPx(), center = Offset(size.width * knob, yy))
                drawCircle(
                    ink,
                    radius = 3.2.dp.toPx(),
                    center = Offset(size.width * knob, yy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
                )
            }
        }
    }
}

/** Tre cursori: altezza della riga e posizione della manopola, in frazioni del disegno. */
@Suppress("MagicNumber") // geometria del disegno
private val SLIDERS = listOf(0.25f to 0.32f, 0.5f to 0.68f, 0.75f to 0.45f)
private val BUTTON = 48.dp
private val GLYPH = 22.dp
