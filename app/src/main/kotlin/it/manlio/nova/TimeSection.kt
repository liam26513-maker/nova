package it.manlio.nova

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
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
private data class AppTime(val name: String, val minutes: Int)

private class Usage(val today: List<AppTime>, val week: List<AppTime>)

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

private fun topApps(
    context: Context,
    from: LocalDate,
): List<AppTime> {
    val pm = context.packageManager
    val begin = from.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return context
        .getSystemService(UsageStatsManager::class.java)
        .queryAndAggregateUsageStats(begin, System.currentTimeMillis())
        .filterKeys { it != context.packageName && pm.getLaunchIntentForPackage(it) != null }
        .map { (pkg, stats) ->
            val name = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            AppTime(name, (stats.totalTimeInForeground / MS_PER_MINUTE).toInt())
        }.filter { it.minutes > 0 }
        .sortedByDescending { it.minutes }
        .take(TOP_APPS)
}

private suspend fun readUsage(context: Context): Usage? =
    withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        if (!hasUsageAccess(context)) {
            null
        } else {
            runCatching { Usage(topApps(context, today), topApps(context, today.minusDays(WEEK_DAYS))) }.getOrNull()
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
        AppTimes(backdrop, stringResource(R.string.time_today), apps.today)
        AppTimes(backdrop, stringResource(R.string.time_week), apps.week)
    }
}

@Composable
private fun AppTimes(
    backdrop: ImageBitmap,
    header: String,
    apps: List<AppTime>,
) {
    SettingsSection(backdrop, header = header) {
        if (apps.isEmpty()) SettingsRow(stringResource(R.string.time_empty))
        apps.forEachIndexed { index, app -> TimeRow(app.name, app.minutes, showDivider = index > 0) }
    }
}

@Composable
private fun TimeRow(
    title: String,
    minutes: Int,
    showDivider: Boolean,
) {
    SettingsRow(
        title,
        showDivider = showDivider,
        trailing = { BasicText(readableTime(minutes), style = TextStyle(Nova.tokens.colors.onSurfaceMuted, 16.sp)) },
    )
}
