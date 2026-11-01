@file:Suppress("MagicNumber") // disegno dei simboli e misure: sono il progetto grafico

package it.manlio.nova.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import it.manlio.nova.core.design.NovaHaptic
import it.manlio.nova.core.design.perform
import kotlinx.coroutines.delay

/** Meteo (Open-Meteo, senza chiave). La città sta nel testo del widget; si scrive lì e si può cambiare. */
@Composable
internal fun WeatherWidget(
    city: String,
    load: (suspend (String) -> Result<Weather>)?,
    onCity: (String) -> Unit,
) {
    val look = LocalHomeLook.current
    var editing by remember(city) { mutableStateOf(city.isBlank()) }
    var refresh by remember { mutableIntStateOf(0) }
    var started by remember { mutableStateOf(false) }
    LifecycleStartEffect(Unit) {
        // Al primo avvio carica già produceState; a ogni ritorno sulla Home si riprova (la cache dell'app evita richieste inutili).
        if (started) refresh++ else started = true
        onStopOrDispose {}
    }
    val state by produceState<Result<Weather>?>(null, city, refresh, load) {
        value = if (city.isBlank() || load == null) null else load(city)
    }
    Box(Modifier.fillMaxSize().padding(16.dp)) {
        when {
            editing ->
                CityField(city, look) { name ->
                    editing = false
                    onCity(name.trim())
                }
            state == null ->
                BasicText(
                    stringResource(R.string.weather_loading),
                    style = TextStyle(look.muted, 15.sp, fontFamily = look.family),
                )
            state!!.isFailure ->
                Column(Modifier.clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { refresh++ }) {
                    BasicText(stringResource(R.string.weather_failed), style = TextStyle(look.ink, 16.sp, fontFamily = look.family))
                    BasicText(stringResource(R.string.weather_retry), style = TextStyle(look.muted, 13.sp, fontFamily = look.family))
                }
            else -> WeatherBody(state!!.getOrThrow(), look) { editing = true }
        }
    }
}

@Composable
private fun WeatherBody(
    w: Weather,
    look: HomeLook,
    onChangeCity: () -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                BasicText(
                    w.city,
                    Modifier.clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onChangeCity() },
                    style = TextStyle(look.ink, 16.sp, FontWeight.SemiBold, fontFamily = look.family),
                    maxLines = 1,
                )
                BasicText("${w.tempC}°", style = TextStyle(look.ink, 52.sp, FontWeight.Light, fontFamily = look.family))
            }
            Canvas(Modifier.size(44.dp)) { drawWeather(w.kind, w.isDay, look.ink) }
        }
        Column {
            BasicText(stringResource(w.kind.label()), style = TextStyle(look.ink, 15.sp, FontWeight.Medium, fontFamily = look.family))
            BasicText("↑${w.hiC}°  ↓${w.loC}°", style = TextStyle(look.muted, 14.sp, fontFamily = look.family))
        }
    }
}

@Composable
private fun CityField(
    initial: String,
    look: HomeLook,
    onDone: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(stringResource(R.string.weather_city_title), style = TextStyle(look.muted, 13.sp, fontFamily = look.family))
        BasicTextField(
            value = text,
            onValueChange = { text = it.take(MAX_CITY) },
            singleLine = true,
            textStyle = TextStyle(look.ink, 20.sp, fontFamily = look.family),
            cursorBrush = SolidColor(look.ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onDone(text) }),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) BasicText(stringResource(R.string.weather_city_hint), style = TextStyle(look.muted, 20.sp))
                    inner()
                }
            },
        )
        BasicText(
            stringResource(R.string.weather_city_save),
            Modifier
                .clickable(
                    remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                ) { if (text.isNotBlank()) onDone(text) }
                .padding(vertical = 4.dp),
            style = TextStyle(look.accent, 16.sp, FontWeight.Medium, fontFamily = look.family),
        )
    }
}

private fun WeatherKind.label() =
    when (this) {
        WeatherKind.CLEAR -> R.string.weather_clear
        WeatherKind.PARTLY -> R.string.weather_partly
        WeatherKind.CLOUDY -> R.string.weather_cloudy
        WeatherKind.FOG -> R.string.weather_fog
        WeatherKind.DRIZZLE -> R.string.weather_drizzle
        WeatherKind.RAIN -> R.string.weather_rain
        WeatherKind.SNOW -> R.string.weather_snow
        WeatherKind.STORM -> R.string.weather_storm
    }

