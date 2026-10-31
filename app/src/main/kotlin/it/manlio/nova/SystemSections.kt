package it.manlio.nova

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.Nova
import it.manlio.nova.core.design.NovaSpacing
import it.manlio.nova.core.design.SettingsBlock
import it.manlio.nova.core.design.SettingsRow
import it.manlio.nova.core.design.SettingsSection
import it.manlio.nova.core.design.SquircleShape
import it.manlio.nova.core.system.ShizukuStatus
import it.manlio.nova.core.update.PreviousVersion
import it.manlio.nova.core.update.RollbackResult
import it.manlio.nova.core.update.UpdateState
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Stato di aggiornamenti e strumenti di sistema mostrato nelle Impostazioni. */
data class SystemUiState(
    val versionName: String,
    val update: UpdateState,
    val hasConfigHistory: Boolean,
    val shizuku: ShizukuStatus,
    val systemChanges: Int,
    /** NOVA è l'app Home predefinita. */
    val novaIsHome: Boolean = false,
)

/** Azioni delle sezioni di sistema. */
data class SystemActions(
    val onCheck: () -> Unit,
    val onInstall: () -> Unit,
    /** Salva il token; restituisce il motivo se non è valido. */
    val onSaveToken: (String) -> String?,
    val onRollbackConfig: () -> Unit,
    val onShizukuAction: () -> Unit,
    val onRevertSystem: () -> Unit,
    /** Versioni precedenti dell'app; in caso di errore l'eccezione porta il motivo leggibile. */
    val onLoadPrevious: suspend () -> Result<List<PreviousVersion>> = { Result.success(emptyList()) },
    /** Chiede il ritorno a una versione precedente. */
    val onRollbackApp: suspend (PreviousVersion) -> RollbackResult = { RollbackResult.Requested },
    /** Apre la scelta dell'app Home di Android. */
    val onChooseHome: () -> Unit = {},
)

@Composable
fun UpdatesSection(
    backdrop: ImageBitmap,
    state: SystemUiState,
    actions: SystemActions,
) {
    var editingToken by remember { mutableStateOf(false) }
    SettingsSection(backdrop, header = stringResource(R.string.updates_title), footer = stringResource(R.string.updates_footer)) {
        SettingsRow(stringResource(R.string.updates_version), trailing = { Muted(state.versionName) })
        SettingsRow(stringResource(R.string.updates_status), summary = describe(state.update), showDivider = true)
        when (val u = state.update) {
            UpdateState.NoToken ->
                SettingsRow(stringResource(R.string.updates_connect), showDivider = true, onClick = { editingToken = true })
            is UpdateState.AppAvailable ->
                SettingsRow(
                    stringResource(R.string.updates_install, u.update.tag),
                    summary = stringResource(R.string.updates_install_summary),
                    showDivider = true,
                    onClick = actions.onInstall,
                )
            UpdateState.Checking, is UpdateState.Downloading, UpdateState.Installing -> Unit
            else -> SettingsRow(stringResource(R.string.updates_check_now), showDivider = true, onClick = actions.onCheck)
        }
        if (state.update != UpdateState.NoToken) {
            SettingsRow(stringResource(R.string.updates_change_token), showDivider = true, onClick = { editingToken = !editingToken })
        }
        if (editingToken) TokenEditor(actions.onSaveToken) { editingToken = false }
        if (state.hasConfigHistory) {
            SettingsRow(
                stringResource(R.string.updates_rollback_config),
                summary = stringResource(R.string.updates_rollback_config_summary),
                showDivider = true,
                onClick = actions.onRollbackConfig,
            )
        }
        if (state.update != UpdateState.NoToken) RollbackApp(actions)
    }
}

/** Passi del ritorno d'emergenza a una versione precedente dell'app. */
private sealed interface RollbackStep {
    data object Closed : RollbackStep

    data object Loading : RollbackStep

