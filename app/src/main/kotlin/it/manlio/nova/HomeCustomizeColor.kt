package it.manlio.nova

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.Nova
import it.manlio.nova.core.design.NovaSpacing
import it.manlio.nova.core.design.SettingsBlock
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

/**
 * Scelta di un colore: «automatico» (se [autoLabel] non è null), i colori della [palette] e un colore libero, che apre tre barre
 * (tonalità, intensità, luminosità). Mentre si trascina si vede l'anteprima; il colore si salva quando si alza il dito.
 */
@Composable
internal fun ColorChoice(
    @StringRes title: Int,
    palette: List<Int>,
    color: Int?,
    autoLabel: String?,
    onPick: (Int?) -> Unit,
) {
    val offset = if (autoLabel != null) 1 else 0
    var hsv by remember(color) { mutableStateOf(toHsv(color ?: palette.first())) }
    var open by remember { mutableStateOf(false) }
    val bars = open || (color != null && color !in palette)
    val customLabel = stringResource(R.string.customize_color_custom)
    val items =
        listOfNotNull(autoLabel?.let { SwatchItem(it, mark = "A") }) +
            palette.map { SwatchItem(colorName(it), color = Color(it)) } +
            SwatchItem(customLabel, color = Color(fromHsv(hsv)).takeIf { bars }, mark = "+")
    val selected = if (bars) items.lastIndex else color?.let { palette.indexOf(it) + offset } ?: 0
    Swatches(title, items, selected) { i ->
        open = i == items.lastIndex
        when {
            open -> onPick(fromHsv(hsv))
            i < offset -> onPick(null)
            else -> onPick(palette[i - offset])
        }
    }
    if (bars) HsvBars(hsv, { hsv = it }) { onPick(fromHsv(hsv)) }
}

/** Le tre barre del colore libero: tonalità, intensità e luminosità. */
@Composable
private fun HsvBars(
    hsv: Hsv,
    onChange: (Hsv) -> Unit,
    onDone: () -> Unit,
) {
    val (h, s, v) = hsv
    SettingsBlock(showDivider = false) {
        Column {
            val hues = List(HUE_STOPS) { Color(fromHsv(Hsv(it * HUE_MAX / (HUE_STOPS - 1), 1f, 1f))) }
            ColorBar(stringResource(R.string.customize_hue), hues, h / HUE_MAX, { onChange(hsv.copy(h = it * HUE_MAX)) }, onDone)
            val grey = Color(fromHsv(Hsv(h, 0f, v)))
            ColorBar(stringResource(R.string.customize_saturation), listOf(grey, Color(fromHsv(Hsv(h, 1f, v)))), s, {
                onChange(hsv.copy(s = it))
            }, onDone)
            ColorBar(stringResource(R.string.customize_brightness), listOf(Color.Black, Color(fromHsv(Hsv(h, s, 1f)))), v, {
                onChange(hsv.copy(v = it))
            }, onDone)
        }
    }
}

/** Una barra da 0 a 1 con il cursore tondo; funziona anche con TalkBack (azione «imposta valore»). */
@Composable
private fun ColorBar(
    label: String,
    colors: List<Color>,
    value: Float,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
) {
    val change by rememberUpdatedState(onChange)
    val done by rememberUpdatedState(onDone)
    BasicText(label, style = TextStyle(Nova.tokens.colors.onSurfaceMuted, 13.sp))
    Spacer(Modifier.height(NovaSpacing.xs))
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(BAR)
            .semantics {
                contentDescription = label
                stateDescription = "${(value * PERCENT).roundToInt()}%"
                setProgress { change(it.coerceIn(0f, 1f)).also { done() }.let { true } }
            }.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    change((down.position.x / size.width).coerceIn(0f, 1f))
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach {
                            change((it.position.x / size.width).coerceIn(0f, 1f))
                            it.consume()
                        }
                    } while (event.changes.any { it.pressed })
                    done()
                }
            },
    ) {
        val r = size.height / 2
        drawRoundRect(Brush.horizontalGradient(colors), cornerRadius = CornerRadius(r))
        val x = (value * size.width).coerceIn(r, size.width - r)
        drawCircle(Color.White, r - KNOB_INSET.toPx(), Offset(x, r), style = Stroke(KNOB_STROKE.toPx()))
    }
    Spacer(Modifier.height(NovaSpacing.s))
}

internal data class Hsv(val h: Float, val s: Float, val v: Float)

internal fun toHsv(argb: Int) = FloatArray(HSV_SIZE).also { AndroidColor.colorToHSV(argb, it) }.let { (h, s, v) -> Hsv(h, s, v) }

internal fun fromHsv(c: Hsv) = AndroidColor.HSVToColor(floatArrayOf(c.h, c.s, c.v))

private const val HUE_MAX = 360f
private const val PERCENT = 100
private const val HUE_STOPS = 7
private const val HSV_SIZE = 3
private val KNOB_INSET = 2.dp
private val KNOB_STROKE = 3.dp
private val BAR = 28.dp
