package it.manlio.nova

import it.manlio.nova.core.modes.ActionOutcome
import it.manlio.nova.core.modes.UndoToken
import it.manlio.nova.feature.home.HomeLayoutStyle
import it.manlio.nova.feature.home.HomePrefsStore
import it.manlio.nova.feature.home.ThemePresets
import java.io.File

/**
 * «Home essenziale» durante una modalità (azione `CALM_HOME`, SPEC §8.1): l'aspetto del tema «Essenziale» (nero morbido, solo testo)
 * nella disposizione a lista (nella griglia le icone resterebbero), e all'uscita aspetto e disposizione di prima. App nascoste e nomi
 * non si toccano. L'aspetto di prima sta in un file, così torna anche
 * se NOVA si riavvia durante la modalità.
 */
internal class CalmHome(private val app: NovaApp) {
    private val before by lazy { HomePrefsStore(File(app.filesDir, "home-prefs-prima-della-modalita.properties")) }

    suspend fun apply(): ActionOutcome {
        val calm = ThemePresets.all.firstOrNull { it.name == THEME } ?: return ActionOutcome.Skipped("")
        val now = app.homePrefs.prefs.value
        before.update { now }
        app.homePrefs.update { it.withLookOf(calm.prefs).copy(layout = HomeLayoutStyle.LIST) }
        return ActionOutcome.Applied(UndoToken(ModeController.KIND_CALM_HOME))
    }

    suspend fun restore(): Boolean {
        val saved = before.prefs.value
        app.homePrefs.update { it.withLookOf(saved).copy(layout = saved.layout) }
        return true
    }

    private companion object {
        const val THEME = "Essenziale"
    }
}
