package it.manlio.nova

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import it.manlio.nova.core.system.ShizukuStatus
import it.manlio.nova.core.update.UpdateState
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
// Application vuota: le schermate non devono avviare Shizuku, WorkManager o la rete.
@Config(qualifiers = "it-w1280dp-h800dp-land-mdpi", sdk = [34], application = Application::class)
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
                ConfigEngine.assemble(
                    read("meta.json"),
                    read("brand.json"),
                    read(theme),
                    read("modules.json"),
                    read("modes.json"),
                    read("debloat.json"),
                ),
            ).current.value
        compose.setContent {
            NovaTheme(tokensFrom(config, graphics.level)) {
                SettingsScreen(
                    brandName = config.brand.displayName,
                    configVersion = config.meta.version,
                    graphics = graphics,
                    profile = SAMPLE_PROFILE,
                    onChoose = onChoose,
                    onMeasure = {},
                    onShowReport = {},
                    system = SAMPLE_SYSTEM,
                    systemActions = SystemActions({}, {}, { null }, {}, {}, {}),
                )
            }
        }
    }

    @Test
    fun light_automatic() {
        render("theme/light.json", GraphicsState(GraphicsLevel.BALANCED, automatic = true))
        compose.onRoot().captureRoboImage("screenshots/impostazioni_chiaro_automatico.png")
    }

    /** Le altre categorie della barra laterale (Aggiornamenti e Strumenti di sistema sono le più lunghe). */
    @Test
    fun updates_and_tools_categories() {
        render("theme/light.json", GraphicsState(GraphicsLevel.BALANCED, automatic = true))
        compose.onNodeWithText("Aggiornamenti").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/impostazioni_aggiornamenti.png")
        compose.onNodeWithText("Strumenti di sistema").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/impostazioni_strumenti.png")
    }

    /** Schermo stretto: prima l'elenco delle categorie, poi il dettaglio con «‹» per tornare. */
    @Test
    @Config(qualifiers = "it-w411dp-h891dp-port-xhdpi")
    fun narrow_screen_shows_the_list_then_the_detail() {
        render("theme/light.json", GraphicsState(GraphicsLevel.BALANCED, automatic = true))
        compose.onRoot().captureRoboImage("screenshots/impostazioni_stretto_elenco.png")
        compose.onNodeWithText("Il tuo tablet").performClick()
        // Il passaggio al dettaglio è animato: su una macchina lenta il testo arriva dopo il primo «idle».
        compose.waitUntil(DETAIL_TIMEOUT_MS) {
            compose.onAllNodesWithText("Processore", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onRoot().captureRoboImage("screenshots/impostazioni_stretto_dettaglio.png")
        compose.onNodeWithText("‹  Impostazioni").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Aggiornamenti").assertExists()
    }

    @Test
    fun light_fixed_maximum() {
        render("theme/light.json", GraphicsState(GraphicsLevel.MAXIMUM, automatic = false))
        compose.onNodeWithText("Livello grafico").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/impostazioni_chiaro_massimo.png")
    }

    @Test
    fun dark_performance() {
        render("theme/dark.json", GraphicsState(GraphicsLevel.PERFORMANCE, automatic = false))
        compose.onNodeWithText("Livello grafico").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/impostazioni_scuro_prestazioni.png")
    }

    @Test
    fun report() {
        val assets = RuntimeEnvironment.getApplication().assets

        fun read(p: String) = assets.open(p).bufferedReader().use { it.readText() }
        val config =
            ConfigEngine(
                1,
                ConfigEngine.assemble(
                    read("meta.json"),
                    read("brand.json"),
                    read("theme/light.json"),
                    read("modules.json"),
                    read("modes.json"),
                    read("debloat.json"),
                ),
            ).current.value
        compose.setContent {
            NovaTheme(tokensFrom(config, GraphicsLevel.BALANCED)) { BenchmarkReportScreen(SAMPLE_PROFILE, onDone = {}) }
        }
        compose.onRoot().captureRoboImage("screenshots/resoconto_misura.png")
    }

    @Test
    fun tapping_maximum_is_allowed_even_when_the_ceiling_is_lower() {
        var chosen: LevelChoice? = null
        render("theme/light.json", GraphicsState(GraphicsLevel.BALANCED, automatic = true)) { chosen = it }
        compose.onNodeWithText("Livello grafico").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Massimo").performClick()
        assertEquals(LevelChoice.Fixed(GraphicsLevel.MAXIMUM), chosen)
    }

    @Test
    fun tapping_automatic_reports_the_choice() {
        var chosen: LevelChoice? = null
        render("theme/light.json", GraphicsState(GraphicsLevel.MAXIMUM, automatic = false)) { chosen = it }
        compose.onNodeWithText("Livello grafico").performClick()
        compose.waitForIdle()
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

private val SAMPLE_SYSTEM =
    SystemUiState(
        versionName = "0.1.0",
        update = UpdateState.UpToDate(checkedAt = 0),
        hasConfigHistory = true,
        shizuku = ShizukuStatus.NEEDS_PERMISSION,
        systemChanges = 2,
    )

private const val DETAIL_TIMEOUT_MS = 5_000L