    data class Choose(val versions: List<PreviousVersion>) : RollbackStep

    data class Confirm(val target: PreviousVersion) : RollbackStep

    data class Sending(val target: PreviousVersion) : RollbackStep

    data class Done(val target: PreviousVersion, val instant: Boolean) : RollbackStep

    data class Error(val reason: String) : RollbackStep
}

/**
 * Ultima voce di "Aggiornamenti", da usare solo se un aggiornamento ha creato un problema che le
 * versioni successive non risolvono. Chiede sempre conferma prima di partire.
 */
@Composable
private fun RollbackApp(actions: SystemActions) {
    var step by remember { mutableStateOf<RollbackStep>(RollbackStep.Closed) }
    val close = { step = RollbackStep.Closed }
    LaunchedEffect(step) { advance(step, actions)?.let { step = it } }
    when (val s = step) {
        RollbackStep.Closed ->
            SettingsRow(
                stringResource(R.string.rollback_app),
                summary = stringResource(R.string.rollback_app_summary),
                showDivider = true,
                onClick = { step = RollbackStep.Loading },
            )
        RollbackStep.Loading -> SettingsRow(stringResource(R.string.rollback_loading), showDivider = true)
        is RollbackStep.Choose -> ChooseVersion(s.versions, onPick = { step = RollbackStep.Confirm(it) }, onCancel = close)
        is RollbackStep.Confirm -> ConfirmRollback(s.target, onConfirm = { step = RollbackStep.Sending(s.target) }, onCancel = close)
        is RollbackStep.Sending -> SettingsRow(stringResource(R.string.rollback_sending), showDivider = true)
        is RollbackStep.Done -> {
            val text = if (s.instant) R.string.rollback_done_instant else R.string.rollback_done
            Outcome(R.string.rollback_done_title, stringResource(text, s.target.update.tag), close)
        }
        is RollbackStep.Error -> Outcome(R.string.rollback_failed, s.reason, close)
    }
}

/** Passi che lavorano in rete: caricare la lista e inviare la richiesta. */
private suspend fun advance(
    step: RollbackStep,
    actions: SystemActions,
): RollbackStep? =
    when (step) {
        RollbackStep.Loading ->
            actions.onLoadPrevious().fold(
                onSuccess = { RollbackStep.Choose(it) },
                onFailure = { RollbackStep.Error(it.message.orEmpty()) },
            )
        is RollbackStep.Sending ->
            when (val r = actions.onRollbackApp(step.target)) {
                RollbackResult.Installing -> RollbackStep.Done(step.target, instant = true)
                RollbackResult.Requested -> RollbackStep.Done(step.target, instant = false)
                is RollbackResult.Failed -> RollbackStep.Error(r.reason)
            }
        else -> null
    }

@Composable
private fun ChooseVersion(
    versions: List<PreviousVersion>,
    onPick: (PreviousVersion) -> Unit,
    onCancel: () -> Unit,
) {
    if (versions.isEmpty()) SettingsRow(stringResource(R.string.rollback_none), showDivider = true)
    val instant = stringResource(R.string.rollback_instant)
    versions.forEach { v ->
        SettingsRow(
            v.update.tag,
            summary = listOfNotNull(v.update.publishedAt?.take(DATE_LENGTH), instant.takeIf { v.standby != null }).joinToString(" · "),
            showDivider = true,
            onClick = { onPick(v) },
        )
    }
    SettingsRow(stringResource(R.string.rollback_cancel), showDivider = true, onClick = onCancel)
}

@Composable
private fun ConfirmRollback(
    target: PreviousVersion,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val tag = target.update.tag
    SettingsBlock(showDivider = true) {
        BasicText(
            stringResource(if (target.standby != null) R.string.rollback_confirm_instant else R.string.rollback_confirm_text, tag),
            style = TextStyle(Nova.tokens.colors.onSurfaceMuted, 14.sp, lineHeight = 19.sp),
        )
    }
    SettingsRow(stringResource(R.string.rollback_confirm, tag), showDivider = true, onClick = onConfirm)
    SettingsRow(stringResource(R.string.rollback_cancel), showDivider = true, onClick = onCancel)
}

