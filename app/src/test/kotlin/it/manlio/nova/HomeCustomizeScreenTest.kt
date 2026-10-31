package it.manlio.nova

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
        compose.onAllNodesWithText("Grande")[0].performScrollTo().performClick()
        compose.onNodeWithText("Sinistra").performScrollTo().performClick()
        assertEquals(TextSize.LARGE, prefs.textSize)
        assertEquals(Side.LEFT, prefs.alphabetSide)

        compose.onNodeWithText("Krita").performScrollTo().performClick()
        assertEquals(emptyMap<String, String>(), prefs.hidden)
        compose.onNodeWithText("Ripristina i nomi originali (1)").performScrollTo().performClick()
        assertEquals(emptyMap<String, String>(), prefs.renamed)
        compose.onNodeWithText("Fatto").performScrollTo().performClick()
        assertEquals(true, done)
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
        compose.onNodeWithText("Aurora").performScrollTo().performClick()
        assertEquals(WallpaperSource.NOVA, prefs.wallpaper)
        assertEquals(IconStyle.MONO, prefs.iconStyle)

        compose.onNodeWithText("Colore").performScrollTo().performClick()
        assertEquals(WallpaperSource.SOLID, prefs.wallpaper)
        compose.onNodeWithText("Chiaro").performScrollTo().performClick()
        assertEquals(ThemeMode.LIGHT, prefs.theme)
        compose.onNodeWithText("Icone di prova").performScrollTo().performClick()
        assertEquals("com.example.icons", prefs.iconPack)
        compose.onNodeWithText("Squircle").performScrollTo().performClick()
        assertEquals(IconShape.SQUIRCLE, prefs.iconShape)
        compose.onRoot().captureRoboImage("screenshots/personalizza_home_studio.png")
    }
}
