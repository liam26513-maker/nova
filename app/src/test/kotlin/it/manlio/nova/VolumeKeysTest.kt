package it.manlio.nova

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VolumeKeysTest {
    private var pending: Runnable? = null
    private var delay = 0L
    private var gaming = true
    private var ups = 0
    private var held = 0

    private val keys =
        VolumeKeys(
            post = { r, ms ->
                pending = r
                delay = ms
            },
            cancel = { if (pending === it) pending = null },
            gaming = { gaming },
            onHeld = { held++ },
            onVolumeUp = { ups++ },
        )

    private fun press(
        code: Int,
        repeat: Int = 0,
    ) = keys.onKey(code, KeyEvent.ACTION_DOWN, repeat)

    private fun release(code: Int) = keys.onKey(code, KeyEvent.ACTION_UP, 0)

    @Test
    fun `holding volume down during gaming opens the menu after three seconds`() {
        press(KeyEvent.KEYCODE_VOLUME_DOWN)
        assertEquals(VolumeKeys.HOLD_MS, delay)
        pending!!.run()
        assertEquals(1, held)
    }

    @Test
    fun `releasing early cancels, and repeats do not restart the wait`() {
        press(KeyEvent.KEYCODE_VOLUME_DOWN)
        val first = pending
        press(KeyEvent.KEYCODE_VOLUME_DOWN, repeat = 1)
        assertEquals(first, pending)
        release(KeyEvent.KEYCODE_VOLUME_DOWN)
        assertNull(pending)
    }

    @Test
    fun `outside gaming volume down does nothing`() {
        gaming = false
        press(KeyEvent.KEYCODE_VOLUME_DOWN)
        assertNull(pending)
    }

    @Test
    fun `volume up is reported once per press, other keys are ignored`() {
        press(KeyEvent.KEYCODE_VOLUME_UP)
        press(KeyEvent.KEYCODE_VOLUME_UP, repeat = 1)
        release(KeyEvent.KEYCODE_VOLUME_UP)
        press(KeyEvent.KEYCODE_POWER)
        assertEquals(1, ups)
        assertNull(pending)
    }
}
