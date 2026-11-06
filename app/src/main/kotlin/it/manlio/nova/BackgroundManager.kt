package it.manlio.nova

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import it.manlio.nova.core.design.SettingsRow

private const val BACKGROUND_MANAGER = "com.softwinner.awmanager"

/**
 * Sui tablet Allwinner un gestore del produttore impedisce di collegare i servizi delle app che non ha autorizzato: senza il suo
 * permesso il servizio di accessibilità e le notifiche si fermano senza dire niente. NOVA non può autorizzarsi da sola.
 */
@Composable
internal fun BackgroundManagerRow() {
    val context = LocalContext.current
    val open = remember { Intent().setClassName(BACKGROUND_MANAGER, "$BACKGROUND_MANAGER.AwManager") }
    if (context.packageManager.resolveActivity(open, 0) == null) return
    SettingsRow(
        stringResource(R.string.system_background),
        summary = stringResource(R.string.system_background_summary),
        showDivider = true,
        onClick = { runCatching { context.startActivity(open) } },
    )
}
