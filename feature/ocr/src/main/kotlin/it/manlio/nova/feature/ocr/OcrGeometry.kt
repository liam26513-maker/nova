package it.manlio.nova.feature.ocr

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Una riga di testo trovata: centro, misure (larghezza lungo la riga) e inclinazione in radianti, in pixel della foto. */
data class TextBox(
    val cx: Float,
    val cy: Float,
    val width: Float,
    val height: Float,
    val angle: Float,
) {
    val top: Float get() = cy - height / 2
}

/**
 * La geometria della lettura del testo, senza Android né OpenCV, così si prova: dalla mappa di probabilità del rilevatore alle
 * righe (componenti connesse, rettangolo di area minima, allargamento come il «DB» di PaddleOCR) e la decodifica CTC.
 */
@Suppress("MagicNumber") // geometria: metà, doppi e angoli retti
object OcrGeometry {
    private const val THRESH = 0.3f
    private const val BOX_THRESH = 0.5f
    private const val UNCLIP = 1.6f
    private const val MIN_SIDE = 3
    private const val LINE_PX = 10f
    private const val HALF_PX = 0.5f

    /** [prob] è la mappa del rilevatore ([w]×[h]); [scaleX] e [scaleY] riportano alle misure della foto. */
    fun boxes(
        prob: FloatArray,
        w: Int,
        h: Int,
        scaleX: Float,
        scaleY: Float,
    ): List<TextBox> {
        val mask = dilate(BooleanArray(prob.size) { prob[it] > THRESH }, w)
        val seen = BooleanArray(prob.size)
        val queue = IntArray(prob.size)
        val found =
            prob.indices
                .filter { mask[it] && !seen[it] }
                .mapNotNull { start -> if (seen[start]) null else component(start, prob, mask, seen, queue, w, h) }
                .mapNotNull { (points, score) -> toBox(points, score, scaleX, scaleY) }
        // Dall'alto in basso, e nella stessa riga da sinistra a destra.
        return found.sortedWith(compareBy({ Math.round(it.top / LINE_PX) }, { it.cx - it.width / 2 }))
    }

    /** Una macchia di testo (componente connessa della maschera) e la sua probabilità media. */
    @Suppress("LongParameterList") // gli array di lavoro si riusano per non allocarli a ogni macchia
    private fun component(
        start: Int,
        prob: FloatArray,
        mask: BooleanArray,
        seen: BooleanArray,
        queue: IntArray,
        w: Int,
        h: Int,
    ): Pair<List<Pair<Int, Int>>, Float> {
        var head = 0
        var tail = 0
        queue[tail++] = start
        seen[start] = true
        var total = 0f
        val points = ArrayList<Pair<Int, Int>>()
        while (head < tail) {
            val p = queue[head++]
            total += prob[p]
            points += p % w to p / w
            for (n in neighbours(p % w, p / w, w, h)) {
                if (!mask[n] || seen[n]) continue
                seen[n] = true
                queue[tail++] = n
            }
        }
        return points to total / points.size
    }

    private fun toBox(
        points: List<Pair<Int, Int>>,
        score: Float,
        scaleX: Float,
        scaleY: Float,
    ): TextBox? {
        val rect = minRect(points)
        if (minOf(rect.width, rect.height) < MIN_SIDE || score < BOX_THRESH) return null
        val grow = rect.width * rect.height * UNCLIP / (2 * (rect.width + rect.height))
        return TextBox(
            (rect.cx + HALF_PX) * scaleX,
            (rect.cy + HALF_PX) * scaleY,
            (rect.width + 2 * grow) * scaleX,
            (rect.height + 2 * grow) * scaleY,
            rect.angle,
        )
    }

    private fun neighbours(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ): IntArray =
        buildList {
            if (x > 0) add(y * w + x - 1)
            if (x < w - 1) add(y * w + x + 1)
            if (y > 0) add((y - 1) * w + x)
            if (y < h - 1) add((y + 1) * w + x)
        }.toIntArray()

