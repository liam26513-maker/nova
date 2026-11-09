package it.manlio.nova.core.system

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentTasksCodecTest {
    @Test
    fun `tasks survive a round trip in order`() {
        val tasks =
            listOf(
                RecentTask(32, "com.google.android.calculator", "com.google.android.calculator/com.android.calculator2.Calculator"),
                RecentTask(7, "jp.ne.ibis.ibispaintx.app", ""),
            )
        assertEquals(tasks, RecentTasksCodec.decode(RecentTasksCodec.encode(tasks)))
    }

    @Test
    fun `broken lines are skipped, the rest stays`() {
        val text = "x\tcom.a\tc\n\n12\t\tc\n5\tcom.b\tcom.b/.Main\n9"
        assertEquals(listOf(RecentTask(5, "com.b", "com.b/.Main")), RecentTasksCodec.decode(text))
    }

    @Test
    fun `nothing from the shell means no tasks`() {
        assertEquals(emptyList<RecentTask>(), RecentTasksCodec.decode(""))
    }
}
