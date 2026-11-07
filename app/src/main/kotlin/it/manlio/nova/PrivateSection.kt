package it.manlio.nova

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import it.manlio.nova.core.design.SettingsRow
import it.manlio.nova.core.design.SettingsSection

/**
 * Impostazioni → Cartella privata (SPEC §13.5): le app nascoste dalla Home si aprono da qui, dopo l'impronta o il blocco schermo
 * di Android. Nessun PIN nuovo da ricordare. Lo sblocco vale finché la pagina resta aperta.
 */
@Composable
internal fun PrivateSection(backdrop: ImageBitmap) {
    val context = LocalContext.current
    val app = context.applicationContext as NovaApp
    val prefs by app.homePrefs.prefs.collectAsState()
    var open by remember { mutableStateOf(false) }
    val secure = context.getSystemService(KeyguardManager::class.java).isDeviceSecure
    SettingsSection(backdrop, header = stringResource(R.string.private_title), footer = stringResource(R.string.private_footer)) {
        when {
            !secure -> SettingsRow(stringResource(R.string.private_no_lock))
            !open -> SettingsRow(stringResource(R.string.private_unlock), onClick = { unlock(context) { open = true } })
            prefs.hidden.isEmpty() -> SettingsRow(stringResource(R.string.private_empty))
            else ->
                prefs.hidden.entries.sortedBy { it.value.lowercase() }.forEachIndexed { index, (key, name) ->
                    SettingsRow(name, showDivider = index > 0, onClick = { launch(context, key) })
                }
        }
    }
}

private fun unlock(
    context: Context,
    onOpen: () -> Unit,
) {
    val prompt =
        BiometricPrompt
            .Builder(context)
            .setTitle(context.getString(R.string.private_title))
            .setAllowedAuthenticators(Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL)
            .build()
    val callback =
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOpen()
        }
    runCatching { prompt.authenticate(CancellationSignal(), context.mainExecutor, callback) }
}

/** La chiave di un'app è «componente#utente». shortcut: si apre solo nell'utente principale, basta finché non c'è un profilo di lavoro. */
private fun launch(
    context: Context,
    key: String,
) {
    val component = ComponentName.unflattenFromString(key.substringBefore('#')) ?: return
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
