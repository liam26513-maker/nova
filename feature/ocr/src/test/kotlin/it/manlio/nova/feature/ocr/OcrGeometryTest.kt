package it.manlio.nova.feature.ocr

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class OcrGeometryTest {
    @Test
    fun `a tilted rectangle of points gives back its size and angle`() {
        val angle = 10 * PI / 180
        val points =
            (0 until 80).flatMap { u ->
                (0 until 12).map { v ->
                    (100 + u * cos(angle) - v * sin(angle)).roundToInt() to (50 + u * sin(angle) + v * cos(angle)).roundToInt()
                }
            }
        val box = OcrGeometry.minRect(points)
        assertEquals(80f, box.width, 3f)
        assertEquals(12f, box.height, 3f)
        assertEquals(angle.toFloat(), box.angle, 0.05f)
    }

    @Test
    fun `two lines in the probability map become two boxes, top first`() {
        val w = 64
        val h = 32
        val prob = FloatArray(w * h)

        fun fill(
            y0: Int,
            y1: Int,
        ) {
            for (y in y0..y1) for (x in 4..59) prob[y * w + x] = 0.9f
        }
        fill(20, 25)
        fill(4, 9)
        val boxes = OcrGeometry.boxes(prob, w, h, 2f, 2f)
        assertEquals(2, boxes.size)
        assert(boxes[0].cy < boxes[1].cy)
        assert(boxes[0].width > boxes[0].height * 3)
    }

    @Test
    fun `weak spots are ignored`() {
        val prob = FloatArray(32 * 32) { 0.35f }
        assertEquals(0, OcrGeometry.boxes(prob, 32, 32, 1f, 1f).size)
    }

    @Test
    fun `ctc drops blanks and repeats`() {
        val chars = listOf("blank", "c", "i", "a", "o", " ")
        val path = intArrayOf(1, 1, 0, 2, 3, 3, 0, 3, 4)
        val scores = FloatArray(path.size * chars.size).also { s -> path.forEachIndexed { t, k -> s[t * chars.size + k] = 1f } }
        assertEquals("ciaao", OcrGeometry.ctc(scores, path.size, chars.size, chars))
    }
}
