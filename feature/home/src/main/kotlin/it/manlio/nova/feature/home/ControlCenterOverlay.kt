package it.manlio.nova.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first

/**
 * Il centro di controllo fuori dalla Home (SPEC §6.8, §2.2): lo stesso pannello a tendina, ma in una finestra che il
 * servizio di accessibilità di NOVA mette sopra le altre app. Si apre da solo con una molla; [closing] a vero lo
 * richiude (Indietro, cambio di finestra) e [onDismissed] dice quando è tornato chiuso, cioè quando si può togliere la finestra.
 * Il riquadro «Modifica la Home» non c'è: modificare la Home ha senso solo sulla Home.
 */
@Composable
fun ControlCenterOverlay(
    controls: ControlActions,
    prefs: HomePrefs,
    systemDark: Boolean,
    closing: Boolean,
    onSettings: () -> Unit,
    onDismissed: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sheet = remember { ControlSheetState(scope) }
    val look = remember(prefs, systemDark) { prefs.look(systemDark) }
    LaunchedEffect(closing) { sheet.settle(!closing) }
    LaunchedEffect(sheet) {
        snapshotFlow { sheet.progress }.first { it > OPENED } // prima deve essersi aperta, poi tornare a zero
        snapshotFlow { sheet.progress }.first { it <= 0f }
        onDismissed()
    }
    CompositionLocalProvider(LocalHomeLook provides look) {
        ControlCenter(
            sheet = sheet,
            controls = controls,
            onSettings = onSettings,
            onEditHome = null,
            firstView = prefs.controlTiles,
            onClose = { sheet.settle(false) },
            onSettled = {},
        )
    }
}

private const val OPENED = 0.5f
