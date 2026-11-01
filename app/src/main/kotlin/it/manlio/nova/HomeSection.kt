package it.manlio.nova

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import it.manlio.nova.core.design.SettingsRow
import it.manlio.nova.core.design.SettingsSection

/** Quale app fa da Home, e come cambiarla (Fase 0, punto 11). */
@Composable
fun HomeSection(
    backdrop: ImageBitmap,
    state: SystemUiState,
    actions: SystemActions,
) {
    SettingsSection(backdrop, header = stringResource(R.string.home_section_title), footer = stringResource(R.string.home_section_footer)) {
        val current = if (state.novaIsHome) R.string.home_default_nova else R.string.home_default_other
        SettingsRow(stringResource(R.string.home_default), trailing = { Muted(stringResource(current)) })
        if (state.novaIsHome) {
            SettingsRow(stringResource(R.string.home_change), showDivider = true, onClick = actions.onChooseHome)
        } else {
            SettingsRow(
                stringResource(R.string.home_use_nova),
                summary = stringResource(R.string.home_use_nova_summary),
                showDivider = true,
                onClick = actions.onChooseHome,
            )
        }
        SettingsRow(
            stringResource(R.string.home_notifications),
            summary = stringResource(if (state.notificationAccess) R.string.home_notifications_on else R.string.home_notifications_off),
            showDivider = true,
            onClick = actions.onNotificationAccess,
        )
        SettingsRow(
            stringResource(R.string.home_overlay),
            summary = stringResource(if (state.controlOverlay) R.string.home_overlay_on else R.string.home_overlay_off),
            showDivider = true,
            onClick = actions.onControlOverlay,
        )
        SettingsRow(
            stringResource(R.string.home_customize),
            summary = stringResource(R.string.home_customize_summary),
            showDivider = true,
            onClick = actions.onCustomizeHome,
        )
    }
}

/** In cima alle Impostazioni: chiedere un miglioramento o segnalare un problema (SPEC §4.2-4.3). */
@Composable
fun ImproveSection(
    backdrop: ImageBitmap,
    actions: SystemActions,
) {
    SettingsSection(backdrop, header = stringResource(R.string.improve_section), footer = stringResource(R.string.improve_section_footer)) {
        SettingsRow(
            stringResource(R.string.improve_row),
            summary = stringResource(R.string.improve_row_summary),
            onClick = actions.onImprove,
        )
        SettingsRow(
            stringResource(R.string.repair_row),
            summary = stringResource(R.string.repair_row_summary),
            showDivider = true,
            onClick = actions.onRepair,
        )
    }
}
