package it.manlio.nova.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.NovaHaptic
import it.manlio.nova.core.design.perform
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Alfabeto a onda sul bordo destro (SPEC §6.3). Sotto il dito le lettere si gonfiano e sporgono
 * verso sinistra con una curva continua (gaussiana), non a scatti; accanto al dito compare una
 * bolla con la lettera. A ogni cambio di lettera: tic aptico e [onLetter]. Le animazioni leggono
 * lo stato solo in fase di disegno, quindi trascinare non ricompone l'interfaccia.
 */
@Composable
internal fun Alphabet(
    letters: List<Char>,
    onLetter: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (letters.size < 2) return
    val view = LocalView.current
    val density = LocalDensity.current
    val sigma = with(density) { WAVE_WIDTH.toPx() }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var touching by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf(letters.first()) }
    var height by remember { mutableIntStateOf(1) }
    val presence by animateFloatAsState(
        if (touching) 1f else 0f,
        spring(dampingRatio = PRESENCE_DAMPING, stiffness = PRESENCE_STIFFNESS),
        label = "alphabet-presence",
    )

    fun track(y: Float) {
        fingerY = y.coerceIn(0f, height.toFloat())
        val letter = letters[(fingerY / height * letters.size).toInt().coerceIn(0, letters.lastIndex)]
        if (letter != current || !touching) {
            current = letter
            view.perform(NovaHaptic.SELECTION)
            onLetter(letter)
        }
    }

    Box(
        modifier
            .onSizeChanged { height = it.height.coerceAtLeast(1) }
            .pointerInput(letters) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    track(down.position.y)
                    touching = true
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            if (change.pressed) {
                                track(change.position.y)
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    touching = false
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            letters.forEachIndexed { i, letter ->
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    WaveLetter(letter, onClick = { onLetter(letter) }) {
                        val centre = (i + HALF) * height / letters.size
                        val d = (centre - fingerY) / sigma
                        presence * exp(-d * d)
                    }
                }
            }
        }
        LetterBubble(current, { fingerY }, { presence })
    }
}

/** Una lettera: [influence] (0–1, letta solo in disegno) la ingrandisce e la spinge a sinistra. */
@Composable
private fun WaveLetter(
    letter: Char,
    onClick: () -> Unit,
    influence: () -> Float,
) {
    val popOut = with(LocalDensity.current) { POP_OUT.toPx() }
    BasicText(
        letter.toString(),
        Modifier
            .graphicsLayer {
                val k = influence()
                val scale = 1f + WAVE_GROWTH * k
                scaleX = scale
                scaleY = scale
                translationX = -popOut * k
                alpha = REST_ALPHA + (1f - REST_ALPHA) * k
            }.semantics {
                contentDescription = letter.toString()
                onClick {
                    onClick()
                    true
                }
            },
        style = TextStyle(HomeInk, 12.sp, FontWeight.Medium),
    )
}

/** Bolla con la lettera accanto al dito: lo segue e si gonfia o sgonfia con una molla. */
@Composable
private fun LetterBubble(
    letter: Char,
    fingerY: () -> Float,
    presence: () -> Float,
) {
    val density = LocalDensity.current
    val gap = with(density) { (BUBBLE_GAP + BUBBLE).toPx() }
    val radius = with(density) { BUBBLE.toPx() / 2 }
    Box(
        Modifier
            .offset { IntOffset(-gap.roundToInt(), (fingerY() - radius).roundToInt()) }
            .size(BUBBLE)
            .graphicsLayer {
                val p = presence()
                alpha = p
                val scale = BUBBLE_MIN_SCALE + (1f - BUBBLE_MIN_SCALE) * p
                scaleX = scale
                scaleY = scale
            }.background(HomeInk.copy(alpha = BUBBLE_ALPHA), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(letter.toString(), style = TextStyle(HomeInk, 34.sp, FontWeight.Light))
    }
}

private val WAVE_WIDTH = 52.dp
private val POP_OUT = 34.dp
private val BUBBLE = 76.dp
private val BUBBLE_GAP = 36.dp
private const val HALF = 0.5f
private const val WAVE_GROWTH = 1.4f
private const val REST_ALPHA = 0.55f
private const val BUBBLE_ALPHA = 0.16f
private const val BUBBLE_MIN_SCALE = 0.4f
private const val PRESENCE_DAMPING = 0.72f
private const val PRESENCE_STIFFNESS = 320f
