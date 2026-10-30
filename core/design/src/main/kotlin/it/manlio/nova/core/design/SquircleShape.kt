package it.manlio.nova.core.design

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

/**
 * Rettangolo con angoli "continui" (curvatura che cresce gradualmente, effetto squircle)
 * invece degli archi di cerchio di RoundedCornerShape (SPEC §5.1).
 */
class SquircleShape(private val cornerRadius: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(squirclePath(size, with(density) { cornerRadius.toPx() }))

    override fun equals(other: Any?) = other is SquircleShape && other.cornerRadius == cornerRadius

    override fun hashCode() = cornerRadius.hashCode()
}

/**
 * Coefficienti (in unità di raggio) di un angolo continuo: tre curve cubiche dal punto
 * a distanza [EXTENT]·r dall'angolo sul primo lato fino allo stesso punto sull'altro lato.
 * La sequenza è simmetrica rispetto alla diagonale dell'angolo.
 */
private const val EXTENT = 1.528665f
private val CORNER =
    floatArrayOf(
        1.088493f, 0f, 0.868407f, 0f, 0.669934f, 0.065496f,
        0.435975f, 0.127571f, 0.127571f, 0.435975f, 0.065496f, 0.669934f,
        0f, 0.868407f, 0f, 1.088493f, 0f, EXTENT,
    )

/** Raggio effettivo: ridotto se l'angolo non sta nella forma. */
internal fun effectiveRadius(
    size: Size,
    requested: Float,
): Float = min(requested, min(size.width, size.height) / (2 * EXTENT)).coerceAtLeast(0f)

fun squirclePath(
    size: Size,
    radiusPx: Float,
): Path {
    val w = size.width
    val h = size.height
    val r = effectiveRadius(size, radiusPx)
    val e = EXTENT * r
    return Path().apply {
        moveTo(e, 0f)
        lineTo(w - e, 0f)
        corner(r) { x, y -> (w - x) to y } // in alto a destra
        lineTo(w, h - e)
        corner(r) { x, y -> (w - y) to (h - x) } // in basso a destra
        lineTo(e, h)
        corner(r) { x, y -> x to (h - y) } // in basso a sinistra
        lineTo(0f, e)
        corner(r) { x, y -> y to x } // in alto a sinistra
        close()
    }
}

/** Disegna un angolo in coordinate locali (angolo in 0,0) trasformate da [map]. */
private inline fun Path.corner(
    r: Float,
    map: (Float, Float) -> Pair<Float, Float>,
) {
    var i = 0
    while (i < CORNER.size) {
        val (x1, y1) = map(CORNER[i] * r, CORNER[i + 1] * r)
        val (x2, y2) = map(CORNER[i + 2] * r, CORNER[i + 3] * r)
        val (x3, y3) = map(CORNER[i + 4] * r, CORNER[i + 5] * r)
        cubicTo(x1, y1, x2, y2, x3, y3)
        i += 6
    }
}
