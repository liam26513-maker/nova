package it.manlio.nova.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

@Composable
internal fun GlassButton(
    text: String,
    description: String?,
    onClick: () -> Unit,
) {
    val look = LocalHomeLook.current
    val view = LocalView.current
    val flat = Nova.tokens.graphicsLevel == GraphicsLevel.PERFORMANCE
    Box(
        Modifier
            .height(BUTTON_HEIGHT)
            .glass(CircleShape, look.dark, flat)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                view.perform(NovaHaptic.TAP)
                onClick()
            }.padding(horizontal = 22.dp)
            .semantics { if (description != null) contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            style = TextStyle(look.ink, if (description == null) 17.sp else 26.sp, FontWeight.Medium, fontFamily = look.family),
        )
    }
}

/** «Cerca»: capsula di vetro con lente; apre la ricerca universale. */
@Composable
internal fun SearchPill(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val look = LocalHomeLook.current
    val view = LocalView.current
    val flat = Nova.tokens.graphicsLevel == GraphicsLevel.PERFORMANCE
    val label = stringResource(R.string.home_search_apps)
    Row(
        Modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0f }
            .glass(CircleShape, look.dark, flat)
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, role = Role.Button) {
                view.perform(NovaHaptic.TAP)
                onClick()
            }.padding(horizontal = 22.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Magnifier(look.muted)
        BasicText(label, style = TextStyle(look.muted, 15.sp, FontWeight.Medium, fontFamily = look.family))
    }
}

/** Lente disegnata (nessuna icona di terzi). */
@Composable
internal fun Magnifier(colour: Color) {
    Canvas(Modifier.size(15.dp)) {
        val stroke = 1.7.dp.toPx()
        val r = size.minDimension * LENS_RADIUS
        drawCircle(colour, r, Offset(r + stroke / 2, r + stroke / 2), style = Stroke(stroke))
        val from = r * 2 + stroke / 2
        drawLine(colour, Offset(from - r * HANDLE_INSET, from - r * HANDLE_INSET), Offset(size.width, size.height), stroke, StrokeCap.Round)
    }
}

/**
 * Dock: pannello di vetro con i primi preferiti (senza nomi, come sull'iPad). In modifica le icone si riordinano
 * trascinando di lato e «−» le toglie dal dock; un'app trascinata dalla griglia sul dock diventa un preferito.
 */
@Composable
internal fun Dock(
    apps: List<AppEntry>,
    actions: HomeActions,
    ipad: IpadActions,
    editing: Boolean,
    onBounds: (Rect) -> Unit,
) {
    val look = LocalHomeLook.current
    val flat = Nova.tokens.graphicsLevel == GraphicsLevel.PERFORMANCE
    val reorder = remember(apps) { ReorderState(apps) { it.key } }
    val step = with(LocalDensity.current) { (look.gridIcon + DOCK_GAP).toPx() }
    Row(
        Modifier
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .glass(SquircleShape(DOCK_RADIUS), look.dark, flat)
            .padding(horizontal = DOCK_PADDING_H, vertical = DOCK_PADDING_V),
        horizontalArrangement = Arrangement.spacedBy(DOCK_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (apps.isEmpty()) {
            BasicText(
                stringResource(R.string.home_dock_empty),
                Modifier.widthIn(max = 420.dp).padding(vertical = 8.dp),
                style = TextStyle(look.muted, 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center, fontFamily = look.family),
            )
        }
        reorder.order.forEach { app ->
            key(app.key) { DockIcon(app, actions, ipad, editing, reorder, step) }
        }
    }
}

@Composable
internal fun DockIcon(
    app: AppEntry,
    actions: HomeActions,
    ipad: IpadActions,
    editing: Boolean,
    reorder: ReorderState<AppEntry>,
    step: Float,
) {
    val view = LocalView.current
    val currentActions by rememberUpdatedState(ipad)
    Box(
        Modifier
            .graphicsLayer { translationX = if (reorder.dragKey == app.key) reorder.offset else 0f }
            .pointerInput(editing, reorder) {
                if (editing) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            reorder.rowHeight = step
                            reorder.start(app.key)
                        },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            if (reorder.drag(dx)) view.perform(NovaHaptic.SELECTION)
                        },
                        onDragEnd = { reorder.end()?.let { order -> currentActions.onReorderDock(order.map { it.key }) } },
                        onDragCancel = { reorder.cancel() },
                    )
                }
            }.launchable(!editing, { ipad.onMenu(app) }) { actions.onLaunch(app, it) }
            .semantics { contentDescription = app.label },
    ) {
        AppIcon(app, actions.icon, LocalHomeLook.current.gridIcon)
        if (editing) RemoveBadge({ actions.onToggleFavorite(app) }, Modifier.align(Alignment.TopStart))
    }
}

private val BUTTON_HEIGHT = 44.dp
private val DOCK_RADIUS = 34.dp
private val DOCK_PADDING_H = 18.dp
private val DOCK_PADDING_V = 12.dp
private val DOCK_GAP = 18.dp
private const val LENS_RADIUS = 0.34f
private const val HANDLE_INSET = 0.7f
