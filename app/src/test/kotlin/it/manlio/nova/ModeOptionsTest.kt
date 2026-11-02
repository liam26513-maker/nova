package it.manlio.nova

import it.manlio.nova.core.modes.ModeAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModeOptionsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun options() = ModeOptions(File(tmp.root, "mode/options.properties"))

    @Test
    fun `the defaults are cautious, with turbo, sync pause and the overlay off`() {
        val o = options()
        assertTrue(o.enabled(ModeAction.PERFORMANCE_GRAPHICS))
        assertTrue(o.enabled(ModeAction.DO_NOT_DISTURB))
        assertTrue(o.enabled(ModeAction.LOCK_ROTATION))
        assertTrue(o.enabled(ModeAction.KEEP_SCREEN_ON))
        assertFalse(o.enabled(ModeAction.TURBO))
        assertFalse(o.enabled(ModeAction.PAUSE_SYNC))
        assertFalse(o.overlayInGames)
    }

    @Test
    fun `a choice is kept after a restart and only that one changes`() {
        options().set(ModeAction.TURBO.name, true)
        options().set(ModeAction.LOCK_ROTATION.name, false)
        options().set(ModeOptions.OVERLAY, true)
        val again = options()
        assertTrue(again.enabled(ModeAction.TURBO))
        assertFalse(again.enabled(ModeAction.LOCK_ROTATION))
        assertTrue(again.overlayInGames)
        assertTrue(again.enabled(ModeAction.DO_NOT_DISTURB)) // le altre restano al predefinito
    }

    @Test
    fun `a damaged file falls back to the defaults`() {
        val file = File(tmp.root, "mode/options.properties").also { it.parentFile.mkdirs() }
        file.writeBytes(byteArrayOf(0x5C, 0x75, 0x00, 0x7F)) // "\u" incompleto: non è una proprietà valida
        val o = ModeOptions(file)
        assertEquals(ModeOptions.DEFAULT_ON.contains(ModeAction.TURBO), o.enabled(ModeAction.TURBO))
        assertFalse(o.overlayInGames)
    }
}
