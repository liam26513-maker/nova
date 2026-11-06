package it.manlio.nova.core.system

/** Il risultato di una misura dei fotogrammi di un gioco (vedi [TimeStatsParser]). Tempi in millisecondi. */
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
    /** Stop più lunghi di 750 ms (caricamenti, menu, app in secondo piano): non contano come scatto. */
    val pauses: Int,
)
