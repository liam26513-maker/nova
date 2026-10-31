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
