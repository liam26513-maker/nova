package it.manlio.nova

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import it.manlio.nova.core.design.SettingsRow
import it.manlio.nova.core.design.SettingsSection

/** Cosa sa fare una penna o tavoletta collegata, da quello che dichiara ad Android. */
private data class PenInfo(
    val name: String,
    val external: Boolean,
    val pressure: Boolean,
    val tilt: Boolean,
)

/** Le penne e tavolette che Android vede adesso (SPEC §9.1). Non legge niente di ciò che si disegna. */
private fun pens(context: Context): List<PenInfo> {
    val input = context.getSystemService(InputManager::class.java)
    return input.inputDeviceIds.toList()
        .mapNotNull { id -> input.getInputDevice(id) }
        .filter { it.supportsSource(InputDevice.SOURCE_STYLUS) || it.supportsSource(InputDevice.SOURCE_BLUETOOTH_STYLUS) }
        .map {
            PenInfo(
                it.name,
                it.isExternal,
                pressure = it.getMotionRange(MotionEvent.AXIS_PRESSURE)?.let { r -> r.range > 0f } == true,
                tilt = it.getMotionRange(MotionEvent.AXIS_TILT) != null,
            )
        }
}

/** Impostazioni → Penna: cosa è collegato e cosa arriva. Si aggiorna da sola quando colleghi o scolleghi qualcosa. */
@Composable
internal fun PenSection(backdrop: ImageBitmap) {
    val context = LocalContext.current
    var found by remember { mutableStateOf(pens(context)) }
    DisposableEffect(Unit) {
        val input = context.getSystemService(InputManager::class.java)
        val listener =
            object : InputManager.InputDeviceListener {
                override fun onInputDeviceAdded(deviceId: Int) {
                    found = pens(context)
                }

                override fun onInputDeviceRemoved(deviceId: Int) {
                    found = pens(context)
                }

                override fun onInputDeviceChanged(deviceId: Int) {
                    found = pens(context)
                }
            }
        input.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
        onDispose { input.unregisterInputDeviceListener(listener) }
    }
    SettingsSection(backdrop, header = stringResource(R.string.pen_found), footer = stringResource(R.string.pen_footer)) {
        if (found.isEmpty()) SettingsRow(stringResource(R.string.pen_none), summary = stringResource(R.string.pen_none_summary))
        found.forEachIndexed { index, pen ->
            val yes = stringResource(R.string.pen_yes)
            val no = stringResource(R.string.pen_no)
            SettingsRow(
                pen.name,
                summary =
                    stringResource(
                        R.string.pen_details,
                        stringResource(if (pen.external) R.string.pen_external else R.string.pen_builtin),
                        if (pen.pressure) yes else no,
                        if (pen.tilt) yes else no,
                    ),
                showDivider = index > 0,
            )
        }
    }
}
