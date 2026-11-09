package it.manlio.nova

import it.manlio.nova.core.modes.ModeAction
import it.manlio.nova.core.modes.Modes
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
    fun `edge swipe, watchdog and auto gaming start on, the rest off, and the user's choice wins`() {
        listOf(ModeOptions.EDGE_SWIPE, ModeOptions.WATCHDOG, ModeOptions.AUTO_GAMING).forEach {
            assertTrue(it, ModeOptions.isOn(emptyMap(), it))
            assertFalse(it, ModeOptions.isOn(mapOf(it to false), it))
        }
        listOf(ModeOptions.PANEL_SWIPE, ModeOptions.PEN_BAR, ModeOptions.DESK_BAR).forEach {
            assertFalse(it, ModeOptions.isOn(emptyMap(), it))
        }
    }

    @Test
    fun `the defaults are cautious, with turbo, sync pause and the overlay off`() {
        val o = options()
        assertTrue(o.enabled(ModeAction.PERFORMANCE_GRAPHICS))
        assertTrue(o.enabled(ModeAction.DO_NOT_DISTURB))
        assertTrue(o.enabled(ModeAction.LOCK_ROTATION))
        assertTrue(o.enabled(ModeAction.KEEP_SCREEN_ON))
        assertFalse(o.enabled(ModeAction.TURBO))
        assertFalse(o.enabled(ModeAction.PAUSE_SYNC))
        assertTrue(o.overlayInGames)
    }

    @Test
    fun `study keeps its base actions and adds the warm filter only when chosen, gaming follows the choices`() {
        val o = options()
        assertEquals(listOf(ModeAction.DO_NOT_DISTURB, ModeAction.KEEP_SCREEN_ON), o.chosen(Modes.STUDY))
        assertEquals(Modes.WORK.actions, o.chosen(Modes.WORK))
        assertFalse(ModeAction.TURBO in o.chosen(Modes.GAMING))
        o.set(ModeAction.NIGHT_LIGHT_ON.name, true)
        assertTrue(ModeAction.NIGHT_LIGHT_ON in o.chosen(Modes.STUDY))
    }

    @Test
    fun `a choice is kept after a restart and only that one changes`() {
        options().set(ModeAction.TURBO.name, true)
        options().set(ModeAction.LOCK_ROTATION.name, false)
        options().set(ModeOptions.OVERLAY, false)
        val again = options()
        assertTrue(again.enabled(ModeAction.TURBO))
        assertFalse(again.enabled(ModeAction.LOCK_ROTATION))
        assertFalse(again.overlayInGames)
        assertTrue(again.enabled(ModeAction.DO_NOT_DISTURB)) // le altre restano al predefinito
    }

    @Test
    fun `a damaged file falls back to the defaults`() {
        val file = File(tmp.root, "mode/options.properties").also { it.parentFile.mkdirs() }
        file.writeBytes(byteArrayOf(0x5C, 0x75, 0x00, 0x7F)) // "\u" incompleto: non è una proprietà valida
        val o = ModeOptions(file)
        assertEquals(ModeOptions.DEFAULT_ON.contains(ModeAction.TURBO), o.enabled(ModeAction.TURBO))
        assertTrue(o.overlayInGames)
    }
}
