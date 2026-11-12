package it.manlio.nova

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import it.manlio.nova.core.design.Nova
import it.manlio.nova.core.design.SettingsRow
import it.manlio.nova.core.design.SettingsSection
import it.manlio.nova.core.modes.Modes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

private const val TOP_APPS = 8
private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60
private const val WEEK_DAYS = 6L

/** Un'app con i suoi minuti in primo piano. */
private data class AppTime(val pkg: String, val name: String, val minutes: Int)

/** [days]: per ognuno degli ultimi 7 giorni (dal più vecchio a oggi), i minuti di ogni app. */
private class Usage(val today: List<AppTime>, val week: List<AppTime>, val days: List<Map<String, Int>>, val start: LocalDate)

/** «45 min» o «2 h 5 min». */
internal fun readableTime(minutes: Int): String {
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        hours == 0 -> "$rest min"
        rest == 0 -> "$hours h"
        else -> "$hours h $rest min"
    }
}

private fun hasUsageAccess(context: Context): Boolean {
    val ops = context.getSystemService(AppOpsManager::class.java)
    return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) ==
        AppOpsManager.MODE_ALLOWED
}

/** I minuti di ogni app nel giorno [day] (solo app avviabili, senza NOVA e senza la tastiera). */
private fun minutesOn(
    context: Context,
    day: LocalDate,
): Map<String, Int> {
    val zone = ZoneId.systemDefault()
    val begin = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = minOf(day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), System.currentTimeMillis())
    val pm = context.packageManager
    // La tastiera è «davanti» mentre si scrive in un'altra app: contarla raddoppierebbe quel tempo.
    val keyboards = context.getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }.toSet()
    return context
        .getSystemService(UsageStatsManager::class.java)
        .queryAndAggregateUsageStats(begin, end)
        .filterKeys { it != context.packageName && it !in keyboards && pm.getLaunchIntentForPackage(it) != null }
        .mapValues { (it.value.totalTimeInForeground / MS_PER_MINUTE).toInt() }
        .filterValues { it > 0 }
}

/** Le app più usate da una somma di minuti per pacchetto. */
private fun top(
    context: Context,
    minutes: Map<String, Int>,
): List<AppTime> {
    val pm = context.packageManager
    return minutes.entries.sortedByDescending { it.value }.take(TOP_APPS).map { (pkg, min) ->
        AppTime(pkg, runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg), min)
    }
}

private suspend fun readUsage(context: Context): Usage? =
    withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        if (!hasUsageAccess(context)) {
            null
        } else {
            runCatching {
                val start = today.minusDays(WEEK_DAYS)
                val days = (0..WEEK_DAYS).map { minutesOn(context, start.plusDays(it)) }
                val week = days.flatMap { it.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
                Usage(top(context, days.last()), top(context, week), days, start)
            }.getOrNull()
        }
    }

/** Impostazioni → Il tuo tempo (SPEC §12.7): solo statistiche. Per le app serve l'accesso ai dati di utilizzo, per le modalità no. */
@Composable
internal fun TimeSection(
    backdrop: ImageBitmap,
    modes: ModeController,
) {
    val context = LocalContext.current
    var resumed by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumed++ }
    var usage by remember { mutableStateOf<Usage?>(null) }
    LaunchedEffect(resumed) { usage = readUsage(context) }
    SettingsSection(backdrop, header = stringResource(R.string.time_chart)) {
        val bars =
            remember(resumed) {
                weekBars(LocalDate.now()) { day -> Modes.all.sumOf { modes.minutes.on(it.id, day) } }
            }
        WeekChart(bars)
    }
    SettingsSection(backdrop, header = stringResource(R.string.time_modes), footer = stringResource(R.string.time_footer)) {
        val today = remember(resumed) { LocalDate.now() }
        Modes.all.forEachIndexed { index, mode ->
            val week = remember(resumed, mode) { modes.minutes.week(mode.id, today) }
            TimeRow(modes.name(mode), week, showDivider = index > 0)
        }
    }
    val apps = usage
    if (apps == null) {
        SettingsSection(backdrop, header = stringResource(R.string.time_apps)) {
            SettingsRow(
                stringResource(R.string.time_grant),
                summary = stringResource(R.string.time_grant_summary),
                onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } },
            )
        }
    } else {
        // Il grafico delle app: tutte insieme, o quella toccata nell'elenco della settimana (ritoccandola si torna a tutte).
        var chosen by remember { mutableStateOf<AppTime?>(null) }
        val pick = chosen
        SettingsSection(
            backdrop,
            header = pick?.let { stringResource(R.string.time_app_chart_one, it.name) } ?: stringResource(R.string.time_app_chart),
        ) {
            val bars =
                remember(apps, pick) {
                    weekBars(LocalDate.now()) { day ->
                        val map = apps.days.getOrNull((day.toEpochDay() - apps.start.toEpochDay()).toInt()).orEmpty()
                        if (pick == null) map.values.sum() else map[pick.pkg] ?: 0
                    }
                }
            WeekChart(bars)
        }
        AppTimes(backdrop, stringResource(R.string.time_today), apps.today)
        AppTimes(backdrop, stringResource(R.string.time_week), apps.week, chosen?.pkg) { app ->
            chosen = if (chosen?.pkg == app.pkg) null else app
        }
    }
}

@Composable
private fun AppTimes(
    backdrop: ImageBitmap,
    header: String,
    apps: List<AppTime>,
    chosen: String? = null,
    onChoose: ((AppTime) -> Unit)? = null,
) {
    SettingsSection(backdrop, header = header) {
        if (apps.isEmpty()) SettingsRow(stringResource(R.string.time_empty))
        apps.forEachIndexed { index, app ->
            TimeRow(app.name, app.minutes, showDivider = index > 0, selected = app.pkg == chosen, onClick = onChoose?.let { { it(app) } })
        }
    }
}

@Composable
private fun TimeRow(
    title: String,
    minutes: Int,
    showDivider: Boolean,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = Nova.tokens.colors
    SettingsRow(
        title,
        showDivider = showDivider,
        onClick = onClick,
        trailing = { BasicText(readableTime(minutes), style = TextStyle(if (selected) colors.accent else colors.onSurfaceMuted, 16.sp)) },
    )
}
