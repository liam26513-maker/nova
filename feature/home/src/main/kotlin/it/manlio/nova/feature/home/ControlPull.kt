package it.manlio.nova.feature.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Tirare giù dall'angolo in alto a destra apre la tendina, che segue il dito (come il centro di controllo dell'iPad).
 * Gira nel passaggio «iniziale», prima dei figli, ma solo se il gesto parte nell'angolo e va in giù: ogni altro
 * tocco passa intatto (icone, liste, scorrimento giù per le notifiche). Non ha nessuna zona che blocchi i tocchi.
 */
internal fun Modifier.pullDownControl(
    sheet: ControlSheetState,
    enabled: Boolean,
    onSettled: (Boolean) -> Unit,
): Modifier =
    if (!enabled) {
        this
    } else {
        pointerInput(sheet) {
            val regionWidth = CORNER_WIDTH.toPx()
            val regionHeight = CORNER_HEIGHT.toPx()
            sheet.estimate(DEFAULT_TRAVEL.toPx())
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val inCorner = down.position.x > size.width - regionWidth && down.position.y < regionHeight
                // Con la tendina già aperta ci pensa il pannello (e il tocco fuori).
                if (inCorner && sheet.progress == 0f) dragDown(sheet, down)?.let { onSettled(sheet.release(it)) }
            }
        }
    }

/**
 * Segue il dito dopo la pressione nell'angolo. Se il gesto va in giù porta la tendina con sé e, al rilascio, dà la
 * velocità (pannelli al secondo); se va di lato o in su lascia perdere subito (null) e il tocco resta a chi lo vuole.
 */
private suspend fun AwaitPointerEventScope.dragDown(
    sheet: ControlSheetState,
    down: PointerInputChange,
): Float? {
    val tracker = VelocityTracker()
    tracker.addPosition(down.uptimeMillis, down.position)
    val slop = viewConfiguration.touchSlop
    var dx = 0f
    var dy = 0f
    var started = false
    var rejected = false // di lato o verso l'alto: non è il nostro gesto
    var change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
    while (change != null && change.pressed && !rejected) {
        val move = change.positionChange()
        tracker.addPosition(change.uptimeMillis, change.position)
        if (started) {
            change.consume()
            sheet.drag(move.y)
        } else {
            dx += move.x
            dy += move.y
            when (pullVerdict(dx, dy, slop)) {
                PullVerdict.DOWN -> {
                    started = true
                    change.consume()
                    sheet.drag(dy)
                }
                PullVerdict.REJECTED -> rejected = true
                PullVerdict.UNDECIDED -> Unit
            }
        }
        if (!rejected) change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
    }
    return if (started) tracker.calculateVelocity().y / sheet.travel else null
}

private enum class PullVerdict { UNDECIDED, DOWN, REJECTED }

/** Dopo la soglia di tocco: in giù più che di lato è il nostro gesto, di lato o in su no. */
private fun pullVerdict(
    dx: Float,
    dy: Float,
    slop: Float,
): PullVerdict =
    when {
        abs(dx) <= slop && abs(dy) <= slop -> PullVerdict.UNDECIDED
        dy > abs(dx) -> PullVerdict.DOWN
        else -> PullVerdict.REJECTED
    }

private val CORNER_WIDTH = 280.dp
private val CORNER_HEIGHT = 120.dp
private val DEFAULT_TRAVEL = 520.dp
