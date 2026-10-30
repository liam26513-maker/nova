package it.manlio.nova.core.design

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class NovaColors(
    val background: Color,
    val surface: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val accent: Color,
)

@Immutable
data class NovaRadii(
    val small: Dp,
    val medium: Dp,
    val large: Dp,
)

/** Scala di spaziature a passi di 4 dp (SPEC §5.1). */
object NovaSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val s = 12.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

/** Parametri delle molle: tutte le transizioni usano molle fisiche interrompibili (SPEC §5.2). */
@Immutable
data class NovaMotion(
    val stiffness: Float,
    val dampingRatio: Float,
) {
    fun <T> spring(visibilityThreshold: T? = null): SpringSpec<T> = spring(dampingRatio, stiffness, visibilityThreshold)
}

@Immutable
data class NovaTokens(
    val dark: Boolean,
    val colors: NovaColors,
    val radii: NovaRadii,
    /** Opacità dei materiali traslucidi (pannelli, dock, centro di controllo). */
    val materialOpacity: Float,
    val motion: NovaMotion,
    val graphicsLevel: GraphicsLevel,
) {
    companion object {
        /** Valori di riserva, usati solo se nessun tema viene fornito (es. anteprime). */
        val Fallback =
            NovaTokens(
                dark = false,
                colors =
                    NovaColors(
                        background = Color(0xFFF7F6F3),
                        surface = Color.White,
                        onSurface = Color(0xFF1D1D1F),
                        onSurfaceMuted = Color(0xFF6E6E73),
                        accent = Color(0xFF4F6D8A),
                    ),
                radii = NovaRadii(10.dp, 16.dp, 26.dp),
                materialOpacity = 0.62f,
                motion = NovaMotion(stiffness = 380f, dampingRatio = 0.86f),
                graphicsLevel = GraphicsLevel.BALANCED,
            )
    }
}

val LocalNovaTokens = staticCompositionLocalOf { NovaTokens.Fallback }

object Nova {
    val tokens: NovaTokens
        @Composable @ReadOnlyComposable
        get() = LocalNovaTokens.current
}

@Composable
fun NovaTheme(
    tokens: NovaTokens,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalNovaTokens provides tokens, content = content)
}

/** Converte un colore "#RRGGBB" o "#AARRGGBB" (formato dei file in config/). */
fun parseNovaColor(hex: String): Color {
    require(hex.length == 7 || hex.length == 9) { "Colore non valido: $hex" }
    val v = hex.substring(1).toLong(16)
    return if (hex.length == 7) Color(0xFF000000 or v) else Color(v)
}
