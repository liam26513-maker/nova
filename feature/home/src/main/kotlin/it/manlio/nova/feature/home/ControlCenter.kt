@file:Suppress("MagicNumber") // disegno dei simboli e misure: sono il progetto grafico

package it.manlio.nova.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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

private class Tile(
    val glyph: ControlGlyph,
    val label: String,
    val active: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Centro di controllo (SPEC §6.8): riquadri e due cursori su una scheda di vetro.
 * Torcia, volume e luminosità agiscono subito; Wi-Fi, Bluetooth e non disturbare aprono il pannello di Android
 * (un'app normale non può cambiarli da sola); livello grafico gira tra Automatico, Massimo, Bilanciato e Prestazioni.
 */
@Composable
internal fun ControlCenter(
    visible: Boolean,
    controls: ControlActions?,
    onSettings: () -> Unit,
    onEditHome: () -> Unit,
    onClose: () -> Unit,
) {
    GlassDialog(visible && controls != null, onClose, CONTROL_WIDTH) {
        if (controls == null) return@GlassDialog
        val torchOn = controls.torch?.on?.collectAsState()?.value ?: false
        val tiles =
            buildList {
                add(Tile(ControlGlyph.WIFI, stringResource(R.string.control_internet)) { controls.openPanel(SystemPanel.INTERNET) })
                add(Tile(ControlGlyph.BLUETOOTH, stringResource(R.string.control_bluetooth)) { controls.openPanel(SystemPanel.BLUETOOTH) })
                add(Tile(ControlGlyph.MOON, stringResource(R.string.control_dnd)) { controls.openPanel(SystemPanel.DND) })
                controls.torch?.let {
                        torch ->
                    add(Tile(ControlGlyph.TORCH, stringResource(R.string.control_torch), torchOn) { torch.toggle() })
                }
                add(Tile(ControlGlyph.LAYERS, controls.graphicsLabel()) { controls.onCycleGraphics() })
                add(Tile(ControlGlyph.EDIT, stringResource(R.string.control_edit_home)) { onEditHome() })
                add(Tile(ControlGlyph.SETTINGS, stringResource(R.string.control_settings)) { onSettings() })
            }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
        }
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

@Composable
private fun SliderRow(
    glyph: ControlGlyph,
    label: String,
    level: Level,
) {
    val look = LocalHomeLook.current
    var value by remember(level) { mutableFloatStateOf(level.read().coerceIn(0f, 1f)) }
    val change = { v: Float ->
        value = v.coerceIn(0f, 1f)
        level.write(value)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(Modifier.size(24.dp)) { drawControlGlyph(glyph, look.muted) }
        GlassSlider(value, label, change, Modifier.weight(1f))
    }
}

/** Cursore a barra: si trascina o si tocca; anche per TalkBack (`setProgress`). */
@Composable
private fun GlassSlider(
    value: Float,
    label: String,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = LocalHomeLook.current
    var width by remember { mutableFloatStateOf(1f) }
    Box(
        modifier
            .height(SLIDER)
            .background(look.ink.copy(alpha = 0.16f), CircleShape)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f)
                setProgress {
                    onChange(it)
                    true
                }
            }.pointerInput(Unit) { detectTapGestures { onChange(it.x / width) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> onChange(change.position.x / width) } }
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) },
    ) {
        Box(Modifier.fillMaxSize(fraction = value.coerceAtLeast(0.04f)).background(look.ink.copy(alpha = 0.85f), CircleShape))
    }
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

/** Pulsante tondo del centro di controllo, accanto alle impostazioni (quattro riquadri disegnati). */
@Composable
internal fun ControlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = LocalHomeLook.current
    val view = LocalView.current
    val label = stringResource(R.string.control_open)
    Box(
        modifier
            .size(48.dp)
            .background(look.ink.copy(alpha = 0.12f), CircleShape)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                view.perform(NovaHaptic.TAP)
                onClick()
            }.semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) {
            val s = size.width * 0.42f
            listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f).forEach { (x, y) ->
                drawRoundRect(
                    look.ink,
                    Offset((size.width - s) * x, (size.height - s) * y),
                    Size(s, s),
                    androidx.compose.ui.geometry.CornerRadius(s * 0.3f),
                )
            }
        }
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
private val SLIDER: Dp = 40.dp
private const val TILES_PER_ROW = 4
