package it.manlio.nova

import org.junit.Assert.assertEquals
import org.junit.Test

class RuleCodecTest {
    @Test
    fun `rules survive a round trip, even with odd text`() {
        val rules =
            listOf(
                Rule(1, true, When.TIME, "22", Then.TOGGLE_ON, "NIGHT_LIGHT"),
                Rule(2, false, When.HEADPHONES_ON, "", Then.OPEN_APP, "com.spotify.music"),
                Rule(3, true, When.BATTERY_BELOW, "20", Then.NOTIFY, "Carica il tablet, è quasi scarico!"),
            )
        assertEquals(rules, RuleCodec.decode(RuleCodec.encode(rules)))
    }

    @Test
    fun `broken or unknown lines are skipped, the others stay`() {
        val good = RuleCodec.encode(listOf(Rule(1, true, When.WIFI_ON, "", Then.MODE_ON, "lavoro")))
        val text = "rotta\n$good\n9\u001Ftrue\u001FFUTURO\u001F\u001FNOTIFY\u001Fx"
        assertEquals(1, RuleCodec.decode(text).size)
    }

    @Test
    fun `newlines in a notification text do not break the file`() {
        val rule = Rule(4, true, When.CHARGER_ON, "", Then.NOTIFY, "riga uno\nriga due")
        assertEquals("riga uno riga due", RuleCodec.decode(RuleCodec.encode(listOf(rule))).single().thenArg)
    }
}
