package it.manlio.nova

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SheetCropTest {
    @Test
    fun `the box wraps only what is drawn`() {
        val paper = 1
        val pixels = IntArray(10 * 8) { paper }
        pixels[2 * 10 + 3] = 9
        pixels[5 * 10 + 6] = 9
        assertArrayEquals(intArrayOf(3, 2, 7, 6), SheetCrop.bounds(pixels, 10, paper))
    }

    @Test
    fun `an empty sheet has no box`() {
        assertNull(SheetCrop.bounds(IntArray(16) { 1 }, 4, 1))
    }
}
