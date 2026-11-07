package it.manlio.nova

/**
 * Il profilo automatico «gioco aperto → Gaming» (SPEC §8.7), spento di serie. Entra solo nel passaggio da un'altra app a un gioco,
 * e solo se nessuna modalità è accesa; esce tornando alla Home solo se era entrato da solo. Se l'utente esce a mano durante il
 * gioco, non rientra finché non si cambia app.
 */
internal class AutoGaming {
    enum class Step { NONE, ENTER, EXIT }

    private var last: String? = null
    private var entered = false

    fun onForeground(
        pkg: String,
        isGame: Boolean,
        isHome: Boolean,
        anyModeOn: Boolean,
        gamingOn: Boolean,
    ): Step {
        if (pkg == last) return Step.NONE
        last = pkg
        if (!gamingOn) entered = false
        return when {
            isGame && !anyModeOn -> Step.ENTER.also { entered = true }
            isHome && entered && gamingOn -> Step.EXIT.also { entered = false }
            else -> Step.NONE
        }
    }
}
