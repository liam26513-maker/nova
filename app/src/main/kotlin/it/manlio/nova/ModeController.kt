package it.manlio.nova

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import it.manlio.nova.core.design.GraphicsLevel
import it.manlio.nova.core.modes.ActionOutcome
import it.manlio.nova.core.modes.EnterReport
import it.manlio.nova.core.modes.ModeAction
import it.manlio.nova.core.modes.ModeDefinition
import it.manlio.nova.core.modes.ModeEngine
import it.manlio.nova.core.modes.ModeEnvironment
import it.manlio.nova.core.modes.Modes
import it.manlio.nova.core.modes.TextSessionStore
import it.manlio.nova.core.modes.UndoToken
import it.manlio.nova.core.system.QuickToggle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * Le modalità (SPEC §8) sul tablet vero: traduce ogni [ModeAction] in ciò che Android permette e sa annullarla.
 * Il motore ([ModeEngine]) tiene il registro su file: se l'app si chiude a metà, al prossimo avvio si ripristina.
 * Ciò che non si può fare (senza Shizuku, senza permesso) viene saltato e detto, mai finto.
 */
class ModeController(
    private val app: NovaApp,
    private val scope: CoroutineScope,
) : ModeEnvironment {
    private val context: Context = app
    private val quick = QuickToggles(app, app, scope)
    private val main = Handler(Looper.getMainLooper())
    private val settings = ModeSettings(context)

    /** Quante app ha chiuso il Turbo all'ultimo ingresso (per dirlo all'utente). */
    private var turboClosed = 0

    /** Cosa fa la modalità, voce per voce (scelto in Impostazioni → Modalità Gaming). */
    val options = ModeOptions(File(app.filesDir, "mode-options.properties"))

    val engine = ModeEngine(this, TextSessionStore(File(app.filesDir, "mode-session.txt")))

    fun isActive(mode: ModeDefinition): Boolean = engine.active.value?.modeId == mode.id

    /** All'avvio dell'app: riprende o conclude una sessione rimasta a metà, ed esce da quella dimenticata. */
    fun start() {
        scope.launch {
            engine.recover()
            engine.exitIfExpired()
        }
    }

    /** Entra o esce dalla modalità [mode]; [onDone] riceve lo stato finale (vero = attiva). Un avviso a schermo dice com'è andata. */
    fun toggle(
        mode: ModeDefinition,
        onDone: (Boolean) -> Unit,
    ) {
        scope.launch {
            val on = !isActive(mode)
            if (on) {
                // Per la Gaming solo le voci che l'utente tiene accese: così si può trovare cosa fa scattare un gioco.
                val actions = if (mode == Modes.GAMING) mode.actions.filter(options::enabled) else mode.actions
                say(summary(mode, engine.enter(mode.copy(actions = actions))))
            } else {
                val report = engine.exit()
                say(context.getString(if (report.pending == 0) R.string.mode_off else R.string.mode_off_pending, name(mode)))
            }
            onDone(isActive(mode))
        }
    }

    override suspend fun apply(action: ModeAction): ActionOutcome =
        when (action) {
            ModeAction.PERFORMANCE_GRAPHICS -> performanceGraphics()
            ModeAction.DO_NOT_DISTURB -> doNotDisturb()
            ModeAction.LOCK_ROTATION -> settings.lockRotation()
            ModeAction.KEEP_SCREEN_ON -> settings.keepScreenOn()
            ModeAction.PAUSE_SYNC -> settings.pauseSync()
            ModeAction.NIGHT_LIGHT_OFF -> nightLightOff()
            ModeAction.FULL_BRIGHTNESS -> settings.fullBrightness()
            ModeAction.TURBO -> turbo()
        }

    override suspend fun undo(token: UndoToken): Boolean =
        when (token.kind) {
            KIND_GRAPHICS -> restoreGraphics(token.value)
            KIND_DND -> !quick.available || app.systemTools.setToggle(QuickToggle.DND, on = false, wasOn = true)
            ModeSettings.KIND_ROTATION -> settings.restoreRotation(token.value)
            ModeSettings.KIND_SCREEN_ON -> settings.restoreScreenTimeout(token.value)
            ModeSettings.KIND_SYNC -> settings.resumeSync()
            KIND_NIGHT_LIGHT -> !quick.available || app.systemTools.setToggle(QuickToggle.NIGHT_LIGHT, on = true, wasOn = false)
            ModeSettings.KIND_BRIGHTNESS -> settings.restoreBrightness(token.value)
            else -> true // segnalibro di una versione futura o sconosciuto: niente da annullare
        }

    private fun performanceGraphics(): ActionOutcome {
        val state = app.graphics.value
        app.graphicsController.setManual(GraphicsLevel.PERFORMANCE)
        return ActionOutcome.Applied(UndoToken(KIND_GRAPHICS, if (state.automatic) AUTO else state.level.name))
    }

    private fun restoreGraphics(value: String): Boolean {
        if (value == AUTO) {
            app.graphicsController.setAutomatic()
        } else {
            app.graphicsController.setManual(runCatching { GraphicsLevel.valueOf(value) }.getOrDefault(GraphicsLevel.BALANCED))
        }
        return true
    }

    private suspend fun doNotDisturb(): ActionOutcome {
        if (!quick.available) return ActionOutcome.Skipped(context.getString(R.string.mode_reason_shizuku))
        if (quick.isOn(QuickToggle.DND)) return ActionOutcome.Applied() // già acceso dall'utente: non lo spegniamo noi all'uscita
        val ok = app.systemTools.setToggle(QuickToggle.DND, on = true, wasOn = false)
        return if (ok) ActionOutcome.Applied(UndoToken(KIND_DND)) else ActionOutcome.Failed("Android non ha obbedito")
    }

    private suspend fun nightLightOff(): ActionOutcome {
        if (!quick.available) return ActionOutcome.Skipped(context.getString(R.string.mode_reason_shizuku))
        if (!quick.isOn(QuickToggle.NIGHT_LIGHT)) return ActionOutcome.Applied() // già spento: all'uscita non va acceso
        val ok = app.systemTools.setToggle(QuickToggle.NIGHT_LIGHT, on = false, wasOn = true)
        return if (ok) ActionOutcome.Applied(UndoToken(KIND_NIGHT_LIGHT)) else ActionOutcome.Failed("Android non ha obbedito")
    }

    private suspend fun turbo(): ActionOutcome {
        if (!quick.available) return ActionOutcome.Skipped(context.getString(R.string.mode_reason_shizuku))
        val done = PerformanceUi(app, scope).runTurbo(withGraphics = false) // la grafica la gestisce la modalità, con il suo ripristino
        turboClosed = done?.stopped ?: 0
        return if (done == null) ActionOutcome.Failed("Turbo non riuscito") else ActionOutcome.Applied()
    }

    /** Il nome della modalità per gli avvisi e per la riga in Altro. */
    fun name(mode: ModeDefinition): String =
        context.getString(
            when (mode) {
                Modes.STUDY -> R.string.more_study
                Modes.WORK -> R.string.more_work
                Modes.CREATIVE -> R.string.more_creative
                Modes.PRESENTATION -> R.string.more_presentation
                else -> R.string.more_gaming
            },
        )

    private fun summary(
        mode: ModeDefinition,
        report: EnterReport,
    ): String {
        val overlay = mode != Modes.GAMING || options.overlayInGames
        val hint = context.getString(if (overlay) R.string.mode_exit_overlay else R.string.mode_exit_home, name(mode))
        val done = report.applied.joinToString(", ") { context.getString(label(it)) }
        val turbo = if (ModeAction.TURBO in report.applied) " " + context.getString(R.string.mode_turbo_closed, turboClosed) else ""
        val missing = report.skipped + report.failed
        if (missing.isEmpty()) return context.getString(R.string.mode_on, name(mode), done) + turbo + " " + hint
        val names = missing.joinToString(", ") { context.getString(label(it.first)) }
        val why = missing.map { it.second }.distinct().joinToString("; ")
        return context.getString(R.string.mode_on_partial, name(mode), done, names, why) + turbo + " " + hint
    }

    private fun label(action: ModeAction): Int =
        when (action) {
            ModeAction.PERFORMANCE_GRAPHICS -> R.string.mode_action_graphics
            ModeAction.DO_NOT_DISTURB -> R.string.mode_action_dnd
            ModeAction.LOCK_ROTATION -> R.string.mode_action_rotation
            ModeAction.KEEP_SCREEN_ON -> R.string.mode_action_screen
            ModeAction.PAUSE_SYNC -> R.string.mode_action_sync
            ModeAction.NIGHT_LIGHT_OFF -> R.string.mode_action_night_light
            ModeAction.FULL_BRIGHTNESS -> R.string.mode_action_brightness
            ModeAction.TURBO -> R.string.mode_action_turbo
        }

    private fun say(text: String) {
        main.post { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
    }

    private companion object {
        const val KIND_GRAPHICS = "graphics"
        const val KIND_DND = "dnd"
        const val KIND_NIGHT_LIGHT = "night_light"
        const val AUTO = "auto"
    }
}
