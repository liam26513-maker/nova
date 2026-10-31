package it.manlio.nova.feature.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.Nova
import it.manlio.nova.core.design.NovaHaptic
import it.manlio.nova.core.design.NovaSpacing
import it.manlio.nova.core.design.SquircleShape
import it.manlio.nova.core.design.perform

/** Contenuto di ogni passo. */
@Composable
internal fun StepContent(
    step: OnboardingStep,
    status: OnboardingStatus,
    actions: OnboardingActions,
) {
    val done = status.isDone(step)
    when (step) {
        OnboardingStep.WELCOME ->
            StepHeader(
                stringResource(R.string.ob_welcome_title),
                stringResource(R.string.ob_welcome_body),
                done = false,
            )
        OnboardingStep.HOME -> {
            StepHeader(stringResource(R.string.ob_home_title), stringResource(R.string.ob_home_body), done)
            if (!done) PrimaryButton(stringResource(R.string.ob_home_action), actions.onSetHome)
        }
        OnboardingStep.SHIZUKU -> {
            StepHeader(stringResource(R.string.ob_shizuku_title), stringResource(R.string.ob_shizuku_body), done)
            when (status.shizuku) {
                ShizukuState.READY -> Unit
                ShizukuState.NEEDS_PERMISSION -> PrimaryButton(stringResource(R.string.ob_shizuku_grant), actions.onShizuku)
                ShizukuState.NOT_RUNNING -> {
                    PrimaryButton(stringResource(R.string.ob_shizuku_open), actions.onShizuku)
                    Spacer(Modifier.height(NovaSpacing.m))
                    BasicText(
                        stringResource(R.string.ob_shizuku_hint),
                        style = TextStyle(Nova.tokens.colors.onSurfaceMuted, 15.sp, lineHeight = 21.sp),
                    )
                }
            }
        }
        OnboardingStep.UPDATES -> {
            StepHeader(stringResource(R.string.ob_updates_title), stringResource(R.string.ob_updates_body), done)
            if (!done) TokenField(actions)
        }
        OnboardingStep.PERFORMANCE -> {
            StepHeader(stringResource(R.string.ob_perf_title), stringResource(R.string.ob_perf_body), done)
            PrimaryButton(stringResource(R.string.ob_perf_action), actions.onMeasure)
        }
        OnboardingStep.LOOK -> {
            StepHeader(stringResource(R.string.ob_look_title), stringResource(R.string.ob_look_body), done = false)
            ThemeCards(status, actions.onPickTheme)
        }
        OnboardingStep.DONE -> Finish(status)
    }
}

@Composable
private fun TokenField(actions: OnboardingActions) {
    val t = Nova.tokens
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val placeholder = stringResource(R.string.ob_updates_placeholder)
    BasicTextField(
        value = text,
        onValueChange = {
            text = it
            error = null
        },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        textStyle = TextStyle(t.colors.onSurface, 17.sp),
        cursorBrush = SolidColor(t.colors.accent),
        modifier =
            Modifier
                .fillMaxWidth()
                .background(t.colors.surface.copy(alpha = t.materialOpacity), SquircleShape(t.radii.medium))
                .padding(NovaSpacing.m)
                .semantics { contentDescription = placeholder },
        decorationBox = { inner ->
            if (text.isEmpty()) BasicText(placeholder, style = TextStyle(t.colors.onSurfaceMuted, 17.sp))
            inner()
        },
    )
    error?.let {
        Spacer(Modifier.height(NovaSpacing.xs))
        BasicText(it, style = TextStyle(t.colors.accent, 15.sp))
    }
    Spacer(Modifier.height(NovaSpacing.m))
    PrimaryButton(stringResource(R.string.ob_updates_save)) { error = actions.onSaveToken(text.trim()) }
    Spacer(Modifier.height(NovaSpacing.s))
    TextButton(stringResource(R.string.ob_updates_help), actions.onTokenHelp)
}

@Composable
private fun ThemeCards(
    status: OnboardingStatus,
    onPick: (Int) -> Unit,
) {
    val t = Nova.tokens
    Column(verticalArrangement = Arrangement.spacedBy(NovaSpacing.s)) {
        status.themes.forEachIndexed { i, theme ->
            val selected = status.selectedTheme == i
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(t.colors.surface.copy(alpha = t.materialOpacity), SquircleShape(t.radii.large))
                    .border(if (selected) 2.dp else 0.dp, if (selected) t.colors.accent else t.colors.surface, SquircleShape(t.radii.large))
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton) { onPick(i) }
                    .padding(NovaSpacing.m),
            ) {
                BasicText(theme.name + if (selected) "  ✓" else "", style = TextStyle(t.colors.onSurface, 18.sp, FontWeight.Medium))
                BasicText(theme.description, style = TextStyle(t.colors.onSurfaceMuted, 15.sp))
            }
        }
    }
}

@Composable
private fun Finish(status: OnboardingStatus) {
    val t = Nova.tokens
    StepHeader(stringResource(R.string.ob_finish_title), stringResource(R.string.ob_finish_body), done = false)
    val pending = status.pending
    if (pending.isNotEmpty()) {
        BasicText(stringResource(R.string.ob_finish_pending), style = TextStyle(t.colors.onSurface, 17.sp, FontWeight.Medium))
        Spacer(Modifier.height(NovaSpacing.xs))
        pending.forEach {
                step ->
            BasicText("•  " + stringResource(step.title()), style = TextStyle(t.colors.onSurfaceMuted, 17.sp, lineHeight = 26.sp))
        }
    }
}

internal fun OnboardingStep.title(): Int =
    when (this) {
        OnboardingStep.WELCOME -> R.string.ob_welcome_title
        OnboardingStep.HOME -> R.string.ob_home_title
        OnboardingStep.SHIZUKU -> R.string.ob_shizuku_title
        OnboardingStep.UPDATES -> R.string.ob_updates_title
        OnboardingStep.PERFORMANCE -> R.string.ob_perf_title
        OnboardingStep.LOOK -> R.string.ob_look_title
        OnboardingStep.DONE -> R.string.ob_finish_title
    }

/** Pulsante pieno nel colore d'accento, che si rimpicciolisce appena sotto il dito. */
@Composable
internal fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
) {
    val t = Nova.tokens
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) PRESSED else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "press")
    BasicText(
        text,
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }.background(t.colors.accent, SquircleShape(t.radii.medium))
            .clickable(interaction, indication = null, role = Role.Button) {
                view.perform(NovaHaptic.TAP)
                onClick()
            }.padding(horizontal = 22.dp, vertical = 14.dp),
        style = TextStyle(androidx.compose.ui.graphics.Color.White, 17.sp, FontWeight.Medium),
    )
}

@Composable
internal fun TextButton(
    text: String,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    BasicText(
        text,
        Modifier
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                view.perform(NovaHaptic.TAP)
                onClick()
            }.padding(vertical = 12.dp, horizontal = 8.dp),
        style = TextStyle(Nova.tokens.colors.accent, 17.sp, FontWeight.Medium),
    )
}

internal fun Modifier.semanticsLabel(label: String) = semantics { contentDescription = label }

private const val PRESSED = 0.96f
