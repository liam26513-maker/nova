package it.manlio.nova.core.design

import android.view.HapticFeedbackConstants
import android.view.View

/** Vocabolario aptico di NOVA (SPEC §5.4). L'intensità si regola nelle impostazioni di sistema. */
enum class NovaHaptic(internal val constant: Int) {
    TAP(HapticFeedbackConstants.VIRTUAL_KEY),
    SELECTION(HapticFeedbackConstants.CLOCK_TICK),
    TOGGLE(HapticFeedbackConstants.CONTEXT_CLICK),
    SUCCESS(HapticFeedbackConstants.CONFIRM),
    WARNING(HapticFeedbackConstants.REJECT),
    BOUNDARY(HapticFeedbackConstants.SEGMENT_TICK),
    LONG_PRESS(HapticFeedbackConstants.LONG_PRESS),
}

/** Esegue un feedback aptico, se l'utente non l'ha disattivato. */
fun View.perform(
    haptic: NovaHaptic,
    enabled: Boolean = true,
) {
    if (enabled) performHapticFeedback(haptic.constant)
}
