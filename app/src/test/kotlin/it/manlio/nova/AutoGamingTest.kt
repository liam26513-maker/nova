package it.manlio.nova

import it.manlio.nova.AutoGaming.Step
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoGamingTest {
    private val auto = AutoGaming()

    private fun open(
        pkg: String,
        game: Boolean = false,
        home: Boolean = false,
        anyOn: Boolean = false,
        gamingOn: Boolean = false,
    ) = auto.onForeground(pkg, game, home, anyOn, gamingOn)

    @Test
    fun `enters on a game and leaves on the Home only if it entered by itself`() {
        assertEquals(Step.ENTER, open("game", game = true))
        assertEquals(Step.NONE, open("game", game = true, anyOn = true, gamingOn = true))
        assertEquals(Step.EXIT, open("home", home = true, anyOn = true, gamingOn = true))
    }

    @Test
    fun `never touches another mode or a gaming started by hand`() {
        assertEquals(Step.NONE, open("game", game = true, anyOn = true))
        assertEquals(Step.NONE, open("home", home = true, anyOn = true, gamingOn = true))
    }

    @Test
    fun `after leaving by hand in the game it does not come back until the app changes`() {
        assertEquals(Step.ENTER, open("game", game = true))
        assertEquals(Step.NONE, open("game", game = true))
        assertEquals(Step.NONE, open("home", home = true))
        assertEquals(Step.ENTER, open("game", game = true))
    }
}
