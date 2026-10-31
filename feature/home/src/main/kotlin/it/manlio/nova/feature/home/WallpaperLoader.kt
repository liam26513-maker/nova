package it.manlio.nova.feature.home

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Environment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import it.manlio.nova.core.design.BoxBlur
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/** Sfondo pronto da disegnare: immagine già sfocata e il suo colore principale (per sfumatura e accento). */
class PreparedWallpaper(
    val image: ImageBitmap,
    val primaryColor: Int?,
)

/**
 * Prepara lo sfondo che la home disegna da sé (sfondo di sistema o foto scelta): rimpicciolito e
 * sfocato una sola volta fuori dal thread principale, poi disegnato ingrandito (costo per frame
 * quasi zero). Disegnando lo sfondo dentro NOVA la finestra è opaca: nelle app recenti non può
 * più trasparire l'app aperta prima.
 */
class WallpaperLoader(private val context: Context) {
    /** La lettura dello sfondo di sistema richiede l'accesso ai file (Android 13+). */
    fun canReadSystemWallpaper(): Boolean = Environment.isExternalStorageManager()

    suspend fun system(level: BlurLevel): PreparedWallpaper? =
        withContext(Dispatchers.IO) {
            if (!canReadSystemWallpaper()) return@withContext null
            runCatching {
                val drawable = WallpaperManager.getInstance(context).drawable ?: return@runCatching null
                val w = drawable.intrinsicWidth.coerceIn(1, MAX_SOURCE)
                val h = drawable.intrinsicHeight.coerceIn(1, MAX_SOURCE)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0, 0, w, h)
                drawable.draw(Canvas(bitmap))
                prepare(bitmap, level)
            }.getOrNull()
        }

    suspend fun photo(level: BlurLevel): PreparedWallpaper? =
        withContext(Dispatchers.IO) {
            val file = photoFile(context)
            if (!file.exists()) return@withContext null
            runCatching { BitmapFactory.decodeFile(file.path)?.let { prepare(it, level) } }.getOrNull()
        }

    companion object {
        private const val MAX_SOURCE = 4096
        private const val MAX_STORED = 2048
        private const val JPEG_QUALITY = 92

        fun photoFile(context: Context) = File(context.filesDir, "home-wallpaper.jpg")

        /** Copia la foto scelta (ridotta a 2048 px) nei file privati di NOVA. @return false se non è un'immagine. */
        suspend fun savePhoto(
            context: Context,
            uri: Uri,
        ): Boolean =
            withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    if (bounds.outWidth <= 0) return@runCatching false
                    var sample = 1
                    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_STORED) sample *= 2
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    val bitmap =
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                            ?: return@runCatching false
                    val target = photoFile(context)
                    val tmp = File(target.parentFile, "${target.name}.tmp")
                    tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                    tmp.renameTo(target)
                }.getOrDefault(false)
            }

        /** Rimpicciolisce e sfoca secondo [level]: più sfocato = immagine più piccola, quindi anche più leggera. */
        fun prepare(
            source: Bitmap,
            level: BlurLevel,
        ): PreparedWallpaper {
            val (scale, radius) = blurPlan(level)
            val w = (source.width * scale).toInt().coerceAtLeast(1)
            val h = (source.height * scale).toInt().coerceAtLeast(1)
            val small = Bitmap.createScaledBitmap(source.copy(Bitmap.Config.ARGB_8888, false), w, h, true)
            val primary = runCatching { WallpaperColors.fromBitmap(small).primaryColor.toArgb() }.getOrNull()
            if (radius == 0) return PreparedWallpaper(small.asImageBitmap(), primary)
            val pixels = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
            val blurred = BoxBlur.blur(pixels, w, h, radius)
            return PreparedWallpaper(Bitmap.createBitmap(blurred, w, h, Bitmap.Config.ARGB_8888).asImageBitmap(), primary)
        }

        /** Fattore di riduzione e raggio della sfocatura per ogni livello. */
        @Suppress("MagicNumber")
        internal fun blurPlan(level: BlurLevel): Pair<Float, Int> =
            when (level) {
                BlurLevel.NONE -> 0.75f to 0
                BlurLevel.LIGHT -> 0.25f to 3
                BlurLevel.STRONG -> 0.16f to 6
                BlurLevel.MAX -> 0.1f to 10
            }
    }
}
