package it.manlio.nova

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.Nova
import it.manlio.nova.core.design.SettingsRow
import it.manlio.nova.core.design.SettingsSection
import it.manlio.nova.core.system.AppOpsParser
import it.manlio.nova.core.system.ProtectedPackages
import it.manlio.nova.core.system.ShizukuStatus
import it.manlio.nova.core.system.SystemCommand
import kotlinx.coroutines.launch

private const val MAX_ROWS = 30
private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L

/** Un accesso da mostrare: app, permesso e quanto tempo fa. */
private class Access(
    val pkg: String,
    val app: String,
    val op: String,
    val agoMs: Long,
)

/** Le app che si aprono, ognuna con `appops get` (sola lettura): circa un decimo di secondo l'una, solo a pagina aperta. */
private suspend fun readAccesses(
    context: Context,
    app: NovaApp,
): List<Access> {
    val pm = context.packageManager
    val launchable =
        pm
            .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
    return launchable
        .flatMap { info ->
            val text = app.shizuku.run(SystemCommand.AppOps(info.packageName)).output
            val name = pm.getApplicationLabel(info).toString()
            AppOpsParser.parse(text).map { Access(info.packageName, name, it.op, it.agoMs) }
        }.sortedBy { it.agoMs }
        .distinctBy { it.pkg to it.op.substringAfter('_') } // posizione precisa e approssimativa: una riga sola
        .take(MAX_ROWS)
}

/** Impostazioni → Privacy (SPEC §13.3): chi ha usato fotocamera, microfono e posizione. Un tocco apre i permessi dell'app. */
@Composable
internal fun PrivacySection(backdrop: ImageBitmap) {
    val context = LocalContext.current
    val app = context.applicationContext as NovaApp
    var rows by remember { mutableStateOf<List<Access>?>(null) }
    val status by app.shizuku.status.collectAsState()
    val ready = status == ShizukuStatus.READY
    LaunchedEffect(ready) { if (ready) rows = runCatching { readAccesses(context, app) }.getOrDefault(emptyList()) }
    SettingsSection(backdrop, header = stringResource(R.string.privacy_recent), footer = stringResource(R.string.privacy_footer)) {
        val list = rows
        when {
            !ready -> SettingsRow(stringResource(R.string.meter_needs_shizuku))
            list == null -> SettingsRow(stringResource(R.string.privacy_loading))
            list.isEmpty() -> SettingsRow(stringResource(R.string.privacy_none))
            else ->
                list.forEachIndexed { index, access ->
                    SettingsRow(
                        access.app,
                        summary = stringResource(opLabel(access.op)),
                        showDivider = index > 0,
                        onClick = { openPermissions(context, access.pkg) },
                        trailing = { BasicText(ago(context, access.agoMs), style = TextStyle(Nova.tokens.colors.onSurfaceMuted, 15.sp)) },
                    )
                }
        }
    }
    if (ready) Firewall(backdrop, app)
}

/** Blocca Internet a singole app (SPEC §13.2). NOVA e le app di sistema protette non compaiono. */
@Composable
private fun Firewall(
    backdrop: ImageBitmap,
    app: NovaApp,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var offline by remember { mutableStateOf(app.systemChanges.offlineApps()) }
    val apps =
        remember {
            val pm = context.packageManager
            pm
                .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.applicationInfo }
                .filter { !ProtectedPackages.isProtected(it.packageName) && it.packageName != context.packageName }
                .distinctBy { it.packageName }
                .map { it.packageName to pm.getApplicationLabel(it).toString() }
                .sortedBy { it.second.lowercase() }
        }
    SettingsSection(backdrop, header = stringResource(R.string.firewall_title), footer = stringResource(R.string.firewall_footer)) {
        apps.forEachIndexed { index, (pkg, name) ->
            val blocked = pkg in offline
            SettingsRow(
                name,
                summary = if (blocked) stringResource(R.string.firewall_blocked) else null,
                showDivider = index > 0,
                onClick = {
                    scope.launch {
                        app.systemChanges.setAppNetwork(pkg, !blocked)
                        offline = app.systemChanges.offlineApps()
                    }
                },
                trailing = { if (blocked) Check() },
            )
        }
    }
}

private fun opLabel(op: String) =
    when (op) {
        "CAMERA" -> R.string.privacy_camera
        "RECORD_AUDIO" -> R.string.privacy_microphone
        else -> R.string.privacy_location
    }

private fun ago(
    context: Context,
    ms: Long,
): String {
    val minutes = ms / MS_PER_MINUTE
    val hours = minutes / MINUTES_PER_HOUR
    return when {
        minutes < 1 -> context.getString(R.string.privacy_now)
        hours < 1 -> context.getString(R.string.privacy_minutes, minutes)
        hours < HOURS_PER_DAY -> context.getString(R.string.privacy_hours, hours)
        else -> context.getString(R.string.privacy_days, hours / HOURS_PER_DAY)
    }
}

private fun openPermissions(
    context: Context,
    pkg: String,
) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)))
    }
}
