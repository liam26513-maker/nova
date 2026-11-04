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

    /** Lo schermo non si spegne per inattività (30 minuti). Serve il permesso di modificare le impostazioni. */
    KEEP_SCREEN_ON,

    /** Sincronizzazione automatica degli account in pausa: niente lavoro in secondo piano durante il gioco. */
    PAUSE_SYNC,

    /** Filtro luce blu spento, per vedere i colori veri (serve Shizuku). */
    NIGHT_LIGHT_OFF,

    /** Filtro caldo acceso per leggere (serve Shizuku). Solo se l'utente lo sceglie per la Studio. */
    NIGHT_LIGHT_ON,

    /** Luminosità al massimo, regolazione automatica spenta. Serve il permesso di modificare le impostazioni. */
    FULL_BRIGHTNESS,

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
     * shortcut: le modalità sono nel codice e non in config/modes/<nome>.json (SPEC §8.1);
     * passare al JSON quando si vorrà cambiarle senza un nuovo APK.
     */
    val GAMING =
        ModeDefinition(
            id = "gaming",
            actions =
                listOf(
                    ModeAction.PERFORMANCE_GRAPHICS,
                    ModeAction.DO_NOT_DISTURB,
                    ModeAction.LOCK_ROTATION,
                    ModeAction.KEEP_SCREEN_ON,
                    ModeAction.PAUSE_SYNC,
                    ModeAction.TURBO,
                ),
            maxMinutes = 6 * 60,
        )

    /** Studio (SPEC §8.4): niente notifiche, schermo acceso e, se scelto, filtro caldo. Il Pomodoro sta in [Pomodoro]. */
    val STUDY =
        ModeDefinition(
            id = "studio",
            actions = listOf(ModeAction.DO_NOT_DISTURB, ModeAction.KEEP_SCREEN_ON, ModeAction.NIGHT_LIGHT_ON),
            maxMinutes = 4 * 60,
        )

    /** Lavoro di base (SPEC §8.3): niente notifiche. Desktop e finestre flottanti sono ancora da fare. */
    val WORK =
        ModeDefinition(
            id = "lavoro",
            actions = listOf(ModeAction.DO_NOT_DISTURB),
            maxMinutes = 10 * 60,
        )

    /** Creativa di base (SPEC §8.5): effetti di NOVA spenti, niente notifiche, schermo fermo e acceso, colori veri. */
    val CREATIVE =
        ModeDefinition(
            id = "creativa",
            actions =
                listOf(
                    ModeAction.PERFORMANCE_GRAPHICS,
                    ModeAction.DO_NOT_DISTURB,
                    ModeAction.LOCK_ROTATION,
                    ModeAction.KEEP_SCREEN_ON,
                    ModeAction.NIGHT_LIGHT_OFF,
                ),
            maxMinutes = 6 * 60,
        )

    /** Presentazione di base (SPEC §8.6): niente notifiche, schermo fermo, acceso e al massimo. La Home «portfolio» è ancora da fare. */
    val PRESENTATION =
        ModeDefinition(
            id = "presentazione",
            actions =
                listOf(
                    ModeAction.DO_NOT_DISTURB,
                    ModeAction.LOCK_ROTATION,
                    ModeAction.KEEP_SCREEN_ON,
                    ModeAction.FULL_BRIGHTNESS,
                ),
            maxMinutes = 3 * 60,
        )

    val all: List<ModeDefinition> = listOf(GAMING, STUDY, WORK, CREATIVE, PRESENTATION)

    fun byId(id: String): ModeDefinition? = all.firstOrNull { it.id == id }
}
