package it.manlio.nova

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/** Le regole del foglio (SPEC §9.3), senza disegno, così si provano. */
internal object SheetRules {
    private const val PEN_MIN = 0.4f
    private const val PEN_RANGE = 1.2f
    private const val PEN_DP = 3f
    private const val ERASER_DP = 24f

    /** Con la penna vicina il dito non disegna: è quasi sempre il palmo appoggiato. */
    fun accepts(
        tool: Int,
        stylusNear: Boolean,
    ) = tool != MotionEvent.TOOL_TYPE_FINGER || !stylusNear

    /** Lo spessore in dp: la penna segue la pressione, la gomma è larga. */
    fun widthDp(
        erasing: Boolean,
        pressure: Float,
    ) = if (erasing) ERASER_DP else PEN_DP * (PEN_MIN + PEN_RANGE * pressure.coerceIn(0f, 1f))

    fun erasing(
        tool: Int,
        buttons: Int,
    ) = tool == MotionEvent.TOOL_TYPE_ERASER || buttons and MotionEvent.BUTTON_STYLUS_PRIMARY != 0
}

/**
 * La tela del foglio rapido. Il tratto in corso si disegna sopra l'immagine e ci entra solo alla fine: così l'immagine non si
 * ricarica a ogni fotogramma. La gomma dipinge col colore della carta.
 * shortcut: disegno normale, non front-buffered (§9.2); da rivedere se sul tablet il tratto resta indietro rispetto alla penna.
 */
@SuppressLint("ViewConstructor") // nasce solo dal codice, con la misura della tela
internal class QuickSheetView(
    context: Context,
    side: Int,
) : View(context) {
    /** Un tratto: x, y e spessore in fila per ogni punto; [clear] è il tasto Pulisci, che così si può annullare. */
    private class Stroke(
        val erasing: Boolean,
        val clear: Boolean = false,
    ) {
        val points = ArrayList<Float>()
    }

    private val base = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).apply { eraseColor(PAPER) }
    private val image = base.copy(Bitmap.Config.ARGB_8888, true)
    private val canvas = Canvas(image)
    private val strokes = ArrayList<Stroke>()
    private val density = resources.displayMetrics.density
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
    private var current: Stroke? = null
    private var stylusNear = false

    init {
        contentDescription = context.getString(R.string.sheet_title)
    }

    /** Il foglio salvato l'ultima volta: resta sotto i tratti nuovi e Annulla non lo tocca. */
    fun load(saved: Bitmap) {
        Canvas(base).drawBitmap(saved, 0f, 0f, null)
        replay()
    }

    /** Una copia da salvare mentre si continua a disegnare. */
    fun snapshot(): Bitmap = image.copy(Bitmap.Config.ARGB_8888, false)

    fun undo() {
        if (strokes.isEmpty()) return
        strokes.removeAt(strokes.lastIndex)
        replay()
    }

    fun clear() {
        strokes.add(Stroke(erasing = false, clear = true))
        replay()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(image, 0f, 0f, null)
        current?.let { draw(canvas, it) }
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        stylusNear = event.actionMasked != MotionEvent.ACTION_HOVER_EXIT && event.getToolType(0) != MotionEvent.TOOL_TYPE_FINGER
        return super.onHoverEvent(event)
    }

    @SuppressLint("ClickableViewAccessibility") // disegnare è un gesto: i comandi stanno nella barra in alto
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val tool = event.getToolType(0)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (!SheetRules.accepts(tool, stylusNear)) return false
            requestUnbufferedDispatch(event)
            current = Stroke(SheetRules.erasing(tool, event.buttonState))
        }
        val stroke = current ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_CANCEL -> current = null // Android l'ha riconosciuto come palmo
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> addPoints(stroke, event)
            MotionEvent.ACTION_UP -> {
                addPoints(stroke, event)
                draw(canvas, stroke)
                strokes.add(stroke)
                current = null
            }
        }
        invalidate()
        return true
    }

    /** Anche i punti intermedi: la penna ne manda più di uno per fotogramma, senza il tratto viene spigoloso. */
    private fun addPoints(
        stroke: Stroke,
        event: MotionEvent,
    ) {
        for (i in 0 until event.historySize) {
            add(stroke, event.getHistoricalX(0, i), event.getHistoricalY(0, i), event.getHistoricalPressure(0, i))
        }
        add(stroke, event.getX(0), event.getY(0), event.getPressure(0))
    }

    private fun add(
        stroke: Stroke,
        x: Float,
        y: Float,
        pressure: Float,
    ) {
        stroke.points.add(x)
        stroke.points.add(y)
        stroke.points.add(SheetRules.widthDp(stroke.erasing, pressure) * density)
    }

    private fun draw(
        target: Canvas,
        stroke: Stroke,
    ) {
        if (stroke.clear) return target.drawColor(PAPER)
        val p = stroke.points
        paint.color = if (stroke.erasing) PAPER else INK
        var i = 0
        while (i < p.size) {
            val from = maxOf(i - POINT, 0)
            paint.strokeWidth = p[i + 2]
            target.drawLine(p[from], p[from + 1], p[i], p[i + 1], paint)
            i += POINT
        }
    }

    private fun replay() {
        canvas.drawBitmap(base, 0f, 0f, null)
        strokes.forEach { draw(canvas, it) }
        invalidate()
    }

    private companion object {
        const val POINT = 3
        val PAPER = Color.rgb(0xF7, 0xF6, 0xF3)
        val INK = Color.rgb(0x1D, 0x1D, 0x1F)
    }
}
