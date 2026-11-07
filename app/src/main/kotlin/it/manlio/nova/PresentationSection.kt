package it.manlio.nova

import android.accessibilityservice.AccessibilityService
import android.app.AlertDialog
import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import it.manlio.nova.core.design.SettingsRow
import it.manlio.nova.core.design.SettingsSection
import it.manlio.nova.core.modes.ExitPin

/** Impostazioni → Modalità Presentazione: il PIN facoltativo per uscire (SPEC §8.6). Toglierlo chiede quello attuale. */
@Composable
internal fun PresentationSection(
    backdrop: ImageBitmap,
    modes: ModeController,
) {
    val context = LocalContext.current
    var on by remember { mutableStateOf(modes.exitPin.isSet) }
    val footer = stringResource(R.string.presentation_pin_footer)
    SettingsSection(backdrop, header = stringResource(R.string.presentation_pin), footer = footer) {
        SettingsRow(
            stringResource(if (on) R.string.presentation_pin_remove else R.string.presentation_pin_set),
            onClick = {
                if (on) {
                    askPin(context, modes.exitPin) {
                        modes.exitPin.clear()
                        on = false
                    }
                } else {
                    choosePin(context, modes.exitPin) { on = true }
                }
            },
        )
    }
}

private fun choosePin(
    context: Context,
    pin: ExitPin,
    onSet: () -> Unit,
) = pinDialog(context, R.string.presentation_pin_choose) { typed ->
    if (ExitPin.valid(typed)) {
        pin.set(typed)
        onSet()
    } else {
        Toast.makeText(context, R.string.presentation_pin_invalid, Toast.LENGTH_LONG).show()
    }
}

/** Chiede il PIN e chiama [onRight] solo se è giusto. */
internal fun askPin(
    context: Context,
    pin: ExitPin,
    onRight: () -> Unit,
) = pinDialog(context, R.string.presentation_pin_ask) { typed ->
    if (pin.matches(typed)) onRight() else Toast.makeText(context, R.string.presentation_pin_wrong, Toast.LENGTH_LONG).show()
}

private fun pinDialog(
    context: Context,
    title: Int,
    onTyped: (String) -> Unit,
) {
    val field =
        EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(ExitPin.MAX_DIGITS))
        }
    val dialog =
        AlertDialog
            .Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setView(field)
            .setPositiveButton(android.R.string.ok) { _, _ -> onTyped(field.text.toString()) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    // Dalla tendina sopra le altre app il contesto è il servizio: senza questo tipo di finestra Android rifiuta il dialogo.
    if (context is AccessibilityService) dialog.window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    dialog.show()
    field.requestFocus()
}
