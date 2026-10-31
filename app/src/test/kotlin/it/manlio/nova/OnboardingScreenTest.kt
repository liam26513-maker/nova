package it.manlio.nova

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import it.manlio.nova.core.design.NovaTheme
import it.manlio.nova.core.design.NovaTokens
import it.manlio.nova.feature.onboarding.OnboardingActions
import it.manlio.nova.feature.onboarding.OnboardingScreen
import it.manlio.nova.feature.onboarding.OnboardingStatus
import it.manlio.nova.feature.onboarding.OnboardingStep
import it.manlio.nova.feature.onboarding.ThemeOption
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La procedura guidata: si percorre tutta, ogni passo chiama l'azione giusta, il riepilogo elenca ciò che manca. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-w1280dp-h800dp-land-mdpi", sdk = [34], application = Application::class)
class OnboardingScreenTest {
    @get:Rule val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private var status by mutableStateOf(OnboardingStatus(themes = listOf(ThemeOption("Notte", "Scuro"), ThemeOption("Carta", "Chiaro"))))

    private fun render() {
        var step by mutableStateOf(OnboardingStep.WELCOME)
        compose.setContent {
            NovaTheme(NovaTokens.Fallback) {
                OnboardingScreen(
                    step,
                    { step = it },
                    status,
                    OnboardingActions(
                        onSetHome = { calls += "home" },
                        onShizuku = { calls += "shizuku" },
                        onSaveToken = {
                                token ->
                            if (token.startsWith("github_pat_")) null.also { status = status.copy(tokenSet = true) } else "Token non valido"
                        },
                        onTokenHelp = { calls += "help" },
                        onMeasure = { calls += "measure" },
                        onPickTheme = { i -> status = status.copy(selectedTheme = i) },
                        onFinish = { calls += "finish" },
                    ),
                )
            }
        }
    }

    @Test
    fun walk_through_every_step() {
        render()
        compose.onRoot().captureRoboImage("screenshots/guida_benvenuto.png")
        compose.onNodeWithText("Iniziamo").performClick()

        compose.onNodeWithText("Scegli NOVA come Home").performClick()
        compose.onNodeWithText("Avanti").performClick()

        compose.onNodeWithText("Apri Shizuku").performClick()
        compose.onNodeWithText("Avanti").performClick()

        compose.onNode(hasSetTextAction()).performTextInput("sbagliato")
        compose.onNodeWithText("Salva il token").performClick()
        compose.onNodeWithText("Token non valido").assertExists()
        compose.onNodeWithText("Come si crea il token").performClick()
        compose.onNodeWithText("Avanti").performClick()

        compose.onNodeWithText("Misura le prestazioni").performClick()
        compose.onNodeWithText("Avanti").performClick()

        compose.onNodeWithText("Carta").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/guida_aspetto.png")
        compose.onNodeWithText("Avanti").performClick()

        compose.onNodeWithText("Tutto pronto").assertExists()
        compose.onNodeWithText("•  Aggiornamenti automatici").assertExists()
        compose.onNodeWithText("Vai alla Home").performClick()
        assertEquals(listOf("home", "shizuku", "help", "measure", "finish"), calls)
        assertEquals(1, status.selectedTheme)
    }

    @Test
    fun a_valid_token_marks_the_step_done() {
        render()
        compose.onNodeWithText("Iniziamo").performClick()
        compose.onNodeWithText("Avanti").performClick()
        compose.onNodeWithText("Avanti").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("github_pat_abc")
        compose.onNodeWithText("Salva il token").performClick()
        compose.onNodeWithText("Fatto", substring = true).assertExists()
    }
}
