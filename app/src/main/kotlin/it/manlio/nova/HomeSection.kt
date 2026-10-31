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
    }
}
