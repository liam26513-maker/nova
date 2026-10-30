package it.manlio.nova.core.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Linguaggio visivo delle schermate di sistema (impostazioni, procedura guidata, stato):
 * calmo, moderno, minimale. Sfondo neutro con una luce appena percettibile, testo ben
 * spaziato, gruppi "inset" traslucidi, nessun colore acceso tranne l'accento sulle scelte.
 */

/** Sfondo calmo: gradiente neutro quasi piatto con due luci morbide e desaturate. */
@Composable
fun rememberCalmBackdrop(): ImageBitmap {
    val dark = Nova.tokens.dark
    val cfg = LocalConfiguration.current
    val density = LocalDensity.current
    // A piena risoluzione: MaterialSurface lo allinea pixel per pixel dietro ai pannelli.
    val w = with(density) { cfg.screenWidthDp.dp.roundToPx() }.coerceAtLeast(1)
    val h = with(density) { cfg.screenHeightDp.dp.roundToPx() }.coerceAtLeast(1)
    return remember(w, h, dark) { calmBackdrop(w, h, dark) }
}

internal fun calmBackdrop(
    w: Int,
    h: Int,
    dark: Boolean,
): ImageBitmap {
    val p = if (dark) CalmPalette.Dark else CalmPalette.Light
    return ImageBitmap(w, h).also { img ->
        val canvas = Canvas(img)
        val fw = w.toFloat()
        val fh = h.toFloat()
        canvas.drawRect(
            0f,
            0f,
            fw,
            fh,
            Paint().apply { shader = LinearGradientShader(Offset.Zero, Offset(0f, fh), listOf(p.top, p.bottom)) },
        )
        listOf(
            Triple(Offset(fw * 0.15f, fh * 0.1f), fw * 0.7f, p.glowA),
            Triple(Offset(fw * 0.95f, fh * 0.9f), fw * 0.6f, p.glowB),
        ).forEach { (centre, radius, colour) ->
            val paint = Paint().apply { shader = RadialGradientShader(centre, radius, listOf(colour, colour.copy(alpha = 0f))) }
            canvas.drawRect(0f, 0f, fw, fh, paint)
        }
    }
}

private data class CalmPalette(
    val top: Color,
    val bottom: Color,
    val glowA: Color,
    val glowB: Color,
) {
    companion object {
        /** Carta calda e pietra, con luci salvia e nebbia appena accennate. */
        val Light = CalmPalette(Color(0xFFF7F6F3), Color(0xFFEDECE8), Color(0x33B9C7BE), Color(0x2EB8C2CC))

        /** Grafite profonda con riflessi freddi minimi. */
        val Dark = CalmPalette(Color(0xFF111214), Color(0xFF0B0B0D), Color(0x1F6E7F78), Color(0x1A5E6C80))
    }
}

/**
 * Impalcatura di ogni schermata di sistema: sfondo calmo, titolo grande e leggero,
 * contenuto centrato in una colonna leggibile e scorrevole.
 */
@Composable
fun SettingsScaffold(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.(backdrop: ImageBitmap) -> Unit,
) {
    val t = Nova.tokens
    val backdrop = rememberCalmBackdrop()
    Box(Modifier.fillMaxSize().background(t.colors.background)) {
        Image(backdrop, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .safeDrawingPadding()
                .padding(horizontal = NovaSpacing.l, vertical = NovaSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = READABLE_WIDTH).fillMaxWidth()) {
                BasicText(title, style = TextStyle(t.colors.onSurface, 34.sp, FontWeight.Light, letterSpacing = (-0.5).sp))
                if (subtitle != null) {
                    Spacer(Modifier.height(NovaSpacing.xs))
                    BasicText(subtitle, style = TextStyle(t.colors.onSurfaceMuted, 15.sp, lineHeight = 22.sp))
                }
                Spacer(Modifier.height(NovaSpacing.xl))
                Column(verticalArrangement = Arrangement.spacedBy(NovaSpacing.xl)) { content(backdrop) }
            }
        }
    }
}

