package it.manlio.nova.core.modes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class QuickNotesTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `no file means an empty note`() {
        assertEquals("", QuickNotes(folder.root.resolve("note.txt")).read())
    }

    @Test
    fun `a note survives a restart, accents and new lines included, and leaves no temporary file`() {
        val file = folder.root.resolve("sotto/note.txt")
        QuickNotes(file).write("prima riga\nperché sì")
        QuickNotes(file).write("seconda versione\nè così")
        assertEquals("seconda versione\nè così", QuickNotes(file).read())
        assertFalse(file.resolveSibling("note.txt.tmp").exists())
    }
}
