package it.manlio.nova

import kotlin.math.hypot

/**
 * Ammorbidisce il tratto. Lo schermo del tablet (gslX680) manda la posizione solo dopo circa 4 pixel di movimento, quindi una linea
 * lenta arriva a scatti, a scalini. Qui ogni punto è la media degli ultimi [WINDOW] (la scala diventa una diagonale),
 * poi una curva liscia: curve quadratiche tra i punti medi, il metodo usato dalle app di disegno.
 * I movimenti più piccoli di quella soglia non arrivano mai ad Android e non si possono recuperare.
 * Puro: riceve punti (x, y, spessore) e restituisce quelli da disegnare.
 */
@Suppress("MagicNumber") // gli ultimi tre punti della curva
internal class StrokeSmoother {
    private val raw = ArrayList<FloatArray>()
    private val recent = ArrayDeque<FloatArray>()
    private var lastInput: FloatArray? = null

    /** Un punto nuovo; restituisce i punti di curva da aggiungere al tratto (x, y, spessore in fila). */
    fun add(
        x: Float,
        y: Float,
        width: Float,
    ): FloatArray {
        val last = lastInput
        if (last != null && last[0] == x && last[1] == y) return EMPTY // lo schermo ripete la stessa posizione: niente da aggiungere
        lastInput = floatArrayOf(x, y, width)
        recent.addLast(floatArrayOf(x, y, width))
        if (recent.size > WINDOW) recent.removeFirst()
        raw +=
            floatArrayOf(
                recent.sumOf {
                    it[0].toDouble()
                }.toFloat() / recent.size,
                recent.sumOf { it[1].toDouble() }.toFloat() / recent.size, width,
            )
        val n = raw.size
        if (n == 1) return floatArrayOf(x, y, width)
        if (n == 2) return EMPTY
        val a = raw[n - 3]
        val b = raw[n - 2]
        val c = raw[n - 1]
        val start = if (n == 3) a else mid(a, b)
        return curve(start, b, mid(b, c), b[2])
    }

    /** Fine del tratto: l'ultimo pezzo arriva fino al punto vero, non al punto medio. */
    fun finish(): FloatArray {
        val last = lastInput ?: return EMPTY
        if (raw.size < 2) return EMPTY
        return floatArrayOf(last[0], last[1], last[2])
    }

    private fun mid(
        p: FloatArray,
        q: FloatArray,
    ) = floatArrayOf((p[0] + q[0]) / 2, (p[1] + q[1]) / 2, (p[2] + q[2]) / 2)

    /** La curva da [from] a [to] con controllo [ctrl], in pezzetti di circa [STEP_PX]; il punto di partenza c'è già. */
    private fun curve(
        from: FloatArray,
        ctrl: FloatArray,
        to: FloatArray,
        width: Float,
    ): FloatArray {
        val length = hypot(ctrl[0] - from[0], ctrl[1] - from[1]) + hypot(to[0] - ctrl[0], to[1] - ctrl[1])
        val steps = (length / STEP_PX).toInt().coerceIn(1, MAX_STEPS)
        val out = FloatArray(steps * POINT)
        for (i in 1..steps) {
            val t = i / steps.toFloat()
            val u = 1 - t
            out[(i - 1) * POINT] = u * u * from[0] + 2 * u * t * ctrl[0] + t * t * to[0]
            out[(i - 1) * POINT + 1] = u * u * from[1] + 2 * u * t * ctrl[1] + t * t * to[1]
            out[(i - 1) * POINT + 2] = width
        }
        return out
    }

    private companion object {
        val EMPTY = FloatArray(0)
        const val POINT = 3
        const val STEP_PX = 2f
        const val MAX_STEPS = 32
        const val WINDOW = 4
    }
}
