package helium314.keyboard.latin

import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.NovaAccent
import helium314.keyboard.latin.settings.Defaults
import kotlin.test.Test
import kotlin.test.assertEquals

/** La tastiera parte con i colori di NOVA, di giorno e di notte. */
class NovaThemeTest {
    @Test fun `the NOVA theme is the default`() {
        assertEquals(KeyboardTheme.THEME_NOVA, Defaults.PREF_THEME_COLORS)
        assertEquals(KeyboardTheme.THEME_NOVA, Defaults.PREF_THEME_COLORS_NIGHT)
    }

    @Test fun `the Work row has the six editing keys`() {
        assertEquals(6, NovaAccent.WORK_KEYS.toSet().size)
    }
}
