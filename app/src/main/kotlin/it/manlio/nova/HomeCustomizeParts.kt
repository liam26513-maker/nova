package it.manlio.nova

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.Nova
import it.manlio.nova.core.design.NovaHaptic
import it.manlio.nova.core.design.NovaSpacing
import it.manlio.nova.core.design.SegmentedChoice
import it.manlio.nova.core.design.SettingsBlock
import it.manlio.nova.core.design.perform
import it.manlio.nova.feature.home.BlurLevel
import it.manlio.nova.feature.home.ClockStyle
import it.manlio.nova.feature.home.HomeFont
import it.manlio.nova.feature.home.IconShape
import it.manlio.nova.feature.home.IconSize
import it.manlio.nova.feature.home.IconStyle
import it.manlio.nova.feature.home.NovaWallpaper
import it.manlio.nova.feature.home.ScrimStrength
import it.manlio.nova.feature.home.Side
import it.manlio.nova.feature.home.TextSize
import it.manlio.nova.feature.home.TextWeight
import it.manlio.nova.feature.home.ThemeMode
import it.manlio.nova.feature.home.WallpaperSource

/** Titolo e scelta a segmenti, nello stile calmo delle Impostazioni. */
@Composable
internal fun <T> Choice(
    @StringRes title: Int,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    divider: Boolean = false,
    onSelect: (T) -> Unit,
) {
    SettingsBlock(showDivider = divider) {
        BasicText(stringResource(title), style = TextStyle(Nova.tokens.colors.onSurface, 17.sp))
        Spacer(Modifier.height(NovaSpacing.xs))
        SegmentedChoice(options = options, selected = selected, label = label, onSelect = onSelect)
    }
}

/** Un campione da scegliere: colore pieno, sfumatura della raccolta o "automatico". */
internal class SwatchItem(
    val name: String,
    val color: Color? = null,
    val gradient: NovaWallpaper? = null,
    val auto: Boolean = false,
)

/** Riga di campioni tondi scorrevole; quello scelto ha un anello d'accento. */
@Composable
internal fun Swatches(
    @StringRes title: Int,
    items: List<SwatchItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val t = Nova.tokens
    val view = LocalView.current
    SettingsBlock(showDivider = true) {
        BasicText(stringResource(title), style = TextStyle(t.colors.onSurface, 17.sp))
        Spacer(Modifier.height(NovaSpacing.xs))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(NovaSpacing.s)) {
            items.forEachIndexed { i, item ->
                val ring = if (i == selected) t.colors.accent else t.colors.onSurface.copy(alpha = 0.12f)
                Box(
                    Modifier
                        .size(SWATCH)
                        .border(if (i == selected) 2.5.dp else 1.dp, ring, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton) {
                            view.perform(NovaHaptic.SELECTION)
                            onSelect(i)
                        }.semantics {
                            contentDescription = item.name
                            this.selected = i == selected
                        },
                    contentAlignment = Alignment.Center,
                ) { SwatchFill(item) }
            }
        }
    }
}

@Composable
private fun SwatchFill(item: SwatchItem) {
    when {
        item.gradient != null -> Canvas(Modifier.size(SWATCH)) { item.gradient.draw(this) }
        item.color != null -> Box(Modifier.size(SWATCH).background(item.color))
        else ->
            Box(Modifier.size(SWATCH).background(Nova.tokens.colors.onSurface.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
                BasicText("A", style = TextStyle(Nova.tokens.colors.onSurfaceMuted, 13.sp))
            }
    }
}

@Composable
internal fun Check() {
    BasicText("✓", Modifier.padding(start = 8.dp), style = TextStyle(Nova.tokens.colors.accent, 18.sp))
}

@Composable
internal fun showHide(show: Boolean) = stringResource(if (show) R.string.customize_show else R.string.customize_hide)

@Composable
internal fun onOff(on: Boolean) = stringResource(if (on) R.string.customize_on else R.string.customize_off)

/** Nome leggibile di un colore della tavolozza (per chi usa TalkBack). */
internal fun colorName(argb: Int) = "#%06X".format(argb and RGB_MASK)

private const val RGB_MASK = 0xFFFFFF

@Suppress("MagicNumber") // tavolozze
internal val SOLID_COLORS =
    listOf(0xFF000000, 0xFF1B2028, 0xFF2B2F3A, 0xFF1F3A34, 0xFF3A2430, 0xFF20304A, 0xFFE9E4D8, 0xFFF7F6F3).map { it.toInt() }

@Suppress("MagicNumber")
internal val ACCENTS =
    listOf(0xFFFFFFFF, 0xFF8FB4D9, 0xFF66C2A5, 0xFFFFC58A, 0xFFE58F9E, 0xFFB7A3E0, 0xFFE3D26F, 0xFF8A6D4F).map { it.toInt() }

private val SWATCH = 44.dp

/** Testo di ogni scelta delle impostazioni della Home. */
@StringRes
@Suppress("CyclomaticComplexMethod", "LongMethod") // una riga per valore: lunga ma lineare
internal fun labelOf(value: Enum<*>): Int =
    when (value) {
        WallpaperSource.SYSTEM -> R.string.customize_wallpaper_system
        WallpaperSource.NOVA -> R.string.customize_wallpaper_nova
        WallpaperSource.PHOTO -> R.string.customize_wallpaper_photo
        WallpaperSource.SOLID -> R.string.customize_wallpaper_solid
        BlurLevel.NONE -> R.string.customize_blur_none
        BlurLevel.LIGHT -> R.string.customize_blur_light
        BlurLevel.STRONG -> R.string.customize_blur_strong
        BlurLevel.MAX -> R.string.customize_blur_max
        ThemeMode.DARK -> R.string.customize_theme_dark
        ThemeMode.LIGHT -> R.string.customize_theme_light
        ThemeMode.AUTO -> R.string.customize_theme_auto
        TextSize.SMALL, IconSize.SMALL -> R.string.customize_text_small
        TextSize.MEDIUM, IconSize.MEDIUM -> R.string.customize_text_medium
        TextSize.LARGE, IconSize.LARGE -> R.string.customize_text_large
        HomeFont.MODERN -> R.string.customize_font_modern
        HomeFont.ELEGANT -> R.string.customize_font_elegant
        HomeFont.TECHNICAL -> R.string.customize_font_technical
        TextWeight.LIGHT -> R.string.customize_weight_light
        TextWeight.REGULAR -> R.string.customize_weight_regular
        TextWeight.BOLD -> R.string.customize_weight_bold
        ClockStyle.THIN -> R.string.customize_clock_thin
        ClockStyle.BOLD -> R.string.customize_clock_bold
        ClockStyle.STACKED -> R.string.customize_clock_stacked
        IconShape.ORIGINAL -> R.string.customize_shape_original
        IconShape.CIRCLE -> R.string.customize_shape_circle
        IconShape.SQUIRCLE -> R.string.customize_shape_squircle
        IconShape.SQUARE -> R.string.customize_shape_square
        IconStyle.COLOR -> R.string.customize_icon_color
        IconStyle.MONO -> R.string.customize_icon_mono
        ScrimStrength.LIGHT -> R.string.customize_scrim_light
        ScrimStrength.MEDIUM -> R.string.customize_scrim_medium
        ScrimStrength.STRONG -> R.string.customize_scrim_strong
        Side.LEFT -> R.string.customize_side_left
        Side.RIGHT -> R.string.customize_side_right
        else -> error("Scelta senza testo: $value")
    }
