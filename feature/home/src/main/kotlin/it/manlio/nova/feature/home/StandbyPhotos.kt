package it.manlio.nova.feature.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Le foto a rotazione dello StandBy (SPEC §12.6), facoltative: le sceglie l'utente con il selettore di Android (nessun permesso
 * sulla galleria) e NOVA ne tiene una copia ridotta nella sua cartella, così non dipende dai file originali.
 */
object StandbyPhotos {
    const val MAX = 20
    private const val MAX_SIDE_PX = 1600
    private const val JPEG_QUALITY = 85

    private fun dir(context: Context) = File(context.filesDir, "standby-photos")

    fun list(context: Context): List<File> = dir(context).listFiles { f -> f.extension == "jpg" }?.sortedBy { it.name }.orEmpty()

    /** Copia ridotte delle foto scelte, al posto di quelle di prima. @return quante ne sono state salvate. */
    fun replace(
        context: Context,
        uris: List<Uri>,
    ): Int {
        clear(context)
        val folder = dir(context).apply { mkdirs() }
        return uris.take(MAX).withIndex().count { (i, uri) ->
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE_PX) }
                val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }!!
                File(folder, "foto-%02d.jpg".format(i)).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                bitmap.recycle()
            }.isSuccess
        }
    }

    fun clear(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }

    /** La riduzione per potenze di due più grande che lascia il lato lungo almeno [maxSide]. Puro. */
    fun sampleSize(
        width: Int,
        height: Int,
        maxSide: Int,
    ): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= maxSide) sample *= 2
        return sample
    }

    /** Ogni quanto cambia la foto: 20 secondi (prima era un minuto, troppo lento per il proprietario). */
    const val PHOTO_MS = 20_000L

    /** Quale foto mostrare ora: cambia ogni [PHOTO_MS] e ricomincia dalla prima. Puro. */
    fun indexFor(
        nowMillis: Long,
        count: Int,
    ): Int = if (count <= 0) 0 else ((nowMillis / PHOTO_MS) % count).toInt()
}

/** Lo sfondo dello StandBy con le foto: una ogni 20 secondi, con una dissolvenza lenta. Il timer gira solo a StandBy aperto. */
@Composable
internal fun StandbyPhotoShow(photos: List<File>) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(StandbyPhotos.PHOTO_MS - System.currentTimeMillis() % StandbyPhotos.PHOTO_MS)
            now = System.currentTimeMillis()
        }
    }
    val file = photos[StandbyPhotos.indexFor(now, photos.size)]
    Crossfade(file, animationSpec = tween(FADE_MS), label = "foto") { current ->
        val image by produceState<ImageBitmap?>(null, current) {
            value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(current.path)?.asImageBitmap() }.getOrNull() }
        }
        image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

private const val FADE_MS = 1200
