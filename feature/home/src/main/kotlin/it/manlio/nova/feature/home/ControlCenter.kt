@file:Suppress("MagicNumber") // disegno dei simboli e misure: sono il progetto grafico

package it.manlio.nova.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.GraphicsLevel
import it.manlio.nova.core.design.Nova
import it.manlio.nova.core.design.NovaHaptic
import it.manlio.nova.core.design.SquircleShape
import it.manlio.nova.core.design.perform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private class Tile(
    val glyph: ControlGlyph,
    val label: String,
    val active: Boolean = false,
    val onClick: () -> Unit,
)

/** Scelte pure del gesto e del cursore: stanno qui, senza Compose, per poterle provare con un test normale. */
internal object ControlSheetMath {
    /** Sopra questa velocità (altezze del pannello al secondo) la tendina segue il gesto, anche se è a meno di metà. */
    const val FLING = 1.2f

    /** Dove si ferma la tendina al rilascio: [velocity] in "pannelli al secondo", positiva = verso l'apertura. */
    fun shouldOpen(
        progress: Float,
        velocity: Float,
    ): Boolean = if (abs(velocity) > FLING) velocity > 0f else progress > 0.5f

    /** Il valore del cursore, tenuto tra 0 e 1 e agganciato ai gradini se il sistema ne ha (volume). */
    fun snap(
        value: Float,
        steps: Int,
    ): Float {
        val v = value.coerceIn(0f, 1f)
        return if (steps > 0) (v * steps).roundToInt().toFloat() / steps else v
    }
}

/**
 * Lo stato della tendina del centro di controllo: [progress] va da 0 (chiusa) a 1 (aperta) e segue il dito
 * mentre si trascina. Si legge solo nella fase di disegno, così durante il gesto non si ricompone niente.
 */
@Stable
class ControlSheetState(
    private val scope: CoroutineScope,
) {
    var progress by mutableFloatStateOf(0f)
        private set

    /** Quanto deve scorrere il pannello per passare da chiuso ad aperto (altezza + margine): il dito lo segue 1 a 1. */
    var travel = 1f
        private set
    private var measured = false

    /** Dove sta andando: serve a non far ripartire l'animazione (e perdere la velocità) quando la scelta è già fatta. */
    var target = false
        private set
    private var job: Job? = null

    internal fun measure(px: Float) {
        travel = px.coerceAtLeast(1f)
        measured = true
    }

    fun estimate(px: Float) {
        if (!measured) travel = px
    }

    fun drag(deltaPx: Float) {
        job?.cancel()
        progress = (progress + deltaPx / travel).coerceIn(0f, 1f)
    }

    /** Al rilascio: decide dove fermarsi (metà o scatto) e ci va con la velocità del dito; dice se resta aperta. */
    fun release(velocity: Float): Boolean = ControlSheetMath.shouldOpen(progress, velocity).also { settle(it, velocity) }

    /** Porta la tendina in fondo con una molla, partendo dalla velocità del dito ([velocity] in pannelli al secondo). */
    fun settle(
        open: Boolean,
        velocity: Float = 0f,
    ) {
        target = open
        job?.cancel()
        job =
            scope.launch {
                val end = if (open) 1f else 0f
                if (coroutineContext[MonotonicFrameClock] == null) {
                    // Un'animazione senza orologio dei fotogrammi lancia un errore e chiude l'app: meglio arrivare in fondo di colpo.
                    progress = end
                } else {
                    val spec = spring<Float>(dampingRatio = SHEET_DAMPING, stiffness = SHEET_STIFFNESS)
                    animate(progress, end, velocity, spec) { v, _ -> progress = v.coerceIn(0f, 1f) }
                }
            }
    }
}

/**
 * Il centro di controllo (SPEC §6.8) come tendina: si tira giù dall'angolo in alto a destra (come sull'iPad) e il
 * pannello segue il dito; si richiude tirando su, toccando fuori o con Indietro. Torcia, volume e luminosità agiscono
 * subito; Wi-Fi, Bluetooth e non disturbare aprono il pannello di Android (un'app normale non può cambiarli da sola);
 * il livello grafico gira tra Automatico, Massimo, Bilanciato e Prestazioni.
 * Da chiuso non è nemmeno nella composizione: non costa niente.
 */
