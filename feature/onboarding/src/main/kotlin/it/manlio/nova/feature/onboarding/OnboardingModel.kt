package it.manlio.nova.feature.onboarding

import androidx.compose.runtime.Immutable

/** Passi della procedura guidata, in ordine. Ogni passo si può saltare e rifare dalle Impostazioni. */
enum class OnboardingStep { WELCOME, HOME, SHIZUKU, UPDATES, PERFORMANCE, LOOK, DONE }

/** Stato di Shizuku visto dalla procedura (senza dipendere dal nucleo di sistema). */
enum class ShizukuState { READY, NEEDS_PERMISSION, NOT_RUNNING }

/** Un tema da proporre al passo "Aspetto": nome e breve descrizione. */
@Immutable
data class ThemeOption(
    val name: String,
    val description: String,
)

/** Ciò che la procedura deve sapere del tablet per mostrare cosa è fatto e cosa no. */
@Immutable
data class OnboardingStatus(
    val novaIsHome: Boolean = false,
    val shizuku: ShizukuState = ShizukuState.NOT_RUNNING,
    val tokenSet: Boolean = false,
    val measured: Boolean = false,
    val themes: List<ThemeOption> = emptyList(),
    val selectedTheme: Int? = null,
) {
    /** true se il passo è già completato (mostra "Fatto"). */
    fun isDone(step: OnboardingStep): Boolean =
        when (step) {
            OnboardingStep.HOME -> novaIsHome
            OnboardingStep.SHIZUKU -> shizuku == ShizukuState.READY
            OnboardingStep.UPDATES -> tokenSet
            OnboardingStep.PERFORMANCE -> measured
            OnboardingStep.LOOK -> selectedTheme != null
            OnboardingStep.WELCOME, OnboardingStep.DONE -> true
        }

    /** Passi ancora da fare (per il riepilogo finale). */
    val pending: List<OnboardingStep>
        get() = OnboardingStep.entries.filter { it != OnboardingStep.WELCOME && it != OnboardingStep.DONE && !isDone(it) }
}

/** Azioni della procedura: le fornisce l'app. */
@Suppress("LongParameterList") // una azione per passo
class OnboardingActions(
    val onSetHome: () -> Unit,
    val onShizuku: () -> Unit,
    /** Salva il token; restituisce il motivo se non è valido. */
    val onSaveToken: (String) -> String?,
    val onTokenHelp: () -> Unit,
    val onMeasure: () -> Unit,
    val onPickTheme: (Int) -> Unit,
    val onFinish: () -> Unit,
)
