package it.manlio.nova.feature.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.roundToInt

/** Elemento che si sta trascinando nella griglia: [id], dove si trova il dito ([pointer]) e quanto si è spostato ([delta]). */
@Stable
internal class GridDragState {
    var id by mutableStateOf<String?>(null)
        internal set
    var pointer by mutableStateOf(Offset.Zero)
        internal set
    var delta by mutableStateOf(Offset.Zero)
        internal set
}

/** Dove cade il rilascio, deciso sulle caselle della pagina (logica pura, provata nei test). */
internal sealed interface DropAction {
    data object None : DropAction

    data class Merge(val draggedKey: String, val ontoId: String) : DropAction

    /** Mette [id] nella casella sotto il dito (vedi [dropSpot]). */
    data class Place(val id: String) : DropAction
}

/**
 * Decide cosa fare rilasciando [dragged] nel punto [pointer]. [page] sono gli elementi della pagina con il loro rettangolo.
 * Un'app sul centro di un'altra app o di una cartella le unisce; sopra sé stesso non succede nulla; altrove l'elemento va
 * nella casella sotto il dito, anche vuota, e chi c'era si sposta avanti.
 */
internal fun dropAction(
    dragged: HomeItem,
    pointer: Offset,
    page: List<Pair<HomeItem, Rect>>,
): DropAction {
    val over = page.firstOrNull { (item, rect) -> item.id != dragged.id && rect.contains(pointer) }
    if (over == null) {
        val onItself = page.any { it.first.id == dragged.id && it.second.contains(pointer) }
        return if (onItself) DropAction.None else DropAction.Place(dragged.id)
    }
    val (target, rect) = over
    val centre = Rect(rect.center - rect.size.toOffset() * MERGE_ZONE, rect.center + rect.size.toOffset() * MERGE_ZONE)
    if (dragged is AppItem && target !is WidgetItem && centre.contains(pointer)) return DropAction.Merge(dragged.key, target.id)
    return DropAction.Place(dragged.id)
}

/**
 * La casella in cui cade [item] rilasciato con il dito in [pointer] (in pixel, dal bordo sinistro della griglia) sulla
 * pagina [page]: l'elemento si centra sul dito e resta tutto dentro la pagina.
 */
internal fun dropSpot(
    page: Int,
    pointer: Offset,
    item: HomeItem,
    metrics: GridMetrics,
    cell: Size,
): Spot {
    val w = item.cols.coerceIn(1, metrics.columns)
    val h = item.rows.coerceIn(1, metrics.rows)
    val col = (pointer.x / cell.width - w / 2f).roundToInt().coerceIn(0, metrics.columns - w)
    val row = (pointer.y / cell.height - h / 2f).roundToInt().coerceIn(0, metrics.rows - h)
    return Spot(page, col, row)
}

private fun Size.toOffset() = Offset(width, height)

/**
 * Gesto della modalità modifica: si afferra un elemento con il dito e lo si trascina anche tra le pagine.
 * Sta sopra il pager e guarda gli eventi per primo ([PointerEventPass.Initial]): se il dito parte da un elemento e supera
 * la soglia, il movimento è suo e il pager non scorre; altrimenti non tocca nulla e si cambia pagina scorrendo.
 */
internal fun Modifier.gridDrag(
    enabled: Boolean,
    state: GridDragState,
    hit: (Offset) -> String?,
    onDrop: (id: String, pointer: Offset) -> Unit,
): Modifier =
    if (!enabled) {
        this
    } else {
        pointerInput(state) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val id = hit(down.position) ?: return@awaitEachGesture
                var started = false
                var finished = false
                while (!finished) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    if (change == null) {
                        finished = true
                    } else {
                        val delta = change.position - down.position
                        if (!started && (abs(delta.x) > viewConfiguration.touchSlop || abs(delta.y) > viewConfiguration.touchSlop)) {
                            started = true
                            state.id = id
                        }
                        if (started) {
                            change.consume()
                            state.pointer = change.position
                            state.delta = delta
                        }
                        finished = change.changedToUpIgnoreConsumed() || !change.pressed
                        if (finished && started) onDrop(id, change.position)
                    }
                }
                state.id = null
                state.delta = Offset.Zero
            }
        }
    }

private const val MERGE_ZONE = 0.22f