@Composable
@Suppress("LongMethod") // pannello + gesto di chiusura: le parti sono già separate in funzioni
internal fun ControlCenter(
    sheet: ControlSheetState,
    controls: ControlActions?,
    onSettings: () -> Unit,
    onEditHome: (() -> Unit)?,
    firstView: List<ControlTileId>,
    onClose: () -> Unit,
    onSettled: (Boolean) -> Unit,
) {
    val shown by remember(sheet) { derivedStateOf { sheet.progress > 0f } }
    if (!shown || controls == null) return
    val look = LocalHomeLook.current
    val flat = Nova.tokens.graphicsLevel == GraphicsLevel.PERFORMANCE
    // La scheda prende un velo dell'accento del tema: cambiando colori o sfondo, cambia anche lei.
    val card = remember(look) { lerp(if (look.dark) CARD else CARD_LIGHT, look.accent.copy(alpha = 1f), CARD_TINT) }
    val inset = with(LocalDensity.current) { OFFSCREEN_MARGIN.toPx() }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black.copy(alpha = DIM * sheet.progress)) }
                .pointerInput(onClose) { detectTapGestures { onClose() } },
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(PANEL_MARGIN)
                .widthIn(max = CONTROL_WIDTH)
                .onSizeChanged { sheet.measure(it.height + inset) }
                .graphicsLayer {
                    translationY = -(1f - sheet.progress) * (size.height + inset)
                    alpha = (sheet.progress * FADE_IN_SPEED).coerceAtMost(1f)
                }.background(card, SquircleShape(CARD_RADIUS))
                .glass(SquircleShape(CARD_RADIUS), look.dark, flat)
                .closeByDragging(sheet, onSettled),
        ) {
            val torchOn = controls.torch?.on?.collectAsState()?.value ?: false
            // I riquadri disponibili, nell'ordine fisso: quelli scelti in Personalizza la Home a prima vista, gli altri sotto «Altro».
            // I riquadri che portano alle impostazioni di Android chiudono la tendina: al ritorno la Home è pulita.
            val leave = { panel: SystemPanel ->
                controls.openPanel(panel)
                onClose()
            }
            val wifiOn = rememberToggleState(controls.toggles[SystemPanel.INTERNET])
            val btOn = rememberToggleState(controls.toggles[SystemPanel.BLUETOOTH])
            val dndOn = rememberToggleState(controls.toggles[SystemPanel.DND])
            val available =
                buildMap {
                    val wifiLabel = stringResource(R.string.control_internet)
                    val wifi = panelTile(ControlGlyph.WIFI, wifiLabel, SystemPanel.INTERNET, controls, wifiOn, leave)
                    val btLabel = stringResource(R.string.control_bluetooth)
                    val bluetooth = panelTile(ControlGlyph.BLUETOOTH, btLabel, SystemPanel.BLUETOOTH, controls, btOn, leave)
                    val dnd = panelTile(ControlGlyph.MOON, stringResource(R.string.control_dnd), SystemPanel.DND, controls, dndOn, leave)
                    put(ControlTileId.WIFI, wifi)
                    put(ControlTileId.BLUETOOTH, bluetooth)
                    put(ControlTileId.DND, dnd)
                    controls.torch?.let { torch ->
                        val label = stringResource(R.string.control_torch)
                        put(ControlTileId.TORCH, Tile(ControlGlyph.TORCH, label, torchOn) { torch.toggle() })
                    }
                    put(ControlTileId.GRAPHICS, Tile(ControlGlyph.LAYERS, controls.graphicsLabel()) { controls.onCycleGraphics() })
                    controls.onTurbo?.let { turbo ->
                        put(ControlTileId.TURBO, Tile(ControlGlyph.BOLT, stringResource(R.string.control_turbo)) { turbo() })
                    }
                    onEditHome?.let { edit ->
                        put(ControlTileId.EDIT_HOME, Tile(ControlGlyph.EDIT, stringResource(R.string.control_edit_home)) { edit() })
                    }
                    put(ControlTileId.SETTINGS, Tile(ControlGlyph.SETTINGS, stringResource(R.string.control_settings)) { onSettings() })
                }
            val essentials = ControlTileId.entries.filter { it in firstView }.mapNotNull(available::get)
            val nova = ControlTileId.entries.filter { it !in firstView }.mapNotNull(available::get)
            var expanded by remember { mutableStateOf(false) }
            Column(
                Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (essentials.isNotEmpty()) TileRows(essentials)
                controls.volume?.let { SliderRow(ControlGlyph.VOLUME, stringResource(R.string.control_volume), it) }
                if (controls.brightness != null) {
                    SliderRow(ControlGlyph.SUN, stringResource(R.string.control_brightness), controls.brightness)
                } else {
                    GrantRow(controls.onGrantBrightness)
                }
                MoreToggle(expanded) { expanded = !expanded }
                AnimatedVisibility(
                    expanded,
                    enter = expandVertically(spring(stiffness = SHEET_STIFFNESS)) + fadeIn(),
                    exit = shrinkVertically(spring(stiffness = SHEET_STIFFNESS)) + fadeOut(),
                ) {
                    Column(
                        Modifier.heightIn(max = MORE_MAX_HEIGHT).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (nova.isNotEmpty()) TileRows(nova)
                        Column { controls.more.forEach { MoreRow(it, onClose) } }
                    }
                }
                // La maniglia dice "si tira su per chiudere".
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(HANDLE_WIDTH, HANDLE_HEIGHT)
                        .background(look.ink.copy(alpha = 0.3f), CircleShape),
                )
            }
        }
    }
}

