package it.manlio.nova.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.Nova
import it.manlio.nova.core.design.NovaHaptic
import it.manlio.nova.core.design.NovaSpacing
import it.manlio.nova.core.design.perform
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Alfabeto sul bordo della lista (anticipo dell'alfabeto a onda, SPEC §6.3): toccando o trascinando
 * il dito si salta alla prima app di quella lettera; la lettera sotto il dito e le vicine si
 * ingrandiscono, con un tic aptico a ogni cambio.
 */
@Composable
internal fun LetterStrip(
    apps: List<AppEntry>,
    onLetter: suspend (index: Int) -> Unit,
) {
    val firstIndex = remember(apps) { AppIndex.firstIndexByLetter(apps.map { it.letter }) }
    val letters = remember(firstIndex) { firstIndex.keys.toList() }
    if (letters.size < 2) return
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var active by remember { mutableIntStateOf(NONE) }
    var height by remember { mutableIntStateOf(1) }
    var lastPicked by remember { mutableStateOf<Char?>(null) }

    fun pick(y: Float) {
        val i = (y / height * letters.size).toInt().coerceIn(0, letters.lastIndex)
        if (i == active) return
        active = i
        val letter = letters[i]
        if (letter != lastPicked) {
            lastPicked = letter
            view.perform(NovaHaptic.SELECTION)
            scope.launch { onLetter(firstIndex.getValue(letter)) }
        }
    }

    Column(
        Modifier
            .fillMaxHeight()
            .width(STRIP_WIDTH)
            .padding(vertical = NovaSpacing.s)
            .onSizeChanged { height = it.height.coerceAtLeast(1) }
            .pointerInput(letters) {
                awaitEachGesture {
                    pick(awaitFirstDown().position.y)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { change ->
                            if (change.pressed) {
                                pick(change.position.y)
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    active = NONE
                    lastPicked = null
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEachIndexed { i, letter ->
            WaveLetter(letter, i, active) { scope.launch { onLetter(firstIndex.getValue(letter)) } }
        }
    }
}

/** Una lettera: più vicina al dito, più grande (l'"onda"); quella sotto il dito prende il colore d'accento. */
@Composable
private fun WaveLetter(
    letter: Char,
    index: Int,
    active: Int,
    onClick: () -> Unit,
) {
    val t = Nova.tokens
    val distance = if (active == NONE) Int.MAX_VALUE else abs(index - active)
    val scale by animateFloatAsState(
        if (distance < WAVE_REACH) 1f + WAVE_GROWTH * (1f - distance / WAVE_REACH.toFloat()) else 1f,
        t.motion.spring(),
        label = "letter-wave",
    )
    val on = index == active
    BasicText(
        letter.toString(),
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.semantics {
                contentDescription = letter.toString()
                onClick {
                    onClick()
                    true
                }
            },
        style = TextStyle(if (on) t.colors.accent else t.colors.onSurfaceMuted, 11.sp, if (on) FontWeight.SemiBold else FontWeight.Medium),
    )
}

private const val NONE = -1
private const val WAVE_REACH = 3
private const val WAVE_GROWTH = 0.7f
private val STRIP_WIDTH = 24.dp
