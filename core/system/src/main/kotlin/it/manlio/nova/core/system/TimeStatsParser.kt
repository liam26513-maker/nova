package it.manlio.nova.core.system

/**
 * Legge l'elenco di `dumpsys SurfaceFlinger --timestats -dump`: per ogni livello grafico il nome, `totalFrames`, `averageFPS` e
 * l'istogramma «presentToPresent» (`8ms=12 9ms=40 …`: quanti fotogrammi sono arrivati a quella distanza dal precedente). Pura,
 * senza Android. Se il formato non è quello atteso restituisce `null` (e il chiamante mostra il testo grezzo per correggere).
 */
object TimeStatsParser {
    private val FRAMES = Regex("totalFrames\\s*=\\s*(\\d+)")
    private val FPS = Regex("averageFPS\\s*=\\s*([0-9.]+)")
    private val BIN = Regex("(\\d+)ms\\s*=\\s*(\\d+)")
    private const val HEADER = "presentToPresent"
    private const val MIN_FRAMES = 20
    private const val SLOW_MS = 25 // oltre una volta e mezza il ritmo di un 60 Hz
    private const val ONE_PERCENT = 100
    private const val MS_PER_S = 1000.0
    private const val PERCENT = 100.0

    fun parse(
        dump: String,
        packageName: String,
    ): FrameReport? =
        dump
            .split("layerName")
            .drop(1)
            .filter { block -> packageName in block.lineSequence().first() }
            .mapNotNull(::layer)
            .maxByOrNull { it.frames }

    private fun layer(block: String): FrameReport? {
        val total = FRAMES.find(block)?.groupValues?.get(1)?.toIntOrNull()
        val fps = FPS.find(block)?.groupValues?.get(1)?.toDoubleOrNull()
        val start = block.indexOf(HEADER)
        if (total == null || fps == null || start < 0 || total < MIN_FRAMES || fps <= 0) return null
        val bins = block.substring(start).lines().drop(1).takeWhile { "histogram" !in it }.flatMap { BIN.findAll(it).toList() }
        val deltas = bins.flatMap { m -> List(m.groupValues[2].toInt()) { m.groupValues[1].toInt() } }
        val moving = deltas.filter { it < FrameCollector.PAUSE_MS }
        if (moving.size < MIN_FRAMES) return null
        val worst = moving.sortedDescending()
        val lowest = worst.take(maxOf(1, moving.size / ONE_PERCENT)).average()
        return FrameReport(
            frames = total,
            seconds = total / fps,
            fps = fps,
            lowFps = MS_PER_S / maxOf(lowest, 1.0),
            slowPercent = PERCENT * moving.count { it > SLOW_MS } / moving.size,
            worstMs = worst.first().toDouble(),
            pauses = deltas.size - moving.size,
        )
    }
}
