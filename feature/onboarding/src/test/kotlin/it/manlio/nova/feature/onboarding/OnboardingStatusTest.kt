package it.manlio.nova.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingStatusTest {
    @Test
    fun `fresh tablet has every real step pending, in order`() {
        assertEquals(
            listOf(OnboardingStep.HOME, OnboardingStep.SHIZUKU, OnboardingStep.UPDATES, OnboardingStep.PERFORMANCE, OnboardingStep.LOOK),
            OnboardingStatus().pending,
        )
    }

    @Test
    fun `completed steps disappear from the summary`() {
        val status =
            OnboardingStatus(novaIsHome = true, shizuku = ShizukuState.READY, tokenSet = true, measured = true, selectedTheme = 0)
        assertEquals(emptyList<OnboardingStep>(), status.pending)
        assertEquals(false, OnboardingStatus(shizuku = ShizukuState.NEEDS_PERMISSION).isDone(OnboardingStep.SHIZUKU))
    }
}
