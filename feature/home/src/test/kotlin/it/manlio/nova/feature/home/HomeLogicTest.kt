package it.manlio.nova.feature.home

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppIndexTest {
    @Test
    fun `accented and lowercase letters go under the base letter, the rest under hash`() {
        assertEquals('E', AppIndex.letterOf("éBay"))
        assertEquals('A', AppIndex.letterOf("  àtlas"))
        assertEquals('Z', AppIndex.letterOf("zoom"))
        assertEquals(AppIndex.OTHER, AppIndex.letterOf("7-Zip"))
        assertEquals(AppIndex.OTHER, AppIndex.letterOf("Ωmega"))
        assertEquals(AppIndex.OTHER, AppIndex.letterOf(""))
    }

    @Test
    fun `italian order ignores case and accents, hash group last`() {
        val sorted = AppIndex.sort(listOf("zoom", "Àrea", "3D Viewer", "banca", "Agenda", "Écran", "elenco")) { it }
        assertEquals(listOf("Agenda", "Àrea", "banca", "Écran", "elenco", "zoom", "3D Viewer"), sorted)
    }

    @Test
    fun `first index of every letter`() {
        assertEquals(mapOf('A' to 0, 'C' to 2, '#' to 3), AppIndex.firstIndexByLetter(listOf('A', 'A', 'C', '#', '#')))
    }
}

class FavoritesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `toggle adds in order, removes, and survives a restart`() =
        runTest {
            val file = tmp.root.resolve("favorites.txt")
            val favorites = Favorites(file)
            favorites.toggle("a")
            favorites.toggle("b")
            favorites.toggle("c")
            favorites.toggle("b")
            assertEquals(listOf("a", "c"), favorites.keys.value)
            assertEquals(listOf("a", "c"), Favorites(file).keys.value)
        }

    @Test
    fun `missing or messy file is read safely`() {
        assertEquals(emptyList<String>(), Favorites(tmp.root.resolve("none.txt")).keys.value)
        val file = tmp.root.resolve("messy.txt").apply { writeText("a\n\n a \nb\na\n") }
        assertEquals(listOf("a", "b"), Favorites(file).keys.value)
    }
}

class HomePrefsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `everything survives a restart, including odd keys and names`() =
        runTest {
            val file = tmp.root.resolve("prefs.properties")
            val store = HomePrefsStore(file)
            store.update {
                it.copy(
                    showIcons = false,
                    textSize = TextSize.LARGE,
                    font = HomeFont.ELEGANT,
                    showClock = false,
                    alphabetSide = Side.LEFT,
                    scrim = ScrimStrength.STRONG,
                    blurLevel = BlurLevel.MAX,
                    theme = ThemeMode.LIGHT,
                    accent = 0xFF66C2A5.toInt(),
                    wallpaper = WallpaperSource.NOVA,
                    novaWallpaper = 3,
                    weight = TextWeight.BOLD,
                    clockStyle = ClockStyle.STACKED,
                    showDate = false,
                    iconSize = IconSize.LARGE,
                    iconShape = IconShape.SQUIRCLE,
                    iconStyle = IconStyle.MONO,
                    iconPack = "com.example.icons",
                    showStatusBar = false,
                    swipeDownNotifications = false,
                    doubleTapSleep = true,
                    hidden = mapOf("com.a/.Main#0" to "Caffè = è: #1"),
                    renamed = mapOf("com.b/.X#10" to "Il mio disegno"),
                )
            }
            assertEquals(store.prefs.value, HomePrefsStore(file).prefs.value)
        }

    @Test
    fun `missing or broken values fall back to the defaults`() {
        val file = tmp.root.resolve("broken.properties").apply { writeText("text=ENORME\nicons=forse\nside=LEFT\n") }
        val prefs = HomePrefsStore(file).prefs.value
        assertEquals(TextSize.MEDIUM, prefs.textSize)
        assertEquals(true, prefs.showIcons)
        assertEquals(Side.LEFT, prefs.alphabetSide)
        assertEquals(HomePrefs(), HomePrefsStore(tmp.root.resolve("none.properties")).prefs.value)
    }

    @Test
    fun `old settings keep working (blur=false becomes no blur) and out-of-range wallpapers are clamped`() {
        val file = tmp.root.resolve("old.properties").apply { writeText("blur=false\nnovaWallpaper=999\n") }
        val prefs = HomePrefsStore(file).prefs.value
        assertEquals(BlurLevel.NONE, prefs.blurLevel)
        assertEquals(NovaWallpapers.all.lastIndex, prefs.novaWallpaper)
    }

    @Test
    fun `a theme carries only the look and keeps your hidden and renamed apps`() =
        runTest {
            val source = HomePrefsStore(tmp.root.resolve("a.properties"))
            source.update { ThemePresets.all[1].prefs.copy(hidden = mapOf("x" to "X"), renamed = mapOf("y" to "Y")) }
            val theme = source.exportTheme()
            assertEquals(false, theme.contains("hidden."))

            val target = HomePrefsStore(tmp.root.resolve("b.properties"))
            target.update { it.copy(hidden = mapOf("mine" to "Mia")) }
            assertEquals(true, target.importTheme(theme))
            assertEquals(ThemePresets.all[1].prefs.appearance(), target.prefs.value.appearance())
            assertEquals(mapOf("mine" to "Mia"), target.prefs.value.hidden)

            assertEquals(false, target.importTheme("ciao=1"))
            assertEquals(false, target.importTheme("\u0000 non è un tema"))
        }

    @Test
    fun `every preset is different and valid`() {
        assertEquals(ThemePresets.all.size, ThemePresets.all.map { it.prefs.appearance() }.toSet().size)
        ThemePresets.all.forEach { assertEquals(it.prefs, PrefsCodec.read(PrefsCodec.write(it.prefs))) }
    }
}

class IpadStyleTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `the new home is the default, with soft icons`() {
        val prefs = HomePrefs()
        assertEquals(HomeLayoutStyle.IPAD, prefs.layout)
        assertEquals(IconShape.SQUIRCLE, prefs.iconShape)
    }

    @Test
    fun `a file saved before the iPad style gets soft icons, but only once`() =
        runTest {
            val file = tmp.root.resolve("before.properties").apply { writeText("nova-home=1\niconShape=ORIGINAL\ntext=LARGE\n") }
            val store = HomePrefsStore(file)
            assertEquals(IconShape.SQUIRCLE, store.prefs.value.iconShape)
            assertEquals(TextSize.LARGE, store.prefs.value.textSize)
            // Dopo il primo salvataggio la scelta è esplicita: tornare a «Originale» resta così.
            store.update { it.copy(iconShape = IconShape.ORIGINAL) }
            assertEquals(IconShape.ORIGINAL, HomePrefsStore(file).prefs.value.iconShape)
        }

    @Test
    fun `an old file keeps a deliberate shape other than the old default`() {
        val file = tmp.root.resolve("circle.properties").apply { writeText("iconShape=CIRCLE\n") }
        assertEquals(IconShape.CIRCLE, HomePrefsStore(file).prefs.value.iconShape)
    }

    @Test
    fun `the layout survives a restart and a broken value falls back to the iPad style`() =
        runTest {
            val file = tmp.root.resolve("layout.properties")
            HomePrefsStore(file).update { it.copy(layout = HomeLayoutStyle.LIST) }
            assertEquals(HomeLayoutStyle.LIST, HomePrefsStore(file).prefs.value.layout)
            file.writeText("layout=MAGICO\n")
            assertEquals(HomeLayoutStyle.IPAD, HomePrefsStore(file).prefs.value.layout)
        }

    @Test
    fun `themes never change the layout`() =
        runTest {
            val store = HomePrefsStore(tmp.root.resolve("theme.properties"))
            store.update { it.copy(layout = HomeLayoutStyle.LIST) }
            store.update { it.withLookOf(ThemePresets.all[2].prefs) }
            assertEquals(HomeLayoutStyle.LIST, store.prefs.value.layout)
            assertEquals(false, store.exportTheme().contains("LIST"))
            assertEquals(true, store.importTheme(store.exportTheme()))
            assertEquals(HomeLayoutStyle.LIST, store.prefs.value.layout)
            // Anche i temi si confrontano senza la disposizione: quello attivo si riconosce.
            assertEquals(ThemePresets.all[2].prefs.appearance(), store.prefs.value.appearance())
        }
}

