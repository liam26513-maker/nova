package it.manlio.nova

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import it.manlio.nova.core.design.GlyphBadge
import it.manlio.nova.core.design.NovaTheme
import it.manlio.nova.core.design.NovaTokens
import it.manlio.nova.core.design.SidebarEntry
import it.manlio.nova.core.design.SidebarGlyph
import it.manlio.nova.core.design.SplitSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Impostazioni a due colonne: barra laterale larga, elenco + dettaglio stretti, Indietro e simboli. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-w1280dp-h800dp-land-mdpi", sdk = [34], application = Application::class)
class SplitSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val entries =
        listOf(
            SidebarEntry("a", "Prima", SidebarGlyph.SPARKLE, Color(0xFF7B68D8)),
            SidebarEntry("b", "Seconda", SidebarGlyph.HOUSE, Color(0xFFE0922F)),
        )
    private var selected by mutableStateOf<String?>(null)
    private val chosen = mutableListOf<String?>()
    private var left = false

    private fun render(withBack: Boolean = false) {
        compose.setContent {
            NovaTheme(NovaTokens.Fallback) {
                SplitSettings(
                    title = "Impostazioni",
                    entries = entries,
                    selectedId = selected,
                    onSelect = {
                        chosen += it
                        selected = it
                    },
                    backLabel = if (withBack) "Fatto" else null,
                    onBack = if (withBack) ({ left = true }) else null,
                ) { entry, _ -> BasicText("contenuto di ${entry.title}") }
            }
        }
    }

    @Test
    fun wide_screen_shows_sidebar_and_the_first_detail_then_switches() {
        render()
        compose.onNode(hasText("Prima") and hasClickAction()).assertIsSelected()
        compose.onNodeWithText("contenuto di Prima").assertExists()
        compose.onNodeWithText("contenuto di Seconda").assertDoesNotExist()

        compose.onNode(hasText("Seconda") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertEquals(listOf<String?>("b"), chosen)
        compose.onNodeWithText("contenuto di Seconda").assertExists()
        compose.onNodeWithText("contenuto di Prima").assertDoesNotExist()
        compose.onRoot().captureRoboImage("screenshots/impostazioni_barra_laterale.png")
    }

    @Test
    fun wide_screen_back_leaves_the_whole_screen() {
        render(withBack = true)
        compose.onNodeWithText("‹  Fatto").performClick()
        assertEquals(true, left)
    }

    @Test
    @Config(qualifiers = "it-w411dp-h891dp-port-xhdpi")
    fun narrow_screen_starts_on_the_list_and_back_returns_to_it() {
        render()
        compose.onNodeWithText("contenuto di Prima").assertDoesNotExist()
        compose.onNodeWithText("Seconda").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("contenuto di Seconda").assertExists()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(listOf("b", null), chosen)
        compose.onNodeWithText("contenuto di Seconda").assertDoesNotExist()
        compose.onNodeWithText("Prima").assertExists()
    }

    @Test
    @Config(qualifiers = "it-w411dp-h891dp-port-xhdpi")
    fun narrow_screen_with_back_label_offers_the_exit_from_the_list() {
        render(withBack = true)
        compose.onNodeWithText("‹  Fatto").performClick()
        assertEquals(true, left)
    }

    /** Tutti i simboli si disegnano (nessuna eccezione) e restano riconoscibili: se ne guarda lo screenshot. */
    @Test
    fun every_glyph_draws() {
        compose.setContent {
            NovaTheme(NovaTokens.Fallback) {
                Row(Modifier.padding(16.dp)) {
                    SidebarGlyph.entries.forEachIndexed { i, glyph ->
                        GlyphBadge(glyph, Color.hsv(i * 30f, 0.6f, 0.8f), Modifier.padding(6.dp), size = 56.dp)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/impostazioni_simboli.png")
    }
}
