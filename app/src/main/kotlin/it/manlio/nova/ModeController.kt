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
import it.manlio.nova.core.modes.ModeMinutes
import it.manlio.nova.core.modes.Modes
import it.manlio.nova.core.modes.Pomodoro
import it.manlio.nova.core.modes.PomodoroPhase
import it.manlio.nova.core.modes.StudyGate
import it.manlio.nova.core.modes.StudyStore
import it.manlio.nova.core.modes.TextSessionStore
import it.manlio.nova.core.modes.UndoToken
import it.manlio.nova.core.modes.withConfig
import it.manlio.nova.core.system.QuickToggle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

/** La modalità come la vuole la configurazione attuale; senza una voce per lei, quella compilata. */
private fun configured(
    app: NovaApp,
    mode: ModeDefinition,
): ModeDefinition =
    app.config.current.value.modes.modes[mode.id]
        ?.let { mode.withConfig(it.actions, it.maxMinutes) } ?: mode

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

    val engine =
        ModeEngine(
            this,
            TextSessionStore(File(app.filesDir, "mode-session.txt")),
            catalog = { Modes.all.map { configured(app, it) } },
            onFinished = { id, spent -> minutes.add(id, LocalDate.now(), spent) },
        )

    /** Quanto tempo passa in ogni modalità (Impostazioni → Il tuo tempo). */
    val minutes = ModeMinutes(File(app.filesDir, "mode-time.properties"))

    /** [start] gira a ogni ritorno sulla Home: chi ascolta il motore deve essere uno solo. */
    private val watching = AtomicBoolean(false)

    val studyStore = StudyStore(File(app.filesDir, "studio.properties"))
    val studyGate = StudyGate(System::currentTimeMillis, studyStore)

    /** Il Pomodoro vive dentro la Studio: uscendo dalla modalità si ferma. */
    val pomodoro =
        Pomodoro(scope, System::currentTimeMillis, studyStore, { scheduleStudyAlarm(context, it) }) { phase, minutes ->
            say(context.getString(if (phase == PomodoroPhase.FOCUS) R.string.pomodoro_focus else R.string.pomodoro_break, minutes))
        }

    fun isActive(mode: ModeDefinition): Boolean = engine.active.value?.modeId == mode.id

    /** All'avvio dell'app: riprende o conclude una sessione rimasta a metà, ed esce da quella dimenticata. */
    fun start() {
        scope.launch {
            engine.recover()
            engine.exitIfExpired()
            if (isActive(Modes.STUDY)) pomodoro.restore()
            if (watching.compareAndSet(false, true)) {
                // NOVA riavviata con una modalità accesa: si rimettono grafica e rotazione senza toccare i segnalibri, così all'uscita
                // torna ciò che c'era prima. Solo al primo avvio: dopo decide l'utente.
                engine.active.value?.tokens?.forEach { token ->
                    if (token.kind == KIND_GRAPHICS) app.graphicsController.setManual(GraphicsLevel.PERFORMANCE)
                    if (token.kind == ModeSettings.KIND_ROTATION) settings.relockRotation()
                }
                engine.active.collect { if (it?.modeId != Modes.STUDY.id) pomodoro.stop() }
            }
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
                // Solo le voci che l'utente tiene accese: così si può trovare cosa fa scattare un gioco.
                val wanted = configured(app, mode)
                say(summary(mode, engine.enter(wanted.copy(actions = options.chosen(wanted)))))
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
            ModeAction.NIGHT_LIGHT_OFF -> nightLight(on = false)
            ModeAction.NIGHT_LIGHT_ON -> nightLight(on = true)
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
            KIND_NIGHT_LIGHT_ON -> !quick.available || app.systemTools.setToggle(QuickToggle.NIGHT_LIGHT, on = false, wasOn = true)
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

    private suspend fun nightLight(on: Boolean): ActionOutcome {
        if (!quick.available) return ActionOutcome.Skipped(context.getString(R.string.mode_reason_shizuku))
        if (quick.isOn(QuickToggle.NIGHT_LIGHT) == on) return ActionOutcome.Applied() // già così: all'uscita non va toccato
        val ok = app.systemTools.setToggle(QuickToggle.NIGHT_LIGHT, on = on, wasOn = !on)
        val token = UndoToken(if (on) KIND_NIGHT_LIGHT_ON else KIND_NIGHT_LIGHT)
        return if (ok) ActionOutcome.Applied(token) else ActionOutcome.Failed("Android non ha obbedito")
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
            ModeAction.NIGHT_LIGHT_ON -> R.string.mode_action_warm
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
        const val KIND_NIGHT_LIGHT_ON = "night_light_on"
        const val AUTO = "auto"
    }
}
