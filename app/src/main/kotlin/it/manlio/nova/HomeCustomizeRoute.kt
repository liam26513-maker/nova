package it.manlio.nova

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.manlio.nova.feature.home.IconPackInfo
import it.manlio.nova.feature.home.IconPacks
import it.manlio.nova.feature.home.WallpaperLoader
import it.manlio.nova.feature.home.WallpaperSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Collega la personalizzazione della Home al sistema: galleria, file dei temi, permesso dello sfondo, pacchetti di icone. */
@Composable
fun HomeCustomizeRoute(
    app: NovaApp,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by app.homePrefs.prefs.collectAsStateWithLifecycle()
    // Ricalcolati a ogni ritorno sulla schermata (es. dopo aver concesso il permesso o installato un pacchetto).
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val resumed = lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    var iconPacks by remember { mutableStateOf(emptyList<IconPackInfo>()) }
    var canRead by remember { mutableStateOf(false) }
    LaunchedEffect(resumed) {
        canRead = WallpaperLoader(context).canReadSystemWallpaper()
        iconPacks = withContext(Dispatchers.IO) { runCatching { IconPacks(context).installed() }.getOrDefault(emptyList()) }
    }

    fun toast(id: Int) = Toast.makeText(context, context.getString(id), Toast.LENGTH_SHORT).show()

    val pickPhoto =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                if (WallpaperLoader.savePhoto(context, uri)) {
                    app.homePrefs.update { it.copy(wallpaper = WallpaperSource.PHOTO) }
                } else {
                    toast(R.string.customize_photo_failed)
                }
            }
        }
    val exportTheme =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val ok = withContext(Dispatchers.IO) { writeText(context, uri, app.homePrefs.exportTheme()) }
                if (ok) toast(R.string.customize_theme_saved)
            }
        }
    val importTheme =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val text = withContext(Dispatchers.IO) { readText(context, uri) }
                val applied = text != null && app.homePrefs.importTheme(text)
                toast(if (applied) R.string.customize_theme_imported else R.string.customize_theme_invalid)
            }
        }

    HomeCustomizeScreen(
        prefs,
        CustomizeActions(
            onChange = { change -> scope.launch { app.homePrefs.update(change) } },
            onDone = onDone,
            onPickPhoto = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onExportTheme = { exportTheme.launch("tema.novatheme") },
            onImportTheme = { importTheme.launch(arrayOf("*/*")) },
            onGrantWallpaperAccess = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")),
                    )
                }
            },
            iconPacks = iconPacks,
            canReadWallpaper = canRead,
        ),
    )
}

/** Un tema è un piccolo file di testo: si legge al massimo 64 KB, per sicurezza. */
private fun readText(
    context: android.content.Context,
    uri: Uri,
): String? =
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(MAX_THEME_BYTES + 1)
            var total = 0
            while (total < buffer.size) {
                val n = stream.read(buffer, total, buffer.size - total)
                if (n < 0) break
                total += n
            }
            if (total > MAX_THEME_BYTES) null else String(buffer, 0, total, Charsets.UTF_8)
        }
    }.getOrNull()

private fun writeText(
    context: android.content.Context,
    uri: Uri,
    text: String,
): Boolean =
    runCatching {
        context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null
    }.getOrDefault(false)

private const val MAX_THEME_BYTES = 64 * 1024
