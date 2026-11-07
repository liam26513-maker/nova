package it.manlio.nova.core.modes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExitPinTest {
    @get:Rule val folder = TemporaryFolder()

    private val file: File get() = File(folder.root, "pin.txt")

    @Test
    fun `the right pin matches, a wrong one does not, and the file never holds it`() {
        val pin = ExitPin(file)
        assertFalse(pin.isSet)
        assertFalse(pin.matches("1234"))

        pin.set("1234")

        assertTrue(pin.isSet)
        assertTrue(ExitPin(file).matches("1234"))
        assertFalse(pin.matches("4321"))
        assertFalse("1234" in file.readText())
    }

    @Test
    fun `clearing removes the pin`() {
        val pin = ExitPin(file)
        pin.set("123456")
        pin.clear()
        assertFalse(pin.isSet)
    }

    @Test
    fun `only four to eight digits are valid`() {
        listOf("1234", "12345678").forEach { assertTrue(ExitPin.valid(it)) }
        listOf("", "123", "123456789", "12a4").forEach { assertFalse(ExitPin.valid(it)) }
    }
}
