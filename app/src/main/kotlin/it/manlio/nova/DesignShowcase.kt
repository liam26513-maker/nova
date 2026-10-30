package it.manlio.nova

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.GraphicsLevel
import it.manlio.nova.core.design.MaterialSurface
import it.manlio.nova.core.design.Nova
import it.manlio.nova.core.design.NovaHaptic
import it.manlio.nova.core.design.NovaSpacing
import it.manlio.nova.core.design.SquircleShape
import it.manlio.nova.core.design.perform

/**
 * Schermata provvisoria della Fase 0: mostra il sistema di design e permette di
 * provare i tre livelli grafici sul tablet. La home vera arriva con la Fase 0, punto 11.
 */
@Composable
fun DesignShowcase(
    brandName: String,
    configVersion: String,
    level: GraphicsLevel,
    onPickLevel: (GraphicsLevel, Boolean) -> Unit,
) {
    val t = Nova.tokens
    val backdrop = rememberDemoWallpaper()
    Box(Modifier.fillMaxSize().background(t.colors.background)) {
        Image(backdrop, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        Column(
            Modifier.safeDrawingPadding().padding(NovaSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(NovaSpacing.l),
        ) {
            BasicText(brandName, style = TextStyle(t.colors.onSurface, 40.sp, FontWeight.Bold))
            MaterialSurface(backdrop, Modifier.width(520.dp)) {
                Column(Modifier.padding(NovaSpacing.l), verticalArrangement = Arrangement.spacedBy(NovaSpacing.s)) {
                    BasicText(stringResource(R.string.graphics_level_title), style = TextStyle(t.colors.onSurface, 20.sp, FontWeight.SemiBold))
                    BasicText(
                        stringResource(R.string.graphics_level_current, stringResource(level.label())),
                        style = TextStyle(t.colors.onSurfaceMuted, 15.sp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(NovaSpacing.xs)) {
                        Chip(stringResource(R.string.graphics_level_auto), selected = false) { onPickLevel(level, false) }
                        GraphicsLevel.entries.forEach { l ->
                            Chip(stringResource(l.label()), selected = l == level) { onPickLevel(l, true) }
                        }
                    }
                }
            }
            MaterialSurface(backdrop, Modifier.width(520.dp)) {
                Column(Modifier.padding(NovaSpacing.l), verticalArrangement = Arrangement.spacedBy(NovaSpacing.xs)) {
                    BasicText(stringResource(R.string.status_title), style = TextStyle(t.colors.onSurface, 20.sp, FontWeight.SemiBold))
                    BasicText(stringResource(R.string.status_config, configVersion), style = TextStyle(t.colors.onSurfaceMuted, 15.sp))
                    BasicText(stringResource(R.string.status_modules_none), style = TextStyle(t.colors.onSurfaceMuted, 15.sp))
                }
            }
        }
    }
}

@Composable
private fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = Nova.tokens
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Feedback entro un frame dal tocco, con molla interrompibile (SPEC §5.2).
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, t.motion.spring(), label = "chip-scale")
    val bg by animateColorAsState(if (selected) t.colors.accent else t.colors.surface, t.motion.spring(), label = "chip-bg")
    Box(
        Modifier
            .scale(scale)
            .background(bg, SquircleShape(t.radii.medium))
            .clickable(interaction, indication = null, role = Role.Button) {
                view.perform(NovaHaptic.SELECTION)
                onClick()
            }.padding(horizontal = NovaSpacing.m, vertical = NovaSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = TextStyle(if (selected) Color.White else t.colors.onSurface, 15.sp, FontWeight.Medium))
    }
}

private fun GraphicsLevel.label() =
    when (this) {
        GraphicsLevel.MAXIMUM -> R.string.graphics_level_maximum
        GraphicsLevel.BALANCED -> R.string.graphics_level_balanced
        GraphicsLevel.PERFORMANCE -> R.string.graphics_level_performance
    }

/** Sfondo dimostrativo colorato, generato in codice (nessun asset di terzi). */
@Composable
private fun rememberDemoWallpaper(): ImageBitmap {
    val cfg = LocalConfiguration.current
    val density = LocalDensity.current
    val w = with(density) { cfg.screenWidthDp.dp.roundToPx() }.coerceAtLeast(1)
    val h = with(density) { cfg.screenHeightDp.dp.roundToPx() }.coerceAtLeast(1)
    return remember(w, h) {
        ImageBitmap(w, h).also { img ->
            val canvas = Canvas(img)
            val blobs =
                listOf(
                    Triple(Offset(w * 0.2f, h * 0.25f), w * 0.45f, Color(0xFF5E5CE6)),
                    Triple(Offset(w * 0.8f, h * 0.3f), w * 0.4f, Color(0xFFFF9F0A)),
                    Triple(Offset(w * 0.55f, h * 0.85f), w * 0.5f, Color(0xFF30D158)),
                )
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { color = Color(0xFFE5E5EA) })
            blobs.forEach { (c, r, col) ->
                val paint = Paint().apply { shader = RadialGradientShader(c, r, listOf(col, col.copy(alpha = 0f))) }
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
            }
        }
    }
}
