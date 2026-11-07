package it.manlio.nova

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetRulesTest {
    @Test
    fun `a finger is ignored while the pen is near, the pen never`() {
        assertFalse(SheetRules.accepts(MotionEvent.TOOL_TYPE_FINGER, stylusNear = true))
        assertTrue(SheetRules.accepts(MotionEvent.TOOL_TYPE_FINGER, stylusNear = false))
        assertTrue(SheetRules.accepts(MotionEvent.TOOL_TYPE_STYLUS, stylusNear = true))
    }

    @Test
    fun `pressing harder draws thicker and the eraser does not depend on pressure`() {
        assertTrue(SheetRules.widthDp(erasing = false, pressure = 1f) > SheetRules.widthDp(erasing = false, pressure = 0.2f))
        assertEquals(SheetRules.widthDp(erasing = true, pressure = 0f), SheetRules.widthDp(erasing = true, pressure = 1f), 0f)
        assertEquals(SheetRules.widthDp(erasing = false, pressure = 1f), SheetRules.widthDp(erasing = false, pressure = 5f), 0f)
    }

    @Test
    fun `the eraser end and the pen button both erase`() {
        assertTrue(SheetRules.erasing(MotionEvent.TOOL_TYPE_ERASER, 0))
        assertTrue(SheetRules.erasing(MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.BUTTON_STYLUS_PRIMARY))
        assertFalse(SheetRules.erasing(MotionEvent.TOOL_TYPE_STYLUS, 0))
    }
}
