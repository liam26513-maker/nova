package it.manlio.nova.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class StandbyPhotosTest {
    @Test
    fun `big photos are reduced by powers of two, never below the wanted side`() {
        assertEquals(1, StandbyPhotos.sampleSize(1600, 1200, 1600))
        assertEquals(2, StandbyPhotos.sampleSize(4000, 3000, 1600))
        assertEquals(4, StandbyPhotos.sampleSize(8000, 6000, 1600))
        assertEquals(1, StandbyPhotos.sampleSize(800, 600, 1600))
    }

    @Test
    fun `one photo every 20 seconds, starting again after the last`() {
        assertEquals(0, StandbyPhotos.indexFor(0, 3))
        assertEquals(1, StandbyPhotos.indexFor(20_000, 3))
        assertEquals(0, StandbyPhotos.indexFor(60_000, 3))
        assertEquals(0, StandbyPhotos.indexFor(123_456, 0))
    }
}
