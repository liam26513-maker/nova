package it.manlio.nova

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.GraphicsLevel
import it.manlio.nova.core.design.GraphicsState
import it.manlio.nova.core.design.Nova
import it.manlio.nova.core.design.NovaSpacing
import it.manlio.nova.core.design.SegmentedChoice
import it.manlio.nova.core.design.SettingsBlock
import it.manlio.nova.core.design.SettingsRow
import it.manlio.nova.core.design.SettingsScaffold
import it.manlio.nova.core.design.SettingsSection

/**
 * Pagina Impostazioni (provvisoria finché non arriva la home, Fase 0 punto 11).
 * Usa il linguaggio visivo calmo comune a tutte le schermate di sistema.
 */
@Composable
fun SettingsScreen(
    brandName: String,
    configVersion: String,
    graphics: GraphicsState,
    onChoose: (LevelChoice) -> Unit,
) {
    val t = Nova.tokens
    SettingsScaffold(title = stringResource(R.string.settings_title), subtitle = brandName) { backdrop ->
        SettingsSection(
            backdrop,
            header = stringResource(R.string.graphics_level_title),
            footer = stringResource(R.string.graphics_level_footer),
        ) {
            SettingsBlock {
                SegmentedChoice(
                    options = LevelChoice.all,
                    selected = LevelChoice.of(graphics),
                    label = { stringResource(it.label()) },
                    onSelect = onChoose,
                )
                Spacer(Modifier.height(NovaSpacing.s))
                val current = stringResource(graphics.level.label())
                BasicText(
                    if (graphics.automatic) {
                        stringResource(R.string.graphics_level_auto_now, current)
                    } else {
                        stringResource(R.string.graphics_level_fixed_now, current)
                    },
                    style = TextStyle(t.colors.onSurfaceMuted, 14.sp),
                )
            }
        }
        SettingsSection(backdrop, header = stringResource(R.string.status_title)) {
            SettingsRow(stringResource(R.string.status_config_title), trailing = { Value(configVersion) })
            SettingsRow(
                stringResource(R.string.status_modules_title),
                showDivider = true,
                trailing = { Value(stringResource(R.string.status_modules_none)) },
            )
        }
    }
}

@Composable
private fun Value(text: String) {
    BasicText(text, style = TextStyle(Nova.tokens.colors.onSurfaceMuted, 17.sp))
}

private fun LevelChoice.label() =
    when (this) {
        LevelChoice.Automatic -> R.string.graphics_level_auto
        is LevelChoice.Fixed -> level.label()
    }

internal fun GraphicsLevel.label() =
    when (this) {
        GraphicsLevel.MAXIMUM -> R.string.graphics_level_maximum
        GraphicsLevel.BALANCED -> R.string.graphics_level_balanced
        GraphicsLevel.PERFORMANCE -> R.string.graphics_level_performance
    }
