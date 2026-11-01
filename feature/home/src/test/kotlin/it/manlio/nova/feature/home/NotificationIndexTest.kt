package it.manlio.nova.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationIndexTest {
    private fun n(
        key: String,
        pkg: String,
        title: String = "Titolo",
        text: String = "Testo",
        whenMs: Long = 1,
        ongoing: Boolean = false,
        summary: Boolean = false,
        media: Boolean = false,
    ) = RawNotification(key, pkg, title, text, whenMs, ongoing, summary, media)

    @Test
    fun `the latest notification of an app wins and all of them are counted`() {
        val map =
            NotificationIndex.build(
                listOf(n("a", "chat", "Mario", "ciao", 10), n("b", "chat", "Anna", "ci sei?", 30), n("c", "chat", "Luca", "ok", 20)),
            )
        val p = map.getValue("chat")
        assertEquals(NotificationPreview("chat", "b", "Anna", "ci sei?", 3, 30), p)
    }

    @Test
    fun `service notifications do not count`() {
        val map =
            NotificationIndex.build(
                listOf(
                    n("1", "vpn", ongoing = true),
                    n("2", "mail", summary = true),
                    n("3", "player", media = true),
                    n("4", "vuota", title = " ", text = ""),
                    n("5", "mail", "Nuovo messaggio", "da Anna"),
                ),
            )
        assertEquals(setOf("mail"), map.keys)
        assertEquals(1, map.getValue("mail").count)
    }

    @Test
    fun `apps are kept apart and there is nothing for no notifications`() {
        val map = NotificationIndex.build(listOf(n("1", "a"), n("2", "b"), n("3", "b")))
        assertEquals(1, map.getValue("a").count)
        assertEquals(2, map.getValue("b").count)
        assertTrue(NotificationIndex.build(emptyList()).isEmpty())
    }

    @Test
    fun `the line shows title and text, or just what is there`() {
        assertEquals("Anna: ciao", NotificationIndex.line(NotificationPreview("p", "k", "Anna", "ciao", 1, 0)))
        assertEquals("ciao", NotificationIndex.line(NotificationPreview("p", "k", "", "ciao", 1, 0)))
        assertEquals("Anna", NotificationIndex.line(NotificationPreview("p", "k", "Anna", " ", 1, 0)))
    }

    @Test
    fun `text is trimmed`() {
        assertEquals("Anna", NotificationIndex.build(listOf(n("1", "p", "  Anna  ", "x"))).getValue("p").title)
    }
}
