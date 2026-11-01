package it.manlio.nova.feature.home

import android.graphics.Rect
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * Home in stile iPad (SPEC §6.1, §6.4): in alto l'ora e le impostazioni, poi pagine di icone
 * squircle da scorrere di lato con i puntini della pagina, il pulsante «Cerca app» (apre l'elenco
 * completo con l'alfabeto) e in basso il dock traslucido con i primi preferiti.
 *
 * Le icone stanno in ordine alfabetico: il posizionamento libero e le cartelle sono della Fase 1.
 */
@Composable
internal fun IpadHome(
    visible: List<AppEntry>?,
    favorites: List<AppEntry>,
    nowMillis: Long,
    prefs: HomePrefs,
    actions: HomeActions,
    scrollToTop: Int,
    onMenu: (AppEntry) -> Unit,
    onShowAll: () -> Unit,
) {
    val dock = remember(favorites) { dockOf(favorites) }
    val gridApps = remember(visible, dock) { gridAppsOf(visible, dock) }
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val margin = if (maxWidth > maxHeight) WIDE_MARGIN else NARROW_MARGIN
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(start = margin, end = margin / 2, top = TOP_MARGIN), verticalAlignment = Alignment.Top) {
                if (prefs.showClock) ClockHeader(nowMillis, compact = true)
                Spacer(Modifier.weight(1f))
                SettingsButton(actions.onOpenSettings)
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
                val metrics = GridMetrics.of((maxWidth - margin * 2).value, (maxHeight - DOTS_AREA).value)
                val pages = remember(gridApps, metrics) { paginate(gridApps, metrics.perPage) }
                val pager = rememberPagerState(pageCount = { pages.size })
                LaunchedEffect(scrollToTop) { if (scrollToTop > 0) pager.animateScrollToPage(0) }
                Column(Modifier.fillMaxSize()) {
                    HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { it }) { index ->
                        GridPage(pages.getOrElse(index) { emptyList() }, metrics, margin, actions, onMenu)
                    }
                    PageDots(pager, Modifier.fillMaxWidth().height(DOTS_AREA))
                }
            }
            SearchPill(onShowAll)
            Spacer(Modifier.height(16.dp))
            Dock(dock, actions, onMenu)
            Spacer(Modifier.height(BOTTOM_MARGIN))
        }
    }
}

