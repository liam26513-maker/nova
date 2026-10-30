package it.manlio.nova

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import it.manlio.nova.core.config.ConfigEngine
import it.manlio.nova.core.design.GraphicsLevel
import it.manlio.nova.core.design.GraphicsState
import it.manlio.nova.core.design.NovaTheme
import it.manlio.nova.core.device.BenchmarkResult
import it.manlio.nova.core.device.DeviceProfile
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Schermate di riferimento delle Impostazioni (tablet 1280×800, 160 dpi come il dispositivo reale). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-w1280dp-h800dp-land-mdpi", sdk = [34])
class SettingsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun render(
        theme: String,
        graphics: GraphicsState,
        onChoose: (LevelChoice) -> Unit = {},
    ) {
        val assets = RuntimeEnvironment.getApplication().assets

        fun read(p: String) = assets.open(p).bufferedReader().use { it.readText() }
        val config =
            ConfigEngine(
                1,
                ConfigEngine.assemble(read("meta.json"), read("brand.json"), read(theme), read("modules.json")),
            ).current.value
        compose.setContent {
            NovaTheme(tokensFrom(config, graphics.level)) {
                SettingsScreen(config.brand.displayName, config.meta.version, graphics, SAMPLE_PROFILE, onChoose, onMeasure = {})
            }
        }
    }

    @Test
    fun light_automatic() {
        render("theme/light.json", GraphicsState(GraphicsLevel.BALANCED, automatic = true))
        compose.onRoot().captureRoboImage("screenshots/impostazioni_chiaro_automatico.png")
    }

    @Test
    fun light_fixed_maximum() {
        render("theme/light.json", GraphicsState(GraphicsLevel.MAXIMUM, automatic = false))
        compose.onRoot().captureRoboImage("screenshots/impostazioni_chiaro_massimo.png")
    }

    @Test
    fun dark_performance() {
        render("theme/dark.json", GraphicsState(GraphicsLevel.PERFORMANCE, automatic = false))
        compose.onRoot().captureRoboImage("screenshots/impostazioni_scuro_prestazioni.png")
    }

    @Test
    fun tapping_automatic_reports_the_choice() {
        var chosen: LevelChoice? = null
        render("theme/light.json", GraphicsState(GraphicsLevel.MAXIMUM, automatic = false)) { chosen = it }
        compose.onNodeWithText("Automatico").performClick()
        assertEquals(LevelChoice.Automatic, chosen)
    }
}

/** Profilo del tablet di riferimento (SPEC §2.1), per schermate realistiche. */
private val SAMPLE_PROFILE =
    DeviceProfile(
        manufacturer = "UJJ",
        model = "U6_EEA",
        soc = "Allwinner A523",
        androidSdk = 34,
        cpuCores = 8,
        cpuClustersMhz = listOf(1800, 1416),
        ramMb = 5_980,
        freeStorageMb = 98_000,
        screenWidthPx = 1280,
        screenHeightPx = 800,
        densityDpi = 160,
        refreshRatesHz = listOf(60f),
        supportsRealtimeBlur = true,
        supportsFreeform = false,
        supportsMultipleUsers = false,
        hasStylusDigitizer = false,
        benchmark =
            BenchmarkResult(
                p90MsByLevel = mapOf(GraphicsLevel.MAXIMUM to 21f, GraphicsLevel.BALANCED to 13f, GraphicsLevel.PERFORMANCE to 9f),
                jankPercentByLevel = mapOf(GraphicsLevel.MAXIMUM to 18f, GraphicsLevel.BALANCED to 2f, GraphicsLevel.PERFORMANCE to 1f),
            ),
        startLevel = GraphicsLevel.BALANCED,
        ceiling = GraphicsLevel.BALANCED,
    )
