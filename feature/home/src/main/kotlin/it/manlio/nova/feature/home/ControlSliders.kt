@file:Suppress("MagicNumber") // misure del cursore: sono il progetto grafico

package it.manlio.nova.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import androidx.compose.runtime.getValue

/**
 * Una riga con simbolo e cursore. Il valore vive qui e si legge solo al disegno; il sistema lo riceve al massimo
 * ~25 volte al secondo mentre si trascina (e sempre l'ultimo valore al rilascio), così il dito non aspetta mai Android.
 */
@Composable
internal fun SliderRow(
    glyph: ControlGlyph,
    label: String,
    level: Level,
) {
    val look = LocalHomeLook.current
    val latest by rememberUpdatedState(level) // la Home può ricostruire le azioni a metà trascinamento: il valore non deve saltare
    val value = remember { mutableFloatStateOf(ControlSheetMath.snap(level.read(), level.steps)) }
    val written = remember { floatArrayOf(value.floatValue) }
    val commit = {
        if (value.floatValue != written[0]) {
            written[0] = value.floatValue
            latest.write(value.floatValue)
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { value.floatValue }.drop(1).collect {
            commit()
            delay(WRITE_GAP_MS)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(Modifier.size(24.dp)) { drawControlGlyph(glyph, look.muted) }
        GlassSlider(
            value = value,
            label = label,
            steps = level.steps,
            onRelease = { commit() },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Cursore a barra, come quelli di iOS: il riempimento segue il dito lungo la linea orizzontale, subito, senza soglia di
 * partenza, e ignora i movimenti in su e in giù (il dito può uscire dalla barra e il cursore lo segue comunque).
 * Mentre si trascina la barra si ingrossa un poco. Anche per TalkBack (`setProgress`).
 */
@Composable
private fun GlassSlider(
    value: MutableFloatState,
    label: String,
    steps: Int,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = LocalHomeLook.current
    val pressed = remember { mutableStateOf(false) }
    val grow = animateFloatAsState(if (pressed.value) PRESSED_SCALE else 1f, spring(stiffness = SHEET_STIFFNESS), label = "slider-grow")
    val set = { v: Float -> value.floatValue = ControlSheetMath.snap(v, steps) }
    Box(
        modifier
            .height(SLIDER)
            .graphicsLayer {
                scaleX = grow.value
                scaleY = grow.value
            }.semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value.floatValue, 0f..1f)
                setProgress {
                    set(it)
                    onRelease()
                    true
                }
            }.pointerInput(steps) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed.value = true
                    try {
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        set(down.position.x / width)
                        down.consume()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.firstOrNull { it.id == down.id }?.let {
                                set(it.position.x / width)
                                it.consume() // il pannello non deve scambiarlo per un gesto verso l'alto
                            }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        pressed.value = false
                        onRelease()
                    }
                }
            }.drawBehind {
                val round = CornerRadius(size.height / 2f)
                drawRoundRect(look.ink.copy(alpha = 0.16f), cornerRadius = round)
                val fill = size.width * value.floatValue
                if (fill > 0f) drawRoundRect(look.ink.copy(alpha = 0.85f), size = Size(fill, size.height), cornerRadius = round)
            },
    )
}

@Composable
private fun GrantRow(onGrant: () -> Unit) {
    val look = LocalHomeLook.current
    Row(
        Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onGrant() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Canvas(Modifier.size(24.dp)) { drawControlGlyph(ControlGlyph.SUN, look.muted) }
        BasicText(stringResource(R.string.control_brightness_grant), style = TextStyle(look.muted, 15.sp, fontFamily = look.family))
    }
}

private val SLIDER: Dp = 44.dp
private const val PRESSED_SCALE = 1.04f
private const val WRITE_GAP_MS = 40L
