package it.manlio.nova.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first

/**
 * Il centro di controllo fuori dalla Home (SPEC §6.8, §2.2): lo stesso pannello a tendina, ma in una finestra che il
 * servizio di accessibilità di NOVA mette sopra le altre app. A guidarlo è [sheet]: chi chiama lo trascina col dito
 * ([ControlSheetState.drag]) e lo lascia ([ControlSheetState.release]), quindi segue il tocco come sulla Home.
 * [closing] a vero lo richiude (Indietro, cambio di finestra); [onDismissed] dice quando è tornato chiuso dopo essersi
 * aperto, cioè quando si può togliere la finestra. Il riquadro «Modifica la Home» non c'è: ha senso solo sulla Home.
 */
@Composable
fun ControlCenterOverlay(
    sheet: ControlSheetState,
    controls: ControlActions,
    prefs: HomePrefs,
    systemDark: Boolean,
    closing: Boolean,
    onSettings: () -> Unit,
    onDismissed: () -> Unit,
) {
    val look = remember(prefs, systemDark) { prefs.look(systemDark) }
    LaunchedEffect(closing) { if (closing) sheet.settle(false) }
    LaunchedEffect(sheet) {
        snapshotFlow { sheet.progress }.first { it > 0f } // prima compare...
        snapshotFlow { sheet.progress }.first { it <= 0f } // ...poi, tornata a zero, si toglie la finestra
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
