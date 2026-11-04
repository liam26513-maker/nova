package it.manlio.nova

import android.app.Application
import android.content.ComponentName
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import it.manlio.nova.core.design.NovaTheme
import it.manlio.nova.core.design.NovaTokens
import it.manlio.nova.feature.home.AppEntry
import it.manlio.nova.feature.home.AppItem
import it.manlio.nova.feature.home.ClockStyle
import it.manlio.nova.feature.home.ControlActions
import it.manlio.nova.feature.home.ControlTileId
import it.manlio.nova.feature.home.FolderItem
import it.manlio.nova.feature.home.HomeActions
import it.manlio.nova.feature.home.HomeFont
import it.manlio.nova.feature.home.HomeLayout
import it.manlio.nova.feature.home.HomeLayoutStyle
import it.manlio.nova.feature.home.HomePrefs
import it.manlio.nova.feature.home.HomeScreen
import it.manlio.nova.feature.home.HomeStyle
import it.manlio.nova.feature.home.IconShape
import it.manlio.nova.feature.home.Level
import it.manlio.nova.feature.home.MediaSource
import it.manlio.nova.feature.home.MoreItem
import it.manlio.nova.feature.home.NotificationPreview
import it.manlio.nova.feature.home.NotificationSource
import it.manlio.nova.feature.home.NowPlaying
import it.manlio.nova.feature.home.Pomodoro
import it.manlio.nova.feature.home.ScrimStrength
import it.manlio.nova.feature.home.Side
import it.manlio.nova.feature.home.SystemPanel
import it.manlio.nova.feature.home.TextSize
import it.manlio.nova.feature.home.ThemeMode
import it.manlio.nova.feature.home.Toggle
import it.manlio.nova.feature.home.Torch
import it.manlio.nova.feature.home.WallpaperSource
import it.manlio.nova.feature.home.Weather
import it.manlio.nova.feature.home.WeatherKind
import it.manlio.nova.feature.home.WidgetItem
import it.manlio.nova.feature.home.WidgetKind
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    private val webSearches = mutableListOf<String>()
    private val reordered = mutableListOf<List<String>>()
    private var layout by mutableStateOf(HomeLayout())
    private val defaultFavorites = listOf(apps[1].key, apps[7].key, apps[15].key, apps[4].key, apps[12].key)

    private fun render(
        favorites: List<String> = defaultFavorites,
        prefs: HomePrefs = LIST,
        list: List<AppEntry> = apps,
        controls: ControlActions? = null,
        weather: (suspend (String) -> Result<Weather>)? = null,
        notifications: NotificationSource? = null,
        media: MediaSource? = null,
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
                onWebSearch = { webSearches += it },
                controls = controls,
                weather = weather,
                notifications = notifications,
                media = media,
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
                        list,
                        favorites,
                        nowMillis = 1_793_443_500_000,
                        actions = actions,
                        style = HomeStyle(tint = Color(0xFF3E6E8E)),
                        prefs = prefs,
                        layout = layout,
                        onLayoutChange = { change -> layout = change(layout) },
                        animate = false,
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
                layout = HomeLayoutStyle.LIST,
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
                layout = HomeLayoutStyle.LIST,
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
        val prefs = LIST.copy(hidden = mapOf(apps[1].key to "Chrome"), renamed = mapOf(apps[0].key to "Zaino"))
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

    // ---- Stile iPad: griglia di icone, dock, «Cerca app» ----

    @Test
    fun ipad_home_has_a_grid_a_dock_and_a_search_pill() {
        render(prefs = HomePrefs())
        compose.onNodeWithText("Calendario").assertExists()
        // I preferiti stanno nel dock (senza nome, ma con descrizione per l'accessibilità), non più nella griglia.
        compose.onNodeWithContentDescription("Chrome").assertExists()
        compose.onNodeWithText("Chrome").assertDoesNotExist()
        compose.onNodeWithText("Cerca app").assertExists()
        compose.onNodeWithText("Tutte le app", substring = true).assertDoesNotExist()
        compose.onRoot().captureRoboImage("screenshots/home_ipad_orizzontale.png")
    }

    @Test
    @Config(qualifiers = "it-w800dp-h1280dp-port-mdpi")
    fun ipad_home_portrait_screenshot() {
        render(prefs = HomePrefs())
        compose.onRoot().captureRoboImage("screenshots/home_ipad_verticale.png")
    }

    @Test
    fun ipad_light_theme_screenshot() {
        render(prefs = HomePrefs(theme = ThemeMode.LIGHT, wallpaper = WallpaperSource.NOVA, novaWallpaper = 3))
        compose.onRoot().captureRoboImage("screenshots/home_ipad_chiaro.png")
    }

    @Test
    fun ipad_tap_launches_from_the_grid_and_from_the_dock() {
        render(prefs = HomePrefs())
        compose.onNodeWithText("Mappe").performClick()
        compose.onNodeWithContentDescription("Chrome").performClick()
        assertEquals(listOf("Mappe", "Chrome"), launched)
    }

    @Test
    fun ipad_long_press_opens_the_menu_from_grid_and_dock() {
        render(prefs = HomePrefs())
        compose.onNodeWithText("Mappe").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Aggiungi ai preferiti").assertExists()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Aggiungi ai preferiti").assertDoesNotExist()
        compose.onNodeWithContentDescription("Chrome").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Togli dai preferiti").performClick()
        assertEquals(listOf("Chrome"), toggled)
    }

    @Test
    fun ipad_empty_dock_explains_how_to_fill_it() {
        render(favorites = emptyList(), prefs = HomePrefs())
        compose.onNodeWithText("Tieni premuta", substring = true).assertExists()
    }

    @Test
    fun ipad_search_pill_opens_search_and_all_apps_from_there_and_back_returns_to_the_grid() {
        render(prefs = HomePrefs())
        compose.onNodeWithText("Cerca app").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Tutte le app  ›").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Calendario").assertExists()
        compose.onNodeWithText("Home", substring = true).assertExists()
        compose.onRoot().captureRoboImage("screenshots/home_ipad_tutte_le_app.png")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Cerca app").assertExists()
    }

    @Test
    fun search_ignores_case_and_accents_launches_and_offers_the_web() {
        render(prefs = HomePrefs())
        compose.onNodeWithText("Cerca app").performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextInput("OROLO")
        compose.waitForIdle()
        compose.onAllNodesWithText("Orologio").onLast().assertExists()
        compose.onRoot().captureRoboImage("screenshots/home_ricerca.png")
        compose.onNodeWithText("Cerca «OROLO» sul web").performClick()
        assertEquals(listOf("OROLO"), webSearches)
        compose.onAllNodesWithText("Orologio").onLast().performClick()
        assertEquals(listOf("Orologio"), launched)
        compose.onNodeWithText("Cerca app").assertExists() // la ricerca si è chiusa
    }

    @Test
    fun search_finds_nova_settings_and_says_when_nothing_matches() {
        render(prefs = HomePrefs())
        compose.onNodeWithText("Cerca app").performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextInput("sfondo")
        compose.waitForIdle()
        compose.onNodeWithText("Impostazioni di NOVA").assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement("qqqq")
        compose.waitForIdle()
        compose.onNodeWithText("Nessun risultato").assertExists()
    }

    @Test
    fun edit_mode_adds_a_widget_and_ends_with_done() {
        render(prefs = HomePrefs())
        compose.onNodeWithText("Mappe").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Modifica la Home").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Fine").assertExists()
        compose.onNodeWithContentDescription("Aggiungi un widget").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Orologio").onLast().performClick() // l'app Orologio sta nella griglia sotto la scheda
        compose.waitForIdle()
        assertEquals(WidgetKind.CLOCK, layout.items.filterIsInstance<WidgetItem>().single().kind)
        compose.onRoot().captureRoboImage("screenshots/home_ipad_modifica.png")
        compose.onNodeWithText("Fine").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Fine").assertDoesNotExist()
    }

    @Test
    fun back_leaves_edit_mode() {
        render(prefs = HomePrefs())
        compose.onNodeWithText("Mappe").performTouchInput { longClick() }
        compose.onNodeWithText("Modifica la Home").performClick()
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Fine").assertDoesNotExist()
    }

    @Test
    fun minus_badge_and_menu_take_an_app_off_the_home_and_back() {
        render(prefs = HomePrefs())
        compose.onNodeWithText("Mappe").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Togli dalla Home").performClick()
        compose.waitForIdle()
        assertEquals(setOf(apps[8].key), layout.offHome)
        compose.onNodeWithText("Mappe").assertDoesNotExist()
        // Resta nell'elenco completo, da dove si rimette.
        compose.onNodeWithText("Cerca app").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Tutte le app  ›").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Mappe").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Aggiungi alla Home").performClick()
        assertEquals(emptySet<String>(), layout.offHome)
    }

    @Test
    fun badge_in_edit_mode_removes_the_item() {
        render(prefs = HomePrefs())
        compose.onNodeWithText("Mappe").performTouchInput { longClick() }
        compose.onNodeWithText("Modifica la Home").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("Togli dalla Home")[0].performClick()
        compose.waitForIdle()
        assertEquals(1, layout.offHome.size)
    }

    @Test
    fun a_folder_shows_its_name_opens_and_launches_its_apps() {
        layout = HomeLayout(listOf(FolderItem("f1", "Studio", listOf(apps[0].key, apps[8].key))))
        render(prefs = HomePrefs())
        compose.onNodeWithText("Studio").assertExists()
        compose.onNodeWithText("Calendario").assertDoesNotExist() // dentro la cartella, non nella griglia
        compose.onRoot().captureRoboImage("screenshots/home_ipad_cartella_chiusa.png")
        compose.onNodeWithText("Studio").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Calendario").assertExists()
        compose.onRoot().captureRoboImage("screenshots/home_ipad_cartella_aperta.png")
        compose.onNodeWithText("Mappe").performClick()
        assertEquals(listOf("Mappe"), launched)
    }

    @Test
    fun an_app_in_a_folder_can_be_taken_out_from_its_menu() {
        layout = HomeLayout(listOf(FolderItem("f1", "Studio", listOf(apps[0].key, apps[8].key, apps[9].key))))
        render(prefs = HomePrefs())
        compose.onNodeWithText("Studio").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Mappe").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Togli dalla cartella").performClick()
        compose.waitForIdle()
        val items = layout.items
        assertEquals(listOf(apps[0].key, apps[9].key), (items.first() as FolderItem).keys)
        assertEquals(apps[8].key, (items[1] as AppItem).key)
    }

    @Test
    fun dragging_an_app_onto_another_in_edit_mode_makes_a_folder() {
        render(prefs = HomePrefs())
        compose.onNodeWithText("Mappe").performTouchInput { longClick() }
        compose.onNodeWithText("Modifica la Home").performClick()
        compose.waitForIdle()
        val target = compose.onNodeWithText("Disegno").fetchSemanticsNode().boundsInRoot.center - Offset(0f, 32f)
        val from = compose.onNodeWithText("Calendario").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Calendario").performTouchInput {
            down(center)
            val end = target - from.topLeft
            repeat(6) { moveBy((end - center) / 6f) }
            up()
        }
        compose.waitForIdle()
        val folder = layout.items.filterIsInstance<FolderItem>().single()
        assertEquals(setOf(apps[0].key, apps[2].key), folder.keys.toSet())
    }

    @Test
    fun dragging_an_app_in_edit_mode_reorders_the_grid() {
        render(prefs = HomePrefs())
        compose.onNodeWithText("Mappe").performTouchInput { longClick() }
        compose.onNodeWithText("Modifica la Home").performClick()
        compose.waitForIdle()
        val label = compose.onNodeWithText("Disegno").fetchSemanticsNode().boundsInRoot
        val from = compose.onNodeWithText("Calendario").fetchSemanticsNode().boundsInRoot
        // Nella parte sinistra della cella di Disegno, fuori dal centro: si inserisce lì, non si unisce.
        val target = Offset(label.center.x - 60f, label.center.y - 32f)
        compose.onNodeWithText("Calendario").performTouchInput {
            down(center)
            val end = target - from.topLeft
            repeat(6) { moveBy((end - center) / 6f) }
            up()
        }
        compose.waitForIdle()
        assertEquals(true, layout.items.none { it is FolderItem })
        val order = layout.items.map { it.id }
        assertEquals(true, order.indexOf(apps[0].key) > order.indexOf(apps[2].key))
    }

    @Test
    fun widgets_show_up_with_a_folder_and_the_note_is_saved() {
        layout =
            HomeLayout(
                listOf(
                    WidgetItem("w1", WidgetKind.CLOCK),
                    WidgetItem("w2", WidgetKind.CALENDAR),
                    WidgetItem("w3", WidgetKind.BATTERY),
                    WidgetItem("w4", WidgetKind.NOTE),
                    FolderItem("f1", "Lavoro", listOf(apps[1].key, apps[3].key, apps[4].key, apps[5].key)),
                ),
            )
        render(prefs = HomePrefs())
        compose.onNodeWithText("Scrivi una nota…").assertExists()
        compose.onNodeWithText("Lavoro").assertExists()
        compose.onRoot().captureRoboImage("screenshots/home_ipad_widget.png")
        compose.onNode(hasSetTextAction()).performTextInput("Comprare il pane")
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertEquals("Comprare il pane", layout.items.filterIsInstance<WidgetItem>().single { it.kind == WidgetKind.NOTE }.text)
    }

    @Test
    fun hybrid_landscape_has_the_list_on_the_left_and_the_grid_on_the_right() {
        render(prefs = HomePrefs(layout = HomeLayoutStyle.HYBRID))
        compose.onNodeWithText("Chrome").assertExists() // preferiti a elenco
        compose.onNodeWithText("Calendario").assertExists() // griglia
        compose.onNodeWithText("Cerca app").assertExists()
        compose.onRoot().captureRoboImage("screenshots/home_ibrida.png")
    }

    @Test
    @Config(qualifiers = "it-w800dp-h1280dp-port-mdpi")
    fun hybrid_portrait_falls_back_to_the_list() {
        render(prefs = HomePrefs(layout = HomeLayoutStyle.HYBRID))
        compose.onNodeWithText("Tutte le app", substring = true).assertExists()
        compose.onNodeWithText("Cerca app").assertDoesNotExist()
    }

    @Test
    fun ipad_pages_scroll_sideways_and_show_the_page() {
        val many =
            (1..120).map {
                val name = "App %03d".format(it)
                AppEntry("pkg.$it/.Main#0", name, ComponentName("pkg.$it", ".Main"), Process.myUserHandle())
            }
        render(favorites = emptyList(), prefs = HomePrefs(), list = many)
        compose.onNodeWithText("App 001").assertExists()
        compose.onNodeWithContentDescription("Pagina 1 di", substring = true).assertExists()
        compose.onNodeWithText("App 120").assertDoesNotExist()
        compose.onNodeWithText("App 001").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Pagina 2 di", substring = true).assertExists()
        compose.onNodeWithText("App 001").assertDoesNotExist()
        compose.onRoot().captureRoboImage("screenshots/home_ipad_pagina_2.png")
    }

    @Test
    fun ipad_hidden_and_renamed_apps_follow_the_prefs() {
        render(prefs = HomePrefs(hidden = mapOf(apps[7].key to "Krita"), renamed = mapOf(apps[8].key to "Cartine")))
        compose.onNodeWithText("Cartine").assertExists()
        compose.onNodeWithText("Mappe").assertDoesNotExist()
        compose.onNodeWithContentDescription("Krita").assertDoesNotExist()
    }

    @Test
    fun ipad_settings_button_still_opens_nova_settings() {
        render(prefs = HomePrefs())
        compose.onNodeWithContentDescription("Impostazioni di NOVA").performClick()
        assertEquals(listOf("settings"), launched)
    }

    // ---- Meteo, Pomodoro, centro di controllo, calcolatrice ----

    private val rome = Weather("Roma", 19, 22, 14, WeatherKind.PARTLY, isDay = true)

    @Test
    fun weather_widget_asks_for_a_city_then_shows_the_weather() {
        val asked = mutableListOf<String>()
        layout = HomeLayout(listOf(WidgetItem("w", WidgetKind.WEATHER)))
        render(prefs = HomePrefs(), weather = { city ->
            asked += city
            Result.success(rome)
        })
        compose.onNodeWithText("Di quale città vuoi il meteo?").assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("Roma")
        compose.onNodeWithText("Salva").performClick()
        compose.waitForIdle()
        assertEquals("Roma", layout.items.filterIsInstance<WidgetItem>().single().text)
        compose.onNodeWithText("19°").assertExists()
        compose.onNodeWithText("Poco nuvoloso").assertExists()
        compose.onNodeWithText("↑22°  ↓14°").assertExists()
        assertEquals(true, asked.all { it == "Roma" })
        compose.onRoot().captureRoboImage("screenshots/home_ipad_meteo.png")
    }

    @Test
    fun weather_widget_says_when_it_fails_and_retries() {
        var calls = 0
        layout = HomeLayout(listOf(WidgetItem("w", WidgetKind.WEATHER, text = "Roma")))
        render(prefs = HomePrefs(), weather = {
            calls++
            if (calls == 1) Result.failure(java.io.IOException("no")) else Result.success(rome)
        })
        compose.onNodeWithText("Meteo non disponibile").assertExists()
        compose.onNodeWithText("Meteo non disponibile").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("19°").assertExists()
    }

    @Test
    fun pomodoro_starts_pauses_and_resets_and_is_saved_in_the_layout() {
        layout = HomeLayout(listOf(WidgetItem("p", WidgetKind.POMODORO)))
        render(prefs = HomePrefs())
        compose.onNodeWithText("25:00").assertExists()
        compose.onNodeWithContentDescription("Avvia").performClick()
        compose.waitForIdle()
        assertEquals(true, Pomodoro.decode(layout.items.filterIsInstance<WidgetItem>().single().text).running)
        compose.onNodeWithContentDescription("Pausa").performClick()
        compose.waitForIdle()
        assertEquals(false, Pomodoro.decode(layout.items.filterIsInstance<WidgetItem>().single().text).running)
        compose.onNodeWithContentDescription("Azzera").performClick()
        compose.waitForIdle()
        assertEquals(Pomodoro(), Pomodoro.decode(layout.items.filterIsInstance<WidgetItem>().single().text))
    }

    /** Apre il centro di controllo come TalkBack: con l'azione di accessibilità (il gesto vero è provato a parte). */
    private fun openControl() {
        compose.onNodeWithContentDescription("Centro di controllo").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /** Apre la sezione «Altro» della tendina (a prima vista c'è solo l'essenziale). */
    private fun openMore() {
        compose.onNodeWithText("Altro").performClick()
        compose.waitForIdle()
    }

    private class FakeTorch : Torch {
        val state = kotlinx.coroutines.flow.MutableStateFlow(false)
        override val on = state

        override fun toggle() {
            state.value = !state.value
        }
    }

    @Test
    fun control_center_opens_panels_toggles_the_torch_and_moves_the_sliders() {
        val panels = mutableListOf<SystemPanel>()
        val torch = FakeTorch()
        var vol = 0.3f
        var bri = 0.5f
        var cycled = 0
        val controls =
            ControlActions(
                openPanel = { panels += it },
                torch = torch,
                volume = Level({ vol }, { vol = it }),
                brightness = Level({ bri }, { bri = it }),
                graphicsLabel = { "Bilanciato" },
                onCycleGraphics = { cycled++ },
            )
        render(prefs = HomePrefs(), controls = controls)
        openControl()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/home_centro_di_controllo.png")
        compose.onNodeWithText("Wi-Fi e rete").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Bluetooth").assertDoesNotExist() // aprire il pannello di Android richiude la tendina
        openControl()
        compose.onNodeWithText("Bluetooth").performClick()
        compose.waitForIdle()
        openControl()
        compose.onNodeWithText("Non disturbare").performClick()
        compose.waitForIdle()
        assertEquals(listOf(SystemPanel.INTERNET, SystemPanel.BLUETOOTH, SystemPanel.DND), panels)
        openControl()
        compose.onNodeWithText("Torcia").performClick()
        assertEquals(true, torch.state.value)
        compose.onNodeWithText("Bilanciato").assertDoesNotExist() // sotto «Altro»
        openMore()
        compose.onNodeWithText("Bilanciato").performClick()
        assertEquals(1, cycled)
        compose.onNodeWithContentDescription("Volume").performSemanticsAction(SemanticsActions.SetProgress) { it(0.8f) }
        compose.waitForIdle()
        assertEquals(0.8f, vol, 0.001f)
        compose.onNodeWithContentDescription("Luminosità").performSemanticsAction(SemanticsActions.SetProgress) { it(0.9f) }
        compose.waitForIdle()
        assertEquals(0.9f, bri, 0.001f)
    }

    @Test
    fun control_center_shows_only_the_essentials_until_more_is_opened() {
        val controls = ControlActions(openPanel = {}, volume = Level({ 0.5f }, {}), more = listOf(MoreItem("Posizione")))
        render(prefs = HomePrefs(), controls = controls)
        openControl()
        compose.onNodeWithText("Wi-Fi e rete").assertExists()
        compose.onNodeWithText("Bluetooth").assertExists()
        compose.onNodeWithText("Non disturbare").assertExists()
        compose.onNodeWithContentDescription("Volume").assertExists()
        compose.onNodeWithText("Posizione").assertDoesNotExist()
        compose.onNodeWithText("Impostazioni di NOVA").assertDoesNotExist()
        openMore()
        compose.onNodeWithText("Posizione").assertExists()
        compose.onNodeWithText("Impostazioni di NOVA").assertExists()
        compose.onRoot().captureRoboImage("screenshots/home_centro_di_controllo_altro.png")
        openMore() // di nuovo: si richiude
        compose.onNodeWithText("Posizione").assertDoesNotExist()
    }

    @Test
    fun control_center_first_view_follows_the_tiles_chosen_in_customize() {
        render(
            prefs = HomePrefs(controlTiles = listOf(ControlTileId.GRAPHICS, ControlTileId.SETTINGS)),
            controls = ControlActions(openPanel = {}, graphicsLabel = { "Bilanciato" }),
        )
        openControl()
        compose.onNodeWithText("Bilanciato").assertExists() // scelti: a prima vista, senza aprire «Altro»
        compose.onNodeWithText("Impostazioni di NOVA").assertExists()
        compose.onNodeWithText("Wi-Fi e rete").assertDoesNotExist() // non scelto: sta sotto «Altro»
        openMore()
        compose.onNodeWithText("Wi-Fi e rete").assertExists()
    }

    @Test
    fun control_center_more_items_open_android_and_close_the_sheet_while_toggles_stay_open() {
        var opened = 0
        var rotation = true
        val items =
            listOf(
                MoreItem("Posizione") {
                    opened++
                    null
                },
                MoreItem("Rotazione automatica", "Sì") {
                    rotation = !rotation
                    if (rotation) "Sì" else "No"
                },
            )
        render(prefs = HomePrefs(), controls = ControlActions(openPanel = {}, more = items))
        openControl()
        openMore()
        compose.onNodeWithText("Rotazione automatica").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("No").assertExists() // l'interruttore cambia il suo stato e la tendina resta aperta
        compose.onNodeWithText("Posizione").performClick()
        compose.waitForIdle()
        assertEquals(1, opened)
        compose.onNodeWithText("Wi-Fi e rete").assertDoesNotExist() // una voce che porta fuori chiude la tendina
    }

    @Test
    fun control_center_slider_follows_the_finger_along_the_line_and_ignores_vertical_drift() {
        var vol = 0f
        render(prefs = HomePrefs(), controls = ControlActions(openPanel = {}, volume = Level({ vol }, { vol = it })))
        openControl()
        compose.onNodeWithContentDescription("Volume").performTouchInput {
            down(Offset(width * 0.1f, centerY))
            moveTo(Offset(width * 0.5f, centerY - 400f)) // il dito si sposta anche molto in su: conta solo la linea orizzontale
            moveTo(Offset(width * 0.75f, centerY + 400f))
            up()
        }
        compose.waitForIdle()
        assertEquals(0.75f, vol, 0.02f)
        compose.onNodeWithText("Wi-Fi e rete").assertExists() // trascinare il cursore non ha chiuso la tendina
    }

    @Test
    fun control_center_volume_snaps_to_the_steps_the_system_has() {
        var vol = 0f
        render(prefs = HomePrefs(), controls = ControlActions(openPanel = {}, volume = Level({ vol }, { vol = it }, steps = 10)))
        openControl()
        compose.onNodeWithContentDescription("Volume").performSemanticsAction(SemanticsActions.SetProgress) { it(0.34f) }
        compose.waitForIdle()
        assertEquals(0.3f, vol, 0.001f)
    }

    @Test
    fun control_center_opens_with_a_pull_down_from_the_top_right_corner_and_closes_by_dragging_up() {
        render(prefs = HomePrefs(), controls = ControlActions(openPanel = {}))
        compose.onNodeWithText("Wi-Fi e rete").assertDoesNotExist()
        compose.onRoot().performTouchInput { swipe(Offset(width - 40f, 20f), Offset(width - 40f, height * 0.8f), 250) }
        compose.waitForIdle()
        compose.onNodeWithText("Wi-Fi e rete").assertExists()
        compose.onNodeWithText("Wi-Fi e rete").performTouchInput { swipe(Offset(centerX, centerY), Offset(centerX, centerY - 700f), 200) }
        compose.waitForIdle()
        compose.onNodeWithText("Wi-Fi e rete").assertDoesNotExist()
    }

    @Test
    fun a_pull_down_away_from_the_corner_also_opens_our_control_sheet_not_androids() {
        render(prefs = HomePrefs(), controls = ControlActions(openPanel = {}))
        compose.onRoot().performTouchInput { swipe(Offset(60f, 20f), Offset(60f, height * 0.8f), 250) }
        compose.waitForIdle()
        compose.onNodeWithText("Wi-Fi e rete").assertExists()
    }

    @Test
    fun control_center_wifi_tile_switches_at_once_without_leaving_the_home() {
        var wifi = false
        val panels = mutableListOf<SystemPanel>()
        val toggle = Toggle(read = { wifi }, set = { on, _ -> wifi = on })
        val controls = ControlActions(openPanel = { panels += it }, toggles = mapOf(SystemPanel.INTERNET to toggle))
        render(prefs = HomePrefs(), controls = controls)
        openControl()
        compose.onNodeWithText("Wi-Fi e rete").performClick()
        compose.waitForIdle()
        assertEquals(true, wifi)
        assertTrue(panels.isEmpty()) // non apre la schermata di Android
        compose.onNodeWithText("Wi-Fi e rete").assertExists() // e la tendina resta aperta
        compose.onNodeWithText("Wi-Fi e rete").performClick()
        compose.waitForIdle()
        assertEquals(false, wifi)
    }

    @Test
    fun control_center_tile_rereads_the_real_state_when_android_refuses() {
        var reads = 0
        val toggle =
            Toggle(
                read = {
                    reads++
                    false
                },
                set = { _, onFailed -> onFailed() },
            )
        render(prefs = HomePrefs(), controls = ControlActions(openPanel = {}, toggles = mapOf(SystemPanel.BLUETOOTH to toggle)))
        openControl()
        val before = reads
        compose.onNodeWithText("Bluetooth").performClick()
        compose.waitForIdle()
        assertTrue(reads > before) // dopo il rifiuto rilegge lo stato vero invece di lasciare quello sperato
    }

    @Test
    fun control_center_without_a_toggle_still_opens_the_android_panel() {
        val panels = mutableListOf<SystemPanel>()
        render(prefs = HomePrefs(), controls = ControlActions(openPanel = { panels += it }))
        openControl()
        compose.onNodeWithText("Bluetooth").performClick()
        compose.waitForIdle()
        assertEquals(listOf(SystemPanel.BLUETOOTH), panels)
    }

    @Test
    fun control_center_more_row_follows_a_state_changed_elsewhere() {
        val live = MutableStateFlow("Sì")
        val item = MoreItem("Modalità Studio", "Sì", live = live)
        render(prefs = HomePrefs(), controls = ControlActions(openPanel = {}, more = listOf(item)))
        openControl()
        openMore()
        compose.onNodeWithText("Sì").assertExists()

        live.value = "No" // un'altra modalità l'ha spenta
        compose.waitForIdle()
        compose.onNodeWithText("No").assertExists()
    }

    @Test
    fun control_center_more_toggle_row_shows_its_state_and_a_late_correction() {
        var report: (String) -> Unit = {}
        val item =
            MoreItem("Modalità aereo", "No") {
                report = it
                "Sì" // subito lo stato sperato...
            }
        render(prefs = HomePrefs(), controls = ControlActions(openPanel = {}, more = listOf(item)))
        openControl()
        openMore()
        compose.onNodeWithText("No").assertExists()
        compose.onNodeWithText("Modalità aereo").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Sì").assertExists()
        compose.runOnIdle { report("No") } // ...poi, se Android non ha obbedito, quello vero
        compose.waitForIdle()
        compose.onNodeWithText("No").assertExists()
    }

    @Test
    fun control_center_has_a_turbo_tile_only_when_it_is_available() {
        var turbo = 0
        render(prefs = HomePrefs(), controls = ControlActions(openPanel = {}, onTurbo = { turbo++ }))
        openControl()
        openMore()
        compose.onNodeWithText("Turbo").performClick()
        assertEquals(1, turbo)
    }

    @Test
    fun control_center_hides_turbo_without_shizuku() {
        render(prefs = HomePrefs(), controls = ControlActions(openPanel = {}))
        openControl()
        compose.waitForIdle()
        openMore()
        compose.onNodeWithText("Turbo").assertDoesNotExist()
    }

    @Test
    fun control_center_asks_for_the_brightness_permission_when_it_is_missing() {
        var asked = 0
        render(prefs = HomePrefs(), controls = ControlActions(openPanel = {}, brightness = null, onGrantBrightness = { asked++ }))
        openControl()
        compose.waitForIdle()
        compose.onNodeWithText("Concedi il permesso per regolare la luminosità").performClick()
        assertEquals(1, asked)
    }

    @Test
    fun control_center_closes_with_back_and_its_button_exists_only_with_controls() {
        render(prefs = HomePrefs())
        compose.onNodeWithContentDescription("Centro di controllo").assertDoesNotExist()
    }

    @Test
    fun control_center_back_closes_it_and_edit_home_starts_editing() {
        render(prefs = HomePrefs(), controls = ControlActions(openPanel = {}))
        openControl()
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Wi-Fi e rete").assertDoesNotExist()
        openControl()
        compose.waitForIdle()
        openMore()
        compose.onNodeWithText("Modifica la Home").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Fine").assertExists()
    }

    @Test
    fun search_calculates_converts_and_finds_commands() {
        render(prefs = HomePrefs(), controls = ControlActions(openPanel = {}))
        compose.onNodeWithText("Cerca app").performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextInput("12*(3+4)")
        compose.waitForIdle()
        compose.onNodeWithText("= 84").assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement("10 km in mi")
        compose.waitForIdle()
        compose.onNodeWithText("= 6.213712 mi").assertExists()
        compose.onNode(hasSetTextAction()).performTextReplacement("torcia")
        compose.waitForIdle()
        compose.onAllNodesWithText("Centro di controllo").onLast().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Wi-Fi e rete").assertExists()
    }

    @Test
    fun search_command_edit_home() {
        render(prefs = HomePrefs())
        compose.onNodeWithText("Cerca app").performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextInput("widget")
        compose.waitForIdle()
        compose.onAllNodesWithText("Modifica la Home").onLast().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Fine").assertExists()
    }

    // ---- Notifiche in linea, isola dinamica, widget Musica ----

    private class FakeNotifications(initial: Map<String, NotificationPreview>) : NotificationSource {
        override val previews = kotlinx.coroutines.flow.MutableStateFlow(initial)
        val dismissed = mutableListOf<String>()

        override fun isEnabled() = true

        override fun dismiss(key: String) {
            dismissed += key
        }
    }

    private class FakeMedia(initial: NowPlaying?) : MediaSource {
        override val nowPlaying = kotlinx.coroutines.flow.MutableStateFlow(initial)
        val calls = mutableListOf<String>()

        override fun previous() {
            calls += "previous"
        }

        override fun toggle() {
            calls += "toggle"
            nowPlaying.value = nowPlaying.value?.let { it.copy(playing = !it.playing) }
        }

        override fun next() {
            calls += "next"
        }

        override fun open() {
            calls += "open"
        }
    }

    private val chatPreview = NotificationPreview("pkg.gmail", "key-1", "Anna", "Ci sei stasera?", 3, 1)

    @Test
    fun notification_count_shows_on_grid_icons_and_in_the_dock() {
        val source = FakeNotifications(mapOf("pkg.gmail" to chatPreview, "pkg.chrome" to chatPreview.copy(pkg = "pkg.chrome", count = 120)))
        render(prefs = HomePrefs(), notifications = source)
        compose.onNodeWithContentDescription("3").assertExists() // Gmail, nella griglia
        compose.onNodeWithContentDescription("99+").assertExists() // Chrome, nel dock
        compose.onRoot().captureRoboImage("screenshots/home_ipad_notifiche.png")
        source.previews.value = emptyMap()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("3").assertDoesNotExist()
    }

    @Test
    fun the_list_style_shows_the_notification_under_the_app() {
        val source = FakeNotifications(mapOf("pkg.chrome" to chatPreview.copy(pkg = "pkg.chrome")))
        render(notifications = source) // stile Elenco: Chrome è tra i preferiti grandi
        compose.onNodeWithText("Anna: Ci sei stasera?").assertExists()
        compose.onRoot().captureRoboImage("screenshots/home_elenco_notifiche.png")
    }

    @Test
    fun the_menu_can_clear_an_apps_notification() {
        val source = FakeNotifications(mapOf("pkg.gmail" to chatPreview))
        render(prefs = HomePrefs(), notifications = source)
        compose.onNodeWithText("Gmail").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Cancella la notifica").performClick()
        assertEquals(listOf("key-1"), source.dismissed)
    }

    @Test
    fun the_menu_has_no_clear_entry_without_a_notification() {
        render(prefs = HomePrefs(), notifications = FakeNotifications(emptyMap()))
        compose.onNodeWithText("Gmail").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Cancella la notifica").assertDoesNotExist()
    }

    private val song = NowPlaying("pkg.musica", "Notte stellata", "Ensemble", playing = true)

    @Test
    fun the_island_shows_the_music_while_it_plays_and_expands_with_controls() {
        val media = FakeMedia(song)
        render(prefs = HomePrefs(), media = media)
        compose.onNodeWithContentDescription("In riproduzione: Notte stellata").assertExists()
        compose.onNodeWithText("Metti in pausa").assertDoesNotExist() // chiusa: niente comandi
        compose.onNodeWithContentDescription("In riproduzione: Notte stellata").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ensemble").assertExists()
        compose.onRoot().captureRoboImage("screenshots/home_isola.png")
        compose.onNodeWithContentDescription("Successiva").performClick()
        compose.onNodeWithContentDescription("Precedente").performClick()
        compose.onNodeWithContentDescription("Metti in pausa").performClick()
        assertEquals(listOf("next", "previous", "toggle"), media.calls)
        compose.waitForIdle()
        // In pausa l'isola sparisce: si riprende dal widget.
        compose.onNodeWithContentDescription("In riproduzione: Notte stellata").assertDoesNotExist()
    }

    @Test
    fun no_island_without_music() {
        render(prefs = HomePrefs(), media = FakeMedia(null))
        compose.onNodeWithContentDescription("In riproduzione", substring = true).assertDoesNotExist()
    }

    @Test
    fun the_music_widget_shows_the_song_and_controls_even_when_paused() {
        val media = FakeMedia(song.copy(playing = false))
        layout = HomeLayout(listOf(WidgetItem("m", WidgetKind.MUSIC)))
        render(prefs = HomePrefs(), media = media)
        compose.onNodeWithText("Notte stellata").assertExists()
        compose.onNodeWithText("Ensemble").assertExists()
        compose.onRoot().captureRoboImage("screenshots/home_ipad_musica.png")
        compose.onNodeWithContentDescription("Riproduci").performClick()
        assertEquals(listOf("toggle"), media.calls)
    }

    @Test
    fun the_music_widget_says_when_nothing_plays() {
        layout = HomeLayout(listOf(WidgetItem("m", WidgetKind.MUSIC)))
        render(prefs = HomePrefs(), media = FakeMedia(null))
        compose.onNodeWithText("Nessuna musica in riproduzione").assertExists()
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
        /** Le prove sull'elenco (stile Niagara) usano questa disposizione; lo stile iPad è il predefinito. */
        val LIST = HomePrefs(layout = HomeLayoutStyle.LIST)
        const val ICON = 56
    }
}
