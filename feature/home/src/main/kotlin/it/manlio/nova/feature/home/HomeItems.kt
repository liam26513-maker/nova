@file:Suppress("MagicNumber") // coefficienti di disegno (trasparenze, proporzioni): sono il progetto grafico

package it.manlio.nova.feature.home

import android.graphics.Rect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.GraphicsLevel
import it.manlio.nova.core.design.Nova
import it.manlio.nova.core.design.NovaHaptic
import it.manlio.nova.core.design.SquircleShape
import it.manlio.nova.core.design.perform

/**
 * Tocco condiviso da icone, cartelle e dock: feedback entro un frame (scala con molla), apertura con il rettangolo
 * dell'icona (per l'animazione di avvio) e pressione lunga. Con [enabled] falso (modalità modifica) non fa nulla.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.launchable(
    enabled: Boolean = true,
    onLongPress: () -> Unit,
    onClick: (Rect) -> Unit,
): Modifier {
    if (!enabled) return this
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) PRESSED_SCALE else 1f,
        spring(dampingRatio = PRESS_DAMPING, stiffness = PRESS_STIFFNESS),
        label = "grid-press",
    )
    val bounds = remember { Rect() }
    return this
        .onGloballyPositioned { c: LayoutCoordinates ->
            val r = c.boundsInWindow()
            bounds.set(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
        }.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }.combinedClickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
            onLongClick = {
                view.perform(NovaHaptic.LONG_PRESS)
                onLongPress()
            },
        ) {
            view.perform(NovaHaptic.TAP)
            onClick(Rect(bounds))
        }
}

/** Le app sul pannello dell'icona, con il nome sotto. [jiggle] = ondeggiamento della modalità modifica. */
@Composable
internal fun AppCell(
    app: AppEntry,
    actions: HomeActions,
    editing: Boolean,
    jiggle: State<Float>?,
    onMenu: (AppEntry) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = LocalHomeLook.current
    ItemFrame(editing, jiggle, app.key, onRemove, modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = CELL_TOP)
                .launchable(!editing, { onMenu(app) }) { actions.onLaunch(app, it) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                AppIcon(app, actions.icon, look.gridIcon)
                LocalNotifications.current[app.component.packageName]?.let { n ->
                    CountBadge(n.count, Modifier.align(Alignment.TopEnd).offset(BADGE_OUT, -BADGE_OUT))
                }
            }
            CellLabel(app.label, look.gridIcon)
        }
    }
}

/** Cartella: pannello con l'anteprima 3×3 delle prime nove app; toccata si apre. */
@Composable
internal fun FolderCell(
    folder: FolderItem,
    apps: List<AppEntry>,
    actions: HomeActions,
    editing: Boolean,
    jiggle: State<Float>?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = LocalHomeLook.current
    ItemFrame(editing, jiggle, folder.id, onRemove, modifier) {
        Column(
            Modifier.fillMaxSize().padding(top = CELL_TOP).launchable(!editing, onEdit) { onOpen() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FolderIcon(apps, actions, look.gridIcon)
            CellLabel(folder.name.ifBlank { stringResource(R.string.home_folder_default) }, look.gridIcon)
        }
    }
}

/** L'icona di una cartella: vetro con le app in miniatura, tre per riga. */
@Composable
internal fun FolderIcon(
    apps: List<AppEntry>,
    actions: HomeActions,
    size: Dp,
) {
    val look = LocalHomeLook.current
    val flat = Nova.tokens.graphicsLevel == GraphicsLevel.PERFORMANCE
    val inset = size * FOLDER_INSET
    val mini = (size - inset * 2 - FOLDER_GAP * 2) / 3
    Column(
        Modifier.size(size).glass(SquircleShape(size * ICON_RADIUS), look.dark, flat).padding(inset),
        verticalArrangement = Arrangement.spacedBy(FOLDER_GAP),
    ) {
        apps.take(FOLDER_PREVIEW).chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(FOLDER_GAP)) { row.forEach { AppIcon(it, actions.icon, mini) } }
        }
    }
}

/** Widget: scheda di vetro; [content] la riempie. Nella modifica ha il pulsante «−». */
@Composable
internal fun WidgetCell(
    editing: Boolean,
    jiggle: State<Float>?,
    id: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val look = LocalHomeLook.current
    val flat = Nova.tokens.graphicsLevel == GraphicsLevel.PERFORMANCE
    ItemFrame(editing, jiggle, id, onRemove, modifier) {
        Box(Modifier.fillMaxSize().padding(WIDGET_GAP).glass(SquircleShape(WIDGET_RADIUS), look.dark, flat)) { content() }
    }
}

/** Cornice comune: ondeggia in modifica e porta il pulsante «−» che toglie l'elemento dalla Home. */
@Composable
private fun ItemFrame(
    editing: Boolean,
    jiggle: State<Float>?,
    seed: String,
    onRemove: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val sign = if (seed.hashCode() % 2 == 0) 1f else -1f
    Box(
        modifier.graphicsLayer { rotationZ = if (editing) (jiggle?.value ?: 0f) * JIGGLE_DEGREES * sign else 0f },
    ) {
        content()
        if (editing) RemoveBadge(onRemove, Modifier.align(Alignment.TopStart).padding(start = BADGE_INSET, top = BADGE_INSET))
    }
}

@Composable
internal fun RemoveBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.home_edit_remove)
    Box(
        modifier
            .size(BADGE)
            .background(Color(BADGE_COLOR), CircleShape)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 10.dp, height = 2.dp).background(Color.White, CircleShape))
    }
}

@Composable
private fun CellLabel(
    text: String,
    iconSize: Dp,
) {
    val look = LocalHomeLook.current
    Spacer(Modifier.height(LABEL_GAP))
    BasicText(
        text,
        Modifier.widthIn(max = iconSize + LABEL_EXTRA),
        style =
            TextStyle(
                look.ink,
                LABEL_SIZE,
                FontWeight.Medium,
                textAlign = TextAlign.Center,
                fontFamily = look.family,
                shadow = if (look.dark) Shadow(Color.Black.copy(alpha = LABEL_SHADOW), Offset(0f, 1f), blurRadius = 4f) else null,
            ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private val CELL_TOP = 10.dp
private val LABEL_GAP = 7.dp
private val LABEL_EXTRA = 28.dp
private val LABEL_SIZE = 13.sp
private val FOLDER_GAP = 3.dp
private val WIDGET_GAP = 6.dp
private val WIDGET_RADIUS = 28.dp
private val BADGE = 22.dp
private val BADGE_INSET = 4.dp
private const val BADGE_COLOR = 0xFF6B7078
private const val FOLDER_INSET = 0.12f
private const val ICON_RADIUS = 0.26f
private const val FOLDER_PREVIEW = 9
private const val JIGGLE_DEGREES = 1.2f
private const val LABEL_SHADOW = 0.35f
private const val PRESSED_SCALE = 0.92f
private const val PRESS_DAMPING = 0.55f
private const val PRESS_STIFFNESS = 700f
private val BADGE_OUT = 6.dp
