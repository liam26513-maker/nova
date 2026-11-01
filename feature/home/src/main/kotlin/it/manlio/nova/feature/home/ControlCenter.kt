@file:Suppress("MagicNumber") // disegno dei simboli e misure: sono il progetto grafico

package it.manlio.nova.feature.home

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
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
internal class ControlSheetState(
    private val scope: CoroutineScope,
) {
    var progress by mutableFloatStateOf(0f)
        private set

    /** Quanto deve scorrere il pannello per passare da chiuso ad aperto (altezza + margine): il dito lo segue 1 a 1. */
    var travel = 1f
        private set
    var measured = false
        private set

    /** Dove sta andando: serve a non far ripartire l'animazione (e perdere la velocità) quando la scelta è già fatta. */
    var target = false
        private set
    private var job: Job? = null

    fun measure(px: Float) {
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
                val spec = spring<Float>(dampingRatio = SHEET_DAMPING, stiffness = SHEET_STIFFNESS)
                animate(progress, if (open) 1f else 0f, velocity, spec) { v, _ -> progress = v.coerceIn(0f, 1f) }
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
    onClose: () -> Unit,
    onSettled: (Boolean) -> Unit,
) {
    val shown by remember(sheet) { derivedStateOf { sheet.progress > 0f } }
    if (!shown || controls == null) return
    val look = LocalHomeLook.current
    val flat = Nova.tokens.graphicsLevel == GraphicsLevel.PERFORMANCE
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
                }.background(if (look.dark) CARD else CARD_LIGHT, SquircleShape(CARD_RADIUS))
                .glass(SquircleShape(CARD_RADIUS), look.dark, flat)
                .closeByDragging(sheet, onSettled),
        ) {
            val torchOn = controls.torch?.on?.collectAsState()?.value ?: false
            val tiles =
                buildList {
                    // I riquadri che portano alle impostazioni di Android chiudono la tendina: al ritorno la Home è pulita.
                    val leave = { panel: SystemPanel ->
                        controls.openPanel(panel)
                        onClose()
                    }
                    add(Tile(ControlGlyph.WIFI, stringResource(R.string.control_internet)) { leave(SystemPanel.INTERNET) })
                    add(Tile(ControlGlyph.BLUETOOTH, stringResource(R.string.control_bluetooth)) { leave(SystemPanel.BLUETOOTH) })
                    add(Tile(ControlGlyph.MOON, stringResource(R.string.control_dnd)) { leave(SystemPanel.DND) })
                    controls.torch?.let { torch ->
                        add(Tile(ControlGlyph.TORCH, stringResource(R.string.control_torch), torchOn) { torch.toggle() })
                    }
                    add(Tile(ControlGlyph.LAYERS, controls.graphicsLabel()) { controls.onCycleGraphics() })
                    controls.onTurbo?.let { turbo -> add(Tile(ControlGlyph.BOLT, stringResource(R.string.control_turbo)) { turbo() }) }
                    onEditHome?.let { edit -> add(Tile(ControlGlyph.EDIT, stringResource(R.string.control_edit_home)) { edit() }) }
                    add(Tile(ControlGlyph.SETTINGS, stringResource(R.string.control_settings)) { onSettings() })
                }
            Column(
                Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                tiles.chunked(TILES_PER_ROW).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { tile -> TileView(tile, Modifier.weight(1f)) }
                        repeat(TILES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                controls.volume?.let { SliderRow(ControlGlyph.VOLUME, stringResource(R.string.control_volume), it) }
                if (controls.brightness != null) {
                    SliderRow(ControlGlyph.SUN, stringResource(R.string.control_brightness), controls.brightness)
                } else {
                    GrantRow(controls.onGrantBrightness)
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
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
        if (change == null || !change.pressed) break
        val move = change.positionChange()
        tracker.addPosition(change.uptimeMillis, change.position)
        if (started) {
            change.consume()
            sheet.drag(move.y)
            continue
        }
        dx += move.x
        dy += move.y
        if (abs(dx) <= slop && abs(dy) <= slop) continue
        if (dy <= abs(dx)) return null
        started = true
        change.consume()
        sheet.drag(dy)
    }
    return if (started) tracker.calculateVelocity().y / sheet.travel else null
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

/**
 * Una riga con simbolo e cursore. Il valore vive qui e si legge solo al disegno; il sistema lo riceve al massimo
 * ~25 volte al secondo mentre si trascina (e sempre l'ultimo valore al rilascio), così il dito non aspetta mai Android.
 */
@Composable
private fun SliderRow(
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

@Suppress("LongMethod", "CyclomaticComplexMethod") // un ramo per simbolo: lungo ma lineare
private fun DrawScope.drawControlGlyph(
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
private val SLIDER: Dp = 44.dp
private val PANEL_MARGIN = 12.dp
private val OFFSCREEN_MARGIN = 120.dp
private val HANDLE_WIDTH = 44.dp
private val HANDLE_HEIGHT = 5.dp
private val CORNER_WIDTH = 280.dp
private val CORNER_HEIGHT = 120.dp
private val DEFAULT_TRAVEL = 520.dp
private val CARD = Color(0xE61D2127)
private val CARD_LIGHT = Color(0xEBF7F6F3)
private val CARD_RADIUS = 34.dp
private const val TILES_PER_ROW = 4
private const val DIM = 0.45f
private const val FADE_IN_SPEED = 3f
private const val PRESSED_SCALE = 1.04f
private const val SHEET_DAMPING = 0.9f
private const val SHEET_STIFFNESS = 700f
private const val WRITE_GAP_MS = 40L
