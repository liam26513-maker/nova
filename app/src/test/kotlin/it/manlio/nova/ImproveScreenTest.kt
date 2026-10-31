package it.manlio.nova

import android.app.Application
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import it.manlio.nova.core.design.NovaTheme
import it.manlio.nova.core.design.NovaTokens
import it.manlio.nova.feature.improve.ImproveMode
import it.manlio.nova.feature.improve.ImproveScreen
import it.manlio.nova.feature.improve.ImproveUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Migliora e Ripara: scrittura, esempi, cambio di scheda, anteprima del report, invio. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-w1280dp-h800dp-land-mdpi", sdk = [34], application = Application::class)
class ImproveScreenTest {
    @get:Rule val compose = createComposeRule()

    private val sent = mutableListOf<Pair<ImproveMode, String>>()

    private fun render(mode: ImproveMode) {
        compose.setContent {
            NovaTheme(NovaTokens.Fallback) {
                ImproveScreen(
                    initialMode = mode,
                    state =
                        ImproveUiState(
                            includes = listOf("Ultimi crash: 1", "Eventi di NOVA: 42"),
                            reportPreview = "# Segnalazione da NOVA\n\n**Descrizione:** prova",
                        ),
                    onSend = { m, t -> sent += m to t },
                    onDone = {},
                )
            }
        }
    }

    @Test
    fun improve_with_an_example_and_send() {
        render(ImproveMode.IMPROVE)
        compose.onRoot().captureRoboImage("screenshots/migliora.png")
        compose.onNodeWithText("Cambia il colore d'accento in viola").performClick()
        compose.onNodeWithText("Invia a Claude Code").performScrollTo().performClick()
        assertEquals(listOf(ImproveMode.IMPROVE to "Cambia il colore d'accento in viola"), sent)
    }

    @Test
    fun repair_shows_what_is_attached_and_the_anonymised_report() {
        render(ImproveMode.IMPROVE)
        compose.onNodeWithText("Ripara").performClick()
        compose.onNodeWithText("Ultimi crash: 1").assertExists()
        compose.onNodeWithText("Guarda il report").performScrollTo().performClick()
        compose.onNodeWithText("# Segnalazione da NOVA", substring = true).assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("la Home si blocca")
        compose.onRoot().captureRoboImage("screenshots/ripara.png")
        compose.onNodeWithText("Invia a Claude Code").performScrollTo().performClick()
        assertEquals(listOf(ImproveMode.REPAIR to "la Home si blocca"), sent)
    }
}
