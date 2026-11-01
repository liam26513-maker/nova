package it.manlio.nova

import android.app.Application
import android.os.BatteryManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import it.manlio.nova.core.design.NovaTheme
import it.manlio.nova.core.design.NovaTokens
import it.manlio.nova.core.design.SettingsScaffold
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-w1280dp-h800dp-land-mdpi", sdk = [34], application = Application::class)
class HealthSectionTest {
    @get:Rule val compose = createComposeRule()

    private val status =
        SystemStatus(
            ramUsedMb = 3_200,
            ramTotalMb = 5_980,
            storageFreeMb = 98_000,
            storageTotalMb = 128_000,
            batteryPercent = 76,
            batteryTempC = 31.5f,
            batteryHealth = BatteryHealth.GOOD,
            batteryCycles = 212,
            charging = true,
        )

    @Test
    fun the_status_shows_memory_space_and_battery() {
        compose.setContent { NovaTheme(NovaTokens.Fallback) { SettingsScaffold("Stato") { HealthSection(it, status) } } }
        compose.onNodeWithText("3,1 GB / 5,8 GB").assertExists()
        compose.onNodeWithText("95,7 GB liberi su 125,0 GB").assertExists()
        compose.onNodeWithText("76% · in carica").assertExists()
        compose.onNodeWithText("Buona").assertExists()
        compose.onNodeWithText("31,5 °C").assertExists()
        compose.onNodeWithText("212").assertExists()
        compose.onRoot().captureRoboImage("screenshots/impostazioni_stato_tablet.png")
    }

    @Test
    fun missing_battery_details_are_left_out() {
        compose.setContent {
            NovaTheme(NovaTokens.Fallback) {
                SettingsScaffold("Stato") {
                    HealthSection(
                        it,
                        status.copy(batteryTempC = null, batteryCycles = null, batteryHealth = BatteryHealth.OVERHEAT, charging = false),
                    )
                }
            }
        }
        compose.onNodeWithText("Surriscaldata").assertExists()
        compose.onNodeWithText("76%").assertExists()
        compose.onNodeWithText("Temperatura della batteria").assertDoesNotExist()
        compose.onNodeWithText("Cicli di carica").assertDoesNotExist()
    }

    @Test
    fun sizes_and_battery_codes_are_read_correctly() {
        assertEquals("512 MB", SystemStatusReader.size(512))
        assertEquals("1,0 GB", SystemStatusReader.size(1024))
        assertEquals("5,8 GB", SystemStatusReader.size(5_980))
        assertEquals(BatteryHealth.GOOD, SystemStatusReader.healthOf(BatteryManager.BATTERY_HEALTH_GOOD))
        assertEquals(BatteryHealth.OVERHEAT, SystemStatusReader.healthOf(BatteryManager.BATTERY_HEALTH_OVERHEAT))
        assertEquals(BatteryHealth.COLD, SystemStatusReader.healthOf(BatteryManager.BATTERY_HEALTH_COLD))
        assertEquals(BatteryHealth.UNKNOWN, SystemStatusReader.healthOf(0))
        assertEquals(BatteryHealth.UNKNOWN, SystemStatusReader.healthOf(999))
    }

    @Test
    fun animation_values_from_android_map_to_the_choices() {
        assertEquals("1", normalized("1.0"))
        assertEquals("1", normalized(null))
        assertEquals("1", normalized("abc"))
        assertEquals("0.5", normalized("0.5"))
        assertEquals("0.25", normalized("0.25"))
        assertEquals("0", normalized("0.0"))
        assertEquals("1", normalized("7")) // valore fuori scelta: si mostra Normali, non si inventa
    }
}
