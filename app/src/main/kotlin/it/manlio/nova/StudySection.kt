package it.manlio.nova

import android.accessibilityservice.AccessibilityService
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.manlio.nova.core.design.SettingsRow
import it.manlio.nova.core.design.SettingsSection
import it.manlio.nova.core.modes.ModeAction
import it.manlio.nova.core.modes.Modes
import it.manlio.nova.core.modes.StudyGate
import it.manlio.nova.feature.home.StudyBar

@Suppress("MagicNumber") // le durate tra cui scegliere
private val FOCUS_CHOICES = listOf(15, 25, 45, 50)

@Suppress("MagicNumber")
private val PAUSE_CHOICES = listOf(5, 10, 15)

/** Impostazioni → Modalità Studio: durate del Pomodoro, minuti studiati e app da bloccare. */
@Composable
internal fun StudySection(
    backdrop: ImageBitmap,
    modes: ModeController,
) {
    val context = LocalContext.current
    val store = modes.studyStore
    val durations by store.durations.collectAsState()
    val blocked by store.blocked.collectAsState()
    val options by modes.options.values.collectAsState()
    var picking by remember { mutableStateOf(false) }
    val apps = remember(picking, blocked) { if (picking) freezable(context.packageManager, blocked.toList()) else emptyList() }
    val (today, week) = remember { modes.pomodoro.minutes() }
    SettingsSection(backdrop, header = stringResource(R.string.study_timer), footer = stringResource(R.string.study_timer_footer)) {
        Choice(R.string.study_focus, FOCUS_CHOICES, durations.focus, { "$it" }) { store.setDurations(durations.copy(focus = it)) }
        Choice(R.string.study_pause, PAUSE_CHOICES, durations.pause, { "$it" }, divider = true) {
            store.setDurations(durations.copy(pause = it))
        }
        SettingsRow(stringResource(R.string.study_stats, today, week), showDivider = true)
    }
    SettingsSection(backdrop, footer = stringResource(R.string.study_auto_footer)) {
        var hour by remember { mutableStateOf(StudySchedule.hour(context)) }
        val off = stringResource(R.string.more_off)
        Choice(R.string.study_auto, StudySchedule.CHOICES, hour, { if (it == StudySchedule.OFF) off else "$it:00" }) {
            hour = it
            StudySchedule.setHour(context, it)
        }
    }
    SettingsSection(backdrop, header = stringResource(R.string.study_reading), footer = stringResource(R.string.study_reading_footer)) {
        val key = ModeAction.NIGHT_LIGHT_ON.name
        val warm = options[key] == true
        SettingsRow(
            stringResource(R.string.study_warm),
            onClick = { modes.options.set(key, !warm) },
            trailing = { if (warm) Check() },
        )
        val calmKey = ModeAction.CALM_HOME.name
        val calm = options[calmKey] == true
        SettingsRow(
            stringResource(R.string.mode_calm_home),
            summary = stringResource(R.string.mode_calm_home_summary),
            showDivider = true,
            onClick = { modes.options.set(calmKey, !calm) },
            trailing = { if (calm) Check() },
        )
    }
    SettingsSection(backdrop, header = stringResource(R.string.study_block), footer = stringResource(R.string.study_block_footer)) {
        blocked.sorted().forEachIndexed { i, pkg ->
            SettingsRow(
                appName(context.packageManager, pkg),
                summary = stringResource(R.string.study_block_remove),
                showDivider = i > 0,
                onClick = { store.setBlocked(pkg, on = false) },
            )
        }
        SettingsRow(
            stringResource(if (picking) R.string.perf_freeze_hide else R.string.study_block_pick),
            showDivider = blocked.isNotEmpty(),
            onClick = { picking = !picking },
        )
        apps.forEach { (pkg, label) -> SettingsRow(label, showDivider = true, onClick = { store.setBlocked(pkg, on = true) }) }
    }
}

private fun appName(
    pm: PackageManager,
    pkg: String,
): String = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

/** La pastiglia della Studio per la Home, finché la modalità è accesa. */
@Composable
internal fun rememberStudyBar(
    context: Context,
    app: NovaApp,
): StudyBar? {
    val active by app.modes.engine.active.collectAsStateWithLifecycle()
    val pomodoro by app.modes.pomodoro.state.collectAsStateWithLifecycle()
    return if (active?.modeId == Modes.STUDY.id) studyBar(context, app, pomodoro) else null
}

/** Non fa niente di suo: la sveglia basta a far ripartire NOVA, che all'avvio riprende il Pomodoro e dà l'avviso. */
class PomodoroAlarm : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) = Unit
}

/** Mette la sveglia alla fine della fase, o la toglie con null. Non è esatta: Android può ritardarla di qualche minuto. */
internal fun scheduleStudyAlarm(
    context: Context,
    at: Long?,
) {
    val alarms = context.getSystemService(AlarmManager::class.java)
    val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    val wake = PendingIntent.getBroadcast(context, 0, Intent(context, PomodoroAlarm::class.java), flags)
    if (at == null) alarms.cancel(wake) else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, wake)
}

/** Durante la Studio, aprire un'app bloccata riporta alla Home e chiede cosa fare; sbloccarla chiede conferma (SPEC §8.4). */
internal class StudyGuard(
    private val service: AccessibilityService,
    private val app: NovaApp,
) {
    private var dialog: AlertDialog? = null

    fun check(pkg: String) {
        val gate = app.modes.studyGate
        if (!app.modes.isActive(Modes.STUDY) || !gate.blocks(pkg) || dialog?.isShowing == true) return
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        show(
            builder()
                .setTitle(R.string.study_blocked_title)
                .setMessage(R.string.study_blocked_text)
                .setPositiveButton(R.string.study_blocked_back, null)
                .setNegativeButton(R.string.study_blocked_unlock) { _, _ -> confirm(gate, pkg) },
        )
    }

    private fun confirm(
        gate: StudyGate,
        pkg: String,
    ) {
        show(
            builder()
                .setTitle(R.string.study_unlock_title)
                .setPositiveButton(R.string.study_blocked_back, null)
                .setNegativeButton(R.string.study_unlock_yes) { _, _ ->
                    gate.unlock(pkg)
                    service.packageManager.getLaunchIntentForPackage(pkg)?.let {
                        service.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                },
        )
    }

    private fun builder() = AlertDialog.Builder(service, android.R.style.Theme_DeviceDefault_Dialog_Alert)

    private fun show(builder: AlertDialog.Builder) {
        dialog =
            builder.create().apply {
                window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
                show()
            }
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }
}
