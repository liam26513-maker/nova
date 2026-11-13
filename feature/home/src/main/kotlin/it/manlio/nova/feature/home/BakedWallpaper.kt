package it.manlio.nova.feature.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lo sfondo della Home con le sfumature già sopra, preparato una volta fuori dal thread principale. Prima la GPU disegnava a
 * ogni fotogramma la foto e poi due sfumature a schermo intero: sul U6, dove la GPU è il limite, tre passate piene invece di una.
 * Null finché non è pronto (si disegna come prima) o se non c'è una foto.
 */
@Composable
internal fun rememberBakedWallpaper(
    image: ImageBitmap?,
    scrim: ScrimColors,
    width: Int,
    height: Int,
): ImageBitmap? =
    produceState<ImageBitmap?>(null, image, scrim, width, height) {
        value =
            if (image == null || width <= 0 || height <= 0) {
                null
            } else {
                withContext(Dispatchers.Default) { runCatching { bake(image.asAndroidBitmap(), scrim, width, height) }.getOrNull() }
            }
    }.value

/** Ritaglia [src] come `ContentScale.Crop` sulla forma dello schermo, alla risoluzione della foto, e ci disegna le sfumature. */
internal fun bake(
    src: Bitmap,
    scrim: ScrimColors,
    screenWidth: Int,
    screenHeight: Int,
): ImageBitmap {
    val c = cropRect(src.width, src.height, screenWidth, screenHeight)
    val crop = Rect(c.left, c.top, c.left + c.width, c.top + c.height)
    val out = Bitmap.createBitmap(c.width, c.height, Bitmap.Config.ARGB_8888)
    val w = out.width.toFloat()
    val h = out.height.toFloat()
    Canvas(out).apply {
        drawBitmap(src, crop, Rect(0, 0, out.width, out.height), Paint(Paint.FILTER_BITMAP_FLAG))
        val vertical = intArrayOf(scrim.top.toArgb(), scrim.middle.toArgb(), scrim.bottom.toArgb())
        drawRect(0f, 0f, w, h, Paint().apply { shader = LinearGradient(0f, 0f, 0f, h, vertical, null, Shader.TileMode.CLAMP) })
        val side = intArrayOf(scrim.side.toArgb(), Color.Transparent.toArgb())
        drawRect(0f, 0f, w, h, Paint().apply { shader = LinearGradient(0f, 0f, w, 0f, side, null, Shader.TileMode.CLAMP) })
    }
    return out.asImageBitmap()
}

internal data class Crop(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

/** La parte centrale della foto con le proporzioni dello schermo (come `ContentScale.Crop`). */
internal fun cropRect(
    srcWidth: Int,
    srcHeight: Int,
    screenWidth: Int,
    screenHeight: Int,
): Crop {
    val screen = screenWidth.toFloat() / screenHeight
    val photo = srcWidth.toFloat() / srcHeight
    return if (photo > screen) {
        val w = (srcHeight * screen).toInt().coerceIn(1, srcWidth)
        Crop((srcWidth - w) / 2, 0, w, srcHeight)
    } else {
        val h = (srcWidth / screen).toInt().coerceIn(1, srcHeight)
        Crop(0, (srcHeight - h) / 2, srcWidth, h)
    }
}

/** I colori della sfumatura sopra lo sfondo: dall'alto in basso e dal lato sinistro. */
internal data class ScrimColors(
    val top: Color,
    val middle: Color,
    val bottom: Color,
    val side: Color,
)

internal fun scrimColors(
    tint: Color,
    blurred: Boolean,
    strength: ScrimStrength,
    dark: Boolean,
): ScrimColors {
    val base = lerp(tint, if (dark) Color.Black else Color.White, if (dark) TINT_TO_BLACK else TINT_TO_WHITE)
    val factor =
        when (strength) {
            ScrimStrength.LIGHT -> LIGHT_FACTOR
            ScrimStrength.MEDIUM -> 1f
            ScrimStrength.STRONG -> STRONG_FACTOR
        }
    val (top, bottom) = (if (blurred) BLURRED_ALPHA else SOLID_ALPHA).let { (a, b) -> a * factor to (b * factor).coerceAtMost(MAX_ALPHA) }
    return ScrimColors(
        base.copy(alpha = top),
        base.copy(alpha = (top + bottom) / 2),
        base.copy(alpha = bottom),
        base.copy(alpha = SIDE_ALPHA * factor),
    )
}
