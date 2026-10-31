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
                    blur = false,
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