    /** Dilatazione 2×2, come PaddleOCR: unisce lettere vicine della stessa parola. */
    private fun dilate(
        mask: BooleanArray,
        w: Int,
    ): BooleanArray =
        BooleanArray(mask.size) { i ->
            val x = i % w
            val y = i / w
            mask[i] || (x > 0 && mask[i - 1]) || (y > 0 && mask[i - w]) || (x > 0 && y > 0 && mask[i - w - 1])
        }

    /** Rettangolo di area minima (calibri rotanti sull'involucro convesso); il lato lungo è la riga. */
    fun minRect(points: List<Pair<Int, Int>>): TextBox {
        val hull = hull(points)
        if (hull.size < 3) {
            val (x, y) = points.first()
            return TextBox(x.toFloat(), y.toFloat(), 1f, 1f, 0f)
        }
        var best: FloatArray? = null
        for (i in hull.indices) {
            val (x1, y1) = hull[i]
            val (x2, y2) = hull[(i + 1) % hull.size]
            val a = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
            val c = cos(a).toFloat()
            val s = sin(a).toFloat()
            var u0 = Float.MAX_VALUE
            var u1 = -Float.MAX_VALUE
            var v0 = Float.MAX_VALUE
            var v1 = -Float.MAX_VALUE
            for ((px, py) in hull) {
                val u = px * c + py * s
                val v = -px * s + py * c
                u0 = minOf(u0, u)
                u1 = maxOf(u1, u)
                v0 = minOf(v0, v)
                v1 = maxOf(v1, v)
            }
            val area = (u1 - u0) * (v1 - v0)
            if (best == null || area < best[0]) best = floatArrayOf(area, a.toFloat(), u0, u1, v0, v1)
        }
        val box = best!!
        val a0 = box[1]
        val u0 = box[2]
        val u1 = box[3]
        val v0 = box[4]
        val v1 = box[5]
        var width = u1 - u0 + 1
        var height = v1 - v0 + 1
        val uc = (u0 + u1) / 2
        val vc = (v0 + v1) / 2
        val c = cos(a0)
        val s = sin(a0)
        var angle = a0
        if (height > width) {
            width = height.also { height = width }
            angle += (PI / 2).toFloat()
        }
        // Inclinazione tra -90° e 90°: il testo non viene letto capovolto.
        angle = ((angle + PI / 2).mod(PI) - PI / 2).toFloat()
        return TextBox(uc * c - vc * s, uc * s + vc * c, width, height, angle)
    }

    private fun hull(points: List<Pair<Int, Int>>): List<Pair<Int, Int>> {
        val pts = points.distinct().sortedWith(compareBy({ it.first }, { it.second }))
        if (pts.size < 3) return pts

        fun cross(
            o: Pair<Int, Int>,
            a: Pair<Int, Int>,
            b: Pair<Int, Int>,
        ) = (a.first - o.first).toLong() * (b.second - o.second) - (a.second - o.second).toLong() * (b.first - o.first)
        val lower = ArrayList<Pair<Int, Int>>()
        for (p in pts) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0) lower.removeAt(lower.lastIndex)
            lower += p
        }
        val upper = ArrayList<Pair<Int, Int>>()
        for (p in pts.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0) upper.removeAt(upper.lastIndex)
            upper += p
        }
        return lower.dropLast(1) + upper.dropLast(1)
    }

    /** Decodifica CTC: il simbolo più probabile a ogni passo, senza ripetizioni consecutive né il vuoto (indice 0). */
    fun ctc(
        scores: FloatArray,
        steps: Int,
        classes: Int,
        chars: List<String>,
    ): String =
        buildString {
            var last = 0
            for (t in 0 until steps) {
                var best = 0
                for (k in 1 until classes) if (scores[t * classes + k] > scores[t * classes + best]) best = k
                if (best != last && best != 0) append(chars.getOrElse(best) { "" })
                last = best
            }
        }
}
