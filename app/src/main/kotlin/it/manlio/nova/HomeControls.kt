package it.manlio.nova

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import it.manlio.nova.core.design.GraphicsLevel
import it.manlio.nova.core.system.ShizukuStatus
import it.manlio.nova.feature.home.ControlActions
import it.manlio.nova.feature.home.Level
import it.manlio.nova.feature.home.SystemPanel
import it.manlio.nova.feature.home.Torch
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
        )

    private fun volume() =
        Level(
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
    }
}
