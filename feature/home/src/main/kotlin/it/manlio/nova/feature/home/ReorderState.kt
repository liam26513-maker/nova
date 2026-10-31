package it.manlio.nova.feature.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import kotlin.math.abs

/**
 * Riordino trascinando (logica pura, provata nei test): la riga trascinata segue il dito; appena
 * supera metà della riga vicina, le due si scambiano. [order] è la lista mostrata durante il trascinamento.
 */
@Stable
internal class ReorderState<T>(
    private val initial: List<T>,
    private val keyOf: (T) -> String,
) {
    var order by mutableStateOf(initial)
        private set
    var dragKey by mutableStateOf<String?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set
    var rowHeight = 1f
    private var moved = false

    fun contains(key: String) = order.any { keyOf(it) == key }

    fun start(key: String) {
        dragKey = key
        offset = 0f
        moved = false
    }

    /** @return true se c'è stato uno scambio (per il tic aptico). */
    fun drag(dy: Float): Boolean {
        val key = dragKey ?: return false
        offset += dy
        if (abs(offset) > rowHeight / MOVE_THRESHOLD_DIVISOR) moved = true
        val from = order.indexOfFirst { keyOf(it) == key }
        val step =
            when {
                offset > rowHeight / 2 -> 1
                offset < -rowHeight / 2 -> -1
                else -> 0
            }
        val to = from + step
        if (step == 0 || to !in order.indices) return false
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        offset -= step * rowHeight
        return true
    }

    /** @return il nuovo ordine se la riga si è spostata, null se era solo una pressione lunga. */
    fun end(): List<T>? {
        val result = order.takeIf { moved }
        dragKey = null
        offset = 0f
        return result
    }

    /** Gesto interrotto: torna all'ordine di partenza. */
    fun cancel() {
        order = initial
        dragKey = null
        offset = 0f
    }

    private companion object {
        const val MOVE_THRESHOLD_DIVISOR = 6
    }
}

/**
 * Gesto di una riga: pressione lunga, poi trascinamento per riordinare; pressione lunga senza
 * movimento = [onLongPress]. Il dito si misura in coordinate dello schermo ([coordinates]), così
 * gli scambi (che spostano la riga) non falsano il movimento; il rilascio viene consumato, così
 * una pressione lunga non apre anche l'app. Va messo dopo il `clickable` della riga.
 */
@Suppress("LongParameterList") // un gesto con le sue quattro reazioni: separarle non lo renderebbe più chiaro
internal fun <T> Modifier.reorderable(
    state: ReorderState<T>,
    key: String,
    coordinates: () -> LayoutCoordinates?,
    onStart: () -> Unit,
    onSwap: () -> Unit,
    onCommit: (List<T>) -> Unit,
    onLongPress: () -> Unit,
): Modifier =
    pointerInput(state, key) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture

            fun rootY(local: Offset) = coordinates()?.localToRoot(local)?.y ?: local.y
            onStart()
            state.start(key)
            var last = rootY(down.position)
            var finished = false
            while (!finished) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                if (change == null) {
                    state.cancel()
                    return@awaitEachGesture
                }
                if (change.pressed) {
                    val y = rootY(change.position)
                    if (state.drag(y - last)) onSwap()
                    last = y
                }
                finished = !change.pressed
                change.consume()
            }
            state.end()?.let(onCommit) ?: onLongPress()
        }
    }
