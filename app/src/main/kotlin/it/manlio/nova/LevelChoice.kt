package it.manlio.nova

import it.manlio.nova.core.design.GraphicsLevel
import it.manlio.nova.core.design.GraphicsState

/** Scelta del livello grafico mostrata all'utente: Automatico oppure un livello fisso. */
sealed interface LevelChoice {
    data object Automatic : LevelChoice

    data class Fixed(val level: GraphicsLevel) : LevelChoice

    companion object {
        val all: List<LevelChoice> = listOf(Automatic) + GraphicsLevel.entries.map(::Fixed)

        fun of(state: GraphicsState): LevelChoice = if (state.automatic) Automatic else Fixed(state.level)
    }
}
