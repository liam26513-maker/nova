package it.manlio.nova.core.system

/** Il risultato di una misura dei fotogrammi di un gioco (vedi [FrameCollector]). Tempi in millisecondi. */
data class FrameReport(
    val frames: Int,
    val seconds: Double,
    /** Fotogrammi al secondo in media. */
    val fps: Double,
    /** FPS del peggiore 1% dei fotogrammi: dice quanto scatta, più dei FPS medi. */
    val lowFps: Double,
    /** Percentuale di fotogrammi più lenti di una volta e mezza il ritmo dello schermo (cioè percepibili come scatto). */
    val slowPercent: Double,
    /** Il fotogramma più lungo (esclusi gli stop come caricamenti o menu). */
    val worstMs: Double,
    /** Stop più lunghi di [FrameCollector.PAUSE_MS] (caricamenti, menu, app in secondo piano): non contano come scatto. */
    val pauses: Int,
)

/**
 * Raccoglie i tempi dei fotogrammi letti più volte da `dumpsys SurfaceFlinger --latency <livello>` (ogni lettura dà gli ultimi
 * 127 fotogrammi) e ne ricava [FrameReport]. Pura, senza Android: si prova con testo di esempio. Formato: la prima riga è il
 * ritmo dello schermo in nanosecondi; poi righe di tre numeri (richiesto, mostrato, pronto) in nanosecondi; `0` o il massimo di un
 * `Long` = fotogramma non ancora mostrato.
 */
class FrameCollector {
    private val presents = ArrayList<Long>()
    private var period = 0L
    private var last = Long.MIN_VALUE

    /** Aggiunge una lettura; i fotogrammi già visti in letture precedenti si scartano. @return quanti ne ha aggiunti di nuovi. */
    fun add(output: String): Int {
        val lines = output.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val head = lines.firstOrNull()?.toLongOrNull() ?: return 0
        if (head > 0) period = head
        var added = 0
        for (line in lines.drop(1)) {
            val shown = line.split(Regex("\\s+")).getOrNull(1)?.toLongOrNull() ?: continue
            if (shown <= 0 || shown == Long.MAX_VALUE || shown <= last) continue
            presents += shown
            last = shown
            added++
        }
        return added
    }

    fun report(): FrameReport? {
        if (presents.size < MIN_FRAMES) return null
        val all = presents.zipWithNext { a, b -> (b - a) / NS_PER_MS }
        val deltas = all.filter { it < PAUSE_MS }
        if (deltas.size < MIN_FRAMES - 1) return null
        val seconds = deltas.sum() / MS_PER_S
        val periodMs = if (period > 0) period / NS_PER_MS else DEFAULT_PERIOD_MS
        val worst = deltas.sortedDescending()
        val worstOne = worst.take(maxOf(1, deltas.size / ONE_PERCENT)).average()
        return FrameReport(
            frames = deltas.size + 1,
            seconds = seconds,
            fps = deltas.size / seconds,
            lowFps = MS_PER_S / worstOne,
            slowPercent = PERCENT * deltas.count { it > periodMs * SLOW_FACTOR } / deltas.size,
            worstMs = worst.first(),
            pauses = all.size - deltas.size,
        )
    }

    companion object {
        /** Uno stop più lungo di questo (in millisecondi) è un caricamento o un menu, non uno scatto. */
        const val PAUSE_MS = 750.0
        private const val MIN_FRAMES = 20
        private const val NS_PER_MS = 1_000_000.0
        private const val MS_PER_S = 1000.0
        private const val DEFAULT_PERIOD_MS = 16.67
        private const val SLOW_FACTOR = 1.5
        private const val ONE_PERCENT = 100
        private const val PERCENT = 100.0
    }
}

/** Sceglie quale livello grafico di `dumpsys SurfaceFlinger --list` è quello di un gioco. */
object LayerPicker {
    /** Preferisce `SurfaceView[<pacchetto>/…]` (è dove disegnano i giochi), poi un livello qualunque dell'app; `null` se non c'è. */
    fun pick(
        list: String,
        packageName: String,
    ): String? {
        val mine = list.lines().map { it.trim() }.filter { packageName in it && !it.startsWith("Background for") }
        return mine.firstOrNull { it.startsWith("SurfaceView[") } ?: mine.firstOrNull { it.contains('/') }
    }
}
