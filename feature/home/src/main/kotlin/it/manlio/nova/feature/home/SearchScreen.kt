package it.manlio.nova.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.NovaHaptic
import it.manlio.nova.core.design.SquircleShape
import it.manlio.nova.core.design.perform

/**
 * Ricerca universale (SPEC §6.6): scrivendo si filtrano le app (senza maiuscole né accenti), compaiono le
 * Impostazioni di NOVA se c'entrano e «Cerca sul web». Senza testo c'è la strada per l'elenco di tutte le app.
 */
@Composable
internal fun SearchScreen(
    apps: List<AppEntry>,
    actions: HomeActions,
    commands: SearchCommands,
    onShowAll: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val look = LocalHomeLook.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val results = remember(apps, query) { if (query.isBlank()) emptyList() else AppSearch.search(apps, query) { it.label } }
    val novaLabel = stringResource(R.string.home_search_nova_settings)
    val calc = remember(query) { Calc.answer(query) }
    val showNova = query.isNotBlank() && (listOf(novaLabel) + NOVA_WORDS).any { AppSearch.score(query, it) != null }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (look.dark) DIM_DARK else DIM_LIGHT))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp)
                .widthIn(max = SEARCH_WIDTH)
                .fillMaxWidth()
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
        ) {
            SearchField(query, { query = it }, focus, results.firstOrNull(), actions, onClose)
            Spacer(Modifier.padding(top = 14.dp))
            LazyColumn(
                Modifier.fillMaxWidth().glass(SquircleShape(RESULTS_RADIUS), look.dark, flat = false),
                verticalArrangement = Arrangement.Top,
            ) {
                if (query.isBlank()) {
                    item("all") { ResultRow(stringResource(R.string.home_search_all_apps) + "  ›", null, actions) { onShowAll() } }
                } else {
                    if (results.isNotEmpty()) item("h-apps") { SectionTitle(stringResource(R.string.home_search_apps_section)) }
                    items(results, key = { it.key }) { app -> ResultRow(app.label, app, actions) { launch(app, actions, onClose) } }
                    commandRows(query, commands)
                    calc?.let { answer -> item("calc") { CalcRow(answer) } }
                    if (showNova) item("nova") { ResultRow(novaLabel, null, actions) { actions.onOpenSettings() } }
                    item(
                        "web",
                    ) {
                        ResultRow(
                            stringResource(R.string.home_search_web, query.trim()),
                            null,
                            actions,
                        ) { actions.onWebSearch(query.trim()) }
                    }
                    if (results.isEmpty() && !showNova && calc == null) {
                        item("empty") { SectionTitle(stringResource(R.string.home_search_empty)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    focus: FocusRequester,
    first: AppEntry?,
    actions: HomeActions,
    onClose: () -> Unit,
) {
    val look = LocalHomeLook.current
    val close = stringResource(R.string.home_search_close)
    Row(
        Modifier.fillMaxWidth().background(
            panel(look.dark),
            CircleShape,
        ).glass(CircleShape, look.dark, flat = false).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = { onQuery(it.take(MAX_QUERY)) },
            singleLine = true,
            textStyle = TextStyle(look.ink, 20.sp, fontFamily = look.family),
            cursorBrush = SolidColor(look.ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions =
                KeyboardActions(onSearch = {
                    first?.let { launch(it, actions, onClose) }
                }),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) BasicText(stringResource(R.string.home_search_hint), style = TextStyle(look.muted, 20.sp))
                    inner()
                }
            },
            modifier = Modifier.weight(1f).focusRequester(focus),
        )
        BasicText(
            "✕",
            Modifier
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onClose() }
                .padding(start = 12.dp)
                .semantics { contentDescription = close },
            style = TextStyle(look.muted, 18.sp),
        )
    }
}

/** Comandi di NOVA che si trovano scrivendo: centro di controllo, modifica della Home. */
internal class SearchCommands(
    val onControl: (() -> Unit)?,
    val onEditHome: () -> Unit,
)

private fun androidx.compose.foundation.lazy.LazyListScope.commandRows(
    query: String,
    commands: SearchCommands,
) {
    val control = commands.onControl
    if (control != null && matches(query, CONTROL_WORDS)) item("cmd-control") { CommandRow(R.string.control_open, control) }
    if (matches(query, EDIT_WORDS)) item("cmd-edit") { CommandRow(R.string.home_edit_enter, commands.onEditHome) }
}

private fun matches(
    query: String,
    words: List<String>,
) = words.any { AppSearch.score(query, it) != null }

@Composable
private fun CommandRow(
    @androidx.annotation.StringRes label: Int,
    onClick: () -> Unit,
) = ResultRow(
    stringResource(label),
    null,
    HomeActions(icon = { null }, onLaunch = {
            _,
            _,
        ->
    }, onToggleFavorite = {}, onOpenSettings = {}),
    onClick,
)

/** Risultato di un calcolo o di una conversione: si tocca per copiarlo. */
@Composable
private fun CalcRow(answer: String) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val look = LocalHomeLook.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(answer))
            }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText("= $answer", style = TextStyle(look.ink, 26.sp, FontWeight.Light, fontFamily = look.family))
    }
}

private fun launch(
    app: AppEntry,
    actions: HomeActions,
    onClose: () -> Unit,
) {
    actions.onLaunch(app, null)
    onClose()
}

@Composable
private fun SectionTitle(text: String) {
    val look = LocalHomeLook.current
    BasicText(
        text,
        Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        style = TextStyle(look.muted, 13.sp, FontWeight.Medium, letterSpacing = 0.8.sp, fontFamily = look.family),
    )
}

@Composable
private fun ResultRow(
    label: String,
    app: AppEntry?,
    actions: HomeActions,
    onClick: () -> Unit,
) {
    val look = LocalHomeLook.current
    val view = LocalView.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                view.perform(NovaHaptic.TAP)
                onClick()
            }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (app != null) {
            AppIcon(app, actions.icon, RESULT_ICON)
            Spacer(Modifier.width(14.dp))
        }
        BasicText(label, style = TextStyle(look.ink, 18.sp, fontFamily = look.family), maxLines = 1)
    }
}

/** Fondo quasi pieno sotto il vetro: le icone della Home non devono trasparire dietro al testo. */
@Suppress("MagicNumber")
private fun panel(dark: Boolean) = if (dark) Color(0xF01D2127) else Color(0xF2F7F6F3)

/** Parole che portano alle Impostazioni di NOVA anche se non c'è il nome intero. */
private val CONTROL_WORDS =
    listOf("controllo", "centro di controllo", "torcia", "luminosità", "volume", "wifi", "bluetooth", "non disturbare")
private val EDIT_WORDS = listOf("modifica", "widget", "cartella", "sposta icone")
private val NOVA_WORDS = listOf("nova", "personalizza", "tema", "sfondo", "grafica", "aggiornamenti", "impostazioni")
private val SEARCH_WIDTH = 640.dp
private val RESULT_ICON = 40.dp
private val RESULTS_RADIUS = 26.dp
private const val DIM_DARK = 0.55f
private const val DIM_LIGHT = 0.35f
private const val MAX_QUERY = 60
