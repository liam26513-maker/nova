package it.manlio.nova

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import it.manlio.nova.core.design.SettingsRow
import it.manlio.nova.core.design.SettingsSection
import it.manlio.nova.feature.home.ControlTileId
import it.manlio.nova.feature.home.HomePrefs

/** Quali riquadri del centro di controllo stanno a prima vista; gli altri si trovano sotto «Altro». */
@Composable
internal fun ControlSection(
    backdrop: ImageBitmap,
    prefs: HomePrefs,
    onChange: ((HomePrefs) -> HomePrefs) -> Unit,
) {
    SettingsSection(
        backdrop,
        header = stringResource(R.string.customize_control),
        footer = stringResource(R.string.customize_control_footer),
    ) {
        ControlTileId.entries.forEachIndexed { index, tile ->
            val on = tile in prefs.controlTiles
            SettingsRow(
                stringResource(controlTileLabel(tile)),
                showDivider = index > 0,
                onClick = {
                    onChange { p ->
                        val chosen = if (on) p.controlTiles - tile else p.controlTiles + tile
                        p.copy(controlTiles = ControlTileId.entries.filter { it in chosen })
                    }
                },
                trailing = { if (on) Check() },
            )
        }
    }
}

@StringRes
private fun controlTileLabel(tile: ControlTileId): Int =
    when (tile) {
        ControlTileId.WIFI -> R.string.ctl_wifi
        ControlTileId.BLUETOOTH -> R.string.ctl_bluetooth
        ControlTileId.DND -> R.string.ctl_dnd
        ControlTileId.TORCH -> R.string.ctl_torch
        ControlTileId.GRAPHICS -> R.string.ctl_graphics
        ControlTileId.TURBO -> R.string.ctl_turbo
        ControlTileId.EDIT_HOME -> R.string.ctl_edit_home
        ControlTileId.SETTINGS -> R.string.ctl_settings
    }
