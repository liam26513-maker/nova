package it.manlio.nova

import it.manlio.nova.core.modes.Modes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AccentProviderTest {
    @Test
    fun `the keyboard reads only the accent and whether Work or Study are on`() {
        accentCursor(0xFF4F6D8A.toInt(), Modes.WORK.id).use {
            assertEquals(1, it.count)
            assertEquals(3, it.columnCount)
            it.moveToFirst()
            assertEquals(0xFF4F6D8A.toInt(), it.getInt(0))
            assertEquals(1, it.getInt(1))
            assertEquals(0, it.getInt(2))
        }
    }

    @Test
    fun `with the automatic accent the colour is empty`() {
        accentCursor(null, Modes.STUDY.id).use {
            it.moveToFirst()
            assertTrue(it.isNull(0))
            assertEquals(0, it.getInt(1))
            assertEquals(1, it.getInt(2))
        }
    }
}
