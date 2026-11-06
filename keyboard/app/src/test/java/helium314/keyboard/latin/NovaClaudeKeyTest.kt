package helium314.keyboard.latin

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode.checkAndConvertCode
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.defaultToolbarPref
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** «Invia a Claude» c'è solo se l'utente lo attiva (SPEC §10.4). */
class NovaClaudeKeyTest {
    @Test fun `the key is off by default`() {
        val entry = ToolbarKey.SEND_TO_CLAUDE.name + Separators.KV + false
        assertTrue(entry in defaultToolbarPref.split(Separators.ENTRY))
    }

    @Test fun `its code is a valid key code`() = assertEquals(KeyCode.SEND_TO_CLAUDE, KeyCode.SEND_TO_CLAUDE.checkAndConvertCode())
}
