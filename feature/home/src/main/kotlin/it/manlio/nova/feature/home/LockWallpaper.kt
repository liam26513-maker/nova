package it.manlio.nova.feature.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Lo sfondo della schermata di blocco uguale a quello di NOVA (SPEC §2.2: la schermata di blocco è quella di Android, ma lo sfondo
 * si può impostare). Lo sfondo di NOVA si disegna con lo **stesso codice** della Home ([NovaWallpaper.draw]), quindi viene identico.
 * Lo sfondo di sistema non si tocca: è già quello dell'utente.
 */
object LockWallpaper {
    /**
     * Cosa mettere: la chiave cambia solo se cambia l'immagine (così non la si riscrive a ogni avvio); `null` = non toccare la
     * schermata di blocco (opzione spenta, o sfondo di sistema). [photoStamp] è l'ora di modifica del file della foto.
     */
    fun key(
        prefs: HomePrefs,
        photoStamp: Long,
    ): String? =
        when {
            !prefs.lockWallpaper -> null
            prefs.wallpaper == WallpaperSource.NOVA -> "nova:${prefs.novaWallpaper}"
            prefs.wallpaper == WallpaperSource.SOLID -> "solid:${prefs.solidColor}"
            prefs.wallpaper == WallpaperSource.PHOTO -> "photo:$photoStamp"
            else -> null
        }

    /**
     * L'immagine quadrata di lato [size]: il sistema ne mostra la parte centrale in orizzontale e in verticale. La foto è quella scelta,
     * senza sfocatura. `null` se manca la foto.
     */
    fun render(
        context: Context,
        prefs: HomePrefs,
        size: Int,
    ): Bitmap? =
        when (prefs.wallpaper) {
            WallpaperSource.NOVA -> drawn(size) { NovaWallpapers.all[prefs.novaWallpaper.coerceIn(NovaWallpapers.all.indices)].draw(this) }
            WallpaperSource.SOLID ->
                Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(prefs.solidColor) }
            WallpaperSource.PHOTO -> WallpaperLoader.photoFile(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
            WallpaperSource.SYSTEM -> null
        }

    private fun drawn(
        size: Int,
        draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
    ): Bitmap {
        val image = ImageBitmap(size, size)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(size.toFloat(), size.toFloat()), draw)
        return image.asAndroidBitmap()
    }
}
