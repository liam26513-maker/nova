package it.manlio.nova

import android.app.Application
import android.content.ComponentName
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import it.manlio.nova.core.design.NovaTheme
import it.manlio.nova.core.design.NovaTokens
import it.manlio.nova.feature.home.AppEntry
import it.manlio.nova.feature.home.ClockStyle
import it.manlio.nova.feature.home.HomeActions
import it.manlio.nova.feature.home.HomeFont
import it.manlio.nova.feature.home.HomePrefs
import it.manlio.nova.feature.home.HomeScreen
import it.manlio.nova.feature.home.HomeStyle
import it.manlio.nova.feature.home.IconShape
import it.manlio.nova.feature.home.ScrimStrength
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

/** Home: preferiti, tutte le app, alfabeto, tocco, pressione lunga e Indietro (tablet 1280×800 come quello reale). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-w1280dp-h800dp-land-mdpi", sdk = [34], application = Application::class)
class HomeScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val names =
        listOf(
            "Calendario", "Chrome", "Disegno", "Fotocamera", "Galleria", "Gmail", "Impostazioni", "Krita",
            "Mappe", "Musica", "Note", "Orologio", "Play Store", "Shizuku", "Telegram", "WhatsApp", "YouTube", "Zoom",
        )
    private val apps =
        names.map { AppEntry("pkg.${it.lowercase()}/.Main#0", it, ComponentName("pkg.${it.lowercase()}", ".Main"), Process.myUserHandle()) }
    private val launched = mutableListOf<String>()
    private val toggled = mutableListOf<String>()
    private val hidden = mutableListOf<String>()
    private val renamed = mutableListOf<Pair<String, String>>()
    private val reordered = mutableListOf<List<String>>()
    private val defaultFavorites = listOf(apps[1].key, apps[7].key, apps[15].key, apps[4].key, apps[12].key)

    private fun render(
        favorites: List<String> = defaultFavorites,
        prefs: HomePrefs = HomePrefs(),
    ) {
        val actions =
            HomeActions(
                icon = { fakeIcon(it.label) },
                onLaunch = { entry, _ -> launched += entry.label },
                onToggleFavorite = { toggled += it.label },
                onOpenSettings = { launched += "settings" },
                onReorderFavorites = { reordered += it },
                onHide = { hidden += it.label },
                onRename = { entry, name -> renamed += entry.label to name },
            )
        compose.setContent {
            NovaTheme(NovaTokens.Fallback) {
                // Uno "sfondo di sistema" finto, per vedere la sfumatura come sul tablet.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(Color(0xFF3E6E8E), Color(0xFFC98B6B), Color(0xFF2E3A59))),
                    ),
                ) {
                    HomeScreen(
                        apps,
                        favorites,
                        nowMillis = 1_793_443_500_000,
                        actions = actions,
                        style = HomeStyle(tint = Color(0xFF3E6E8E)),
                        prefs = prefs,
                    )
                }
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
    fun all_apps_screenshot_and_back_to_favorites() {
        render()
        compose.onNodeWithText("Tutte le app", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Calendario").assertExists()
        compose.onRoot().captureRoboImage("screenshots/home_tutte_le_app.png")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Tutte le app", substring = true).assertExists()
    }

    @Test
    fun empty_favorites_explain_how_to_add_them() {
        render(favorites = emptyList())
        compose.onNodeWithText("Nessun preferito", substring = true).assertExists()
    }

    @Test
    fun tap_opens_and_long_press_opens_the_menu() {
        render()
        compose.onNodeWithText("Chrome").performClick()
        assertEquals(listOf("Chrome"), launched)
        compose.onNodeWithText("Chrome").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Togli dai preferiti").assertExists()
        compose.onRoot().captureRoboImage("screenshots/home_menu.png")
        compose.onNodeWithText("Togli dai preferiti").performClick()
        assertEquals(listOf("Chrome"), toggled)
        compose.waitForIdle()
        compose.onNodeWithText("Rinomina").assertDoesNotExist()
    }

    @Test
    fun menu_hides_and_renames_from_all_apps() {
        render()
        compose.onNodeWithText("Tutte le app", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Calendario").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Aggiungi ai preferiti").assertExists()
        compose.onNodeWithText("Nascondi").performClick()
        assertEquals(listOf("Calendario"), hidden)

        compose.waitForIdle()
        compose.onNodeWithText("Disegno").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Rinomina").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Arte")
        compose.onNodeWithText("Salva il nome").performClick()
        assertEquals(listOf("Disegno" to "Arte"), renamed)
    }

    @Test
    fun long_press_and_drag_reorders_favorites() {
        render()
        compose.onNodeWithText("Chrome").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            repeat(4) { moveBy(Offset(0f, 35f)) }
            up()
        }
        compose.waitForIdle()
        assertEquals(1, reordered.size)
        assertEquals("pkg.chrome/.Main#0", reordered.single()[2])
    }

    @Test
    fun prefs_hide_rename_and_restyle() {
        val prefs =
            HomePrefs(
                showIcons = false,
                textSize = TextSize.LARGE,
                font = HomeFont.ELEGANT,
                alphabetSide = Side.LEFT,
                scrim = ScrimStrength.STRONG,
                hidden = mapOf(apps[7].key to "Krita"),
                renamed = mapOf(apps[1].key to "Navigare"),
            )
        render(prefs = prefs)
        compose.onNodeWithText("Krita").assertDoesNotExist()
        compose.onNodeWithText("Navigare").assertExists()
        compose.onRoot().captureRoboImage("screenshots/home_personalizzata.png")
    }

    @Test
    fun light_theme_with_nova_wallpaper_and_monochrome_squircle_icons() {
        val prefs =
            HomePrefs(
                theme = ThemeMode.LIGHT,
                wallpaper = WallpaperSource.NOVA,
                novaWallpaper = 3,
                iconShape = IconShape.SQUIRCLE,
                clockStyle = ClockStyle.STACKED,
            )
        render(prefs = prefs)
        compose.onRoot().captureRoboImage("screenshots/home_tema_chiaro.png")
    }

    @Test
    fun hidden_apps_disappear_and_renamed_ones_are_sorted_by_their_new_name() {
        val prefs = HomePrefs(hidden = mapOf(apps[1].key to "Chrome"), renamed = mapOf(apps[0].key to "Zaino"))
        val shown = prefs.apply(apps)!!
        assertEquals(false, shown.any { it.label == "Chrome" })
        assertEquals(listOf("YouTube", "Zaino", "Zoom"), shown.takeLast(3).map { it.label })
    }

    @Test
    fun alphabet_opens_all_apps_at_the_letter() {
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
        Canvas(
            bitmap,
        ).drawCircle(Offset(ICON / 2f, ICON / 2f), ICON / 2f, Paint().apply { color = Color(palette[label.length % palette.size]) })
        return bitmap
    }

    private companion object {
        const val ICON = 56
    }
}
