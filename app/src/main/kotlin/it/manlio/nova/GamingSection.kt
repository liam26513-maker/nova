package it.manlio.nova

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import it.manlio.nova.core.design.SettingsRow
import it.manlio.nova.core.design.SettingsSection
import it.manlio.nova.core.modes.ModeAction
import it.manlio.nova.core.modes.Modes

/**
 * Impostazioni → Modalità Gaming: una voce per ogni cosa che la modalità fa, e quella della tendina sopra i giochi.
 * Se un gioco scatta o non risponde bene, si spegne una voce alla volta e si riprova: così si trova cosa lo causa.
 */
@Composable
fun GamingSection(
    backdrop: ImageBitmap,
    options: ModeOptions,
) {
    val values by options.values.collectAsState()
    SettingsSection(backdrop, header = stringResource(R.string.gaming_title), footer = stringResource(R.string.gaming_footer)) {
        Modes.GAMING.actions.forEachIndexed { index, action ->
            val on = values[action.name] ?: (action in ModeOptions.DEFAULT_ON)
            SettingsRow(
                stringResource(gamingTitle(action)),
                summary = stringResource(gamingSummary(action)),
                showDivider = index > 0,
                onClick = { options.set(action.name, !on) },
                trailing = { if (on) Check() },
            )
        }
    }
    ExitOptions(backdrop, options, values)
    val overlayHeader = stringResource(R.string.gaming_overlay_title)
    SettingsSection(backdrop, header = overlayHeader, footer = stringResource(R.string.gaming_overlay_footer)) {
        val on = values[ModeOptions.OVERLAY] ?: true
        SettingsRow(
            stringResource(R.string.gaming_overlay_row),
            onClick = { options.set(ModeOptions.OVERLAY, !on) },
            trailing = { if (on) Check() },
        )
    }
}

/** Le uscite di emergenza che costano qualcosa al gioco: le accende l'utente. */
@Composable
private fun ExitOptions(
    backdrop: ImageBitmap,
    options: ModeOptions,
    values: Map<String, Boolean>,
) {
    SettingsSection(backdrop, header = stringResource(R.string.gaming_exits), footer = stringResource(R.string.gaming_exits_footer)) {
        val swipe = values[ModeOptions.EDGE_SWIPE] == true
        val watchdog = values[ModeOptions.WATCHDOG] == true
        SettingsRow(
            stringResource(R.string.gaming_edge),
            summary = stringResource(R.string.gaming_edge_summary),
            onClick = { options.set(ModeOptions.EDGE_SWIPE, !swipe) },
            trailing = { if (swipe) Check() },
        )
        SettingsRow(
            stringResource(R.string.gaming_watchdog),
            summary = stringResource(R.string.gaming_watchdog_summary),
            showDivider = true,
            onClick = { options.set(ModeOptions.WATCHDOG, !watchdog) },
            trailing = { if (watchdog) Check() },
        )
    }
}

private fun gamingTitle(action: ModeAction): Int =
    when (action) {
        ModeAction.PERFORMANCE_GRAPHICS -> R.string.gaming_graphics
        ModeAction.DO_NOT_DISTURB -> R.string.gaming_dnd
        ModeAction.LOCK_ROTATION -> R.string.gaming_rotation
        ModeAction.KEEP_SCREEN_ON -> R.string.gaming_screen
        ModeAction.PAUSE_SYNC -> R.string.gaming_sync
        ModeAction.NIGHT_LIGHT_OFF -> R.string.mode_action_night_light // non sono voci della Gaming: qui non compaiono
        ModeAction.FULL_BRIGHTNESS -> R.string.mode_action_brightness
        ModeAction.TURBO -> R.string.gaming_turbo
    }

private fun gamingSummary(action: ModeAction): Int =
    when (action) {
        ModeAction.PERFORMANCE_GRAPHICS -> R.string.gaming_graphics_summary
        ModeAction.DO_NOT_DISTURB -> R.string.gaming_dnd_summary
        ModeAction.LOCK_ROTATION -> R.string.gaming_rotation_summary
        ModeAction.KEEP_SCREEN_ON -> R.string.gaming_screen_summary
        ModeAction.PAUSE_SYNC -> R.string.gaming_sync_summary
        ModeAction.NIGHT_LIGHT_OFF -> R.string.mode_action_night_light
        ModeAction.FULL_BRIGHTNESS -> R.string.mode_action_brightness
        ModeAction.TURBO -> R.string.gaming_turbo_summary
    }
