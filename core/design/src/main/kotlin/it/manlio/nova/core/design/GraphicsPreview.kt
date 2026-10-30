package it.manlio.nova.core.design

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Canvas as ImageCanvas

/**
 * Anteprima dal vivo del livello grafico: forme che scorrono dietro un pannello di vetro.
 * - Massimo: il vetro sfoca in tempo reale ciò che passa dietro.
 * - Bilanciato: il vetro mostra una sfocatura calcolata una volta (non segue il movimento): costa quasi nulla.
 * - Prestazioni: pannello pieno, nessuna sfocatura.
 * Serve a rendere visibile la differenza, che sullo sfondo calmo delle impostazioni è volutamente minima.
 */
@Composable
fun GraphicsPreview(
    level: GraphicsLevel,
    modifier: Modifier = Modifier,
) {
    val t = Nova.tokens
    val shape = SquircleShape(t.radii.medium)
    val phase by rememberInfiniteTransition(label = "preview").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(LOOP_MS, easing = LinearEasing), RepeatMode.Reverse),
        label = "phase",
    )
    BoxWithConstraints(modifier.fillMaxWidth().height(PREVIEW_HEIGHT).clip(shape)) {
        val density = LocalDensity.current
        val full = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        val panel = Size(full.width * PANEL_WIDTH_FRACTION, with(density) { PANEL_HEIGHT.toPx() })
        val panelOrigin = Offset((full.width - panel.width) / 2, (full.height - panel.height) / 2)
        val dark = t.dark
        val frozen = remember(full, dark) { frozenBlur(full, dark, t.colors.background) }

        val base = t.colors.background
        Canvas(Modifier.fillMaxSize()) {
            drawRect(base)
            scene(full, phase, dark).forEach { drawCircle(it.color, it.radius, it.centre) }
        }

        Box(
            Modifier
                .align(Alignment.Center)
                .size(with(density) { panel.width.toDp() }, PANEL_HEIGHT)
                .clip(shape),
        ) {
            // Il pannello copre ciò che sta dietro: si vede solo la sua versione (sfocata o piena).
            Canvas(Modifier.fillMaxSize()) { drawRect(base) }
            when (level) {
                GraphicsLevel.MAXIMUM ->
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { renderEffect = BlurEffect(LIVE_BLUR, LIVE_BLUR, TileMode.Clamp) },
                    ) {
                        translate(-panelOrigin.x, -panelOrigin.y) {
                            scene(full, phase, dark).forEach { drawCircle(it.color, it.radius, it.centre) }
                        }
                    }
                GraphicsLevel.BALANCED ->
                    Canvas(Modifier.fillMaxSize()) {
                        drawImage(
                            frozen,
                            srcOffset = IntOffset((panelOrigin.x * FROZEN_SCALE).roundToInt(), (panelOrigin.y * FROZEN_SCALE).roundToInt()),
                            srcSize = IntSize((panel.width * FROZEN_SCALE).roundToInt(), (panel.height * FROZEN_SCALE).roundToInt()),
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        )
                    }
                GraphicsLevel.PERFORMANCE -> Unit
            }
            val alpha = if (level == GraphicsLevel.PERFORMANCE) SOLID_ALPHA else t.materialOpacity * GLASS_TINT
            Canvas(Modifier.fillMaxSize()) { drawRect(t.colors.surface.copy(alpha = alpha)) }
        }
    }
}

private data class Blob(
    val centre: Offset,
    val radius: Float,
    val color: Color,
)

/** Tre forme in toni calmi ma visibili, che attraversano l'anteprima. */
private fun scene(
    size: Size,
    phase: Float,
    dark: Boolean,
): List<Blob> {
    val alpha = if (dark) 0.8f else 0.95f
    val r = size.height * 0.36f
    // Le forme attraversano il centro, cioè passano proprio dietro al vetro.
    return listOf(
        Blob(Offset(size.width * (0.05f + 0.6f * phase), size.height * 0.42f), r, Color(0xFF7F9CC0).copy(alpha = alpha)),
        Blob(Offset(size.width * (0.95f - 0.6f * phase), size.height * 0.58f), r * 0.9f, Color(0xFFA9BF92).copy(alpha = alpha)),
        Blob(Offset(size.width * (0.30f + 0.4f * phase), size.height * 0.5f), r * 0.6f, Color(0xFFD9A982).copy(alpha = alpha)),
    )
}

/** Fotografia sfocata della scena (fase 0), calcolata una volta: è il "finto blur" del livello Bilanciato. */
private fun frozenBlur(
    size: Size,
    dark: Boolean,
    base: Color,
): ImageBitmap {
    val w = (size.width * FROZEN_SCALE).roundToInt().coerceAtLeast(1)
    val h = (size.height * FROZEN_SCALE).roundToInt().coerceAtLeast(1)
    val small = ImageBitmap(w, h)
    val canvas = ImageCanvas(small)
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { color = base })
    scene(Size(w.toFloat(), h.toFloat()), 0f, dark).forEach { b ->
        canvas.drawCircle(b.centre, b.radius, Paint().apply { color = b.color })
    }
    val px = IntArray(w * h).also { small.readPixels(it) }
    val blurred = BoxBlur.blur(px, w, h, radius = FROZEN_BLUR_RADIUS)
    return Bitmap.createBitmap(blurred, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private val PREVIEW_HEIGHT = 132.dp
private val PANEL_HEIGHT = 84.dp
private const val PANEL_WIDTH_FRACTION = 0.56f
private const val LOOP_MS = 5_000
private const val LIVE_BLUR = 48f
private const val FROZEN_SCALE = 0.25f
private const val FROZEN_BLUR_RADIUS = 5
private const val GLASS_TINT = 0.45f
private const val SOLID_ALPHA = 0.96f
