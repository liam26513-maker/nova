package it.manlio.nova.feature.home

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Raccolta di sfondi di NOVA: sfumature disegnate al volo (niente immagini, niente asset di terzi),
 * pensate per stare sotto una lista di testo.
 */
@Immutable
data class NovaWallpaper(
    val name: String,
    val colors: List<Color>,
    /** Luce morbida opzionale: centro (frazioni dello schermo) e colore. */
    val glow: Pair<Offset, Color>? = null,
) {
    fun draw(scope: DrawScope) =
        with(scope) {
            drawRect(Brush.linearGradient(colors, start = Offset.Zero, end = Offset(size.width, size.height)))
            glow?.let { (centre, color) ->
                val c = Offset(size.width * centre.x, size.height * centre.y)
                drawRect(Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), c, size.maxDimension * GLOW_RADIUS))
            }
        }

    /** Colore principale, per la sfumatura e l'accento automatico. */
    val primary: Color get() = colors[colors.size / 2]

    private companion object {
        const val GLOW_RADIUS = 0.6f
    }
}

@Suppress("MagicNumber") // i colori della raccolta
object NovaWallpapers {
    val all =
        listOf(
            NovaWallpaper(
                "Notte",
                listOf(Color(0xFF0E1218), Color(0xFF1C2433), Color(0xFF0B0D12)),
                Offset(0.2f, 0.1f) to Color(0x334F6D8A),
            ),
            NovaWallpaper(
                "Aurora",
                listOf(Color(0xFF0D1B1E), Color(0xFF14403F), Color(0xFF1D2B4A)),
                Offset(0.8f, 0.2f) to Color(0x4466C2A5),
            ),
            NovaWallpaper(
                "Tramonto",
                listOf(Color(0xFF2B1A2E), Color(0xFF7A3E48), Color(0xFFD08C5B)),
                Offset(0.7f, 0.9f) to Color(0x40FFC58A),
            ),
            NovaWallpaper(
                "Carta",
                listOf(Color(0xFFF4F1EA), Color(0xFFE9E4D8), Color(0xFFDCD5C4)),
                Offset(0.1f, 0.2f) to Color(0x33B9C7BE),
            ),
            NovaWallpaper("Salvia", listOf(Color(0xFF9DB0A3), Color(0xFF6F8778), Color(0xFF3F5246))),
            NovaWallpaper(
                "Oceano",
                listOf(Color(0xFF0A2540), Color(0xFF1B4F72), Color(0xFF3A7CA5)),
                Offset(0.3f, 0.8f) to Color(0x3381D4FA),
            ),
            NovaWallpaper("Lavanda", listOf(Color(0xFF2D2640), Color(0xFF5B4B8A), Color(0xFFA494C9))),
            NovaWallpaper("Grafite", listOf(Color(0xFF101010), Color(0xFF242424), Color(0xFF0A0A0A))),
        )
}
