package it.manlio.nova

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import it.manlio.nova.core.design.NovaTheme
import it.manlio.nova.core.design.NovaTokens
import it.manlio.nova.feature.home.ControlTileId
import it.manlio.nova.feature.home.HomeLayoutStyle
import it.manlio.nova.feature.home.HomePrefs
import it.manlio.nova.feature.home.IconPackInfo
import it.manlio.nova.feature.home.IconShape
import it.manlio.nova.feature.home.IconStyle
import it.manlio.nova.feature.home.Side
import it.manlio.nova.feature.home.TextSize
import it.manlio.nova.feature.home.ThemeMode
import it.manlio.nova.feature.home.WallpaperSource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-w1280dp-h800dp-land-mdpi", sdk = [34], application = Application::class)
class HomeCustomizeScreenTest {
    @get:Rule val compose = createComposeRule()

    /** Apre una categoria dalla barra laterale. */
    private fun open(category: String) {
        compose.onNode(hasText(category) and hasClickAction()).performClick()
        compose.waitForIdle()
    }

    @Test
    fun choices_change_the_prefs_and_hidden_apps_come_back() {
        var prefs by mutableStateOf(HomePrefs(hidden = mapOf("p/.A#0" to "Krita"), renamed = mapOf("p/.B#0" to "Arte")))
        var done = false
        compose.setContent {
            NovaTheme(NovaTokens.Fallback) {
                HomeCustomizeScreen(prefs, CustomizeActions(onChange = { change -> prefs = change(prefs) }, onDone = { done = true }))
            }
        }
        compose.onRoot().captureRoboImage("screenshots/personalizza_home.png")
        open("Testo e orologio")
        compose.onAllNodesWithText("Grande")[0].performScrollTo().performClick()
        assertEquals(TextSize.LARGE, prefs.textSize)
        open("Disposizione e gesti")
        compose.onNodeWithText("Sinistra").performScrollTo().performClick()
        assertEquals(Side.LEFT, prefs.alphabetSide)
        compose.onRoot().captureRoboImage("screenshots/personalizza_home_disposizione.png")
        compose.onNodeWithText("Elenco").performScrollTo().performClick()
        assertEquals(HomeLayoutStyle.LIST, prefs.layout)
        compose.onNodeWithText("Icone e dock").performScrollTo().performClick()
        assertEquals(HomeLayoutStyle.IPAD, prefs.layout)

        open("App nascoste")
        compose.onNodeWithText("Krita").performScrollTo().performClick()
        assertEquals(emptyMap<String, String>(), prefs.hidden)
        compose.onNodeWithText("Ripristina i nomi originali (1)").performScrollTo().performClick()
        assertEquals(emptyMap<String, String>(), prefs.renamed)
        compose.onNodeWithText("‹  Fatto").performClick()
        assertEquals(true, done)
    }

    @Test
    fun control_center_tiles_are_chosen_from_the_customize_screen() {
        var prefs by mutableStateOf(HomePrefs())
        compose.setContent {
            NovaTheme(NovaTokens.Fallback) {
                HomeCustomizeScreen(prefs, CustomizeActions(onChange = { change -> prefs = change(prefs) }, onDone = {}))
            }
        }
        open("Centro di controllo")
        compose.onNodeWithText("Turbo").performScrollTo().performClick()
        assertEquals(ControlTileId.DEFAULT + ControlTileId.TURBO, prefs.controlTiles)
        compose.onNodeWithText("Torcia").performScrollTo().performClick()
        assertEquals(listOf(ControlTileId.WIFI, ControlTileId.BLUETOOTH, ControlTileId.DND, ControlTileId.TURBO), prefs.controlTiles)
        compose.onNodeWithText("Livello grafico").performScrollTo().performClick()
        val expected = listOf(ControlTileId.WIFI, ControlTileId.BLUETOOTH, ControlTileId.DND, ControlTileId.GRAPHICS, ControlTileId.TURBO)
        assertEquals(expected, prefs.controlTiles)
    }

    @Test
    fun themes_wallpaper_colors_and_icons() {
        var prefs by mutableStateOf(HomePrefs())
        compose.setContent {
            NovaTheme(NovaTokens.Fallback) {
                HomeCustomizeScreen(
                    prefs,
                    CustomizeActions(
                        onChange = { change -> prefs = change(prefs) },
                        onDone = {},
                        iconPacks = listOf(IconPackInfo("com.example.icons", "Icone di prova")),
                    ),
                )
            }
        }
        // La prima categoria è «Temi».
        compose.onNodeWithText("Aurora").performScrollTo().performClick()
        assertEquals(WallpaperSource.NOVA, prefs.wallpaper)
        assertEquals(IconStyle.MONO, prefs.iconStyle)

        open("Sfondo")
        compose.onNodeWithText("Colore").performScrollTo().performClick()
        assertEquals(WallpaperSource.SOLID, prefs.wallpaper)
        open("Colori")
        compose.onNodeWithText("Chiaro").performScrollTo().performClick()
        assertEquals(ThemeMode.LIGHT, prefs.theme)
        open("Icone")
        compose.onNodeWithText("Icone di prova").performScrollTo().performClick()
        assertEquals("com.example.icons", prefs.iconPack)
        compose.onNodeWithText("Originale").performScrollTo().performClick()
        assertEquals(IconShape.ORIGINAL, prefs.iconShape)
        compose.onNodeWithText("Morbido").performScrollTo().performClick()
        assertEquals(IconShape.SQUIRCLE, prefs.iconShape)
        compose.onRoot().captureRoboImage("screenshots/personalizza_home_studio.png")
    }
}
