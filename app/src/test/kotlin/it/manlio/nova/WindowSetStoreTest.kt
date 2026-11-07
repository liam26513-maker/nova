package it.manlio.nova

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WindowSetStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `newest set first, no duplicates, at most three`() {
        val store = WindowSetStore(File(tmp.root, "sets.txt"))
        store.add(listOf("a/A", "b/B"))
        store.add(listOf("c/C"))
        store.add(listOf("a/A", "b/B"))
        store.add(listOf("d/D"))
        store.add(listOf("e/E"))

        assertEquals(listOf(listOf("e/E"), listOf("d/D"), listOf("a/A", "b/B")), store.all())
    }
}
