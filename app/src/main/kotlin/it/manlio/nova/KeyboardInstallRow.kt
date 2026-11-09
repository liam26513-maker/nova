package it.manlio.nova

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import it.manlio.nova.core.design.SettingsRow
import kotlinx.coroutines.launch

/**
 * Se la tastiera di NOVA non c'è, un tocco la installa (con Shizuku, come gli aggiornamenti); poi apre le impostazioni di Android
 * per accenderla, perché attivare una tastiera è una scelta che Android lascia all'utente.
 */
@Composable
internal fun KeyboardInstallRow() {
    val context = LocalContext.current
    val app = context.applicationContext as? NovaApp ?: return // nelle prove l'app è finta
    var installed by remember { mutableStateOf(app.updater.keyboardInstalled()) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    if (installed) return
    SettingsRow(
        stringResource(if (working) R.string.keyboard_installing else R.string.keyboard_install),
        summary = error ?: stringResource(R.string.keyboard_install_summary),
        showDivider = true,
        onClick = {
            if (working) return@SettingsRow
            working = true
            error = null
            scope.launch {
                error = app.updater.installKeyboard()
                working = false
                installed = app.updater.keyboardInstalled()
                if (installed) {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            }
        },
    )
}
