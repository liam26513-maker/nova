package it.manlio.nova.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.GraphicsLevel
import it.manlio.nova.core.design.Nova
import it.manlio.nova.core.design.NovaHaptic
import it.manlio.nova.core.design.SquircleShape
import it.manlio.nova.core.design.perform

/** Sfondo scurito a tutto schermo con una scheda di vetro al centro; toccare fuori chiude. */
@Composable
private fun GlassDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    width: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    val look = LocalHomeLook.current
    val flat = Nova.tokens.graphicsLevel == GraphicsLevel.PERFORMANCE
    AnimatedVisibility(visible, enter = fadeIn(spring(stiffness = FADE)), exit = fadeOut(spring(stiffness = FADE))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = DIM))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .animateEnterExit(
                        enter = scaleIn(spring(dampingRatio = POP_DAMPING, stiffness = POP_STIFFNESS), initialScale = POP_FROM),
                        exit = scaleOut(spring(stiffness = FADE), targetScale = POP_FROM),
                    ).widthIn(max = width)
                    .padding(24.dp)
                    .background(if (look.dark) CARD else CARD_LIGHT, SquircleShape(CARD_RADIUS))
                    .glass(SquircleShape(CARD_RADIUS), look.dark, flat)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {},
            ) { content() }
        }
    }
}

/** Cartella aperta: il nome si può cambiare, le app si aprono con un tocco e hanno il solito menu con una pressione lunga. */
@Composable
internal fun FolderOverlay(
    folder: FolderItem?,
    apps: Map<String, AppEntry>,
    actions: HomeActions,
    onRename: (String) -> Unit,
    onMenu: (AppEntry) -> Unit,
    onClose: () -> Unit,
) {
    val last = remember { arrayOfNulls<FolderItem>(1) }
    if (folder != null) last[0] = folder
    GlassDialog(folder != null, onClose, FOLDER_WIDTH) {
        val shown = last[0] ?: return@GlassDialog
        val look = LocalHomeLook.current
        var name by remember(shown.id) { mutableStateOf(shown.name) }
        val commit by rememberUpdatedState { if (name != shown.name) onRename(name) }
        DisposableEffect(shown.id) { onDispose { commit() } }
        val fallback = stringResource(R.string.home_folder_default)
        Column(Modifier.padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BasicTextField(
                value = name,
                onValueChange = { name = it.take(HomeLayout.MAX_FOLDER_NAME) },
                singleLine = true,
                textStyle = TextStyle(look.ink, 24.sp, FontWeight.SemiBold, textAlign = TextAlign.Center, fontFamily = look.family),
                cursorBrush = SolidColor(look.ink),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (name.isEmpty()) BasicText(fallback, style = TextStyle(look.muted, 24.sp, FontWeight.SemiBold))
                        inner()
                    }
                },
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp).fillMaxWidth(),
            )
            val members = remember(shown.keys, apps) { shown.keys.mapNotNull(apps::get) }
            LazyVerticalGrid(
                GridCells.Fixed(FOLDER_COLUMNS),
                Modifier.heightIn(max = FOLDER_MAX_HEIGHT).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                items(members, key = { it.key }) { app ->
                    Box(Modifier.heightIn(min = FOLDER_CELL), contentAlignment = Alignment.TopCenter) {
                        AppCell(app, actions, editing = false, jiggle = null, onMenu = onMenu, onRemove = {})
                    }
                }
            }
        }
    }
}

/** Una voce della scheda dei widget. */
internal data class WidgetChoice(
    val id: String,
    val label: String,
    val summary: String,
    val onPick: () -> Unit,
)

/** Scelta del widget da aggiungere: prima quelli di NOVA, poi quelli delle altre app. */
@Composable
internal fun WidgetPicker(
    visible: Boolean,
    nova: List<WidgetChoice>,
    apps: List<WidgetChoice>,
    onClose: () -> Unit,
) {
    GlassDialog(visible, onClose, PICKER_WIDTH) {
        val look = LocalHomeLook.current
        val view = LocalView.current
        LazyColumn(Modifier.heightIn(max = PICKER_MAX_HEIGHT).padding(vertical = 18.dp)) {
            item { PickerTitle(stringResource(R.string.widget_picker_title), big = true) }
            item { PickerTitle(stringResource(R.string.widget_picker_nova), big = false) }
            items(nova.size) { PickerRow(nova[it]) { view.perform(NovaHaptic.TAP) } }
            item { PickerTitle(stringResource(R.string.widget_picker_apps), big = false) }
            if (apps.isEmpty()) {
                item {
                    BasicText(
                        stringResource(R.string.widget_picker_none),
                        Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                        style = TextStyle(look.muted, 15.sp, fontFamily = look.family),
                    )
                }
            }
            items(apps.size) { PickerRow(apps[it]) { view.perform(NovaHaptic.TAP) } }
        }
    }
}

@Composable
private fun PickerTitle(
    text: String,
    big: Boolean,
) {
    val look = LocalHomeLook.current
    BasicText(
        text,
        Modifier.padding(horizontal = 22.dp, vertical = if (big) 6.dp else 10.dp),
        style =
            if (big) {
                TextStyle(look.ink, 22.sp, FontWeight.SemiBold, fontFamily = look.family)
            } else {
                TextStyle(look.muted, 13.sp, FontWeight.Medium, letterSpacing = 0.8.sp, fontFamily = look.family)
            },
    )
}

@Composable
private fun PickerRow(
    choice: WidgetChoice,
    feedback: () -> Unit,
) {
    val look = LocalHomeLook.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                feedback()
                choice.onPick()
            }.padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        BasicText(choice.label, style = TextStyle(look.ink, 17.sp, fontFamily = look.family))
        if (choice.summary.isNotEmpty()) BasicText(choice.summary, style = TextStyle(look.muted, 13.sp, fontFamily = look.family))
    }
}

@Suppress("MagicNumber")
private val CARD = Color(0xE61D2127)

@Suppress("MagicNumber")
private val CARD_LIGHT = Color(0xEBF7F6F3)
private val FOLDER_WIDTH = 520.dp
private val PICKER_WIDTH = 460.dp
private val PICKER_MAX_HEIGHT = 560.dp
private val FOLDER_MAX_HEIGHT = 420.dp
private val FOLDER_CELL = 118.dp
private val CARD_RADIUS = 34.dp
private const val FOLDER_COLUMNS = 4
private const val DIM = 0.45f
private const val FADE = 600f
private const val POP_DAMPING = 0.72f
private const val POP_STIFFNESS = 520f
private const val POP_FROM = 0.86f
