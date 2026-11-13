package helium314.keyboard.latin

import helium314.keyboard.settings.screens.KeyArrangement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** L'editor della disposizione dei tasti di NOVA: lettura, scambio e scrittura del formato semplice. */
class NovaKeyArrangementTest {
    private val qwerty = "q\nw\ne é è\n\na\ns\n\nz\nx\n"

    @Test fun `rows are read with their long press characters`() {
        assertEquals(listOf(listOf("q", "w", "e é è"), listOf("a", "s"), listOf("z", "x")), KeyArrangement.parse(qwerty))
        assertEquals("e", KeyArrangement.label("e é è"))
    }

    @Test fun `swapping moves the key with its long press characters, also across rows`() {
        val rows = KeyArrangement.swap(KeyArrangement.parse(qwerty)!!, 0 to 2, 2 to 1)
        assertEquals(listOf(listOf("q", "w", "x"), listOf("a", "s"), listOf("z", "e é è")), rows)
        assertEquals("q\nw\nx\n\na\ns\n\nz\ne é è", KeyArrangement.serialize(rows))
    }

    @Test fun `json layouts are not edited here`() {
        assertNull(KeyArrangement.parse("[[{\"label\": \"q\"}]]"))
        assertNull(KeyArrangement.parse(""))
    }
}
