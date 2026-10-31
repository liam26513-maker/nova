package it.manlio.nova.feature.home

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Come appaiono testi e icone della home, ricavato da [HomePrefs]. Solo caratteri di sistema. */
@Immutable
internal data class HomeLook(
    val showIcons: Boolean,
    val bigLabel: TextUnit,
    val smallLabel: TextUnit,
    val family: FontFamily,
)

@Suppress("MagicNumber") // misure del testo per ciascuna scelta
internal fun HomePrefs.look(): HomeLook {
    val (big, small) =
        when (textSize) {
            TextSize.SMALL -> 22.sp to 17.sp
            TextSize.MEDIUM -> 28.sp to 20.sp
            TextSize.LARGE -> 34.sp to 24.sp
        }
    val family =
        when (font) {
            HomeFont.MODERN -> FontFamily.SansSerif
            HomeFont.ELEGANT -> FontFamily.Serif
            HomeFont.TECHNICAL -> FontFamily.Monospace
        }
    return HomeLook(showIcons, big, small, family)
}

internal val LocalHomeLook = staticCompositionLocalOf { HomePrefs().look() }
