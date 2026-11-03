package it.manlio.nova

import android.app.WallpaperManager
import it.manlio.nova.feature.home.HomePrefs
import it.manlio.nova.feature.home.LockWallpaper
import it.manlio.nova.feature.home.WallpaperLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Tiene lo sfondo della schermata di blocco uguale a quello di NOVA (Impostazioni → Personalizza la Home → Sfondo). La schermata
 * di blocco resta quella di Android: cambia solo l'immagine, con il permesso normale `SET_WALLPAPER`. Si riscrive solo se
 * l'immagine cambia (la chiave sta in un file), non a ogni avvio.
 */
internal object LockScreenWallpaper {
    fun start(app: NovaApp) {
        app.appScope.launch {
            app.homePrefs.prefs
                .map { Triple(it.wallpaper to it.novaWallpaper, it.solidColor, it.lockWallpaper) }
                .distinctUntilChanged()
                .collect { apply(app, app.homePrefs.prefs.value) }
        }
    }

    /** Mette lo sfondo giusto se serve. @return vero se la schermata di blocco ora ha lo sfondo di NOVA. */
    suspend fun apply(
        app: NovaApp,
        prefs: HomePrefs,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val key = LockWallpaper.key(prefs, WallpaperLoader.photoFile(app).lastModified()) ?: return@withContext false
            val keyFile = File(app.filesDir, "lock-wallpaper.key")
            if (keyFile.exists() && keyFile.readText() == key) return@withContext true
            val side = maxOf(app.resources.displayMetrics.widthPixels, app.resources.displayMetrics.heightPixels)
            val bitmap = LockWallpaper.render(app, prefs, side) ?: return@withContext false
            val ok =
                runCatching { WallpaperManager.getInstance(app).setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK) }.isSuccess
            bitmap.recycle()
            if (ok) keyFile.writeText(key)
            ok
        }
}
