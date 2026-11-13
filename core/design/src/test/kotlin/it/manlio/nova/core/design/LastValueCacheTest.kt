package it.manlio.nova.core.design

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastValueCacheTest {
    @Test
    fun `the same key is computed once even when asked together`() =
        runBlocking {
            val cache = LastValueCache<String, Int>()
            val key = "sfondo"
            var made = 0
            val results =
                (1..5)
                    .map {
                        async {
                            cache.get(key) {
                                made++
                                delay(10)
                                42
                            }
                        }
                    }.awaitAll()
            assertEquals(List(5) { 42 }, results)
            assertEquals(1, made)
            assertEquals(42, cache.peek(key))
        }

    @Test
    fun `a new key replaces the old one`() =
        runBlocking {
            val cache = LastValueCache<String, Int>()
            val first = "a"
            cache.get(first) { 1 }
            cache.get("b") { 2 }
            assertNull(cache.peek(first))
            assertEquals(2, cache.peek("b"))
        }
}
