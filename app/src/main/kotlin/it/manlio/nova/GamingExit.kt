package it.manlio.nova

import android.accessibilityservice.AccessibilityService
import android.app.AlertDialog
import android.view.KeyEvent
import android.view.WindowManager
import it.manlio.nova.core.modes.Modes

/**
 * I tasti del volume per il servizio di accessibilità: volume giù tenuto [HOLD_MS] durante la Gaming chiama [onHeld] (uscita di
 * emergenza, SPEC §8.2 punto 2); volume su chiama [onVolumeUp] (avvio della misura). Non consuma mai il tasto: il volume cambia
 * come sempre.
 */
internal class VolumeKeys(
    private val post: (Runnable, Long) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val gaming: () -> Boolean,
    onHeld: () -> Unit,
    private val onVolumeUp: () -> Unit,
) {
    private val held = Runnable(onHeld)

    fun onKey(
        keyCode: Int,
        action: Int,
        repeatCount: Int,
    ) {
        val pressed = action == KeyEvent.ACTION_DOWN && repeatCount == 0
        when {
            keyCode == KeyEvent.KEYCODE_VOLUME_UP && pressed -> onVolumeUp()
            keyCode != KeyEvent.KEYCODE_VOLUME_DOWN -> Unit
            pressed && gaming() -> post(held, HOLD_MS)
            action == KeyEvent.ACTION_UP -> cancel(held)
        }
    }

    companion object {
        const val HOLD_MS = 3000L
    }
}

/** Il menu dell'uscita di emergenza, sopra il gioco: esci dalla modalità, chiudi il gioco, torna alla Home. */
internal class GamingExitMenu(
    private val service: AccessibilityService,
    private val app: NovaApp,
) {
    private var dialog: AlertDialog? = null

    fun show() {
        if (dialog?.isShowing == true) return
        val game = app.lastGame
        val choices =
            buildList<Pair<Int, () -> Unit>> {
                add(R.string.exit_menu_leave to { if (app.modes.isActive(Modes.GAMING)) app.modes.toggle(Modes.GAMING) {} })
                if (game != null) add(R.string.exit_menu_close to { GameTools(service, app).close(game) })
                add(R.string.exit_menu_home to { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) })
            }
        val labels = choices.map { service.getString(it.first) }.toTypedArray<CharSequence>()
        dialog =
            AlertDialog
                .Builder(service, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.exit_menu_title)
                .setItems(labels) { _, which -> choices[which].second() }
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .apply {
                    window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
                    show()
                }
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }
}
