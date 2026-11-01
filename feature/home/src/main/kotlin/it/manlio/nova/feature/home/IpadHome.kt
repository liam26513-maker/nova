@file:Suppress("TooManyFunctions") // la Home a icone è una sola schermata: ogni parte è una funzione piccola

package it.manlio.nova.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import it.manlio.nova.core.design.NovaHaptic
import it.manlio.nova.core.design.perform
import java.util.UUID

/**
 * Home in stile iPad (SPEC §6.1, §6.4): in alto l'ora e le impostazioni, poi pagine con icone, cartelle e
 * widget disposti da te, i puntini della pagina, «Cerca» e in basso il dock traslucido con i primi preferiti.
 * Con [editing] (pressione lunga sullo sfondo o «Modifica la Home») le icone ondeggiano, si trascinano anche
 * tra le pagine, si uniscono in cartelle, si tolgono con «−» e si aggiungono widget con «+».
 */
@Composable
internal fun IpadHome(
    layout: HomeLayout,
    entries: Map<String, AppEntry>,
    dock: List<AppEntry>,
    nowMillis: Long,
    prefs: HomePrefs,
    actions: HomeActions,
    ipad: IpadActions,
    scrollToTop: Int,
    editing: Boolean,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    var openFolder by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    var dockBounds by remember { mutableStateOf<Rect?>(null) }
    val jiggle = if (editing && animate) rememberJiggle() else null
    val folder = layout.items.firstOrNull { it.id == openFolder } as? FolderItem
    BackHandler(enabled = folder != null || picking) {
        openFolder = null
        picking = false
    }
    Box(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val margin = if (maxWidth > maxHeight) WIDE_MARGIN else NARROW_MARGIN
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Header(editing, prefs, nowMillis, actions, margin, onAdd = { picking = true }, onDone = { ipad.onEditing(false) })
                GridArea(
                    layout, entries, nowMillis, actions, ipad, scrollToTop, editing, jiggle, margin, dockBounds,
                    onOpenFolder = { openFolder = it },
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp),
                )
                SearchPill(!editing, ipad.onSearch)
                Spacer(Modifier.height(16.dp))
                Dock(dock, actions, ipad, editing) { dockBounds = it }
                Spacer(Modifier.height(BOTTOM_MARGIN))
            }
        }
        FolderOverlay(
            folder,
            entries,
            actions,
            onRename = { name -> openFolder?.let { id -> ipad.onLayout { it.renameFolder(id, name) } } },
            onMenu = ipad.onMenu,
            onClose = { openFolder = null },
        )
        WidgetPicker(
            picking,
            novaWidgetChoices(ipad) { picking = false },
            appWidgetChoices(actions, ipad) { picking = false },
        ) { picking = false }
    }
}

@Composable
private fun novaWidgetChoices(
    ipad: IpadActions,
    close: () -> Unit,
): List<WidgetChoice> {
    val labels =
        listOf(
            WidgetKind.CLOCK to stringResource(R.string.widget_clock),
            WidgetKind.CALENDAR to stringResource(R.string.widget_calendar),
            WidgetKind.BATTERY to stringResource(R.string.widget_battery),
            WidgetKind.NOTE to stringResource(R.string.widget_note),
            WidgetKind.WEATHER to stringResource(R.string.widget_weather),
            WidgetKind.POMODORO to stringResource(R.string.widget_pomodoro),
            WidgetKind.MUSIC to stringResource(R.string.widget_music),
        )
    return labels.map { (kind, label) ->
        WidgetChoice(kind.name, label, "${kind.cols} × ${kind.rows}") {
            ipad.onLayout { it.add(WidgetItem(newId(), kind)) }
            close()
        }
    }
}

@Composable
private fun appWidgetChoices(
    actions: HomeActions,
    ipad: IpadActions,
    close: () -> Unit,
): List<WidgetChoice> {
    val host = actions.widgets ?: return emptyList()
    return remember(host) { host.providers() }.map { p ->
        WidgetChoice(p.id, p.label, "${p.cols} × ${p.rows}") {
            host.add(p) { ref, cols, rows -> ipad.onLayout { it.add(WidgetItem(newId(), WidgetKind.EXTERNAL, cols, rows, ref)) } }
            close()
        }
    }
}

private fun newId() = UUID.randomUUID().toString().take(ID_LENGTH)

/** In alto: ora e impostazioni; in modifica «+» per i widget e «Fine». */
@Composable
private fun Header(
    editing: Boolean,
    prefs: HomePrefs,
    nowMillis: Long,
    actions: HomeActions,
    margin: Dp,
    onAdd: () -> Unit,
    onDone: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(start = margin, end = margin / 2, top = TOP_MARGIN), verticalAlignment = Alignment.Top) {
        if (editing) {
            GlassButton("+", stringResource(R.string.home_edit_add), onAdd)
            Spacer(Modifier.weight(1f))
            GlassButton(stringResource(R.string.home_edit_done), null, onDone)
        } else {
            if (prefs.showClock) ClockHeader(nowMillis, compact = true)
            Spacer(Modifier.weight(1f))
            LocalOpenControl.current?.let { ControlButton(it, Modifier.padding(end = 10.dp)) }
            SettingsButton(actions.onOpenSettings)
        }
    }
}

