package it.manlio.nova

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AccentProviderTest {
    @Test
    fun `a chosen accent is the only thing the keyboard reads`() {
        accentCursor(0xFF4F6D8A.toInt()).use {
            assertEquals(1, it.count)
            assertEquals(1, it.columnCount)
            it.moveToFirst()
            assertEquals(0xFF4F6D8A.toInt(), it.getInt(0))
        }
    }

    @Test
    fun `with the automatic accent there is nothing to read`() {
        accentCursor(null).use { assertEquals(0, it.count) }
    }
}
