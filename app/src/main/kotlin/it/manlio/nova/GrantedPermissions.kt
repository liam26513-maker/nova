package it.manlio.nova

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import it.manlio.nova.core.design.SettingsRow
import it.manlio.nova.core.design.SettingsSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Un'app con i permessi «pericolosi» che ha davvero ricevuto, già tradotti da Android. */
private class Granted(
    val pkg: String,
    val app: String,
    val permissions: List<String>,
)

/** I permessi concessi a [info] tra quelli che Android considera pericolosi (fotocamera, microfono, posizione, contatti…). */
private fun dangerousGranted(
    context: Context,
    pm: PackageManager,
    info: PackageInfo,
): List<String> {
    val names = info.requestedPermissions ?: return emptyList()
    val flags = info.requestedPermissionsFlags ?: return emptyList()
    return names.indices
        .filter { flags[it] and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 }
        .mapNotNull { runCatching { pm.getPermissionInfo(names[it], 0) }.getOrNull() }
        .filter { it.protection == PermissionInfo.PROTECTION_DANGEROUS }
        .map { PermissionGroups.of(it.name) }
        .distinct()
        .sortedBy { it.ordinal }
        .map { context.getString(it.label) }
}

/** I permessi pericolosi raggruppati in poche voci comprensibili: Android ne ha decine con nomi lunghissimi. Puro. */
internal enum class PermissionGroups(val label: Int, private vararg val words: String) {
    CAMERA(R.string.perm_camera, "CAMERA"),
    MICROPHONE(R.string.perm_microphone, "RECORD_AUDIO"),
    LOCATION(R.string.perm_location, "LOCATION"),
    CONTACTS(R.string.perm_contacts, "CONTACTS", "ACCOUNTS"),
    PHONE(R.string.perm_phone, "PHONE", "CALL", "VOICEMAIL", "SIP"),
    SMS(R.string.perm_sms, "SMS", "MMS", "WAP_PUSH", "CELL_BROADCAST"),
    CALENDAR(R.string.perm_calendar, "CALENDAR"),
    MEDIA(R.string.perm_media, "STORAGE", "MEDIA_"),
    NEARBY(R.string.perm_nearby, "BLUETOOTH", "NEARBY", "UWB"),
    BODY(R.string.perm_body, "SENSORS", "ACTIVITY_RECOGNITION"),
    NOTIFICATIONS(R.string.perm_notifications, "NOTIFICATIONS"),
    OTHER(R.string.perm_other),
    ;

    companion object {
        fun of(permission: String): PermissionGroups {
            val name = permission.substringAfterLast('.')
            return entries.firstOrNull { group -> group.words.any { it in name } } ?: OTHER
        }
    }
}

private fun readGranted(context: Context): List<Granted> {
    val pm = context.packageManager
    return pm
        .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName != context.packageName }
        .mapNotNull { app ->
            val info = runCatching { pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS) }.getOrNull()
            val granted = info?.let { dangerousGranted(context, pm, it) }.orEmpty()
            if (granted.isEmpty()) null else Granted(app.packageName, pm.getApplicationLabel(app).toString(), granted)
        }.sortedByDescending { it.permissions.size }
}

/** Impostazioni → Privacy: quali permessi ha ogni app (SPEC §13.3). Non serve Shizuku; un tocco apre i permessi in Android. */
@Composable
internal fun GrantedSection(backdrop: ImageBitmap) {
    val context = LocalContext.current
    val apps by produceState<List<Granted>?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { readGranted(context) }.getOrDefault(emptyList()) }
    }
    SettingsSection(backdrop, header = stringResource(R.string.granted_title), footer = stringResource(R.string.granted_footer)) {
        val list = apps
        if (list == null) SettingsRow(stringResource(R.string.privacy_loading))
        list?.forEachIndexed { index, app ->
            SettingsRow(
                app.app,
                summary = app.permissions.joinToString(", "),
                showDivider = index > 0,
                onClick = { openPermissions(context, app.pkg) },
            )
        }
    }
}
