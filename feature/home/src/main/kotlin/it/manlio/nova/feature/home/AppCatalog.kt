package it.manlio.nova.feature.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
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

/** Stile delle icone (vedi [IconStyler]); [ink] e [plate] sono colori ARGB del tema della home. */
data class IconSpec(
    val shape: IconShape = IconShape.ORIGINAL,
    val style: IconStyle = IconStyle.COLOR,
    val iconPack: String? = null,
    val ink: Int = android.graphics.Color.WHITE,
    val plate: Int = DEFAULT_PLATE,
) {
    companion object {
        const val DEFAULT_PLATE = 0x33FFFFFF
    }
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
    private var lastSpec: IconSpec? = null
    private val densityDpi = context.resources.displayMetrics.densityDpi

    /** Pacchetti di icone installati (lettura su richiesta, solo per le impostazioni). */
    val iconPacks = IconPacks(context)
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

    /**
     * Icona dell'app a [sizePx] nello stile [spec], caricata fuori dal thread principale e tenuta in
     * memoria. Se cambia lo stile la memoria si svuota.
     */
    suspend fun icon(
        entry: AppEntry,
        sizePx: Int,
        spec: IconSpec = IconSpec(),
    ): ImageBitmap? {
        synchronized(icons) {
            if (spec != lastSpec) {
                icons.evictAll()
                lastSpec = spec
            }
        }
        return icons[entry.key] ?: withContext(Dispatchers.IO) {
            runCatching {
                val packIcon = spec.iconPack?.let { iconPacks.icon(it, entry.component.flattenToString(), densityDpi) }
                val drawable =
                    packIcon ?: launcherApps
                        .resolveActivity(Intent(Intent.ACTION_MAIN).setComponent(entry.component), entry.user)
                        ?.getBadgedIcon(densityDpi)
                drawable?.let { IconStyler.render(it, sizePx, spec.shape, spec.style, spec.ink, spec.plate) }
            }.getOrNull()
        }?.also { icons.put(entry.key, it) }
    }

    /** @return false se l'app non si può aprire (disinstallata nel frattempo, disattivata…). */
    fun launch(
        entry: AppEntry,
        sourceBounds: Rect? = null,
        options: Bundle? = null,
    ): Boolean = runCatching { launcherApps.startMainActivity(entry.component, entry.user, sourceBounds, options) }.isSuccess

    /** Apre la scheda dell'app nelle impostazioni di Android. */
    fun openInfo(
        entry: AppEntry,
        sourceBounds: Rect? = null,
    ): Boolean = runCatching { launcherApps.startAppDetailsActivity(entry.component, entry.user, sourceBounds, null) }.isSuccess

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

    private companion object {
        const val ICON_CACHE = 300
    }
}
