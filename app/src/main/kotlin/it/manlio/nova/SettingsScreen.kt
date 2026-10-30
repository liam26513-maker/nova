package it.manlio.nova

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.GraphicsLevel
import it.manlio.nova.core.design.GraphicsPreview
import it.manlio.nova.core.design.GraphicsState
import it.manlio.nova.core.design.Nova
import it.manlio.nova.core.design.NovaSpacing
import it.manlio.nova.core.design.SegmentedChoice
import it.manlio.nova.core.design.SettingsBlock
import it.manlio.nova.core.design.SettingsRow
import it.manlio.nova.core.design.SettingsScaffold
import it.manlio.nova.core.design.SettingsSection
import it.manlio.nova.core.device.DeviceProfile
import kotlin.math.roundToInt

/**
 * Pagina Impostazioni (provvisoria finché non arriva la home, Fase 0 punto 11).
 * Usa il linguaggio visivo calmo comune a tutte le schermate di sistema.
 */
@Composable
fun SettingsScreen(
    brandName: String,
    configVersion: String,
    graphics: GraphicsState,
    profile: DeviceProfile?,
    onChoose: (LevelChoice) -> Unit,
    onMeasure: () -> Unit,
    onShowReport: () -> Unit,
    system: SystemUiState,
    systemActions: SystemActions,
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
                Spacer(Modifier.height(NovaSpacing.m))
                GraphicsPreview(graphics.level)
                Spacer(Modifier.height(NovaSpacing.s))
                BasicText(stringResource(graphics.level.explanation()), style = TextStyle(t.colors.onSurface, 15.sp, lineHeight = 21.sp))
                Spacer(Modifier.height(NovaSpacing.xxs))
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
        DeviceSection(backdrop, profile, onMeasure, onShowReport)
        UpdatesSection(backdrop, system, systemActions)
        SystemToolsSection(backdrop, system, systemActions)
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
private fun DeviceSection(
    backdrop: ImageBitmap,
    profile: DeviceProfile?,
    onMeasure: () -> Unit,
    onShowReport: () -> Unit,
) {
    SettingsSection(
        backdrop,
        header = stringResource(R.string.device_title),
        footer = stringResource(R.string.device_footer),
    ) {
        if (profile != null) {
            val cpu =
                listOf(profile.soc.ifBlank { profile.model }, stringResource(R.string.device_cores, profile.cpuCores))
                    .joinToString(" · ")
            SettingsRow(stringResource(R.string.device_cpu), trailing = { Value(cpu) })
            SettingsRow(
                stringResource(R.string.device_ram),
                showDivider = true,
                trailing = { Value(stringResource(R.string.device_gb, (profile.ramMb + MB_PER_GB / 2) / MB_PER_GB)) },
            )
            val hz = profile.refreshRatesHz.firstOrNull()?.roundToInt() ?: 0
            SettingsRow(
                stringResource(R.string.device_screen),
                showDivider = true,
                trailing = { Value(stringResource(R.string.device_screen_value, profile.screenWidthPx, profile.screenHeightPx, hz)) },
            )
            val advice =
                if (profile.benchmark == null) {
                    stringResource(R.string.device_not_measured)
                } else {
                    stringResource(profile.ceiling.label())
                }
            SettingsRow(stringResource(R.string.device_ceiling), showDivider = true, trailing = { Value(advice) })
            if (profile.benchmark != null) {
                SettingsRow(stringResource(R.string.device_report), showDivider = true, onClick = onShowReport)
            }
        }
        SettingsRow(
            stringResource(if (profile?.benchmark == null) R.string.device_measure else R.string.device_measure_again),
            summary = stringResource(R.string.device_measure_summary),
            showDivider = profile != null,
            onClick = onMeasure,
        )
    }
}

private const val MB_PER_GB = 1024L

@Composable
private fun Value(text: String) {
    BasicText(text, style = TextStyle(Nova.tokens.colors.onSurfaceMuted, 17.sp))
}

private fun LevelChoice.label() =
    when (this) {
        LevelChoice.Automatic -> R.string.graphics_level_auto
        is LevelChoice.Fixed -> level.label()
    }

private fun GraphicsLevel.explanation() =
    when (this) {
        GraphicsLevel.MAXIMUM -> R.string.graphics_explain_maximum
        GraphicsLevel.BALANCED -> R.string.graphics_explain_balanced
        GraphicsLevel.PERFORMANCE -> R.string.graphics_explain_performance
    }

internal fun GraphicsLevel.label() =
    when (this) {
        GraphicsLevel.MAXIMUM -> R.string.graphics_level_maximum
        GraphicsLevel.BALANCED -> R.string.graphics_level_balanced
        GraphicsLevel.PERFORMANCE -> R.string.graphics_level_performance
    }
