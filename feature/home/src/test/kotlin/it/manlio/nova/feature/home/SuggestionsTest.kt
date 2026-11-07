package it.manlio.nova.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class SuggestionsTest {
    private val zone = ZoneOffset.UTC
    private val now = LocalDateTime.of(2026, 11, 7, 20, 0).toInstant(zone).toEpochMilli()
    private val day = 86_400_000L
    private val hour = 3_600_000L

    @Test
    fun `apps opened at this hour beat apps opened as often at another hour`() {
        val opens = listOf("study" to now - day - 10 * hour, "game" to now - day)
        assertEquals(listOf("game", "study"), Suggestions.rank(opens, now, zone))
    }

    @Test
    fun `recent opens weigh more than old ones`() {
        val opens = listOf("old" to now - 20 * day, "old" to now - 21 * day, "new" to now - day)
        assertEquals("new", Suggestions.rank(opens, now, zone).first())
    }

    @Test
    fun `skipped apps never appear and at most five are suggested`() {
        val opens = (1..8).map { "app$it" to now - it * hour } + ("home" to now - hour)
        val ranked = Suggestions.rank(opens, now, zone, skip = setOf("home"))
        assertEquals(Suggestions.COUNT, ranked.size)
        assert("home" !in ranked)
    }
}
