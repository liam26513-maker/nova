package it.manlio.nova.core.design

/**
 * Sfocatura a scatola (tre passate ≈ gaussiana) su pixel ARGB.
 * Usata una sola volta per preparare lo sfondo del livello Bilanciato, su
 * un'immagine rimpicciolita: il costo per frame è zero (SPEC §5.3).
 * Il canale alfa viene sfocato come gli altri.
 */
object BoxBlur {
    fun blur(
        pixels: IntArray,
        width: Int,
        height: Int,
        radius: Int,
        passes: Int = 3,
    ): IntArray {
        require(pixels.size == width * height) { "Dimensioni non coerenti" }
        if (radius <= 0 || width == 0 || height == 0) return pixels.copyOf()
        var src = pixels.copyOf()
        var tmp = IntArray(src.size)
        repeat(passes) {
            pass(src, tmp, width, height, radius, horizontal = true)
            pass(tmp, src, width, height, radius, horizontal = false)
        }
        return src
    }

    private fun pass(
        input: IntArray,
        output: IntArray,
        w: Int,
        h: Int,
        r: Int,
        horizontal: Boolean,
    ) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val window = 2 * r + 1
        for (line in 0 until lines) {
            fun at(i: Int): Int {
                val c = i.coerceIn(0, len - 1) // bordi estesi
                return if (horizontal) input[line * w + c] else input[c * w + line]
            }
            var a = 0
            var red = 0
            var g = 0
            var b = 0
            for (i in -r..r) {
                val p = at(i)
                a += p ushr 24
                red += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
            }
            for (i in 0 until len) {
                val out = ((a / window) shl 24) or ((red / window) shl 16) or ((g / window) shl 8) or (b / window)
                if (horizontal) output[line * w + i] = out else output[i * w + line] = out
                val add = at(i + r + 1)
                val sub = at(i - r)
                a += (add ushr 24) - (sub ushr 24)
                red += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                g += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                b += (add and 0xFF) - (sub and 0xFF)
            }
        }
    }
}
