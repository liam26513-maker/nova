package it.manlio.nova.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Il vetro liquido (26 novembre 2026): spento di serie, si salva, il tema «Vetro» lo accende. */
class LiquidGlassTest {
    @Test
    fun `spento di serie e salvato`() {
        assertFalse(HomePrefs().liquidGlass)
        assertTrue(PrefsCodec.read(PrefsCodec.write(HomePrefs(liquidGlass = true))).liquidGlass)
        assertTrue(HomePrefs(liquidGlass = true).look().liquid)
    }

    @Test
    fun `il tema Vetro accende il vetro liquido e tiene la disposizione`() {
        val vetro = ThemePresets.all.first { it.name == "Vetro" }
        val mine = HomePrefs(renamed = mapOf("a" to "Mia"))
        val applied = mine.withLookOf(vetro.prefs)
        assertTrue(applied.liquidGlass)
        assertEquals(mapOf("a" to "Mia"), applied.renamed)
    }
}
