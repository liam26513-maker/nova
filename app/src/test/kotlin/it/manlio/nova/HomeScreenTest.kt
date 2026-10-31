package it.manlio.nova

import android.app.Application
import android.content.ComponentName
import android.os.Process
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import it.manlio.nova.core.design.NovaTheme
import it.manlio.nova.core.design.NovaTokens
import it.manlio.nova.feature.home.AppEntry
import it.manlio.nova.feature.home.HomeActions
import it.manlio.nova.feature.home.HomeScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Home minima: lista, preferiti, alfabeto, tocco e pressione lunga (tablet 1280×800 come quello reale). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-w1280dp-h800dp-land-mdpi", sdk = [34], application = Application::class)
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    private val names =
        listOf(
            "Calendario", "Chrome", "Disegno", "Fotocamera", "Galleria", "Gmail", "Impostazioni", "Krita",
            "Mappe", "Musica", "Note", "Orologio", "Play Store", "Shizuku", "Telegram", "WhatsApp", "YouTube", "Zoom",
        )
    private val apps =
        names.map { AppEntry("pkg.${it.lowercase()}/.Main#0", it, ComponentName("pkg.${it.lowercase()}", ".Main"), Process.myUserHandle()) }
    private val launched = mutableListOf<String>()
    private val toggled = mutableListOf<String>()

    private fun render(favorites: List<String> = listOf(apps[1].key, apps[7].key, apps[15].key)) {
        val actions =
            HomeActions(
                icon = { fakeIcon(it.label) },
                onLaunch = { launched += it.label },
                onToggleFavorite = { toggled += it.label },
                onOpenSettings = { launched += "settings" },
            )
        compose.setContent {
            NovaTheme(NovaTokens.Fallback) {
                // 31 ottobre 2026, 11:45 (ora locale del test)
                HomeScreen(apps, favorites, nowMillis = 1_793_443_500_000, actions = actions)
            }
        }
    }

    @Test
    fun landscape_screenshot() {
        render()
        compose.onRoot().captureRoboImage("screenshots/home_orizzontale.png")
    }

    @Test
    @Config(qualifiers = "it-w800dp-h1280dp-port-mdpi")
    fun portrait_screenshot() {
        render()
        compose.onRoot().captureRoboImage("screenshots/home_verticale.png")
    }

    @Test
    fun empty_favorites_explain_how_to_add_them() {
        render(favorites = emptyList())
        compose.onNodeWithText("Tieni premuta un'app", substring = true).assertExists()
        compose.onRoot().captureRoboImage("screenshots/home_senza_preferiti.png")
    }

    @Test
    fun tap_opens_and_long_press_toggles_favorite() {
        render()
        compose.onNodeWithText("Calendario").performClick()
        compose.onNodeWithText("Calendario").performTouchInput { longClick() }
        assertEquals(listOf("Calendario"), launched)
        assertEquals(listOf("Calendario"), toggled)
    }

    @Test
    fun letter_strip_jumps_to_the_letter() {
        render()
        compose.onNodeWithContentDescription("Z").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithText("Zoom").assertExists()
    }

    @Test
    fun settings_button_opens_nova_settings() {
        render()
        compose.onNodeWithContentDescription("Impostazioni di NOVA").performClick()
        assertEquals(listOf("settings"), launched)
    }

    private fun fakeIcon(label: String): ImageBitmap {
        val palette = listOf(0xFF8FA7BF, 0xFFB9C7BE, 0xFFD8C3A5, 0xFFA7A3C4, 0xFFC4A3A3)
        val bitmap = ImageBitmap(ICON, ICON)
        Canvas(bitmap).drawCircle(
            androidx.compose.ui.geometry.Offset(ICON / 2f, ICON / 2f),
            ICON / 2f,
            Paint().apply { color = Color(palette[label.length % palette.size]) },
        )
        return bitmap
    }

    private companion object {
        const val ICON = 56
    }
}
