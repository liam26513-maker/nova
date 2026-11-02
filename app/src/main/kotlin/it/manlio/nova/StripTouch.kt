package it.manlio.nova

import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import it.manlio.nova.feature.home.ControlSheetState

/**
 * Il tocco sulla striscia in cima allo schermo (centro di controllo sopra le altre app). Appena il dito scende oltre la
 * soglia chiede [openPanel] e da quel momento **trascina la tendina col dito**, come sulla Home: il tocco resta di questa
 * finestra per tutto il gesto, quindi i movimenti arrivano qui anche se il pannello è già comparso. Al rilascio la tendina
 * si ferma dove conviene (metà o scatto). [hideAndroidShade] chiede di richiudere la tendina di Android, se il sistema
 * l'ha aperta insieme alla nostra (`true` = gesto finito).
 */
internal class StripTouch(
    private val slop: Float,
    private val openPanel: () -> ControlSheetState?,
    private val hideAndroidShade: (Boolean) -> Unit,
    private val log: (String) -> Unit = {},
) : View.OnTouchListener {
    private val detector = PullDetector(slop)
    private val velocity = VelocityTracker.obtain()
    private var sheet: ControlSheetState? = null // non nullo mentre la tendina segue il dito
    private var downY = 0f
    private var lastY = 0f
    private var maxDown = 0f // quanto è sceso il dito prima di essere riconosciuto (serve se il sistema annulla il tocco)

    override fun onTouch(
        v: View,
        e: MotionEvent,
    ): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> down(e)
            MotionEvent.ACTION_MOVE -> move(e)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> end(e)
        }
        return true
    }

    private fun down(e: MotionEvent) {
        detector.down(e.rawX, e.rawY)
        downY = e.rawY
        lastY = e.rawY
        maxDown = 0f
        velocity.clear()
        velocity.addMovement(e)
        log("tocco sulla striscia")
    }

    private fun move(e: MotionEvent) {
        velocity.addMovement(e)
        val following = sheet
        if (following == null) maxDown = maxOf(maxDown, e.rawY - downY)
        if (following != null) {
            following.drag(e.rawY - lastY)
        } else if (detector.move(e.rawX, e.rawY)) {
            sheet = openPanel()?.also { it.drag(e.rawY - downY) }
            log(if (sheet != null) "gesto riconosciuto: la tendina segue il dito" else "gesto riconosciuto ma la tendina non si apre")
        } else {
            return
        }
        lastY = e.rawY
        if (sheet != null) hideAndroidShade(false)
    }

    /**
     * Fine del tocco. Se il sistema lo **annulla** (succede quando Android riconosce a sua volta il gesto dalla barra di
     * stato) non è un «rilascio»: il dito stava scendendo, quindi la tendina si apre comunque, invece di richiudersi.
     */
    private fun end(e: MotionEvent) {
        val cancelled = e.actionMasked == MotionEvent.ACTION_CANCEL
        val following = sheet
        if (following != null) {
            velocity.addMovement(e)
            velocity.computeCurrentVelocity(PIXELS_PER_SECOND)
            if (cancelled) following.settle(true) else following.release(velocity.yVelocity / following.travel)
            hideAndroidShade(true)
        } else if (cancelled && maxDown > slop / 2) {
            openPanel()?.settle(true)
            hideAndroidShade(true)
        }
        val down = maxDown.toInt()
        log(if (cancelled) "tocco annullato dal sistema (seguiva=${following != null}, sceso=$down px)" else "tocco rilasciato")
        detector.cancel()
        sheet = null
    }

    fun recycle() = velocity.recycle()

    private companion object {
        const val PIXELS_PER_SECOND = 1000
    }
}
