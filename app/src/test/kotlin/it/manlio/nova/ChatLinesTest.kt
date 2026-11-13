package it.manlio.nova

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatLinesTest {
    @Test
    fun `the last messages are shown, oldest first, with who wrote them`() {
        val messages = (1..8).map { "Anna" to "messaggio $it" } + (null to "ok, arrivo")
        val lines = ChatLines.from(messages, "Anna", "ok, arrivo")
        assertEquals(ChatLines.MAX, lines.size)
        assertEquals(ChatLine("Anna", "messaggio 4"), lines.first())
        assertEquals(ChatLine(null, "ok, arrivo"), lines.last())
    }

    @Test
    fun `without single messages the notification text is enough`() {
        assertEquals(listOf(ChatLine("Mario", "ci vediamo?")), ChatLines.from(emptyList(), "Mario", " ci vediamo? "))
        assertEquals(emptyList<ChatLine>(), ChatLines.from(listOf("Mario" to " "), "Mario", ""))
    }
}
