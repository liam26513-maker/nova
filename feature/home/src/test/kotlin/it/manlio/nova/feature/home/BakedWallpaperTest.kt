package it.manlio.nova.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class BakedWallpaperTest {
    @Test
    fun `a portrait photo on a landscape screen keeps the middle band`() {
        // Foto 800x1280, schermo 1280x800: si tiene tutta la larghezza e una fascia centrale alta 500.
        assertEquals(Crop(0, 390, 800, 500), cropRect(800, 1280, 1280, 800))
    }

    @Test
    fun `a wide photo keeps the middle columns`() {
        assertEquals(Crop(100, 0, 400, 250), cropRect(600, 250, 1280, 800))
    }

    @Test
    fun `same shape keeps everything`() {
        assertEquals(Crop(0, 0, 320, 200), cropRect(320, 200, 1280, 800))
    }
}
