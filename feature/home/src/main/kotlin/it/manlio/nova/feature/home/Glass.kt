@file:Suppress("MagicNumber") // coefficienti di disegno (trasparenze, proporzioni): sono il progetto grafico

package it.manlio.nova.feature.home

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Vetro: un riempimento traslucido più chiaro in alto, con un filo di luce sul bordo (più forte in alto,
 * quasi spento in basso). Solo colori e un tratto: niente sfocatura in tempo reale, quindi costa quasi nulla.
 * Con [flat] (livello grafico Prestazioni) resta un riempimento piatto più pieno, senza bordo.
 */
internal fun Modifier.glass(
    shape: Shape,
    dark: Boolean,
    flat: Boolean,
): Modifier =
    drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        val top = if (dark) 0.22f else 0.55f
        val bottom = if (dark) 0.12f else 0.38f
        if (flat) {
            drawOutline(outline, Color.White.copy(alpha = if (dark) 0.30f else 0.80f))
            return@drawBehind
        }
        drawOutline(outline, Brush.verticalGradient(listOf(Color.White.copy(alpha = top), Color.White.copy(alpha = bottom))))
        drawOutline(
            outline,
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.22f)),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
            style = Stroke(1.dp.toPx()),
        )
    }
