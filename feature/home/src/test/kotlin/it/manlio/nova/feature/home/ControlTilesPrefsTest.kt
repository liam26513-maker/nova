package it.manlio.nova.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class ControlTilesPrefsTest {
    private fun read(text: String) = PrefsCodec.read(text)

    @Test
    fun `the default first view is wifi, bluetooth, do not disturb and torch`() {
        assertEquals(ControlTileId.DEFAULT, HomePrefs().controlTiles)
        assertEquals(listOf(ControlTileId.WIFI, ControlTileId.BLUETOOTH, ControlTileId.DND, ControlTileId.TORCH), ControlTileId.DEFAULT)
    }

    @Test
    fun `the chosen tiles survive writing and reading`() {
        val chosen = listOf(ControlTileId.DND, ControlTileId.GRAPHICS, ControlTileId.TURBO)
        assertEquals(chosen, read(PrefsCodec.write(HomePrefs(controlTiles = chosen))).controlTiles)
    }

    @Test
    fun `an empty choice is kept, an absent one falls back to the default`() {
        assertEquals(emptyList<ControlTileId>(), read(PrefsCodec.write(HomePrefs(controlTiles = emptyList()))).controlTiles)
        val old = PrefsCodec.write(HomePrefs()).lines().filterNot { it.startsWith("controlTiles") }.joinToString("\n")
        assertEquals(ControlTileId.DEFAULT, read(old).controlTiles) // file scritto da una versione precedente
    }

    @Test
    fun `unknown and repeated names are ignored and the order is the fixed one`() {
        val text = PrefsCodec.write(HomePrefs()).lines().filterNot { it.startsWith("controlTiles") }.joinToString("\n")
        val read = read(text + "\ncontrolTiles=TURBO,NOPE,WIFI,WIFI, TORCH,torch\n").controlTiles
        assertEquals(listOf(ControlTileId.WIFI, ControlTileId.TORCH, ControlTileId.TURBO), read)
    }

    @Test
    fun `a shared theme neither carries nor overwrites the control tiles`() {
        val mine = HomePrefs(controlTiles = listOf(ControlTileId.TURBO))
        assertEquals(ControlTileId.DEFAULT, mine.appearance().controlTiles)
        assertEquals(listOf(ControlTileId.TURBO), mine.withLookOf(HomePrefs(theme = ThemeMode.LIGHT)).controlTiles)
    }
}
