package it.manlio.nova.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.NovaHaptic
import it.manlio.nova.core.design.SquircleShape
import it.manlio.nova.core.design.perform

/**
 * Menu di un'app (pressione lunga): preferiti, rinomina, nascondi, informazioni, disinstalla.
 * Appare con una molla sopra uno sfondo scurito; si chiude toccando fuori o con Indietro.
 */
@Composable
internal fun AppMenu(
    app: AppEntry?,
    isFavorite: Boolean,
    actions: HomeActions,
    extras: MenuExtras = MenuExtras(),
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = app != null, onBack = onDismiss)
    AnimatedVisibility(
        app != null,
        enter = fadeIn(spring(stiffness = FADE_STIFFNESS)),
        exit = fadeOut(spring(stiffness = FADE_STIFFNESS)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = DIM_ALPHA))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            // Il contenuto resta quello dell'ultima app anche durante l'animazione di chiusura.
            val last = remember { arrayOfNulls<AppEntry>(1) }
            if (app != null) last[0] = app
            last[0]?.let { current ->
                Box(
                    Modifier
                        .animateEnterExit(
                            enter = scaleIn(spring(dampingRatio = POP_DAMPING, stiffness = POP_STIFFNESS), initialScale = POP_FROM),
                            exit = scaleOut(spring(stiffness = FADE_STIFFNESS), targetScale = POP_FROM),
                        ).widthIn(max = CARD_WIDTH)
                        .padding(24.dp)
                        .background(if (LocalHomeLook.current.dark) CARD else CARD_LIGHT, SquircleShape(26.dp))
                        // Toccare la scheda non deve chiudere il menu.
                        .clickable(remember { MutableInteractionSource() }, indication = null) {},
                ) {
                    MenuContent(current, isFavorite, actions, extras, onDismiss)
                }
            }
        }
    }
}

@Composable
private fun MenuContent(
    app: AppEntry,
    isFavorite: Boolean,
    actions: HomeActions,
    extras: MenuExtras,
    onDismiss: () -> Unit,
) {
    var renaming by remember(app.key) { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 18.dp)) {
        Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, actions.icon, 40.dp)
            Spacer(Modifier.width(14.dp))
            BasicText(
                app.label,
                style = TextStyle(LocalHomeLook.current.ink, 22.sp, FontWeight.Light, fontFamily = LocalHomeLook.current.family),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(10.dp))
        if (renaming) {
            RenameField(app, onDone = { name ->
                actions.onRename(app, name)
                onDismiss()
            }, onCancel = { renaming = false })
        } else {
            // Prima le scorciatoie dell'app, come sull'iPad tenendo premuta un'icona.
            val shortcuts by produceState(emptyList<AppShortcut>(), app.key) { value = actions.shortcuts(app) }
            shortcuts.forEach { s ->
                MenuItem(s.label) {
                    actions.onShortcut(app, s)
                    onDismiss()
                }
            }
            val favoriteLabel = if (isFavorite) R.string.home_menu_unfavorite else R.string.home_menu_favorite
            MenuItem(stringResource(favoriteLabel)) {
                actions.onToggleFavorite(app)
                onDismiss()
            }
            if (actions.canOpenWindow()) {
                MenuItem(stringResource(R.string.home_menu_window)) {
                    actions.onOpenWindow(app)
                    onDismiss()
                }
            }
            extras.onClearNotification?.let { MenuItem(stringResource(R.string.home_menu_clear_notification), onClick = it) }
            extras.onEdit?.let { MenuItem(stringResource(R.string.home_edit_enter), onClick = it) }
            extras.onTakeOut?.let { MenuItem(stringResource(R.string.home_menu_take_out), onClick = it) }
            extras.onRemove?.let { MenuItem(stringResource(R.string.home_edit_remove), onClick = it) }
            extras.onAddToHome?.let { MenuItem(stringResource(R.string.home_menu_add_to_home), onClick = it) }
            MenuItem(stringResource(R.string.home_menu_rename)) { renaming = true }
            MenuItem(stringResource(R.string.home_menu_hide), stringResource(R.string.home_menu_hide_summary)) {
                actions.onHide(app)
                onDismiss()
            }
            MenuItem(stringResource(R.string.home_menu_info)) {
                actions.onAppInfo(app)
                onDismiss()
            }
            MenuItem(stringResource(R.string.home_menu_uninstall)) {
                actions.onUninstall(app)
                onDismiss()
            }
        }
    }
}

@Composable
private fun RenameField(
    app: AppEntry,
    onDone: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var text by remember(app.key) { mutableStateOf(app.label) }
    Column {
        BasicTextField(
            value = text,
            onValueChange = { text = it.take(MAX_NAME) },
            singleLine = true,
            textStyle = TextStyle(LocalHomeLook.current.ink, 18.sp),
            cursorBrush = SolidColor(LocalHomeLook.current.ink),
            modifier =
                Modifier
                    .padding(horizontal = 22.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .background(LocalHomeLook.current.ink.copy(alpha = FIELD_ALPHA), SquircleShape(12.dp))
                    .padding(12.dp),
        )
        MenuItem(stringResource(R.string.home_menu_rename_save)) { onDone(text.trim()) }
        MenuItem(stringResource(R.string.home_menu_rename_reset)) { onDone("") }
        MenuItem(stringResource(R.string.home_menu_cancel), onClick = onCancel)
    }
}

@Composable
private fun MenuItem(
    title: String,
    summary: String? = null,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                view.perform(NovaHaptic.TAP)
                onClick()
            }.padding(horizontal = 22.dp, vertical = 11.dp),
    ) {
        BasicText(title, style = TextStyle(LocalHomeLook.current.ink, 17.sp))
        if (summary != null) BasicText(summary, style = TextStyle(LocalHomeLook.current.muted, 13.sp))
    }
}

@Suppress("MagicNumber") // grafite quasi opaca
private val CARD = Color(0xF21D2127)

@Suppress("MagicNumber")
private val CARD_LIGHT = Color(0xF5F7F6F3)
private val CARD_WIDTH = 420.dp
private const val DIM_ALPHA = 0.45f
private const val FIELD_ALPHA = 0.1f
private const val FADE_STIFFNESS = 600f
private const val POP_DAMPING = 0.72f
private const val POP_STIFFNESS = 520f
private const val POP_FROM = 0.86f
private const val MAX_NAME = 40