@Composable
private fun Outcome(
    title: Int,
    summary: String,
    onClose: () -> Unit,
) {
    SettingsRow(stringResource(title), summary = summary, showDivider = true)
    SettingsRow(stringResource(R.string.rollback_close), showDivider = true, onClick = onClose)
}

@Composable
fun SystemToolsSection(
    backdrop: ImageBitmap,
    state: SystemUiState,
    actions: SystemActions,
) {
    SettingsSection(backdrop, header = stringResource(R.string.system_title), footer = stringResource(R.string.system_footer)) {
        val (label, action) =
            when (state.shizuku) {
                ShizukuStatus.READY -> R.string.system_shizuku_ready to null
                ShizukuStatus.NEEDS_PERMISSION -> R.string.system_shizuku_permission to R.string.system_shizuku_grant
                ShizukuStatus.NOT_RUNNING -> R.string.system_shizuku_off to R.string.system_shizuku_open
            }
        SettingsRow("Shizuku", trailing = { Muted(stringResource(label)) })
        if (action != null) SettingsRow(stringResource(action), showDivider = true, onClick = actions.onShizukuAction)
        if (state.systemChanges > 0) {
            SettingsRow(
                stringResource(R.string.system_revert, state.systemChanges),
                summary = stringResource(R.string.system_revert_summary),
                showDivider = true,
                onClick = actions.onRevertSystem,
            )
        }
    }
}

@Composable
private fun TokenEditor(
    onSave: (String) -> String?,
    onDone: () -> Unit,
) {
    val t = Nova.tokens
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    SettingsBlock(showDivider = true) {
        BasicText(stringResource(R.string.updates_token_help), style = TextStyle(t.colors.onSurfaceMuted, 14.sp, lineHeight = 19.sp))
        Spacer(Modifier.height(NovaSpacing.s))
        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                error = null
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = TextStyle(t.colors.onSurface, 16.sp),
            cursorBrush = SolidColor(t.colors.accent),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(t.colors.onSurface.copy(alpha = 0.06f), SquircleShape(t.radii.small))
                    .padding(NovaSpacing.s),
        )
        error?.let {
            Spacer(Modifier.height(NovaSpacing.xs))
            BasicText(it, style = TextStyle(t.colors.accent, 14.sp))
        }
    }
    SettingsRow(stringResource(R.string.updates_token_save), showDivider = true, onClick = {
        error = onSave(text)
        if (error == null) onDone()
    })
}

@Composable
private fun describe(state: UpdateState): String =
    when (state) {
        UpdateState.Idle -> stringResource(R.string.updates_idle)
        UpdateState.NoToken -> stringResource(R.string.updates_no_token)
        UpdateState.Checking -> stringResource(R.string.updates_checking)
        is UpdateState.UpToDate ->
            stringResource(R.string.updates_up_to_date, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.checkedAt)))
        is UpdateState.AppAvailable -> stringResource(R.string.updates_available, state.update.tag)
        is UpdateState.Downloading -> stringResource(R.string.updates_downloading, (state.progress * PERCENT).roundToInt())
        UpdateState.Installing -> stringResource(R.string.updates_installing)
        UpdateState.AwaitingConfirmation -> stringResource(R.string.updates_confirm)
        is UpdateState.ConfigApplied -> stringResource(R.string.updates_config_applied, state.version)
        is UpdateState.Failed -> state.reason
    }

@Composable
internal fun Muted(text: String) {
    BasicText(text, style = TextStyle(Nova.tokens.colors.onSurfaceMuted, 17.sp))
}

private const val PERCENT = 100
private const val DATE_LENGTH = 10
