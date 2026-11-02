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
    slop: Float,
    private val openPanel: () -> ControlSheetState?,
    private val hideAndroidShade: (Boolean) -> Unit,
) : View.OnTouchListener {
    private val detector = PullDetector(slop)
    private val velocity = VelocityTracker.obtain()
    private var sheet: ControlSheetState? = null // non nullo mentre la tendina segue il dito
    private var downY = 0f
    private var lastY = 0f

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
        velocity.clear()
        velocity.addMovement(e)
    }

    private fun move(e: MotionEvent) {
        velocity.addMovement(e)
        val following = sheet
        if (following != null) {
            following.drag(e.rawY - lastY)
        } else if (detector.move(e.rawX, e.rawY)) {
            sheet = openPanel()?.also { it.drag(e.rawY - downY) }
        } else {
            return
        }
        lastY = e.rawY
        if (sheet != null) hideAndroidShade(false)
    }

    private fun end(e: MotionEvent) {
        sheet?.let {
            velocity.addMovement(e)
            velocity.computeCurrentVelocity(PIXELS_PER_SECOND)
            it.release(velocity.yVelocity / it.travel)
            hideAndroidShade(true)
        }
        detector.cancel()
        sheet = null
    }

    fun recycle() = velocity.recycle()

    private companion object {
        const val PIXELS_PER_SECOND = 1000
    }
}
