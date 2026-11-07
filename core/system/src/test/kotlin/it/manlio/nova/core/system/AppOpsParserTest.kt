package it.manlio.nova.core.system

import org.junit.Assert.assertEquals
import org.junit.Test

class AppOpsParserTest {
    @Test
    fun `keeps only camera, microphone and location with a time`() {
        val text =
            """
            Uid mode: COARSE_LOCATION: foreground
            FINE_LOCATION: allow; time=+2h3m ago
            RECORD_AUDIO: allow; time=+5d20h0m42s447ms ago; duration=+1s983ms
            CAMERA: ignore
            WAKE_LOCK: allow; time=+1m ago
            """.trimIndent()

        assertEquals(
            listOf(
                SensitiveUse("FINE_LOCATION", (2 * 60 + 3) * 60_000L),
                SensitiveUse("RECORD_AUDIO", 5 * 86_400_000L + 20 * 3_600_000L + 42_447L),
            ),
            AppOpsParser.parse(text),
        )
    }

    @Test
    fun `milliseconds are not read as minutes`() {
        assertEquals(1_500L, AppOpsParser.duration("1s500ms"))
    }

    @Test
    fun `the appops command lists one valid package only`() {
        assertEquals(listOf("appops", "get", "com.example.app"), SystemCommand.AppOps("com.example.app").argv)
    }
}