/** Simboli del tempo, disegnati da NOVA: sole, luna, nuvola con pioggia, neve, fulmine, nebbia. */
@Suppress("LongMethod", "CyclomaticComplexMethod") // un ramo per tipo di tempo: lungo ma lineare
private fun DrawScope.drawWeather(
    kind: WeatherKind,
    day: Boolean,
    ink: Color,
) {
    val u = size.minDimension
    val w = u * 0.07f
    val sun = Color(0xFFFFC857)

    fun cloud(dy: Float = 0f) {
        val c = if (kind == WeatherKind.CLOUDY || kind == WeatherKind.FOG) ink else ink.copy(alpha = 0.92f)
        drawCircle(c, u * 0.2f, Offset(u * 0.36f, u * (0.5f + dy)))
        drawCircle(c, u * 0.26f, Offset(u * 0.58f, u * (0.44f + dy)))
        drawRoundRect(c, Offset(u * 0.16f, u * (0.5f + dy)), Size(u * 0.68f, u * 0.2f), androidx.compose.ui.geometry.CornerRadius(u * 0.1f))
    }
    when (kind) {
        WeatherKind.CLEAR ->
            if (day) {
                drawCircle(sun, u * 0.2f, Offset(u / 2, u / 2))
                repeat(8) { i ->
                    val a = i * Math.PI / 4
                    drawLine(
                        sun,
                        Offset(u * (0.5f + 0.3f * Math.cos(a).toFloat()), u * (0.5f + 0.3f * Math.sin(a).toFloat())),
                        Offset(u * (0.5f + 0.42f * Math.cos(a).toFloat()), u * (0.5f + 0.42f * Math.sin(a).toFloat())),
                        w,
                        StrokeCap.Round,
                    )
                }
            } else {
                drawCircle(ink, u * 0.28f, Offset(u / 2, u / 2))
                drawCircle(Color.Black.copy(alpha = 0.55f), u * 0.24f, Offset(u * 0.62f, u * 0.42f))
            }
        WeatherKind.PARTLY -> {
            drawCircle(if (day) sun else ink.copy(alpha = 0.6f), u * 0.17f, Offset(u * 0.34f, u * 0.34f))
            cloud(0.12f)
        }
        WeatherKind.CLOUDY -> cloud()
        WeatherKind.FOG -> {
            cloud(-0.08f)
            listOf(0.72f, 0.86f).forEach { y -> drawLine(ink, Offset(u * 0.2f, u * y), Offset(u * 0.8f, u * y), w, StrokeCap.Round) }
        }
        WeatherKind.DRIZZLE, WeatherKind.RAIN -> {
            cloud(-0.08f)
            val n = if (kind == WeatherKind.RAIN) 3 else 2
            repeat(
                n,
            ) { i ->
                drawLine(
                    Color(0xFF6FB1FF),
                    Offset(u * (0.3f + 0.2f * i), u * 0.72f),
                    Offset(u * (0.26f + 0.2f * i), u * 0.88f),
                    w,
                    StrokeCap.Round,
                )
            }
        }
        WeatherKind.SNOW -> {
            cloud(-0.08f)
            repeat(3) { i -> drawCircle(ink, w * 0.7f, Offset(u * (0.3f + 0.2f * i), u * (0.78f + 0.06f * (i % 2)))) }
        }
        WeatherKind.STORM -> {
            cloud(-0.08f)
            drawPath(
                Path().apply {
                    moveTo(u * 0.52f, u * 0.62f)
                    lineTo(u * 0.4f, u * 0.82f)
                    lineTo(u * 0.52f, u * 0.82f)
                    lineTo(u * 0.44f, u * 0.98f)
                    lineTo(u * 0.66f, u * 0.74f)
                    lineTo(u * 0.54f, u * 0.74f)
                    close()
                },
                sun,
            )
        }
    }
}

/** Pomodoro: anello di avanzamento, tempo che resta, avvio/pausa e azzeramento. Il tic ogni secondo c'è solo mentre va. */
@Composable
internal fun PomodoroWidget(
    text: String,
    onChange: (String) -> Unit,
) {
    val look = LocalHomeLook.current
    val view = LocalView.current
    val state = remember(text) { Pomodoro.decode(text) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.running, state.endAt) {
        while (state.running) {
            now = System.currentTimeMillis()
            val next = state.tick(now)
            if (next != state) {
                view.perform(NovaHaptic.SUCCESS)
                onChange(next.encode())
                return@LaunchedEffect
            }
            delay(1000)
        }
        now = System.currentTimeMillis()
    }
    val label = stringResource(if (state.phase == Pomodoro.Phase.FOCUS) R.string.pomodoro_focus else R.string.pomodoro_break)
    Row(
        Modifier.fillMaxSize().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val s = 5.dp.toPx()
                val d = size.minDimension - s
                val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
                drawArc(look.ink.copy(alpha = 0.18f), -90f, 360f, false, tl, Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
                drawArc(look.accent, -90f, 360f * state.progress(now), false, tl, Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
            }
        }
        Column(Modifier.weight(1f)) {
            BasicText(Pomodoro.clock(state.remaining(now)), style = TextStyle(look.ink, 26.sp, FontWeight.Light, fontFamily = look.family))
            BasicText(label, style = TextStyle(look.muted, 13.sp, fontFamily = look.family))
        }
        RoundAction(
            if (state.running) "❚❚" else "▶",
            stringResource(if (state.running) R.string.pomodoro_pause else R.string.pomodoro_start),
        ) {
            now = System.currentTimeMillis()
            onChange((if (state.running) state.pause(now) else state.start(now)).encode())
        }
        RoundAction("↺", stringResource(R.string.pomodoro_reset)) { onChange(state.reset().encode()) }
    }
}

@Composable
private fun RoundAction(
    glyph: String,
    description: String,
    onClick: () -> Unit,
) {
    val look = LocalHomeLook.current
    Box(
        Modifier
            .size(38.dp)
            .background(look.ink.copy(alpha = 0.14f), CircleShape)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { BasicText(glyph, style = TextStyle(look.ink, 14.sp)) }
}

private const val MAX_CITY = 60
