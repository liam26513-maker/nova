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
 * Tiene lo sfondo della schermata di blocco e quello di sistema uguali a quello di NOVA (Impostazioni → Personalizza la Home →
 * Sfondo). Lo sfondo di sistema è quello che Android mostra dietro le App recenti, che disegna il launcher di sistema e non NOVA.
 * Le schermate restano quelle di Android: cambia solo l'immagine, con il permesso normale `SET_WALLPAPER`. Si riscrive solo se
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
            val keyFile = File(app.filesDir, "wallpaper-v2.key") // v2: anche lo sfondo di sistema, si riscrive una volta
            if (keyFile.exists() && keyFile.readText() == key) return@withContext true
            val side = maxOf(app.resources.displayMetrics.widthPixels, app.resources.displayMetrics.heightPixels)
            val bitmap = LockWallpaper.render(app, prefs, side) ?: return@withContext false
            val which = WallpaperManager.FLAG_LOCK or WallpaperManager.FLAG_SYSTEM
            val ok = runCatching { WallpaperManager.getInstance(app).setBitmap(bitmap, null, true, which) }.isSuccess
            bitmap.recycle()
            if (ok) keyFile.writeText(key)
            ok
        }
}