/** Le pagine con gli elementi, il gesto di trascinamento in modifica e i puntini. */
@Suppress("LongMethod") // misure, trascinamento e pager condividono lo stesso stato: separarli non lo renderebbe più chiaro
@Composable
private fun GridArea(
    layout: HomeLayout,
    entries: Map<String, AppEntry>,
    nowMillis: Long,
    actions: HomeActions,
    ipad: IpadActions,
    scrollToTop: Int,
    editing: Boolean,
    jiggle: State<Float>?,
    margin: Dp,
    dockBounds: Rect?,
    onOpenFolder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val metrics = GridMetrics.of((maxWidth - margin * 2).value, (maxHeight - DOTS_AREA).value)
        val pages = remember(layout.items, metrics) { pack(layout.items, metrics.columns, metrics.rows) }
        val pager = rememberPagerState(pageCount = { pages.size })
        val cellW = (maxWidth - margin * 2) / metrics.columns
        val cellH = (maxHeight - DOTS_AREA) / metrics.rows
        val drag = remember { GridDragState() }
        var origin by remember { mutableStateOf(Offset.Zero) }
        LaunchedEffect(scrollToTop) { if (scrollToTop > 0) pager.animateScrollToPage(0) }
        // Rettangoli (in pixel, nel riquadro del pager) degli elementi della pagina mostrata.
        val rects by rememberUpdatedState(
            pages.getOrNull(pager.currentPage).orEmpty().map { p ->
                with(density) {
                    val left = margin.toPx() + cellW.toPx() * p.col
                    val top = cellH.toPx() * p.row
                    p.item to Rect(left, top, left + cellW.toPx() * p.cols, top + cellH.toPx() * p.rows)
                }
            },
        )
        val currentLayout by rememberUpdatedState(layout)
        val currentDock by rememberUpdatedState(dockBounds)
        val nextFirst by rememberUpdatedState(pages.getOrNull(pager.currentPage + 1)?.firstOrNull()?.item?.id)
        val defaultName = stringResource(R.string.home_folder_default)
        val width = with(density) { (maxWidth).toPx() }
        EdgePaging(drag, pager, width, density.run { EDGE.toPx() })
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { origin = it.positionInRoot() }
                    .gridDrag(editing, drag, hit = { pos -> rects.firstOrNull { it.second.contains(pos) }?.first?.id }) { id, pos ->
                        val dragged = currentLayout.items.firstOrNull { it.id == id } ?: return@gridDrag
                        val dockRect = currentDock
                        if (dragged is AppItem && dockRect != null && dockRect.contains(origin + pos)) {
                            entries[id]?.let { actions.onToggleFavorite(it) }
                            return@gridDrag
                        }
                        when (val a = dropAction(dragged, pos, rects, currentLayout.items, nextFirst)) {
                            DropAction.None -> Unit
                            is DropAction.Merge -> ipad.onLayout { it.merge(a.draggedKey, a.ontoId, newId(), defaultName) }
                            is DropAction.Move -> ipad.onLayout { it.move(a.id, a.beforeId) }
                        }
                        view.perform(NovaHaptic.SELECTION)
                    },
            ) {
                HorizontalPager(pager, Modifier.fillMaxSize(), key = { it }) { index ->
                    GridPage(
                        pages.getOrElse(index) {
                            emptyList()
                        },
                        cellW, cellH, margin, entries, nowMillis, actions, ipad, editing, jiggle, drag, onOpenFolder,
                    )
                }
            }
            PageDots(pager, Modifier.fillMaxWidth().height(DOTS_AREA))
        }
    }
}

/** Trascinando un elemento fino al bordo, dopo un attimo si passa alla pagina vicina. */
@Composable
private fun EdgePaging(
    drag: GridDragState,
    pager: PagerState,
    widthPx: Float,
    edgePx: Float,
) {
    val side by remember(drag, widthPx, edgePx) {
        derivedStateOf {
            when {
                drag.id == null -> 0
                drag.pointer.x < edgePx -> -1
                drag.pointer.x > widthPx - edgePx -> 1
                else -> 0
            }
        }
    }
    LaunchedEffect(side) {
        while (side != 0) {
            kotlinx.coroutines.delay(EDGE_DWELL_MS)
            pager.animateScrollToPage((pager.currentPage + side).coerceIn(0, pager.pageCount - 1))
        }
    }
}

