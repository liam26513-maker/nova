package it.manlio.nova.feature.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Disegna un'icona nello stile scelto:
 * - **forma**: originale, cerchio, squircle o quadrato arrotondato. Le icone adattive si ritagliano a
 *   piena area; le altre stanno su una base nella forma scelta;
 * - **monocromatica**: usa l'icona tematica dell'app (Android 13+) colorata con il colore del testo,
 *   o in mancanza una versione in scala di grigi.
 */
internal object IconStyler {
    fun render(
        drawable: Drawable,
        size: Int,
        shape: IconShape,
        style: IconStyle,
        ink: Int,
        plate: Int,
    ): ImageBitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val mono = (drawable as? AdaptiveIconDrawable)?.monochrome
        when {
            style == IconStyle.MONO && mono != null -> {
                fill(canvas, size, shape, plate)
                mono.mutate().colorFilter = PorterDuffColorFilter(ink, PorterDuff.Mode.SRC_IN)
                drawCentered(canvas, mono, size, scale = ADAPTIVE_BLEED)
            }
            shape == IconShape.ORIGINAL -> drawCentered(canvas, drawable, size, scale = 1f, gray = style == IconStyle.MONO)
            drawable is AdaptiveIconDrawable -> {
                canvas.clipPath(path(shape, size))
                drawCentered(canvas, drawable.background ?: drawable, size, scale = ADAPTIVE_BLEED, gray = style == IconStyle.MONO)
                drawable.foreground?.let { drawCentered(canvas, it, size, scale = ADAPTIVE_BLEED, gray = style == IconStyle.MONO) }
            }
            else -> {
                fill(canvas, size, shape, plate)
                drawCentered(canvas, drawable, size, scale = LEGACY_SCALE, gray = style == IconStyle.MONO)
            }
        }
        return bitmap.asImageBitmap()
    }

    /** Contorno della forma, in un quadrato di lato [size]. */
    fun path(
        shape: IconShape,
        size: Int,
    ): Path {
        val s = size.toFloat()
        return Path().apply {
            when (shape) {
                IconShape.CIRCLE, IconShape.ORIGINAL -> addCircle(s / 2, s / 2, s / 2, Path.Direction.CW)
                IconShape.SQUARE -> addRoundRect(RectF(0f, 0f, s, s), s * SQUARE_RADIUS, s * SQUARE_RADIUS, Path.Direction.CW)
                IconShape.SQUIRCLE -> squircle(this, s)
            }
        }
    }

    /** Superellisse (|x|^4 + |y|^4 = r^4): la forma "continua" delle icone di tipo iOS. */
    private fun squircle(
        path: Path,
        s: Float,
    ) {
        val r = s / 2
        val steps = SQUIRCLE_STEPS
        for (i in 0..steps) {
            val t = 2 * Math.PI * i / steps
            val c = Math.cos(t)
            val sn = Math.sin(t)
            val x = r + r * Math.signum(c) * Math.pow(Math.abs(c), SQUIRCLE_EXPONENT)
            val y = r + r * Math.signum(sn) * Math.pow(Math.abs(sn), SQUIRCLE_EXPONENT)
            if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
    }

    private fun fill(
        canvas: Canvas,
        size: Int,
        shape: IconShape,
        color: Int,
    ) {
        val target = if (shape == IconShape.ORIGINAL) IconShape.CIRCLE else shape
        canvas.drawPath(path(target, size), Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }

    private fun drawCentered(
        canvas: Canvas,
        drawable: Drawable,
        size: Int,
        scale: Float,
        gray: Boolean = false,
    ) {
        val side = (size * scale).toInt()
        val offset = (size - side) / 2
        drawable.setBounds(offset, offset, offset + side, offset + side)
        if (gray) drawable.mutate().colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        drawable.draw(canvas)
    }

    /** Le icone adattive hanno un margine di sicurezza (72 su 108 dp): ingrandendole riempiono la forma. */
    private const val ADAPTIVE_BLEED = 1.5f
    private const val LEGACY_SCALE = 0.7f
    private const val SQUARE_RADIUS = 0.22f
    private const val SQUIRCLE_STEPS = 96
    private const val SQUIRCLE_EXPONENT = 0.5 // 2/n con n = 4
}
