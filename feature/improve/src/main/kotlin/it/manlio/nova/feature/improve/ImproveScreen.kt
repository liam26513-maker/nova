package it.manlio.nova.feature.improve

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.Nova
import it.manlio.nova.core.design.NovaSpacing
import it.manlio.nova.core.design.SegmentedChoice
import it.manlio.nova.core.design.SettingsBlock
import it.manlio.nova.core.design.SettingsRow
import it.manlio.nova.core.design.SettingsScaffold
import it.manlio.nova.core.design.SettingsSection
import it.manlio.nova.core.design.SquircleShape

enum class ImproveMode { IMPROVE, REPAIR }

/** Cosa mostra la schermata mentre prepara e invia la richiesta. */
data class ImproveUiState(
    val sending: Boolean = false,
    /** Esito dell'ultimo invio, in parole semplici. */
    val message: String? = null,
    /** Elementi del report di Ripara (es. "2 crash", "120 eventi del log"). */
    val includes: List<String> = emptyList(),
    /** Anteprima del report già anonimizzato: si vede esattamente cosa esce dal tablet. */
    val reportPreview: String? = null,
)

/**
 * Migliora e Ripara (SPEC §4.2-4.3): si scrive cosa si vuole, NOVA aggiunge il contesto (e per
 * Ripara un report anonimizzato) e apre Claude Code con la richiesta già scritta: resta solo da
 * toccare Invia. Nessuna chiave API: tutto passa dall'abbonamento del proprietario.
 */
@Composable
fun ImproveScreen(
    initialMode: ImproveMode,
    state: ImproveUiState,
    onSend: (ImproveMode, String) -> Unit,
    onDone: () -> Unit,
) {
    BackHandler(onBack = onDone)
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var text by rememberSaveable { mutableStateOf("") }
    val title = stringResource(if (mode == ImproveMode.IMPROVE) R.string.improve_title else R.string.repair_title)
    SettingsScaffold(title = title, subtitle = stringResource(R.string.improve_subtitle)) { backdrop ->
        SettingsSection(backdrop) {
            SettingsBlock {
                SegmentedChoice(
                    options = ImproveMode.entries,
                    selected = mode,
                    label = { stringResource(if (it == ImproveMode.IMPROVE) R.string.improve_tab else R.string.repair_tab) },
                    onSelect = { mode = it },
                )
                Spacer(Modifier.height(NovaSpacing.m))
                RequestField(text, mode) { text = it }
            }
            if (mode == ImproveMode.IMPROVE) {
                Examples { text = it }
            }
        }
        if (mode == ImproveMode.REPAIR) ReportSection(backdrop, state)
        SettingsSection(backdrop, footer = stringResource(R.string.improve_footer)) {
            val sendLabel = stringResource(if (state.sending) R.string.improve_sending else R.string.improve_send)
            SettingsRow(sendLabel, summary = state.message, onClick = if (state.sending) null else ({ onSend(mode, text) }))
            SettingsRow(stringResource(R.string.improve_close), showDivider = true, onClick = onDone)
        }
    }
}

@Composable
private fun RequestField(
    text: String,
    mode: ImproveMode,
    onChange: (String) -> Unit,
) {
    val t = Nova.tokens
    val hint = stringResource(if (mode == ImproveMode.IMPROVE) R.string.improve_hint else R.string.repair_hint)
    BasicTextField(
        value = text,
        onValueChange = { onChange(it.take(MAX_REQUEST)) },
        textStyle = TextStyle(t.colors.onSurface, 18.sp, lineHeight = 26.sp),
        cursorBrush = SolidColor(t.colors.accent),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp)
                .background(t.colors.onSurface.copy(alpha = 0.05f), SquircleShape(t.radii.medium))
                .padding(NovaSpacing.m)
                .semantics { contentDescription = hint },
        decorationBox = { inner ->
            if (text.isEmpty()) BasicText(hint, style = TextStyle(t.colors.onSurfaceMuted, 18.sp, lineHeight = 26.sp))
            inner()
        },
    )
}

@Composable
private fun Examples(onPick: (String) -> Unit) {
    listOf(R.string.improve_example_1, R.string.improve_example_2, R.string.improve_example_3).forEach { id ->
        val example = stringResource(id)
        SettingsRow(example, showDivider = true, onClick = { onPick(example) })
    }
}

@Composable
private fun ReportSection(
    backdrop: androidx.compose.ui.graphics.ImageBitmap,
    state: ImproveUiState,
) {
    val t = Nova.tokens
    var open by remember { mutableStateOf(false) }
    SettingsSection(backdrop, header = stringResource(R.string.repair_report), footer = stringResource(R.string.repair_report_footer)) {
        state.includes.forEachIndexed { i, item -> SettingsRow(item, showDivider = i > 0) }
        val preview = state.reportPreview
        if (preview != null) {
            SettingsRow(
                stringResource(if (open) R.string.repair_hide_preview else R.string.repair_show_preview),
                showDivider = true,
                onClick = { open = !open },
            )
            if (open) {
                SettingsBlock(showDivider = true) {
                    BasicText(
                        preview.lines().take(PREVIEW_LINES).joinToString("\n"),
                        Modifier.clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { open = false },
                        style =
                            TextStyle(
                                t.colors.onSurfaceMuted,
                                12.sp,
                                FontWeight.Normal,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp,
                            ),
                    )
                }
            }
        }
    }
}

private const val MAX_REQUEST = 4000
private const val PREVIEW_LINES = 80
