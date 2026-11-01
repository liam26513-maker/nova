@file:Suppress("MagicNumber") // coefficienti di disegno (trasparenze, proporzioni): sono il progetto grafico

package it.manlio.nova.feature.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Widget nativi di NOVA (SPEC §6.5): disegnati in Compose, aggiornati solo mentre la Home è visibile.

/** Orologio analogico: quadrante con tacche, lancette di ore e minuti. */
@Composable
internal fun ClockWidget(nowMillis: Long) {
    val look = LocalHomeLook.current
    val cal = remember(nowMillis) { Calendar.getInstance().apply { timeInMillis = nowMillis } }
    val minute = cal.get(Calendar.MINUTE)
    val hour = (cal.get(Calendar.HOUR) + minute / MINUTES_PER_HOUR.toFloat()) / HOURS_ON_FACE
    Canvas(Modifier.fillMaxSize().padding(14.dp)) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        repeat(TICKS) { i ->
            val a = i * 2 * PI / TICKS
            val long = i % TICKS_PER_QUARTER == 0
            val from = r * if (long) 0.80f else 0.88f
            drawLine(
                look.ink.copy(alpha = if (long) 0.9f else 0.4f),
                Offset(c.x + (cos(a) * from).toFloat(), c.y + (sin(a) * from).toFloat()),
                Offset(c.x + (cos(a) * r * 0.96f).toFloat(), c.y + (sin(a) * r * 0.96f).toFloat()),
                if (long) 2.4.dp.toPx() else 1.2.dp.toPx(),
                StrokeCap.Round,
            )
        }
        hand(c, r * 0.5f, hour, 4.dp.toPx(), look.ink)
        hand(c, r * 0.76f, minute / MINUTES_PER_HOUR.toFloat(), 2.6.dp.toPx(), look.ink)
        drawCircle(look.accent, 4.dp.toPx(), c)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.hand(
    centre: Offset,
    length: Float,
    turn: Float,
    width: Float,
    color: Color,
) {
    val a = turn * 2 * PI - PI / 2
    drawLine(color, centre, Offset(centre.x + (cos(a) * length).toFloat(), centre.y + (sin(a) * length).toFloat()), width, StrokeCap.Round)
}

/** Calendario: giorno della settimana, numero grande, mese e anno. */
@Composable
internal fun CalendarWidget(nowMillis: Long) {
    val look = LocalHomeLook.current
    val locale = LocalConfiguration.current.locales[0]
    val date = Date(nowMillis)
    val weekday = remember(nowMillis, locale) { SimpleDateFormat("EEEE", locale).format(date).uppercase(locale) }
    val day = remember(nowMillis, locale) { SimpleDateFormat("d", locale).format(date) }
    val month = remember(nowMillis, locale) { SimpleDateFormat("MMMM yyyy", locale).format(date).replaceFirstChar { it.titlecase(locale) } }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
        BasicText(weekday, style = TextStyle(look.accent, 14.sp, FontWeight.SemiBold, letterSpacing = 0.8.sp, fontFamily = look.family))
        BasicText(day, style = TextStyle(look.ink, 64.sp, FontWeight.Light, fontFamily = look.family))
        BasicText(month, style = TextStyle(look.muted, 15.sp, fontFamily = look.family))
    }
}

/** Batteria: percentuale e barra; il ricevitore esiste solo mentre la Home è visibile. */
@Composable
internal fun BatteryWidget() {
    val look = LocalHomeLook.current
    val context = LocalContext.current
    var percent by remember { mutableIntStateOf(readBattery(context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))) }
    var charging by remember { mutableStateOf(false) }
    LifecycleStartEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context,
                    intent: Intent,
                ) {
                    percent = readBattery(intent)
                    charging = isCharging(intent)
                }
            }
        val sticky = context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        percent = readBattery(sticky)
        charging = isCharging(sticky)
        onStopOrDispose { context.unregisterReceiver(receiver) }
    }
    Row(
        Modifier.fillMaxSize().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            Column {
                BasicText("$percent%", style = TextStyle(look.ink, 30.sp, FontWeight.Light, fontFamily = look.family))
                BasicText(
                    stringResource(if (charging) R.string.widget_battery_charging else R.string.widget_battery),
                    style = TextStyle(look.muted, 13.sp, fontFamily = look.family),
                )
            }
        }
        Canvas(Modifier.size(GAUGE)) {
            val stroke = 7.dp.toPx()
            val d = size.minDimension - stroke
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(look.ink.copy(alpha = 0.18f), -90f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(
                look.accent,
                -90f,
                360f * percent / PERCENT_MAX,
                false,
                topLeft,
                Size(d, d),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

private fun readBattery(intent: Intent?): Int {
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    return if (level < 0 || scale <= 0) 0 else level * PERCENT_MAX / scale
}

private fun isCharging(intent: Intent?): Boolean {
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
}

/** Nota rapida: si scrive qui e si salva da sola poco dopo l'ultima lettera. */
@Composable
internal fun NoteWidget(
    text: String,
    onChange: (String) -> Unit,
) {
    val look = LocalHomeLook.current
    var value by remember(text) { mutableStateOf(text) }
    LaunchedEffect(value) {
        if (value != text) {
            delay(SAVE_DELAY_MS)
            onChange(value)
        }
    }
    Box(Modifier.fillMaxSize().padding(16.dp)) {
        if (value.isEmpty()) {
            BasicText(
                stringResource(R.string.widget_note_hint),
                style = TextStyle(look.muted, 16.sp, fontFamily = look.family),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = { value = it.take(HomeLayout.MAX_NOTE) },
            textStyle = TextStyle(look.ink, 16.sp, lineHeight = 22.sp, fontFamily = look.family),
            cursorBrush = SolidColor(look.ink),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private const val MINUTES_PER_HOUR = 60
private const val HOURS_ON_FACE = 12f
private const val TICKS = 12
private const val TICKS_PER_QUARTER = 3
private const val PERCENT_MAX = 100
private val GAUGE = 56.dp
private const val SAVE_DELAY_MS = 700L
