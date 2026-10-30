package it.manlio.nova.core.safemode

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.Nova
import it.manlio.nova.core.design.NovaTheme
import it.manlio.nova.core.design.NovaTokens
import it.manlio.nova.core.design.SettingsBlock
import it.manlio.nova.core.design.SettingsRow
import it.manlio.nova.core.design.SettingsScaffold
import it.manlio.nova.core.design.SettingsSection
import it.manlio.nova.core.system.ShizukuBridge
import it.manlio.nova.core.system.SystemChangeLog
import it.manlio.nova.core.telemetry.BootGuard
import it.manlio.nova.core.telemetry.CrashReporter
import it.manlio.nova.core.telemetry.KeyValueFile
import it.manlio.nova.core.telemetry.SafeModeReason
import kotlinx.coroutines.launch
import java.io.File

/**
 * Home minimale di emergenza (SPEC §14.2). Usa solo i token di riserva del design:
 * deve funzionare anche se la configurazione o un modulo sono rotti.
 */
class SafeModeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val reason =
            intent.getStringExtra(EXTRA_REASON)?.let { runCatching { SafeModeReason.valueOf(it) }.getOrNull() }
                ?: SafeModeReason.REQUESTED
        val lastCrash = crashDir(this).listFiles()?.maxByOrNull { it.name }?.readLines()?.take(CRASH_PREVIEW_LINES)
        val apps = launchableApps()
        val shizuku = ShizukuBridge(this).also { it.start() }
        val changes = SystemChangeLog(SystemChangeLog.fileIn(filesDir), shizuku)
        setContent {
            val scope = rememberCoroutineScope()
            var pendingChanges by remember { mutableIntStateOf(changes.entries().size) }
            NovaTheme(NovaTokens.Fallback.copy(dark = isNight())) {
                SafeModeScreen(
                    reason = reason,
                    lastCrash = lastCrash,
                    apps = apps,
                    onLeave = ::leave,
                    onSystemLauncher = { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) },
                    onOpenApp = { pkg -> packageManager.getLaunchIntentForPackage(pkg)?.let(::startActivity) },
                    systemChanges = pendingChanges,
                    onRevertSystem = {
                        scope.launch { pendingChanges = changes.revertAll() }
                    },
                )
            }
        }
    }

    private fun leave() {
        BootGuard(KeyValueFile(bootFile(this))).leaveSafeMode()
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        finish()
    }

    private fun isNight() =
        (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private fun launchableApps(): List<Pair<String, String>> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager
            .queryIntentActivities(query, PackageManager.ResolveInfoFlags.of(0))
            .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
            .filter { (pkg, _) -> pkg != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
    }

    companion object {
        const val EXTRA_REASON = "reason"
        private const val CRASH_PREVIEW_LINES = 12

        fun bootFile(context: Context) = File(context.filesDir, "boot.properties")

        fun crashDir(context: Context) = File(context.filesDir, "crashes")

        fun intent(
            context: Context,
            reason: SafeModeReason,
        ): Intent =
            Intent(context, SafeModeActivity::class.java)
                .putExtra(EXTRA_REASON, reason.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        /** Quanti report di crash aspettano di essere letti. */
        fun pendingCrashes(context: Context): Int = crashDir(context).listFiles { f -> f.name.startsWith(CrashReporter.PREFIX) }?.size ?: 0
    }
}

@Composable
private fun SafeModeScreen(
    reason: SafeModeReason,
    lastCrash: List<String>?,
    apps: List<Pair<String, String>>,
    onLeave: () -> Unit,
    onSystemLauncher: () -> Unit,
    onOpenApp: (String) -> Unit,
    systemChanges: Int,
    onRevertSystem: () -> Unit,
) {
    val t = Nova.tokens
    var showCrash by remember { mutableStateOf(false) }
    val why =
        when (reason) {
            SafeModeReason.REPEATED_START_CRASHES -> stringResource(R.string.safemode_reason_starts)
            SafeModeReason.CRASHES_AFTER_UPDATE -> stringResource(R.string.safemode_reason_update)
            SafeModeReason.REQUESTED -> stringResource(R.string.safemode_reason_requested)
        }
    SettingsScaffold(title = stringResource(R.string.safemode_title), subtitle = why) { backdrop ->
        SettingsSection(backdrop, header = stringResource(R.string.safemode_actions)) {
            SettingsRow(
                stringResource(R.string.safemode_leave),
                summary = stringResource(R.string.safemode_leave_summary),
                onClick = onLeave,
            )
            SettingsRow(
                stringResource(R.string.safemode_system_launcher),
                summary = stringResource(R.string.safemode_system_launcher_summary),
                showDivider = true,
                onClick = onSystemLauncher,
            )
            if (systemChanges > 0) {
                SettingsRow(
                    stringResource(R.string.safemode_revert_system, systemChanges),
                    summary = stringResource(R.string.safemode_revert_system_summary),
                    showDivider = true,
                    onClick = onRevertSystem,
                )
            }
            if (lastCrash != null) {
                SettingsRow(
                    stringResource(R.string.safemode_last_error),
                    summary = lastCrash.getOrNull(CRASH_TITLE_LINE),
                    showDivider = true,
                    onClick = { showCrash = !showCrash },
                )
                if (showCrash) {
                    SettingsBlock(showDivider = true) {
                        BasicText(
                            lastCrash.joinToString("\n"),
                            style = TextStyle(t.colors.onSurfaceMuted, 12.sp, fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            }
        }
        SettingsSection(backdrop, header = stringResource(R.string.safemode_apps)) {
            apps.forEachIndexed { i, (pkg, label) ->
                SettingsRow(label, showDivider = i > 0, onClick = { onOpenApp(pkg) })
            }
        }
    }
}

/** Riga del report che contiene il tipo di errore (dopo intestazione, ora, thread e riga vuota). */
private const val CRASH_TITLE_LINE = 4
