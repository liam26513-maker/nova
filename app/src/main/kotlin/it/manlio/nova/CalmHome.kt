package it.manlio.nova

import it.manlio.nova.core.modes.ActionOutcome
import it.manlio.nova.core.modes.UndoToken
import it.manlio.nova.feature.home.BlurLevel
import it.manlio.nova.feature.home.ClockStyle
import it.manlio.nova.feature.home.HomeFont
import it.manlio.nova.feature.home.HomePrefs
import it.manlio.nova.feature.home.HomePrefsStore
import it.manlio.nova.feature.home.IconStyle
import it.manlio.nova.feature.home.TextWeight
import java.io.File

/**
 * «Home essenziale» durante una modalità (azione `CALM_HOME`, SPEC §8.1): la tua Home di sempre, ma più quieta. Il tuo sfondo
 * molto sfocato (velo di sempre, niente nero), icone monocromatiche, carattere elegante e sottile, orologio sottile.
 * Disposizione, app, nomi e sfondo scelto non cambiano: la versione nera a lista era troppo vuota e opprimente per il
 * proprietario (13 novembre 2026). All'uscita torna l'aspetto di prima, salvato in un file così torna anche se NOVA si riavvia
 * durante la modalità.
 */
internal class CalmHome(private val app: NovaApp) {
    private val before by lazy { HomePrefsStore(File(app.filesDir, "home-prefs-prima-della-modalita.properties")) }

    suspend fun apply(): ActionOutcome {
        val now = app.homePrefs.prefs.value
        before.update { now }
        app.homePrefs.update(::calmLook)
        return ActionOutcome.Applied(UndoToken(ModeController.KIND_CALM_HOME))
    }

    suspend fun restore(): Boolean {
        val saved = before.prefs.value
        app.homePrefs.update { it.withLookOf(saved).copy(layout = saved.layout) }
        return true
    }

    companion object {
        /** L'aspetto quieto a partire dal tuo. */
        fun calmLook(prefs: HomePrefs): HomePrefs =
            prefs.copy(
                blurLevel = BlurLevel.MAX,
                font = HomeFont.ELEGANT,
                weight = TextWeight.LIGHT,
                clockStyle = ClockStyle.THIN,
                iconStyle = IconStyle.MONO,
            )
    }
}
