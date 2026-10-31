package it.manlio.nova.feature.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Un'app avviabile. [key] è stabile (componente + profilo utente) e identifica anche i preferiti. */
@Immutable
data class AppEntry(
    val key: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
) {
    val letter: Char = AppIndex.letterOf(label)
}

/**
 * Elenco delle app installate, aggiornato dalle notifiche di sistema di [LauncherApps] (niente polling).
 * Consuma qualcosa solo tra [start] e [stop], cioè mentre la home è visibile.
 */
class AppCatalog(context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val _apps = MutableStateFlow<List<AppEntry>?>(null)

    /** null finché il primo caricamento non è finito. */
    val apps: StateFlow<List<AppEntry>?> = _apps.asStateFlow()
    private val icons = LruCache<String, ImageBitmap>(ICON_CACHE)
    private var scope: CoroutineScope? = null

    private val callback =
        object : LauncherApps.Callback() {
            override fun onPackageRemoved(
                packageName: String,
                user: UserHandle,
            ) = changed(packageName)

            override fun onPackageAdded(
                packageName: String,
                user: UserHandle,
            ) = changed(packageName)

            override fun onPackageChanged(
                packageName: String,
                user: UserHandle,
            ) = changed(packageName)

            override fun onPackagesAvailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean,
            ) = packageNames.forEach(::changed)

            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean,
            ) = packageNames.forEach(::changed)
        }

    fun start() {
        if (scope != null) return
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun stop() {
        launcherApps.unregisterCallback(callback)
        scope?.cancel()
        scope = null
    }

    /** Icona dell'app a [sizePx], caricata fuori dal thread principale e tenuta in memoria. */
    suspend fun icon(
        entry: AppEntry,
        sizePx: Int,
    ): ImageBitmap? =
        icons[entry.key] ?: withContext(Dispatchers.IO) {
            runCatching {
                launcherApps
                    .resolveActivity(Intent(Intent.ACTION_MAIN).setComponent(entry.component), entry.user)
                    ?.getBadgedIcon(0)
                    ?.toImageBitmap(sizePx)
            }.getOrNull()
        }?.also { icons.put(entry.key, it) }

    /** @return false se l'app non si può aprire (disinstallata nel frattempo, disattivata…). */
    fun launch(
        entry: AppEntry,
        sourceBounds: Rect? = null,
        options: Bundle? = null,
    ): Boolean = runCatching { launcherApps.startMainActivity(entry.component, entry.user, sourceBounds, options) }.isSuccess

    private fun changed(packageName: String) {
        icons.snapshot().keys.filter { it.startsWith("$packageName/") }.forEach(icons::remove)
        refresh()
    }

    private fun refresh() {
        scope?.launch { _apps.value = load() }
    }

    private fun load(): List<AppEntry> {
        val entries =
            userManager.userProfiles.flatMap { user ->
                val serial = userManager.getSerialNumberForUser(user)
                launcherApps.getActivityList(null, user).map { info ->
                    AppEntry("${info.componentName.flattenToString()}#$serial", info.label.toString(), info.componentName, user)
                }
            }
        return AppIndex.sort(entries) { it.label }
    }

    private fun Drawable.toImageBitmap(size: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        setBounds(0, 0, size, size)
        draw(Canvas(bitmap))
        return bitmap.asImageBitmap()
    }

    private companion object {
        const val ICON_CACHE = 300
    }
}
