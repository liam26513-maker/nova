package it.manlio.nova.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.Nova
import it.manlio.nova.core.design.NovaSpacing
import it.manlio.nova.core.design.rememberCalmBackdrop

/**
 * Procedura guidata del primo avvio (SPEC, Fase 0 punto 12), nello stile calmo delle Impostazioni:
 * un passo per pagina, che scivola con una molla; in basso Indietro, i puntini del progresso e
 * Avanti/Più tardi. [step] e [onStep] sono tenuti da chi la mostra (così sopravvive alla misura
 * delle prestazioni, che apre un'altra schermata).
 */
@Composable
fun OnboardingScreen(
    step: OnboardingStep,
    onStep: (OnboardingStep) -> Unit,
    status: OnboardingStatus,
    actions: OnboardingActions,
) {
    val t = Nova.tokens
    val steps = OnboardingStep.entries
    val index = steps.indexOf(step)
    BackHandler(enabled = index > 0) { onStep(steps[index - 1]) }
    Box(Modifier.fillMaxSize().background(t.colors.background)) {
        Image(rememberCalmBackdrop(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = NovaSpacing.l, vertical = NovaSpacing.xl)) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val enter =
                        slideInHorizontally(
                            spring(dampingRatio = DAMPING, stiffness = STIFFNESS),
                        ) { if (forward) it / SLIDE else -it / SLIDE }
                    val exit = slideOutHorizontally(spring(stiffness = STIFFNESS)) { if (forward) -it / SLIDE else it / SLIDE }
                    (enter + fadeIn(spring(stiffness = STIFFNESS))).togetherWith(exit + fadeOut(spring(stiffness = FADE_OUT)))
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "onboarding-step",
            ) { shown ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Column(Modifier.widthIn(max = READABLE).fillMaxWidth()) { StepContent(shown, status, actions) }
                }
            }
            Footer(step, index, steps.size, onStep, actions)
        }
    }
}

@Composable
private fun Footer(
    step: OnboardingStep,
    index: Int,
    count: Int,
    onStep: (OnboardingStep) -> Unit,
    actions: OnboardingActions,
) {
    val steps = OnboardingStep.entries
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            if (index > 0) TextButton(stringResource(R.string.ob_back)) { onStep(steps[index - 1]) }
        }
        Dots(index, count)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            when (step) {
                OnboardingStep.WELCOME -> PrimaryButton(stringResource(R.string.ob_welcome_action)) { onStep(steps[index + 1]) }
                OnboardingStep.DONE -> PrimaryButton(stringResource(R.string.ob_finish_action), actions.onFinish)
                else -> TextButton(stringResource(R.string.ob_next)) { onStep(steps[index + 1]) }
            }
        }
    }
}

@Composable
private fun Dots(
    index: Int,
    count: Int,
) {
    val t = Nova.tokens
    val label = stringResource(R.string.ob_step_of, index + 1, count)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semanticsLabel(label)) {
        repeat(count) { i ->
            val on = i == index
            Box(
                Modifier
                    .size(if (on) 10.dp else 7.dp)
                    .background(if (on) t.colors.accent else t.colors.onSurface.copy(alpha = 0.18f), CircleShape),
            )
        }
    }
}

/** Titolo grande e leggero, testo e, se il passo è completato, il segno "Fatto". */
@Composable
internal fun StepHeader(
    title: String,
    body: String,
    done: Boolean,
) {
    val t = Nova.tokens
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            title,
            Modifier.weight(1f, fill = false),
            style = TextStyle(t.colors.onSurface, 40.sp, FontWeight.Light, letterSpacing = (-0.8).sp),
        )
        if (done) {
            Spacer(Modifier.size(NovaSpacing.s))
            BasicText(
                "✓  " + stringResource(R.string.ob_done_badge),
                Modifier.background(t.colors.accent.copy(alpha = 0.12f), CircleShape).padding(horizontal = 12.dp, vertical = 4.dp),
                style = TextStyle(t.colors.accent, 14.sp, FontWeight.Medium),
            )
        }
    }
    Spacer(Modifier.height(NovaSpacing.m))
    BasicText(body, style = TextStyle(t.colors.onSurfaceMuted, 18.sp, lineHeight = 27.sp))
    Spacer(Modifier.height(NovaSpacing.xl))
}

private val READABLE = 640.dp
private const val SLIDE = 6
private const val DAMPING = 0.86f
private const val STIFFNESS = 340f
private const val FADE_OUT = 900f
