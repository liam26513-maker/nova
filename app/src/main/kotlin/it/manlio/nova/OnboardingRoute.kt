package it.manlio.nova

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.manlio.nova.core.system.ShizukuStatus
import it.manlio.nova.core.update.UpdateState
import it.manlio.nova.feature.home.ThemePresets
import it.manlio.nova.feature.onboarding.OnboardingActions
import it.manlio.nova.feature.onboarding.OnboardingScreen
import it.manlio.nova.feature.onboarding.OnboardingStatus
import it.manlio.nova.feature.onboarding.OnboardingStep
import it.manlio.nova.feature.onboarding.ShizukuState
import it.manlio.nova.feature.onboarding.ThemeOption
import kotlinx.coroutines.launch

/** Collega la procedura guidata allo stato reale del tablet (Home, Shizuku, token, misura, tema). */
@Composable
fun OnboardingRoute(
    app: NovaApp,
    step: OnboardingStep,
    onStep: (OnboardingStep) -> Unit,
    system: SystemUiState,
    actions: SystemActions,
    onMeasure: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by app.profile.collectAsStateWithLifecycle()
    var selectedTheme by rememberSaveable { mutableStateOf<Int?>(null) }
    val status =
        OnboardingStatus(
            novaIsHome = system.novaIsHome,
            shizuku =
                when (system.shizuku) {
                    ShizukuStatus.READY -> ShizukuState.READY
                    ShizukuStatus.NEEDS_PERMISSION -> ShizukuState.NEEDS_PERMISSION
                    ShizukuStatus.NOT_RUNNING -> ShizukuState.NOT_RUNNING
                },
            tokenSet = system.update != UpdateState.NoToken,
            measured = profile?.benchmark != null,
            themes = ThemePresets.all.map { ThemeOption(it.name, it.description) },
            selectedTheme = selectedTheme,
        )
    OnboardingScreen(
        step = step,
        onStep = onStep,
        status = status,
        actions =
            OnboardingActions(
                onSetHome = actions.onChooseHome,
                onShizuku = actions.onShizukuAction,
                onSaveToken = actions.onSaveToken,
                onTokenHelp = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TOKEN_PAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                },
                onMeasure = onMeasure,
                onPickTheme = { i ->
                    selectedTheme = i
                    scope.launch { app.homePrefs.update { it.withLookOf(ThemePresets.all[i].prefs) } }
                },
                onFinish = {
                    app.onboardingDone = true
                    onClose()
                    // Si chiude sulla Home (quella scelta da Android: NOVA, se impostata).
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            ),
    )
}

/** Pagina di GitHub per creare un token fine-grained (le istruzioni sono in GUIDA-AVVIO.md). */
private const val TOKEN_PAGE = "https://github.com/settings/personal-access-tokens/new"
