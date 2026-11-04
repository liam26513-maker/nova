package it.manlio.nova

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AccessibilityConfigTest {
    private val config = File("src/main/res/xml/accessibility_service.xml").readText()

    @Test
    fun `the service keeps a normal switch, tied to no shortcut, and still sees the volume keys`() {
        assertFalse(config.contains("android:accessibilityFlags=\"flagRequestAccessibilityButton"))
        assertFalse(config.contains("|flagRequestAccessibilityButton"))
        assertTrue(config.contains("flagRequestFilterKeyEvents"))
    }
}
