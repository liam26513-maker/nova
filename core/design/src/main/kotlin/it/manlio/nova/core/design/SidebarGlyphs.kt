package it.manlio.nova.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Simboli disegnati da NOVA per le voci delle Impostazioni (nessun set di icone di terzi, SPEC §5.1). */
enum class SidebarGlyph { SPARKLE, LAYERS, TABLET, HOUSE, DOWNLOAD, SLIDERS, CHECK, IMAGE, DROP, TEXT, GRID, EYE }

/** Una categoria delle Impostazioni: voce della barra laterale (o della lista, su schermi stretti). */
@Immutable
data class SidebarEntry(
    val id: String,
    val title: String,
    val glyph: SidebarGlyph,
    val color: Color,
)

/** Pastiglia colorata ad angoli morbidi con un simbolo bianco, come nelle liste di Impostazioni dell'iPad. */
@Composable
fun GlyphBadge(
    glyph: SidebarGlyph,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = BADGE_SIZE,
) {
    Box(modifier.size(size).background(color, SquircleShape(size * BADGE_RADIUS)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size * GLYPH_FRACTION)) { drawGlyph(glyph, Color.White) }
    }
}

/** Disegna [glyph] in un quadrato di lato `size.minDimension`; le coordinate sono frazioni del lato. */
@Suppress("MagicNumber", "LongMethod", "CyclomaticComplexMethod") // geometria: una riga per tratto
internal fun DrawScope.drawGlyph(
    glyph: SidebarGlyph,
    ink: Color,
) {
    val u = size.minDimension
    val w = u * 0.09f
    val line = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun p(
        x: Float,
        y: Float,
    ) = Offset(x * u, y * u)

    fun polyline(vararg pts: Pair<Float, Float>) {
        val path = Path().apply { pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * u, y * u) else lineTo(x * u, y * u) } }
        drawPath(path, ink, style = line)
    }
    when (glyph) {
        SidebarGlyph.SPARKLE ->
            drawPath(
                Path().apply {
                    moveTo(.5f * u, .06f * u)
                    lineTo(.62f * u, .38f * u)
                    lineTo(.94f * u, .5f * u)
                    lineTo(.62f * u, .62f * u)
                    lineTo(.5f * u, .94f * u)
                    lineTo(.38f * u, .62f * u)
                    lineTo(.06f * u, .5f * u)
                    lineTo(.38f * u, .38f * u)
                    close()
                },
                ink,
            )
        SidebarGlyph.LAYERS -> {
            drawCircle(ink, .27f * u, p(.36f, .5f), style = line)
            drawCircle(ink, .27f * u, p(.64f, .5f), style = line)
        }
        SidebarGlyph.TABLET -> {
            drawRoundRect(ink, p(.18f, .1f), Size(.64f * u, .8f * u), CornerRadius(.1f * u), line)
            drawCircle(ink, .035f * u, p(.5f, .78f))
        }
        SidebarGlyph.HOUSE -> {
            polyline(.1f to .48f, .5f to .12f, .9f to .48f)
            polyline(.22f to .42f, .22f to .86f, .78f to .86f, .78f to .42f)
            polyline(.42f to .86f, .42f to .62f, .58f to .62f, .58f to .86f)
        }
        SidebarGlyph.DOWNLOAD -> {
            polyline(.5f to .12f, .5f to .64f)
            polyline(.3f to .46f, .5f to .66f, .7f to .46f)
            polyline(.2f to .86f, .8f to .86f)
        }
        SidebarGlyph.SLIDERS ->
            listOf(.25f to .32f, .5f to .68f, .75f to .45f).forEach { (y, knob) ->
                drawLine(ink, p(.08f, y), p(.92f, y), w, StrokeCap.Round)
                drawCircle(ink, .1f * u, p(knob, y))
            }
        SidebarGlyph.CHECK -> {
            drawCircle(ink, .4f * u, p(.5f, .5f), style = line)
            polyline(.3f to .52f, .45f to .66f, .72f to .36f)
        }
        SidebarGlyph.IMAGE -> {
            drawRoundRect(ink, p(.1f, .18f), Size(.8f * u, .64f * u), CornerRadius(.1f * u), line)
            drawCircle(ink, .07f * u, p(.34f, .38f))
            polyline(.14f to .74f, .42f to .5f, .6f to .66f, .72f to .58f, .88f to .72f)
        }
        SidebarGlyph.DROP ->
            drawPath(
                Path().apply {
                    moveTo(.5f * u, .1f * u)
                    cubicTo(.5f * u, .1f * u, .2f * u, .46f * u, .2f * u, .64f * u)
                    cubicTo(.2f * u, .8f * u, .34f * u, .9f * u, .5f * u, .9f * u)
                    cubicTo(.66f * u, .9f * u, .8f * u, .8f * u, .8f * u, .64f * u)
                    cubicTo(.8f * u, .46f * u, .5f * u, .1f * u, .5f * u, .1f * u)
                    close()
                },
                ink,
                style = line,
            )
        SidebarGlyph.TEXT -> {
            polyline(.14f to .3f, .86f to .3f)
            polyline(.14f to .5f, .86f to .5f)
            polyline(.14f to .7f, .56f to .7f)
        }
        SidebarGlyph.GRID ->
            listOf(.1f, .56f).forEach { x ->
                listOf(.1f, .56f).forEach { y -> drawRoundRect(ink, p(x, y), Size(.34f * u, .34f * u), CornerRadius(.08f * u)) }
            }
        SidebarGlyph.EYE -> {
            drawPath(
                Path().apply {
                    moveTo(.06f * u, .5f * u)
                    quadraticBezierTo(.5f * u, .1f * u, .94f * u, .5f * u)
                    quadraticBezierTo(.5f * u, .9f * u, .06f * u, .5f * u)
                    close()
                },
                ink,
                style = line,
            )
            drawCircle(ink, .11f * u, p(.5f, .5f))
        }
    }
}

private val BADGE_SIZE = 30.dp
private const val BADGE_RADIUS = 0.28f
private const val GLYPH_FRACTION = 0.62f