/** Una pagina di icone: righe di uguale altezza, colonne di uguale larghezza, celle vuote a completare. */
@Composable
private fun GridPage(
    apps: List<AppEntry>,
    metrics: GridMetrics,
    margin: Dp,
    actions: HomeActions,
    onMenu: (AppEntry) -> Unit,
) {
    val rows = remember(apps, metrics) { apps.chunked(metrics.columns) }
    Column(Modifier.fillMaxSize().padding(horizontal = margin)) {
        repeat(metrics.rows) { r ->
            val rowApps = rows.getOrElse(r) { emptyList() }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                rowApps.forEach { app -> GridCell(app, actions, onMenu, Modifier.weight(1f)) }
                repeat(metrics.columns - rowApps.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Icona con il nome sotto; si rimpicciolisce sotto il dito e torna con una molla. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridCell(
    app: AppEntry,
    actions: HomeActions,
    onMenu: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = LocalHomeLook.current
    Column(
        modifier
            .fillMaxHeight()
            .padding(top = CELL_TOP)
            .launchable(app, actions) { onMenu(app) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(app, actions.icon, look.gridIcon)
        Spacer(Modifier.height(LABEL_GAP))
        BasicText(
            app.label,
            Modifier.widthIn(max = look.gridIcon + LABEL_EXTRA),
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
}

/**
 * Tocco condiviso da celle e dock: feedback entro un frame (scala con molla), apertura con il rettangolo
 * dell'icona (per l'animazione di avvio) e pressione lunga per il menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.launchable(
    app: AppEntry,
    actions: HomeActions,
    onLongPress: () -> Unit,
): Modifier {
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
            actions.onLaunch(app, Rect(bounds))
        }
}

/** Puntini della pagina: quello attuale è pieno, gli altri tenui; cambiano con una molla. */
@Composable
private fun PageDots(
    pager: PagerState,
    modifier: Modifier = Modifier,
) {
    val count = pager.pageCount
    if (count < 2) {
        Spacer(modifier)
        return
    }
    val look = LocalHomeLook.current
    val description = stringResource(R.string.home_page, pager.currentPage + 1, count)
    Row(
        modifier.semantics { contentDescription = description },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val colour by animateColorAsState(
                look.ink.copy(alpha = if (i == pager.currentPage) DOT_ACTIVE else DOT_IDLE),
                spring(stiffness = DOT_STIFFNESS),
                label = "dot",
            )
            Box(Modifier.padding(horizontal = DOT_GAP).size(DOT).background(colour, CircleShape))
        }
    }
}

/** «Cerca app»: capsula traslucida con lente; apre l'elenco di tutte le app con l'alfabeto. */
@Composable
private fun SearchPill(onClick: () -> Unit) {
    val look = LocalHomeLook.current
    val view = LocalView.current
    val label = stringResource(R.string.home_search_apps)
    Row(
        Modifier
            .background(look.ink.copy(alpha = if (look.dark) PILL_DARK else PILL_LIGHT), CircleShape)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
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
private fun Magnifier(colour: Color) {
    Canvas(Modifier.size(15.dp)) {
        val stroke = 1.7.dp.toPx()
        val r = size.minDimension * LENS_RADIUS
        drawCircle(colour, r, Offset(r + stroke / 2, r + stroke / 2), style = Stroke(stroke))
        val from = r * 2 + stroke / 2
        drawLine(colour, Offset(from - r * HANDLE_INSET, from - r * HANDLE_INSET), Offset(size.width, size.height), stroke, StrokeCap.Round)
    }
}

/**
 * Dock: pannello traslucido ad angoli morbidi con i primi preferiti (senza nomi, come sull'iPad).
 * Con il livello grafico Prestazioni diventa più opaco e senza bordo luminoso.
 */
@Composable
private fun Dock(
    apps: List<AppEntry>,
    actions: HomeActions,
    onMenu: (AppEntry) -> Unit,
) {
    val look = LocalHomeLook.current
    val flat = Nova.tokens.graphicsLevel == GraphicsLevel.PERFORMANCE
    val fill =
        if (look.dark) {
            Color.White.copy(
                alpha = if (flat) DOCK_FLAT_DARK else DOCK_DARK,
            )
        } else {
            Color.White.copy(alpha = if (flat) DOCK_FLAT_LIGHT else DOCK_LIGHT)
        }
    val shape = SquircleShape(DOCK_RADIUS)
    Row(
        Modifier
            .background(fill, shape)
            .then(if (flat) Modifier else Modifier.border(0.75.dp, Color.White.copy(alpha = DOCK_EDGE), shape))
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
        apps.forEach { app -> DockIcon(app, actions, onMenu) }
    }
}

@Composable
private fun DockIcon(
    app: AppEntry,
    actions: HomeActions,
    onMenu: (AppEntry) -> Unit,
) {
    Box(
        Modifier
            .launchable(app, actions) { onMenu(app) }
            .semantics { contentDescription = app.label },
    ) { AppIcon(app, actions.icon, LocalHomeLook.current.gridIcon) }
}

private val WIDE_MARGIN = 72.dp
private val NARROW_MARGIN = 28.dp
private val TOP_MARGIN = 28.dp
private val BOTTOM_MARGIN = 20.dp
private val DOTS_AREA = 28.dp
private val CELL_TOP = 10.dp
private val LABEL_GAP = 7.dp
private val LABEL_EXTRA = 28.dp
private val LABEL_SIZE = 13.sp
private val DOT = 7.dp
private val DOT_GAP = 5.dp
private val DOCK_RADIUS = 30.dp
private val DOCK_PADDING_H = 18.dp
private val DOCK_PADDING_V = 12.dp
private val DOCK_GAP = 18.dp
private const val LABEL_SHADOW = 0.35f
private const val PRESSED_SCALE = 0.92f
private const val PRESS_DAMPING = 0.55f
private const val PRESS_STIFFNESS = 700f
private const val DOT_ACTIVE = 0.92f
private const val DOT_IDLE = 0.32f
private const val DOT_STIFFNESS = 500f
private const val PILL_DARK = 0.16f
private const val PILL_LIGHT = 0.10f
private const val LENS_RADIUS = 0.34f
private const val HANDLE_INSET = 0.7f
private const val DOCK_DARK = 0.18f
private const val DOCK_LIGHT = 0.46f
private const val DOCK_FLAT_DARK = 0.30f
private const val DOCK_FLAT_LIGHT = 0.80f
private const val DOCK_EDGE = 0.22f
