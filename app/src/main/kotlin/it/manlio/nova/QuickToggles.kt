package it.manlio.nova

import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.provider.Settings
import it.manlio.nova.core.system.QuickToggle
import it.manlio.nova.core.system.ShizukuStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Gli interruttori del pannello rapido di Android che il centro di controllo cambia con un tocco.
 * Lo stato si legge sempre dalle API pubbliche (nessun permesso speciale); cambiarlo, invece, un'app normale non può:
 * si fa con i comandi fissi di [QuickToggle] tramite Shizuku, registrati e annullabili ([it.manlio.nova.core.system.SystemChangeLog]).
 */
internal class QuickToggles(
    private val context: Context,
    private val app: NovaApp,
    private val scope: CoroutineScope,
) {
    /** Si possono cambiare dalla tendina solo se Shizuku è pronto; altrimenti le voci aprono la schermata di Android. */
    val available: Boolean get() = app.shizuku.status.value == ShizukuStatus.READY

    fun isOn(toggle: QuickToggle): Boolean = runCatching { read(toggle) }.getOrDefault(false)

    /** Cambia subito; se Android non obbedisce chiama [onFailed] (chi chiama rimette lo stato com'è davvero). */
    fun set(
        toggle: QuickToggle,
        on: Boolean,
        onFailed: () -> Unit,
    ) {
        val wasOn = isOn(toggle)
        scope.launch {
            val ok = runCatching { app.systemTools.setToggle(toggle, on, wasOn) }.getOrDefault(false)
            if (!ok) onFailed()
        }
    }

    private fun read(toggle: QuickToggle): Boolean =
        when (toggle) {
            QuickToggle.WIFI -> context.getSystemService(WifiManager::class.java).isWifiEnabled
            QuickToggle.BLUETOOTH -> global("bluetooth_on")
            QuickToggle.AIRPLANE -> global(Settings.Global.AIRPLANE_MODE_ON)
            QuickToggle.DND -> context.getSystemService(NotificationManager::class.java).currentInterruptionFilter.let {
                it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            }
            QuickToggle.DARK_THEME ->
                Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            QuickToggle.LOCATION -> context.getSystemService(LocationManager::class.java).isLocationEnabled
            QuickToggle.BATTERY_SAVER -> context.getSystemService(PowerManager::class.java).isPowerSaveMode
            QuickToggle.DATA_SAVER ->
                context.getSystemService(ConnectivityManager::class.java).restrictBackgroundStatus !=
                    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED
            QuickToggle.NIGHT_LIGHT -> secure("night_display_activated")
            QuickToggle.INVERSION -> secure("accessibility_display_inversion_enabled")
        }

    private fun global(key: String) = Settings.Global.getInt(context.contentResolver, key, 0) == 1

    private fun secure(key: String) = Settings.Secure.getInt(context.contentResolver, key, 0) == 1
}