/** Gruppo di voci con intestazione discreta, su pannello traslucido. */
@Composable
fun SettingsSection(
    backdrop: ImageBitmap,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = Nova.tokens
    Column {
        if (header != null) {
            BasicText(
                header.uppercase(),
                style = TextStyle(t.colors.onSurfaceMuted, 12.sp, FontWeight.Medium, letterSpacing = 1.2.sp),
                modifier = Modifier.padding(start = NovaSpacing.m, bottom = NovaSpacing.xs),
            )
        }
        MaterialSurface(backdrop, Modifier.fillMaxWidth(), shape = SquircleShape(t.radii.large)) {
            Column(Modifier.padding(vertical = NovaSpacing.xxs), content = content)
        }
        if (footer != null) {
            BasicText(
                footer,
                style = TextStyle(t.colors.onSurfaceMuted, 13.sp, lineHeight = 18.sp),
                modifier = Modifier.padding(start = NovaSpacing.m, end = NovaSpacing.m, top = NovaSpacing.xs),
            )
        }
    }
}

/** Una voce: titolo, descrizione facoltativa e un elemento a destra (valore, interruttore…). */
@Composable
fun SettingsRow(
    title: String,
    summary: String? = null,
    showDivider: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val t = Nova.tokens
    Column {
        if (showDivider) Hairline(Modifier.padding(start = NovaSpacing.m))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = NovaSpacing.m, vertical = NovaSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                BasicText(title, style = TextStyle(t.colors.onSurface, 17.sp))
                if (summary != null) {
                    BasicText(summary, style = TextStyle(t.colors.onSurfaceMuted, 14.sp, lineHeight = 19.sp))
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(NovaSpacing.m))
                trailing()
            }
        }
    }
}

/** Contenuto libero dentro una sezione, con i margini delle voci. */
@Composable
fun SettingsBlock(
    showDivider: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        if (showDivider) Hairline(Modifier.padding(start = NovaSpacing.m))
        Column(Modifier.fillMaxWidth().padding(horizontal = NovaSpacing.m, vertical = NovaSpacing.s), content = content)
    }
}

@Composable
private fun Hairline(modifier: Modifier = Modifier) {
    val t = Nova.tokens
    Box(modifier.fillMaxWidth().height(HAIRLINE).background(t.colors.onSurface.copy(alpha = 0.08f)))
}

/**
 * Selettore segmentato: un "cuscinetto" che scivola con una molla sotto l'opzione scelta.
 * Tutte le opzioni hanno la stessa larghezza.
 */
@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = Nova.tokens
    val view = LocalView.current
    val track = t.colors.onSurface.copy(alpha = if (t.dark) 0.10f else 0.06f)
    val thumb = if (t.dark) Color(0xFF3A3A3C) else Color.White
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(SEGMENT_HEIGHT)
            .background(track, SquircleShape(t.radii.medium))
            .padding(SEGMENT_INSET),
    ) {
        val segment: Dp = maxWidth / options.size
        val index = options.indexOf(selected).coerceAtLeast(0)
        val thumbOffset by animateDpAsState(segment * index, t.motion.spring(), label = "segment-thumb")
        Box(
            Modifier
                .offset(x = thumbOffset)
                .fillMaxHeight()
                .widthIn(min = segment, max = segment)
                .background(thumb, SquircleShape(t.radii.medium - SEGMENT_INSET)),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEach { option ->
                val isSelected = option == selected
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale by animateFloatAsState(if (pressed) 0.96f else 1f, t.motion.spring(), label = "segment-press")
                val colour by animateColorAsState(
                    if (isSelected) t.colors.onSurface else t.colors.onSurfaceMuted,
                    t.motion.spring(),
                    label = "segment-text",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .scale(scale)
                        .semantics { this.selected = isSelected }
                        .clickable(interaction, indication = null, role = Role.Tab) {
                            if (!isSelected) view.perform(NovaHaptic.SELECTION)
                            onSelect(option)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        label(option),
                        style = TextStyle(colour, 15.sp, if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
                    )
                }
            }
        }
    }
}

private val READABLE_WIDTH = 640.dp
private val HAIRLINE = 0.5.dp
private val SEGMENT_HEIGHT = 40.dp
private val SEGMENT_INSET = 3.dp
