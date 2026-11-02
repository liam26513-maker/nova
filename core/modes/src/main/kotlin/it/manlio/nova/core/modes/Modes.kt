package it.manlio.nova.core.modes

/**
 * Le cose che una modalità può fare all'ingresso (SPEC §8.1). Insieme **chiuso**: una modalità non può chiedere altro.
 * Ognuna sa annullarsi da sola ([ModeEnvironment.undo]) con ciò che ha salvato entrando.
 */
enum class ModeAction {
    /** Livello grafico di NOVA su «Prestazioni». */
    PERFORMANCE_GRAPHICS,

    /** Non disturbare acceso (serve Shizuku). */
    DO_NOT_DISTURB,

    /** Rotazione dello schermo bloccata com'è (serve il permesso di modificare le impostazioni). */
    LOCK_ROTATION,

    /** Chiude le app in secondo piano (serve Shizuku). Non si può annullare: le app si riaprono quando servono. */
    TURBO,
}

/** Una modalità: [actions] all'ingresso, nell'ordine dato; si esce da sola dopo [maxMinutes] (rete di sicurezza). */
data class ModeDefinition(
    val id: String,
    val actions: List<ModeAction>,
    val maxMinutes: Int,
)

object Modes {
    /**
     * Gaming di base (SPEC §8.2), solo manuale. Il Turbo va per ultimo perché è la parte lenta.
     * Non c'è il Wi-Fi a bassa latenza: per Android il blocco funziona solo se l'app che lo tiene è in primo piano,
     * e durante il gioco NOVA non lo è.
     * shortcut: definita qui nel codice e non in config/modes/*.json (SPEC §8.1); passare al JSON quando le modalità saranno più d'una.
     */
    val GAMING =
        ModeDefinition(
            id = "gaming",
            actions =
                listOf(
                    ModeAction.PERFORMANCE_GRAPHICS,
                    ModeAction.DO_NOT_DISTURB,
                    ModeAction.LOCK_ROTATION,
                    ModeAction.TURBO,
                ),
            maxMinutes = 6 * 60,
        )

    val all: List<ModeDefinition> = listOf(GAMING)

    fun byId(id: String): ModeDefinition? = all.firstOrNull { it.id == id }
}