/**
 * Tirare su il pannello lo chiude: segue il dito e al rilascio decide (distanza o scatto).
 * Un cursore che sta trascinando consuma i movimenti, quindi qui non arrivano.
 */
private fun Modifier.closeByDragging(
    sheet: ControlSheetState,
    onSettled: (Boolean) -> Unit,
): Modifier =
    pointerInput(sheet) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            val first = awaitVerticalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
            if (first != null) {
                sheet.drag(first.positionChange().y)
                verticalDrag(first.id) { change ->
                    tracker.addPosition(change.uptimeMillis, change.position)
                    sheet.drag(change.positionChange().y)
                    change.consume()
                }
                onSettled(sheet.release(tracker.calculateVelocity().y / sheet.travel))
            }
        }
    }

/**
 * Il modo per aprire il centro di controllo senza trascinare: TalkBack e i comandi vocali non fanno il gesto dall'angolo.
 * Non si vede e non occupa spazio, ma c'è solo se il centro di controllo esiste.
 */
@Composable
internal fun ControlCenterEntry(
    enabled: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    val label = stringResource(R.string.control_open)
    Box(
        modifier
            .size(1.dp)
            .semantics {
                contentDescription = label
                onClick(label) {
                    onOpen()
                    true
                }
            },
    )
}

/**
 * Lo stato di un interruttore diretto, riletto a ogni apertura della tendina. Falso e inutilizzato se l'interruttore non c'è.
 */
@Composable
private fun rememberToggleState(toggle: Toggle?): MutableState<Boolean> = remember(toggle) { mutableStateOf(toggle?.read() ?: false) }

/**
 * Riquadro di Wi-Fi, Bluetooth o non disturbare. Con un interruttore diretto cambia subito (lo stato si vede senza aspettare
 * Android e poi si corregge se non ha obbedito); senza, apre la schermata di Android e chiude la tendina.
 */
private fun panelTile(
    glyph: ControlGlyph,
    label: String,
    panel: SystemPanel,
    controls: ControlActions,
    state: MutableState<Boolean>,
    leave: (SystemPanel) -> Unit,
): Tile {
    val toggle = controls.toggles[panel] ?: return Tile(glyph, label) { leave(panel) }
    return Tile(glyph, label, state.value) {
        val next = !state.value
        state.value = next
        toggle.set(next) { state.value = toggle.read() }
    }
}

