package it.manlio.nova

import kotlin.math.abs

/**
 * Riconosce «tirare giù» sulla striscia dell'angolo (centro di controllo sopra le altre app).
 * Pura, senza Android, per poterla provare con un test normale: [down] inizia il gesto, [move] dice `true`
 * una sola volta quando il dito ha superato [slop] andando in giù più che di lato; di lato o in su il gesto
 * è scartato fino al prossimo [down].
 */
internal class PullDetector(
    private val slop: Float,
) {
    private var startX = 0f
    private var startY = 0f
    private var alive = false

    fun down(
        x: Float,
        y: Float,
    ) {
        startX = x
        startY = y
        alive = true
    }

    fun move(
        x: Float,
        y: Float,
    ): Boolean {
        if (!alive) return false
        val dx = x - startX
        val dy = y - startY
        if (abs(dx) <= slop && abs(dy) <= slop) return false
        alive = false // deciso, in un verso o nell'altro: una volta sola per gesto
        return dy > abs(dx)
    }

    fun cancel() {
        alive = false
    }
}
