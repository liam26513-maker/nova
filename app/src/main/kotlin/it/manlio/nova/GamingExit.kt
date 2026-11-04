package it.manlio.nova

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.PixelFormat
import android.os.PowerManager
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import it.manlio.nova.core.modes.Modes
import it.manlio.nova.core.system.ShizukuStatus
import it.manlio.nova.core.system.SystemCommand
import it.manlio.nova.core.system.SystemCommand.TimeStats.Step
import it.manlio.nova.core.system.TimeStatsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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

/** Doppio swipe dal bordo (SPEC §8.2, punto 1): il primo avvisa soltanto, il secondo entro [WINDOW_MS] esegue. */
internal class DoubleSwipe(private val clock: () -> Long) {
    private var first: Long? = null

    /** @return vero se questo è il secondo swipe. */
    fun swipe(): Boolean {
        val now = clock()
        val second = first?.let { now - it <= WINDOW_MS } == true
        first = if (second) null else now
        return second
    }

    private companion object {
        const val WINDOW_MS = 2000L
    }
}

/** Dice se il gioco è fermo: il conto dei fotogrammi non cresce tra due letture. Senza dati non dà mai l'allarme. */
internal class FreezeDetector {
    private var last: Int? = null

    fun update(frames: Int?): Boolean {
        val frozen = frames != null && frames == last
        last = frames
        return frozen
    }

    fun reset() {
        last = null
    }
}

/**
 * La sottile fascia sul bordo destro che riconosce il doppio swipe durante la Gaming. Prende i tocchi su quei pochi pixel, perciò
 * è spenta di serie: si accende in Impostazioni → Modalità Gaming.
 */
internal class EdgeSwipeWindow(
    private val service: AccessibilityService,
    private val onSwipe: () -> Unit,
) {
    private val windows = service.getSystemService(WindowManager::class.java)
    private var view: View? = null

    @SuppressLint("ClickableViewAccessibility") // niente da leggere: è un gesto, e il menu ha altre uscite accessibili
    fun add() {
        if (view != null) return
        val density = service.resources.displayMetrics.density
        var startX = 0f
        val edge = View(service)
        edge.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> startX = event.rawX
                MotionEvent.ACTION_UP -> if (startX - event.rawX > SWIPE_DP * density) onSwipe()
            }
            true
        }
        val params =
            WindowManager.LayoutParams(
                (EDGE_DP * density).toInt(),
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSPARENT,
            ).apply {
                gravity = Gravity.END
                setFitInsetsTypes(0)
            }
        if (runCatching { windows.addView(edge, params) }.isSuccess) view = edge
    }

    fun remove() {
        view?.let { runCatching { windows.removeView(it) } }
        view = null
    }

    private companion object {
        const val EDGE_DP = 16
        const val SWIPE_DP = 48
    }
}

/**
 * Il watchdog (SPEC §8.2, punto 3): ogni [CHECK_MS] legge il conto dei fotogrammi del gioco davanti e, se non cresce, chiama [onFrozen].
 * Ogni lettura ferma un attimo il compositore e nelle prove disturbava il gioco: per questo è spento di serie.
 */
internal class GameWatchdog(
    private val app: NovaApp,
    private val onFrozen: () -> Unit,
) {
    private val detector = FreezeDetector()

    /** Gira finché non viene annullata; alla fine spegne sempre le statistiche. */
    suspend fun watch() {
        try {
            stats(Step.ENABLE)
            while (true) {
                delay(CHECK_MS)
                if (!check()) continue
                onFrozen()
                detector.reset()
                delay(PAUSE_AFTER_ALARM_MS)
            }
        } finally {
            withContext(NonCancellable) { stats(Step.DISABLE) }
        }
    }

    private suspend fun check(): Boolean {
        val game = app.foreground.value?.takeIf(app::isGamePackage)
        if (game == null || app.gameMeter.running || !app.getSystemService(PowerManager::class.java).isInteractive) {
            detector.reset()
            return false
        }
        stats(Step.ENABLE) // la misura del gioco, finendo, le spegne
        val dump = app.shizuku.run(SystemCommand.TimeStats(Step.DUMP)).output
        return detector.update(withContext(Dispatchers.Default) { TimeStatsParser.parse(dump, game)?.frames })
    }

    private suspend fun stats(step: Step) {
        if (app.shizuku.status.value == ShizukuStatus.READY) app.shizuku.run(SystemCommand.TimeStats(step))
    }

    private companion object {
        const val CHECK_MS = 8_000L
        const val PAUSE_AFTER_ALARM_MS = 60_000L
    }
}
