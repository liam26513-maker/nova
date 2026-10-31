package it.manlio.nova

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.Nova
import it.manlio.nova.core.design.NovaSpacing
import it.manlio.nova.core.design.SegmentedChoice
import it.manlio.nova.core.design.SettingsBlock
import it.manlio.nova.core.design.SettingsRow
import it.manlio.nova.core.design.SettingsScaffold
import it.manlio.nova.core.design.SettingsSection
import it.manlio.nova.feature.home.HomeFont
import it.manlio.nova.feature.home.HomePrefs
import it.manlio.nova.feature.home.ScrimStrength
import it.manlio.nova.feature.home.Side
import it.manlio.nova.feature.home.TextSize

/**
 * Personalizzazione della Home (le opzioni migliori di Niagara): ogni scelta si applica subito,
 * anche alla Home già aperta. [onChange] riceve la modifica da applicare alle preferenze correnti.
 */
@Composable
fun HomeCustomizeScreen(
    prefs: HomePrefs,
    onChange: ((HomePrefs) -> HomePrefs) -> Unit,
    onDone: () -> Unit,
) {
    BackHandler(onBack = onDone)
    SettingsScaffold(title = stringResource(R.string.customize_title), subtitle = stringResource(R.string.customize_subtitle)) { backdrop ->
        SettingsSection(backdrop, header = stringResource(R.string.customize_look)) {
            Choice(
                R.string.customize_icons,
                listOf(true, false),
                prefs.showIcons,
                { showHide(it) },
            ) { v -> onChange { it.copy(showIcons = v) } }
            Choice(R.string.customize_text, TextSize.entries, prefs.textSize, { stringResource(it.label()) }, divider = true) { v ->
                onChange { it.copy(textSize = v) }
            }
            Choice(R.string.customize_font, HomeFont.entries, prefs.font, { stringResource(it.label()) }, divider = true) { v ->
                onChange { it.copy(font = v) }
            }
            Choice(R.string.customize_clock, listOf(true, false), prefs.showClock, { showHide(it) }, divider = true) { v ->
                onChange { it.copy(showClock = v) }
            }
        }
        SettingsSection(
            backdrop,
            header = stringResource(R.string.customize_background),
            footer = stringResource(R.string.customize_background_footer),
        ) {
            Choice(R.string.customize_scrim, ScrimStrength.entries, prefs.scrim, {
                stringResource(it.label())
            }) { v -> onChange { it.copy(scrim = v) } }
            Choice(
                R.string.customize_blur,
                listOf(true, false),
                prefs.blur,
                { onOff(it) },
                divider = true,
            ) { v -> onChange { it.copy(blur = v) } }
        }
        SettingsSection(backdrop, header = stringResource(R.string.customize_alphabet)) {
            Choice(R.string.customize_side, listOf(Side.LEFT, Side.RIGHT), prefs.alphabetSide, { stringResource(it.label()) }) { v ->
                onChange { it.copy(alphabetSide = v) }
            }
        }
        HiddenAndRenamed(backdrop, prefs, onChange)
        SettingsSection(backdrop) { SettingsRow(stringResource(R.string.customize_done), onClick = onDone) }
    }
}

@Composable
private fun HiddenAndRenamed(
    backdrop: androidx.compose.ui.graphics.ImageBitmap,
    prefs: HomePrefs,
    onChange: ((HomePrefs) -> HomePrefs) -> Unit,
) {
    SettingsSection(
        backdrop,
        header = stringResource(R.string.customize_hidden),
        footer = stringResource(R.string.customize_hidden_footer),
    ) {
        if (prefs.hidden.isEmpty()) SettingsRow(stringResource(R.string.customize_hidden_none))
        prefs.hidden.entries.sortedBy { it.value.lowercase() }.forEachIndexed { i, (key, label) ->
            SettingsRow(
                label,
                summary = stringResource(R.string.customize_unhide),
                showDivider = i > 0,
                onClick = { onChange { it.copy(hidden = it.hidden - key) } },
            )
        }
        if (prefs.renamed.isNotEmpty()) {
            SettingsRow(
                stringResource(R.string.customize_reset_names, prefs.renamed.size),
                showDivider = true,
                onClick = { onChange { it.copy(renamed = emptyMap()) } },
            )
        }
    }
}

@Composable
private fun <T> Choice(
    title: Int,
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

@Composable
private fun showHide(show: Boolean) = stringResource(if (show) R.string.customize_show else R.string.customize_hide)

@Composable
private fun onOff(on: Boolean) = stringResource(if (on) R.string.customize_on else R.string.customize_off)

private fun TextSize.label() =
    when (this) {
        TextSize.SMALL -> R.string.customize_text_small
        TextSize.MEDIUM -> R.string.customize_text_medium
        TextSize.LARGE -> R.string.customize_text_large
    }

private fun HomeFont.label() =
    when (this) {
        HomeFont.MODERN -> R.string.customize_font_modern
        HomeFont.ELEGANT -> R.string.customize_font_elegant
        HomeFont.TECHNICAL -> R.string.customize_font_technical
    }

private fun ScrimStrength.label() =
    when (this) {
        ScrimStrength.LIGHT -> R.string.customize_scrim_light
        ScrimStrength.MEDIUM -> R.string.customize_scrim_medium
        ScrimStrength.STRONG -> R.string.customize_scrim_strong
    }

private fun Side.label() = if (this == Side.LEFT) R.string.customize_side_left else R.string.customize_side_right
