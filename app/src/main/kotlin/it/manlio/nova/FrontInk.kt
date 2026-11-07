package it.manlio.nova

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.hardware.HardwareBuffer
import android.os.Build
import android.view.MotionEvent
import android.view.SurfaceView
import androidx.graphics.lowlatency.CanvasFrontBufferedRenderer

/** Un pezzo di tratto: da (x0, y0) a (x1, y1), con spessore e colore. */
internal class InkSegment(
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
    val width: Float,
    val color: Int,
)

/**
 * Lo strato trasparente sopra il Foglio rapido che disegna i pezzi nuovi del tratto direttamente sul buffer dello schermo
 * (SPEC §9.2, «front-buffered»): la linea compare senza aspettare il fotogramma successivo. Finito il tratto, la tela sotto lo
 * ridisegna e questo strato si svuota. Si usa solo se il tablet lo permette ([supported]); altrimenti resta la tela normale.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility") // nasce solo dal codice; i tocchi li gestisce la tela sotto
internal class FrontInk(
    context: Context,
    private val sheet: QuickSheetView,
) : SurfaceView(context) {
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

    private val renderer =
        CanvasFrontBufferedRenderer(
            this,
            object : CanvasFrontBufferedRenderer.Callback<InkSegment> {
                override fun onDrawFrontBufferedLayer(
                    canvas: Canvas,
                    bufferWidth: Int,
                    bufferHeight: Int,
                    param: InkSegment,
                ) = draw(canvas, param)

                // Lo strato «normale» tiene il tratto appena finito finché la tela sotto non l'ha ridisegnato.
                override fun onDrawMultiBufferedLayer(
                    canvas: Canvas,
                    bufferWidth: Int,
                    bufferHeight: Int,
                    params: Collection<InkSegment>,
                ) {
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    params.forEach { draw(canvas, it) }
                }
            },
        )

    init {
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSPARENT)
        // I tocchi arrivano qui (lo strato è sopra), ma il tratto lo costruisce la tela: le misure sono le stesse.
        setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) requestUnbufferedDispatch(event)
            sheet.onTouchEvent(event)
        }
        setOnHoverListener { _, event -> sheet.onHoverEvent(event) }
    }

    fun segment(segment: InkSegment) = renderer.renderFrontBufferedLayer(segment)

    fun commit() = renderer.commit()

    /** Annulla, Ripeti e Pulisci cambiano la tela sotto: qui non deve restare niente. */
    fun clear() = renderer.renderMultiBufferedLayer(emptyList())

    fun release() = renderer.release(true)

    private fun draw(
        canvas: Canvas,
        s: InkSegment,
    ) {
        paint.color = s.color
        paint.strokeWidth = s.width
        canvas.drawLine(s.x0, s.y0, s.x1, s.y1, paint)
    }

    companion object {
        private const val PROBE = 64

        /** Vero se lo schermo accetta un buffer «davanti» (Android 13 o più e una GPU che lo gestisce). */
        fun supported(): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
            val usage =
                HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or HardwareBuffer.USAGE_COMPOSER_OVERLAY or HardwareBuffer.USAGE_FRONT_BUFFER
            return runCatching { HardwareBuffer.isSupported(PROBE, PROBE, HardwareBuffer.RGBA_8888, 1, usage) }.getOrDefault(false)
        }
    }
}
