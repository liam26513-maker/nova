package it.manlio.nova

import android.app.Application
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ScreenAwakeTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun timeout() = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)

    @Test
    fun `turning it off puts back the time chosen before, even after a restart`() {
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 300_000)
        assertFalse(ScreenAwake(context).on)

        assertTrue(ScreenAwake(context).set(true))
        assertTrue(ScreenAwake(context).on)
        assertEquals(Int.MAX_VALUE, timeout())

        assertTrue(ScreenAwake(context).set(false))
        assertEquals(300_000, timeout())
    }

    @Test
    fun `turning it on twice does not lose the time chosen before`() {
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 120_000)
        ScreenAwake(context).set(true)
        ScreenAwake(context).set(true)
        ScreenAwake(context).set(false)
        assertEquals(120_000, timeout())
    }
}
