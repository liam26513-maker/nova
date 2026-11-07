package it.manlio.nova

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import androidx.input.motionprediction.MotionEventPredictor

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
 * Per sembrare più pronto, il tratto in corso disegna anche il punto dove la penna sta per arrivare ([MotionEventPredictor], §9.2).
 * Se il tablet lo permette, il tratto in corso lo disegna lo strato veloce [FrontInk] e questa tela ridisegna solo a tratto finito.
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
        val color: Int = INK,
    ) {
        val points = ArrayList<Float>()
        val smoother = StrokeSmoother()
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
    private val undone = ArrayList<Stroke>()
    private val predictor = MotionEventPredictor.newInstance(this)
    private val ahead = ArrayList<Float>()

    /** Lo strato veloce sopra la tela, se il tablet lo permette: allora il tratto in corso lo disegna lui. */
    var front: FrontInk? = null
    private var sent = 0

    /** Strumenti scelti nella barra: colore, spessore (moltiplica quello della pressione) e gomma. */
    var color = INK
    var size = 1f
    var eraser = false

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
        undone += strokes.removeAt(strokes.lastIndex)
        front?.clear()
        replay()
    }

    fun redo() {
        if (undone.isEmpty()) return
        strokes += undone.removeAt(undone.lastIndex)
        front?.clear()
        replay()
    }

    fun clear() {
        strokes.add(Stroke(erasing = false, clear = true))
        undone.clear()
        front?.clear()
        replay()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawBitmap(image, 0f, 0f, null)
        if (front == null) {
            current?.let {
                draw(canvas, it)
                drawAhead(canvas, it)
            }
        }
    }

    /** Il pezzetto previsto: dall'ultimo punto vero a dove arriverà la penna. Non entra mai nel disegno. */
    private fun drawAhead(
        target: Canvas,
        stroke: Stroke,
    ) {
        val p = stroke.points
        if (ahead.isEmpty() || p.size < POINT) return
        paint.color = if (stroke.erasing) PAPER else stroke.color
        var x = p[p.size - POINT]
        var y = p[p.size - POINT + 1]
        paint.strokeWidth = p[p.size - 1]
        var i = 0
        while (i < ahead.size) {
            target.drawLine(x, y, ahead[i], ahead[i + 1], paint)
            x = ahead[i]
            y = ahead[i + 1]
            i += 2
        }
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
            current = Stroke(eraser || SheetRules.erasing(tool, event.buttonState), color = color)
            sent = 0
        }
        val stroke = current ?: return false
        predictor.record(event)
        ahead.clear()
        when (event.actionMasked) {
            MotionEvent.ACTION_CANCEL -> {
                current = null // Android l'ha riconosciuto come palmo
                front?.clear()
            }
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                addPoints(stroke, event)
                val fast = front
                if (fast != null) return true.also { sendNew(fast, stroke) }
                predictor.predict()?.let { predicted ->
                    for (i in 0 until predicted.historySize) {
                        ahead += predicted.getHistoricalX(0, i)
                        ahead += predicted.getHistoricalY(0, i)
                    }
                    ahead += predicted.x
                    ahead += predicted.y
                    predicted.recycle()
                }
            }
            MotionEvent.ACTION_UP -> {
                addPoints(stroke, event)
                stroke.smoother.finish().forEach(stroke.points::add)
                front?.let { sendNew(it, stroke) }
                draw(canvas, stroke)
                strokes.add(stroke)
                undone.clear()
                current = null
                front?.commit()
            }
        }
        invalidate()
        return true
    }

    /** Allo strato veloce solo i pezzi nuovi del tratto, da dove si era arrivati. */
    private fun sendNew(
        fast: FrontInk,
        stroke: Stroke,
    ) {
        val p = stroke.points
        while (sent + POINT <= p.size) {
            val from = maxOf(sent - POINT, 0)
            fast.segment(InkSegment(p[from], p[from + 1], p[sent], p[sent + 1], p[sent + 2], if (stroke.erasing) PAPER else stroke.color))
            sent += POINT
        }
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
        stroke.smoother.add(x, y, SheetRules.widthDp(stroke.erasing, pressure) * density * size).forEach(stroke.points::add)
    }

    private fun draw(
        target: Canvas,
        stroke: Stroke,
    ) {
        if (stroke.clear) return target.drawColor(PAPER)
        val p = stroke.points
        paint.color = if (stroke.erasing) PAPER else stroke.color
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

    companion object {
        private const val POINT = 3
        private val PAPER = Color.rgb(0xF7, 0xF6, 0xF3)
        val INK = Color.rgb(0x1D, 0x1D, 0x1F)

        /** I colori della barra: inchiostro, blu, rosso, verde, arancio. */
        val COLORS =
            listOf(INK, Color.rgb(0x2F, 0x6F, 0xD6), Color.rgb(0xD6, 0x3A, 0x3A), Color.rgb(0x3F, 0xA3, 0x4D), Color.rgb(0xE0, 0x92, 0x2F))
        val SIZES = listOf(0.6f, 1f, 2.2f)
    }
}
