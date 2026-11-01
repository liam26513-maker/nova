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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
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

/** Ora grande e leggera e data per esteso; [compact] la riduce per l'intestazione della home a icone. */
@Composable
internal fun ClockHeader(
    nowMillis: Long,
    compact: Boolean = false,
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val time = remember(nowMillis, locale) { android.text.format.DateFormat.getTimeFormat(context).format(Date(nowMillis)) }
    val date =
        remember(nowMillis, locale) {
            SimpleDateFormat("EEEE d MMMM", locale).format(Date(nowMillis)).replaceFirstChar { it.titlecase(locale) }
        }
    val look = LocalHomeLook.current
    Column {
        if (compact) {
            val weight = if (look.clockStyle == ClockStyle.BOLD) FontWeight.SemiBold else FontWeight.Light
            ClockText(time, COMPACT_TIME, weight)
            if (look.showDate) BasicText(date, style = TextStyle(look.muted, COMPACT_DATE, FontWeight.Normal, fontFamily = look.family))
            return@Column
        }
        when (look.clockStyle) {
            ClockStyle.THIN -> ClockText(time, 88.sp, FontWeight.Thin)
            ClockStyle.BOLD -> ClockText(time, 80.sp, FontWeight.SemiBold)
            ClockStyle.STACKED ->
                time.split(':', '.', limit = 2).forEach { part -> ClockText(part.trim(), 72.sp, FontWeight.Light, lineHeight = 70.sp) }
        }
        if (look.showDate) BasicText(date, style = TextStyle(look.muted, 20.sp, FontWeight.Light, fontFamily = look.family))
    }
}

@Composable
private fun ClockText(
    text: String,
    size: TextUnit,
    weight: FontWeight,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    val look = LocalHomeLook.current
    BasicText(text, style = TextStyle(look.ink, size, weight, letterSpacing = (-2).sp, lineHeight = lineHeight, fontFamily = look.family))
}

/** Pulsante tondo con tre cursori disegnati (nessuna icona di terzi). */
@Composable
internal fun SettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val label = stringResource(R.string.home_settings)
    Box(
        modifier
            .size(BUTTON)
            .background(LocalHomeLook.current.ink.copy(alpha = BUTTON_ALPHA), CircleShape)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                view.perform(NovaHaptic.TAP)
                onClick()
            }.semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val ink = LocalHomeLook.current.ink
        val knobFill = if (LocalHomeLook.current.dark) KNOB_FILL else KNOB_FILL_LIGHT
        Canvas(Modifier.size(GLYPH)) {
            val stroke = 1.6.dp.toPx()
            SLIDERS.forEach { (y, knob) ->
                val yy = size.height * y
                drawLine(ink, Offset(0f, yy), Offset(size.width, yy), stroke, StrokeCap.Round)
                drawCircle(knobFill, radius = 3.2.dp.toPx(), center = Offset(size.width * knob, yy))
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

private val COMPACT_TIME = 52.sp
private val COMPACT_DATE = 17.sp

/** Tre cursori: altezza della riga e posizione della manopola, in frazioni del disegno. */
@Suppress("MagicNumber") // geometria del disegno
private val SLIDERS = listOf(0.25f to 0.32f, 0.5f to 0.68f, 0.75f to 0.45f)
private val BUTTON = 48.dp
private const val BUTTON_ALPHA = 0.12f

@Suppress("MagicNumber")
private val KNOB_FILL = Color(0xFF3A3F47)

@Suppress("MagicNumber")
private val KNOB_FILL_LIGHT = Color(0xFFE8E6E1)
private val GLYPH = 22.dp