/** I riquadri a righe di [TILES_PER_ROW]; l'ultima riga si completa con spazi per tenere le colonne allineate. */
@Composable
private fun TileRows(tiles: List<Tile>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        tiles.chunked(TILES_PER_ROW).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile -> TileView(tile, Modifier.weight(1f)) }
                repeat(TILES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** «Altro» con una freccia che si gira: apre e chiude il resto delle impostazioni. */
@Composable
private fun MoreToggle(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val look = LocalHomeLook.current
    val label = stringResource(R.string.control_more)
    val turn = animateFloatAsState(if (expanded) 180f else 0f, spring(stiffness = SHEET_STIFFNESS), label = "more-arrow")
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onToggle() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        BasicText(label, style = TextStyle(look.muted, 15.sp, FontWeight.Medium, fontFamily = look.family))
        Spacer(Modifier.width(6.dp))
        Canvas(Modifier.size(12.dp).graphicsLayer { rotationZ = turn.value }) {
            val w = size.width
            val line = Stroke(w * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(
                Path().apply {
                    moveTo(w * 0.1f, w * 0.3f)
                    lineTo(w * 0.5f, w * 0.7f)
                    lineTo(w * 0.9f, w * 0.3f)
                },
                look.muted,
                style = line,
            )
        }
    }
}

/** Una impostazione di Android sotto «Altro»: nome a sinistra, stato (se è un interruttore) a destra. */
@Composable
private fun MoreRow(
    item: MoreItem,
    onClose: () -> Unit,
) {
    val look = LocalHomeLook.current
    val view = LocalView.current
    var state by remember(item) { mutableStateOf(item.state) }
    LaunchedEffect(item) { item.live?.collect { state = it } }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                view.perform(NovaHaptic.TAP)
                val next = item.onClick { late -> state = late }
                if (next == null) onClose() else state = next
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            item.label,
            style = TextStyle(look.ink, 16.sp, fontFamily = look.family),
            modifier = Modifier.weight(1f),
        )
        state?.let { BasicText(it, style = TextStyle(look.muted, 14.sp, fontFamily = look.family)) }
    }
}

