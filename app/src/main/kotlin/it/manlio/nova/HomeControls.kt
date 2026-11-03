package it.manlio.nova

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.Settings
import it.manlio.nova.core.design.GraphicsLevel
import it.manlio.nova.core.modes.Modes
import it.manlio.nova.core.system.QuickToggle
import it.manlio.nova.core.system.ShizukuStatus
import it.manlio.nova.feature.home.ControlActions
import it.manlio.nova.feature.home.Level
import it.manlio.nova.feature.home.MoreItem
import it.manlio.nova.feature.home.SystemPanel
import it.manlio.nova.feature.home.Toggle
import it.manlio.nova.feature.home.Torch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * Il centro di controllo della Home (SPEC §6.8) con le cose che Android lascia fare a un'app normale:
 * torcia, volume, luminosità (con il permesso «Modifica impostazioni di sistema», che si concede da una voce chiara)
 * e livello grafico di NOVA. Il resto apre il pannello di sistema giusto.
 * La torcia ascolta i cambiamenti solo tra [start] e [stop], cioè mentre la Home è visibile.
 */
class HomeControls(
    private val context: Context,
    private val app: NovaApp,
    /** Dove girano i comandi degli interruttori (la Home: il suo ciclo di vita; il servizio: il proprio). */
    private val scope: CoroutineScope,
    /** Apre la tendina notifiche di Android (sulla Home: API di sistema; sopra le altre app: azione di accessibilità). */
    private val onSystemNotifications: () -> Unit,
    /** Apre le Impostazioni rapide di Android: raggiunge ogni riquadro che NOVA non ha. */
    private val onSystemQuickSettings: () -> Unit,
    private val onTurbo: () -> Unit,
) {
    private val camera = context.getSystemService(CameraManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val torchId: String? =
        runCatching {
            camera.cameraIdList.firstOrNull { id ->
                val c = camera.getCameraCharacteristics(id)
                val back = c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true && back
            }
        }.getOrNull()
    private val quick = QuickToggles(context, app, scope)
    private val torchOn = MutableStateFlow(false)
    private val callback =
        object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(
                cameraId: String,
                enabled: Boolean,
            ) {
                if (cameraId == torchId) torchOn.value = enabled
            }
        }

    fun start() {
        if (torchId != null) runCatching { camera.registerTorchCallback(callback, Handler(Looper.getMainLooper())) }
    }

    fun stop() {
        if (torchId != null) runCatching { camera.unregisterTorchCallback(callback) }
    }

    private val torch: Torch? =
        torchId?.let { id ->
            object : Torch {
                override val on: StateFlow<Boolean> = torchOn.asStateFlow()

                override fun toggle() {
                    runCatching { camera.setTorchMode(id, !torchOn.value) }
                }
            }
        }

    /** Da rifare quando si torna sulla Home: il permesso della luminosità può essere cambiato nel frattempo. */
    fun actions(): ControlActions =
        ControlActions(
            openPanel = ::open,
            torch = torch,
            volume = volume(),
            brightness = if (Settings.System.canWrite(context)) brightness() else null,
            onGrantBrightness = {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
            graphicsLabel = ::graphicsLabel,
            onCycleGraphics = ::cycleGraphics,
            onTurbo = onTurbo.takeIf { app.shizuku.status.value == ShizukuStatus.READY },
            more = more(),
            toggles = toggles(),
        )

    /** Wi-Fi, Bluetooth e non disturbare cambiano subito dal riquadro se Shizuku lo permette; senza, il riquadro apre Android. */
    private fun toggles(): Map<SystemPanel, Toggle> {
        if (!quick.available) return emptyMap()

        fun of(t: QuickToggle) = Toggle(read = { quick.isOn(t) }, set = { on, onFailed -> quick.set(t, on, onFailed) })
        return mapOf(
            SystemPanel.INTERNET to of(QuickToggle.WIFI),
            SystemPanel.BLUETOOTH to of(QuickToggle.BLUETOOTH),
            SystemPanel.DND to of(QuickToggle.DND),
        )
    }

    /**
     * Le altre impostazioni del pannello di Android, sotto «Altro». Un'app normale può cambiare da sola solo la rotazione
     * (col permesso già usato per la luminosità); le altre aprono la schermata giusta di Android. Le due in cima
     * (notifiche e impostazioni rapide di Android) sono la rete di sicurezza: dietro c'è ogni riquadro che manca qui.
     */
    private fun more(): List<MoreItem> {
        fun go(
            label: Int,
            action: String,
        ) = MoreItem(context.getString(label)) {
            startSettings(action)
            null
        }

        /** Interruttore: con Shizuku cambia subito e mostra Sì/No; senza, apre la schermata di Android. */
        fun toggle(
            label: Int,
            t: QuickToggle,
            action: String,
        ): MoreItem {
            val text = { on: Boolean -> context.getString(if (on) R.string.more_on else R.string.more_off) }
            if (!quick.available) return go(label, action)
            return MoreItem(context.getString(label), text(quick.isOn(t))) { report ->
                val next = !quick.isOn(t)
                quick.set(t, next) { report(text(quick.isOn(t))) }
                text(next)
            }
        }
        return listOf(
            standby(),
            gaming(),
            MoreItem(context.getString(R.string.more_notifications)) { onSystemNotifications().let { null } },
            MoreItem(context.getString(R.string.more_quick_settings)) { onSystemQuickSettings().let { null } },
            rotation(),
            toggle(R.string.more_airplane, QuickToggle.AIRPLANE, Settings.ACTION_AIRPLANE_MODE_SETTINGS),
            toggle(R.string.more_location, QuickToggle.LOCATION, Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            toggle(R.string.more_battery_saver, QuickToggle.BATTERY_SAVER, Settings.ACTION_BATTERY_SAVER_SETTINGS),
            toggle(R.string.more_data, QuickToggle.DATA_SAVER, ACTION_DATA_USAGE),
            toggle(R.string.more_dark_theme, QuickToggle.DARK_THEME, Settings.ACTION_DISPLAY_SETTINGS),
            toggle(R.string.more_night_light, QuickToggle.NIGHT_LIGHT, ACTION_NIGHT_DISPLAY),
            toggle(R.string.more_color_inversion, QuickToggle.INVERSION, ACTION_COLOR_INVERSION),
            go(R.string.more_hotspot, Settings.ACTION_WIRELESS_SETTINGS),
            go(R.string.more_nfc, Settings.ACTION_NFC_SETTINGS),
            go(R.string.more_cast, Settings.ACTION_CAST_SETTINGS),
            go(R.string.more_sound, Settings.ACTION_SOUND_SETTINGS),
            go(R.string.more_display, Settings.ACTION_DISPLAY_SETTINGS),
            go(R.string.more_color_correction, ACTION_COLOR_CORRECTION),
            go(R.string.more_accessibility, Settings.ACTION_ACCESSIBILITY_SETTINGS),
            go(R.string.more_alarms, AlarmClock.ACTION_SHOW_ALARMS),
        ) + GameTools(context, app).items()
    }

    /** StandBy (SPEC §12.6): chiede alla Home di mostrarlo e la porta in primo piano (anche da sopra un'altra app). */
    private fun standby() =
        MoreItem(context.getString(R.string.more_standby)) {
            app.standbyRequest.value = true
            context.startActivity(Intent(context, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            null
        }

    /** Modalità Gaming (SPEC §8.2, di base): un tocco entra, un tocco esce e rimette tutto com'era. È anche l'uscita di emergenza. */
    private fun gaming(): MoreItem {
        val text = { on: Boolean -> context.getString(if (on) R.string.more_on else R.string.more_off) }
        val modes = app.modes
        return MoreItem(context.getString(R.string.more_gaming), text(modes.isActive(Modes.GAMING))) { report ->
            val next = !modes.isActive(Modes.GAMING)
            modes.toggle(Modes.GAMING) { report(text(it)) }
            text(next)
        }
    }

    /** Rotazione automatica: si cambia qui se c'è il permesso, altrimenti si apre lo schermo di Android. */
    private fun rotation(): MoreItem {
        val resolver = context.contentResolver
        val on = { Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1 }
        val text = { v: Boolean -> context.getString(if (v) R.string.more_on else R.string.more_off) }
        return MoreItem(context.getString(R.string.more_rotation), text(on())) {
            if (Settings.System.canWrite(context)) {
                val next = !on()
                runCatching { Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, if (next) 1 else 0) }
                text(on())
            } else {
                startSettings(Settings.ACTION_DISPLAY_SETTINGS)
                null
            }
        }
    }

    private fun startSettings(action: String) {
        runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun volume() =
        Level(
            steps = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            read = { audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) },
            write = {
                    v ->
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).roundToInt(), 0)
            },
        )

    private fun brightness() =
        Level(
            read = {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, DEFAULT_BRIGHTNESS) / MAX_BRIGHTNESS
            },
            write = { v ->
                runCatching {
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                    )
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                        (v * MAX_BRIGHTNESS).roundToInt().coerceIn(1, MAX_BRIGHTNESS.toInt()),
                    )
                }
            },
        )

    private fun open(panel: SystemPanel) {
        val action =
            when (panel) {
                SystemPanel.INTERNET -> Settings.Panel.ACTION_INTERNET_CONNECTIVITY
                SystemPanel.VOLUME -> Settings.Panel.ACTION_VOLUME
                SystemPanel.BLUETOOTH -> Settings.ACTION_BLUETOOTH_SETTINGS
                SystemPanel.DISPLAY -> Settings.ACTION_DISPLAY_SETTINGS
                SystemPanel.DND -> Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS
            }
        runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun graphicsLabel(): String {
        val state = app.graphics.value
        return context.getString(
            if (state.automatic) {
                R.string.graphics_level_auto
            } else {
                when (state.level) {
                    GraphicsLevel.MAXIMUM -> R.string.graphics_level_maximum
                    GraphicsLevel.BALANCED -> R.string.graphics_level_balanced
                    GraphicsLevel.PERFORMANCE -> R.string.graphics_level_performance
                }
            },
        )
    }

    /** Automatico → Massimo → Bilanciato → Prestazioni → Automatico. */
    private fun cycleGraphics() {
        val state = app.graphics.value
        val c = app.graphicsController
        when {
            state.automatic -> c.setManual(GraphicsLevel.MAXIMUM)
            state.level == GraphicsLevel.MAXIMUM -> c.setManual(GraphicsLevel.BALANCED)
            state.level == GraphicsLevel.BALANCED -> c.setManual(GraphicsLevel.PERFORMANCE)
            else -> c.setAutomatic()
        }
    }

    private companion object {
        const val MAX_BRIGHTNESS = 255f
        const val DEFAULT_BRIGHTNESS = 128

        // Azioni di Android non esposte come costanti pubbliche in tutte le versioni: se mancano, il tocco non fa nulla.
        const val ACTION_DATA_USAGE = "android.settings.DATA_USAGE_SETTINGS"
        const val ACTION_NIGHT_DISPLAY = "android.settings.NIGHT_DISPLAY_SETTINGS"
        const val ACTION_COLOR_INVERSION = "android.settings.COLOR_INVERSION_SETTINGS"
        const val ACTION_COLOR_CORRECTION = "android.settings.COLOR_CORRECTION_SETTINGS"
    }
}
