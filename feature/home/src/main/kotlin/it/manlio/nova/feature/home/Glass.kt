@file:Suppress("MagicNumber") // coefficienti di disegno (trasparenze, proporzioni): sono il progetto grafico

package it.manlio.nova.feature.home

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.dp

/**
 * Vetro: un riempimento traslucido più chiaro in alto, con un filo di luce sul bordo (più forte in alto,
 * quasi spento in basso). Solo colori e un tratto: niente sfocatura in tempo reale, quindi costa quasi nulla.
 * Con [flat] (livello grafico Prestazioni) resta un riempimento piatto più pieno, senza bordo.
 * Con [liquid] il vetro è più limpido e lucido: un riflesso morbido in alto a sinistra e un bordo doppio che prende luce
 * da un lato e la rimanda dall'altro. Sempre solo sfumature e tratti: lo stesso costo del vetro normale.
 */
internal fun Modifier.glass(
    shape: Shape,
    dark: Boolean,
    flat: Boolean,
    liquid: Boolean = false,
): Modifier =
    drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        if (liquid && !flat) {
            liquidGlass(shape, dark)
            return@drawBehind
        }
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

private fun DrawScope.liquidGlass(
    shape: Shape,
    dark: Boolean,
) {
    val outline = shape.createOutline(size, layoutDirection, this)
    // Corpo limpido: poco colore, più chiaro in alto.
    val top = if (dark) 0.09f else 0.36f
    val bottom = if (dark) 0.02f else 0.18f
    drawOutline(outline, Brush.verticalGradient(listOf(Color.White.copy(alpha = top), Color.White.copy(alpha = bottom))))
    // Riflesso morbido in alto a sinistra.
    drawOutline(
        outline,
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = if (dark) 0.13f else 0.26f), Color.Transparent),
            center = Offset(size.width * 0.18f, 0f),
            radius = size.maxDimension * 0.75f,
        ),
    )
    // Bordo esterno: luce forte in alto a sinistra, quasi spento a metà, di nuovo acceso in basso a destra.
    drawOutline(
        outline,
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.85f),
                Color.White.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.05f),
                Color.White.copy(alpha = 0.45f),
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
        style = Stroke(1.4.dp.toPx()),
    )
    // Bordo interno sottile: dà spessore al vetro.
    val gap = 2.dp.toPx()
    inset(gap) {
        drawOutline(
            shape.createOutline(size, layoutDirection, this),
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent, Color.White.copy(alpha = 0.10f))),
            style = Stroke(0.8.dp.toPx()),
        )
    }
}

/** Il fondo delle schede sotto il vetro: col vetro liquido è più trasparente, così lo sfondo si vede attraverso. */
internal fun Color.glassBase(liquid: Boolean): Color = if (liquid) copy(alpha = alpha * 0.62f) else this