class HomeGridTest {
    @Test
    fun `columns and rows follow the room, within limits`() {
        // Tablet in orizzontale (1280×800 dp, margini e intestazione tolti).
        assertEquals(GridMetrics(7, 4), GridMetrics.of(1136f, 520f))
        // In verticale.
        assertEquals(GridMetrics(4, 6), GridMetrics.of(744f, 800f))
        // Spazi minuscoli o enormi non rompono la griglia.
        assertEquals(GridMetrics(4, 2), GridMetrics.of(10f, 10f))
        assertEquals(GridMetrics(8, 6), GridMetrics.of(9000f, 9000f))
        assertEquals(28, GridMetrics(7, 4).perPage)
    }

    @Test
    fun `pages are full except the last, and nothing still gives one empty page`() {
        assertEquals(listOf(10, 10, 5), paginate((1..25).toList(), 10).map { it.size })
        assertEquals(listOf((1..25).toList().take(10)), paginate((1..25).toList(), 10).take(1))
        assertEquals(listOf(emptyList<Int>()), paginate(emptyList<Int>(), 10))
        assertEquals(listOf(listOf(1), listOf(2)), paginate(listOf(1, 2), 0)) // pagina da zero: si tratta come da uno
        assertEquals(1, paginate((1..32).toList(), 32).size)
    }
}

class IconsAndWallpaperTest {
    @Test
    fun `icon pack components are read in both long and relative form`() {
        assertEquals("com.app/com.app.Main", IconPackParser.componentKey("ComponentInfo{com.app/com.app.Main}"))
        assertEquals("com.app/com.app.Main", IconPackParser.componentKey("ComponentInfo{com.app/.Main}"))
        assertEquals(null, IconPackParser.componentKey(":LAUNCHER_ACTION_APP_DRAWER"))
        assertEquals(null, IconPackParser.componentKey("ComponentInfo{}"))
    }

    @Test
    fun `more blur means a smaller image and a larger radius`() {
        val plans = BlurLevel.entries.map { WallpaperLoader.blurPlan(it) }
        assertEquals(0, plans.first().second)
        plans.zipWithNext().forEach { (a, b) ->
            assertEquals(true, b.first < a.first)
            assertEquals(true, b.second > a.second)
        }
    }
}

class FavoritesReorderTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `reorder moves visible favorites and keeps hidden ones in place`() =
        runTest {
            val favorites = Favorites(tmp.root.resolve("f.txt"))
            listOf("a", "hidden", "b", "c").forEach { favorites.toggle(it) }
            favorites.reorder(listOf("c", "a", "b"))
            assertEquals(listOf("c", "hidden", "a", "b"), favorites.keys.value)
            favorites.reorder(listOf("c", "a")) // non corrisponde: ignorato
            favorites.reorder(listOf("x", "a", "b"))
            assertEquals(listOf("c", "hidden", "a", "b"), favorites.keys.value)
        }
}

class ReorderStateTest {
    private fun state() = ReorderState(listOf("a", "b", "c", "d")) { it }.apply { rowHeight = 100f }

    @Test
    fun `dragging past half a row swaps, and the offset stays under the finger`() {
        val s = state()
        s.start("a")
        assertEquals(false, s.drag(40f))
        assertEquals(true, s.drag(20f)) // 60 > 50: scambio con "b"
        assertEquals(listOf("b", "a", "c", "d"), s.order)
        assertEquals(-40f, s.offset, 0.001f)
        s.drag(100f)
        assertEquals(listOf("b", "c", "a", "d"), s.order)
        assertEquals(listOf("b", "c", "a", "d"), s.end())
        assertEquals(null, s.dragKey)
    }

    @Test
    fun `no movement means long press, and edges do not overflow`() {
        val s = state()
        s.start("a")
        s.drag(5f)
        assertEquals(null, s.end()) // solo pressione lunga: menu
        s.start("a")
        s.drag(-300f)
        assertEquals(listOf("a", "b", "c", "d"), s.order)
        s.start("d")
        s.drag(500f)
        assertEquals(listOf("a", "b", "c", "d"), s.order)
        s.cancel()
        assertEquals(null, s.dragKey)
    }
}
