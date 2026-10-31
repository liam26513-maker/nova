package it.manlio.nova.feature.home

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Come appaiono testi, colori e icone della home, ricavato da [HomePrefs]. Solo caratteri di sistema. */
@Immutable
internal data class HomeLook(
    val dark: Boolean,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val showIcons: Boolean,
    val bigIcon: Dp,
    val smallIcon: Dp,
    val bigLabel: TextUnit,
    val smallLabel: TextUnit,
    val family: FontFamily,
    val weight: FontWeight,
    val clockStyle: ClockStyle,
    val showDate: Boolean,
)

/** [systemDark]: tema scuro di sistema (per "Automatico"); [tint]: colore principale dello sfondo. */
@Suppress("MagicNumber") // misure e colori per ciascuna scelta
internal fun HomePrefs.look(
    systemDark: Boolean = true,
    tint: Color = HomeStyle.DEFAULT_TINT,
): HomeLook {
    val dark =
        when (theme) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.AUTO -> systemDark
        }
    val ink = if (dark) Color.White else Color(0xFF15171A)
    val (big, small) = labelSizes(textSize)
    val (bigIcon, smallIcon) = iconSizes(iconSize)
    // Accento automatico: il colore dello sfondo, schiarito sul tema scuro e scurito su quello chiaro.
    val accentColor = accent?.let(::Color) ?: lerp(tint, if (dark) Color.White else Color.Black, 0.45f)
    return HomeLook(
        dark = dark,
        ink = ink,
        muted = ink.copy(alpha = 0.64f),
        accent = accentColor,
        showIcons = showIcons,
        bigIcon = bigIcon,
        smallIcon = smallIcon,
        bigLabel = big,
        smallLabel = small,
        family = family(font),
        weight = fontWeight(weight),
        clockStyle = clockStyle,
        showDate = showDate,
    )
}

@Suppress("MagicNumber")
private fun labelSizes(size: TextSize): Pair<TextUnit, TextUnit> =
    when (size) {
        TextSize.SMALL -> 22.sp to 17.sp
        TextSize.MEDIUM -> 28.sp to 20.sp
        TextSize.LARGE -> 34.sp to 24.sp
    }

@Suppress("MagicNumber")
private fun iconSizes(size: IconSize): Pair<Dp, Dp> =
    when (size) {
        IconSize.SMALL -> 34.dp to 28.dp
        IconSize.MEDIUM -> 44.dp to 34.dp
        IconSize.LARGE -> 54.dp to 42.dp
    }

private fun family(font: HomeFont): FontFamily =
    when (font) {
        HomeFont.MODERN -> FontFamily.SansSerif
        HomeFont.ELEGANT -> FontFamily.Serif
        HomeFont.TECHNICAL -> FontFamily.Monospace
    }

private fun fontWeight(weight: TextWeight): FontWeight =
    when (weight) {
        TextWeight.LIGHT -> FontWeight.Light
        TextWeight.REGULAR -> FontWeight.Normal
        TextWeight.BOLD -> FontWeight.SemiBold
    }

internal val LocalHomeLook = staticCompositionLocalOf { HomePrefs().look() }
