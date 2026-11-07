package it.manlio.nova

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import it.manlio.nova.core.design.SettingsRow
import it.manlio.nova.core.design.SettingsSection
import it.manlio.nova.core.modes.ModeDefinition
import it.manlio.nova.core.modes.Modes

/**
 * Decide i profili automatici legati a un accessorio (SPEC §8.7): collegato → entra, se nessuna modalità è accesa; scollegato →
 * esce, solo se era entrato da solo e la modalità è ancora quella. Puro, si prova senza Android.
 */
internal class DeviceRule {
    private var entered = false

    fun connected(anyModeOn: Boolean): Boolean = (!anyModeOn).also { if (it) entered = true }

    fun disconnected(stillOn: Boolean): Boolean = (entered && stillOn).also { entered = false }
}

/** Ascolta gli accessori solo se almeno un profilo è acceso; tastiera fisica → Lavoro, penna o tavoletta → Creativa. */
internal class AutoDevices(
    private val context: Context,
    private val app: NovaApp,
) {
    private val input = context.getSystemService(InputManager::class.java)
    private val keyboard = DeviceRule()
    private val pen = DeviceRule()
    private var present = emptySet<Kind>()
    private var listening = false

    private enum class Kind { KEYBOARD, PEN }

    private val listener =
        object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) = refresh()

            override fun onInputDeviceRemoved(deviceId: Int) = refresh()

            override fun onInputDeviceChanged(deviceId: Int) = refresh()
        }

    fun set(on: Boolean) {
        if (on == listening) return
        listening = on
        if (on) {
            present = kinds()
            input.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
        } else {
            input.unregisterInputDeviceListener(listener)
        }
    }

    private fun refresh() {
        val now = kinds()
        val values = app.modes.options.values.value
        if (values[ModeOptions.AUTO_WORK] == true) apply(Kind.KEYBOARD, now, keyboard, Modes.WORK)
        if (values[ModeOptions.AUTO_CREATIVE] == true) apply(Kind.PEN, now, pen, Modes.CREATIVE)
        present = now
    }

    private fun apply(
        kind: Kind,
        now: Set<Kind>,
        rule: DeviceRule,
        mode: ModeDefinition,
    ) {
        val anyOn = app.modes.engine.active.value != null
        val flip =
            when {
                kind in now && kind !in present -> rule.connected(anyOn)
                kind !in now && kind in present -> rule.disconnected(app.modes.isActive(mode))
                else -> false
            }
        if (flip) app.modes.toggle(mode) {}
    }

    private fun kinds(): Set<Kind> =
        input.inputDeviceIds
            .toList()
            .mapNotNull { id -> input.getInputDevice(id) }
            .filter { it.isExternal }
            .mapNotNull {
                when {
                    it.supportsSource(InputDevice.SOURCE_STYLUS) -> Kind.PEN
                    !it.isVirtual && it.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC -> Kind.KEYBOARD
                    else -> null
                }
            }.toSet()
}

/** Impostazioni → Modalità Gaming, in fondo: gli altri profili automatici, spenti di serie. */
@Composable
internal fun AutoDeviceOptions(
    backdrop: ImageBitmap,
    options: ModeOptions,
) {
    val values by options.values.collectAsState()
    SettingsSection(backdrop, header = stringResource(R.string.auto_title), footer = stringResource(R.string.auto_footer)) {
        val work = values[ModeOptions.AUTO_WORK] == true
        val creative = values[ModeOptions.AUTO_CREATIVE] == true
        SettingsRow(
            stringResource(R.string.auto_work),
            onClick = { options.set(ModeOptions.AUTO_WORK, !work) },
            trailing = { if (work) Check() },
        )
        SettingsRow(
            stringResource(R.string.auto_creative),
            showDivider = true,
            onClick = { options.set(ModeOptions.AUTO_CREATIVE, !creative) },
            trailing = { if (creative) Check() },
        )
    }
}