@Composable
private fun GridPage(
    placements: List<Placement>,
    cellW: Dp,
    cellH: Dp,
    margin: Dp,
    entries: Map<String, AppEntry>,
    nowMillis: Long,
    actions: HomeActions,
    ipad: IpadActions,
    editing: Boolean,
    jiggle: State<Float>?,
    drag: GridDragState,
    onOpenFolder: (String) -> Unit,
) {
    Box(
        Modifier.fillMaxSize().pointerInput(editing) {
            if (!editing) detectTapGestures(onLongPress = { ipad.onEditing(true) })
        },
    ) {
        placements.forEach { p ->
            key(p.item.id) {
                val dragging = drag.id == p.item.id
                Box(
                    Modifier
                        .offset(margin + cellW * p.col, cellH * p.row)
                        .size(cellW * p.cols, cellH * p.rows)
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            if (drag.id == p.item.id) {
                                translationX = drag.delta.x
                                translationY = drag.delta.y
                                scaleX = DRAG_SCALE
                                scaleY = DRAG_SCALE
                                alpha = DRAG_ALPHA
                            }
                        },
                ) { ItemContent(p.item, entries, nowMillis, actions, ipad, editing, jiggle, onOpenFolder) }
            }
        }
    }
}

@Composable
private fun ItemContent(
    item: HomeItem,
    entries: Map<String, AppEntry>,
    nowMillis: Long,
    actions: HomeActions,
    ipad: IpadActions,
    editing: Boolean,
    jiggle: State<Float>?,
    onOpenFolder: (String) -> Unit,
) {
    val remove = { ipad.onLayout { it.remove(item.id) } }
    when (item) {
        is AppItem -> entries[item.key]?.let { AppCell(it, actions, editing, jiggle, ipad.onMenu, remove) }
        is FolderItem ->
            FolderCell(
                item,
                item.keys.mapNotNull(entries::get),
                actions,
                editing,
                jiggle,
                onOpen = {
                    onOpenFolder(item.id)
                },
                onEdit = { ipad.onEditing(true) },
                onRemove = remove,
            )
        is WidgetItem ->
            WidgetCell(
                editing,
                jiggle,
                item.id,
                onRemove = {
                    if (item.kind == WidgetKind.EXTERNAL) actions.widgets?.delete(item.ref)
                    remove()
                },
            ) {
                WidgetContent(item, nowMillis, actions, ipad)
            }
    }
}

/** Il contenuto di un widget, secondo il suo tipo. */
@Composable
private fun WidgetContent(
    item: WidgetItem,
    nowMillis: Long,
    actions: HomeActions,
    ipad: IpadActions,
) {
    when (item.kind) {
        WidgetKind.CLOCK -> ClockWidget(nowMillis)
        WidgetKind.CALENDAR -> CalendarWidget(nowMillis)
        WidgetKind.BATTERY -> BatteryWidget()
        WidgetKind.NOTE -> NoteWidget(item.text) { text -> ipad.onLayout { it.setNote(item.id, text) } }
        WidgetKind.WEATHER -> WeatherWidget(item.text, actions.weather) { city -> ipad.onLayout { it.setNote(item.id, city) } }
        WidgetKind.MUSIC -> MusicWidget(actions.media)
        WidgetKind.POMODORO -> PomodoroWidget(item.text) { state -> ipad.onLayout { it.setNote(item.id, state) } }
        WidgetKind.EXTERNAL ->
            AndroidView(
                factory = { ctx -> actions.widgets?.view(ctx, item.ref) ?: android.view.View(ctx) },
                modifier = Modifier.fillMaxSize(),
            )
    }
}

@Composable
private fun rememberJiggle(): State<Float> =
    rememberInfiniteTransition(label = "jiggle").animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(JIGGLE_MS, easing = LinearEasing), RepeatMode.Reverse),
        label = "jiggle-value",
    )

/** Puntini della pagina: quello attuale è pieno, gli altri tenui; cambiano con una molla. */
@Composable
private fun PageDots(
    pager: PagerState,
    modifier: Modifier = Modifier,
) {
    val count = pager.pageCount
    if (count < 2) {
        Spacer(modifier)
        return
    }
    val look = LocalHomeLook.current
    val description = stringResource(R.string.home_page, pager.currentPage + 1, count)
    Row(
        modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val colour by animateColorAsState(
                look.ink.copy(alpha = if (i == pager.currentPage) DOT_ACTIVE else DOT_IDLE),
                spring(stiffness = DOT_STIFFNESS),
                label = "dot",
            )
            Box(Modifier.padding(horizontal = DOT_GAP).size(DOT).background(colour, CircleShape))
        }
    }
}

private val WIDE_MARGIN = 72.dp
private val NARROW_MARGIN = 28.dp
private val TOP_MARGIN = 28.dp
private val BOTTOM_MARGIN = 20.dp
private val DOTS_AREA = 28.dp
private val DOT = 7.dp
private val DOT_GAP = 5.dp
private val EDGE = 48.dp
private const val ID_LENGTH = 8
private const val JIGGLE_MS = 120
private const val EDGE_DWELL_MS = 650L
private const val DRAG_SCALE = 1.08f
private const val DRAG_ALPHA = 0.92f
private const val DOT_ACTIVE = 0.92f
private const val DOT_IDLE = 0.32f
private const val DOT_STIFFNESS = 500f