@Composable
private fun TileView(
    tile: Tile,
    modifier: Modifier,
) {
    val look = LocalHomeLook.current
    val view = LocalView.current
    val flat = Nova.tokens.graphicsLevel == GraphicsLevel.PERFORMANCE
    val ink = if (tile.active) Color.White else look.ink
    Column(
        modifier.clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
            view.perform(NovaHaptic.TAP)
            tile.onClick()
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(TILE).background(if (tile.active) look.accent else Color.Transparent, SquircleShape(TILE * 0.3f)).let {
                if (tile.active) it else it.glass(SquircleShape(TILE * 0.3f), look.dark, flat)
            },
            contentAlignment = Alignment.Center,
        ) { Canvas(Modifier.size(TILE * 0.5f)) { drawControlGlyph(tile.glyph, ink) } }
        Spacer(Modifier.height(6.dp))
        BasicText(
            tile.label,
            style = TextStyle(look.ink, 12.sp, FontWeight.Medium, textAlign = TextAlign.Center, fontFamily = look.family),
            maxLines = 2,
        )
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod") // un ramo per simbolo: lungo ma lineare
internal fun DrawScope.drawControlGlyph(
    glyph: ControlGlyph,
    ink: Color,
) {
    val u = size.minDimension
    val w = u * 0.1f
    val line = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun p(
        x: Float,
        y: Float,
    ) = Offset(x * u, y * u)
    when (glyph) {
        ControlGlyph.WIFI -> {
            listOf(0.25f, 0.47f, 0.69f).forEach { r ->
                drawArc(ink, 225f, 90f, false, p(0.5f - r, 0.82f - r), Size(2 * r * u, 2 * r * u), style = line)
            }
            drawCircle(ink, 0.07f * u, p(0.5f, 0.82f))
        }
        ControlGlyph.BLUETOOTH ->
            drawPath(
                Path().apply {
                    moveTo(0.28f * u, 0.3f * u)
                    lineTo(0.72f * u, 0.7f * u)
                    lineTo(0.5f * u, 0.9f * u)
                    lineTo(0.5f * u, 0.1f * u)
                    lineTo(0.72f * u, 0.3f * u)
                    lineTo(0.28f * u, 0.7f * u)
                },
                ink,
                style = line,
            )
        ControlGlyph.MOON -> {
            drawArc(ink, 60f, 240f, false, p(0.12f, 0.12f), Size(0.76f * u, 0.76f * u), style = line)
            drawArc(ink, 300f, -180f, false, p(0.36f, 0.12f), Size(0.5f * u, 0.76f * u), style = line)
        }
        ControlGlyph.TORCH -> {
            drawRoundRect(ink, p(0.32f, 0.08f), Size(0.36f * u, 0.3f * u), androidx.compose.ui.geometry.CornerRadius(0.06f * u), line)
            drawRoundRect(ink, p(0.4f, 0.38f), Size(0.2f * u, 0.54f * u), androidx.compose.ui.geometry.CornerRadius(0.06f * u), line)
        }
        ControlGlyph.LAYERS -> {
            drawCircle(ink, 0.27f * u, p(0.36f, 0.5f), style = line)
            drawCircle(ink, 0.27f * u, p(0.64f, 0.5f), style = line)
        }
        ControlGlyph.SETTINGS ->
            listOf(0.25f to 0.32f, 0.5f to 0.68f, 0.75f to 0.45f).forEach { (y, knob) ->
                drawLine(ink, p(0.08f, y), p(0.92f, y), w, StrokeCap.Round)
                drawCircle(ink, 0.1f * u, p(knob, y))
            }
        ControlGlyph.EDIT -> {
            listOf(0.15f, 0.58f).forEach {
                    x ->
                listOf(0.15f, 0.58f).forEach {
                        y ->
                    drawRoundRect(ink, p(x, y), Size(0.27f * u, 0.27f * u), androidx.compose.ui.geometry.CornerRadius(0.07f * u))
                }
            }
        }
        ControlGlyph.VOLUME -> {
            drawPath(
                Path().apply {
                    moveTo(0.1f * u, 0.38f * u)
                    lineTo(0.3f * u, 0.38f * u)
                    lineTo(0.55f * u, 0.15f * u)
                    lineTo(0.55f * u, 0.85f * u)
                    lineTo(0.3f * u, 0.62f * u)
                    lineTo(0.1f * u, 0.62f * u)
                    close()
                },
                ink,
            )
            drawArc(ink, -40f, 80f, false, p(0.45f, 0.3f), Size(0.4f * u, 0.4f * u), style = line)
        }
        ControlGlyph.BOLT ->
            drawPath(
                Path().apply {
                    moveTo(0.58f * u, 0.06f * u)
                    lineTo(0.2f * u, 0.56f * u)
                    lineTo(0.46f * u, 0.56f * u)
                    lineTo(0.38f * u, 0.94f * u)
                    lineTo(0.8f * u, 0.4f * u)
                    lineTo(0.54f * u, 0.4f * u)
                    close()
                },
                ink,
            )
        ControlGlyph.SUN -> {
            drawCircle(ink, 0.2f * u, p(0.5f, 0.5f))
            repeat(8) { i ->
                val a = i * Math.PI / 4
                drawLine(
                    ink,
                    p(0.5f + (0.32f * Math.cos(a)).toFloat(), 0.5f + (0.32f * Math.sin(a)).toFloat()),
                    p(0.5f + (0.45f * Math.cos(a)).toFloat(), 0.5f + (0.45f * Math.sin(a)).toFloat()),
                    w,
                    StrokeCap.Round,
                )
            }
        }
    }
}

private val CONTROL_WIDTH = 460.dp
private val TILE: Dp = 76.dp
private val PANEL_MARGIN = 12.dp
private val OFFSCREEN_MARGIN = 48.dp
private val HANDLE_WIDTH = 44.dp
private val HANDLE_HEIGHT = 5.dp
private val CARD = Color(0xE61D2127)
private val CARD_LIGHT = Color(0xEBF7F6F3)
private val CARD_RADIUS = 34.dp
private val ROW_HEIGHT = 44.dp
private val MORE_MAX_HEIGHT = 300.dp
private const val CARD_TINT = 0.1f
private const val TILES_PER_ROW = 4
private const val DIM = 0.45f
private const val FADE_IN_SPEED = 3f
private const val SHEET_DAMPING = 1f
internal const val SHEET_STIFFNESS = 1500f
