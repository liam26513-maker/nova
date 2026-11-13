package it.manlio.nova

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PenBarTest {
    @Test
    fun `the pen bar appears only in ibisPaint and in NOVA notes`() {
        assertTrue(PenBar.penTarget("jp.ne.ibis.ibispaintx.app"))
        assertTrue(PenBar.penTarget(PenBar.NOTES))
        assertFalse(PenBar.penTarget("com.whatsapp"))
        assertFalse(PenBar.penTarget("it.manlio.nova"))
        assertFalse(PenBar.penTarget(null))
    }
}
