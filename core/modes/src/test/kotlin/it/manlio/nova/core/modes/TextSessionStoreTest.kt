package it.manlio.nova.core.modes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TextSessionStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store() = TextSessionStore(File(tmp.root, "mode/session.txt"))

    @Test
    fun `a session survives writing and reading`() {
        val session = Session("gaming", 12345L, listOf(UndoToken("graphics", "auto"), UndoToken("rotation", "1")), leftover = false)
        store().save(session)
        assertEquals(session, store().load())
    }

    @Test
    fun `tabs, new lines and backslashes in the values are kept`() {
        val session = Session("x", 1L, listOf(UndoToken("a\tb", "riga1\nriga2\\fine")), leftover = true)
        store().save(session)
        assertEquals(session, store().load())
    }

    @Test
    fun `saving null removes the file and a missing or damaged file reads as no session`() {
        val s = store()
        s.save(Session("gaming", 1L, emptyList()))
        s.save(null)
        assertFalse(File(tmp.root, "mode/session.txt").exists())
        assertNull(s.load())
        File(tmp.root, "mode/session.txt").writeText("roba senza senso\n")
        assertNull(s.load())
    }
}
