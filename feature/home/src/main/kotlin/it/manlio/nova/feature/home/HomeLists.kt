package it.manlio.nova.feature.home

import android.graphics.Rect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.NovaHaptic
import it.manlio.nova.core.design.SquircleShape
import it.manlio.nova.core.design.perform
import kotlinx.coroutines.delay

/**
 * Preferiti. Tenendo premuto e trascinando si riordinano (come in Niagara): la riga si solleva e
 * segue il dito, le altre si spostano con una molla. Tenendo premuto senza muoversi si apre il menu.
 */
@Composable
internal fun FavoritesList(
    favorites: List<AppEntry>,
    loaded: Boolean,
    state: LazyListState,
    actions: HomeActions,
    onMenu: (AppEntry) -> Unit,
    onShowAll: () -> Unit,
) {
    val reorder = remember(favorites) { ReorderState(favorites) { it.key } }
    LazyColumn(state = state, contentPadding = PaddingValues(bottom = 24.dp), userScrollEnabled = reorder.dragKey == null) {
        itemsIndexed(reorder.order, key = { _, app -> app.key }) { index, app ->
            val dragging = app.key == reorder.dragKey
            val lift by animateFloatAsState(if (dragging) 1f else 0f, spring(stiffness = LIFT_STIFFNESS), label = "lift")
            AppRow(
                app,
                big = true,
                favorite = true,
                actions,
                onLongPress = null,
                gesture = rememberFavoriteGesture(reorder, app.key, favorites, actions, onMenu),
                modifier =
                    Modifier
                        .animateItem(placementSpec = if (dragging) null else spring(stiffness = PLACEMENT_STIFFNESS))
                        .cascade(index)
                        .graphicsLayer {
                            translationY = if (dragging) reorder.offset else 0f
                            val s = 1f + LIFT_SCALE * lift
                            scaleX = s
                            scaleY = s
                            alpha = 1f - LIFT_FADE * lift
                        },
            )
        }
        if (loaded) {
            item(key = "show-all") {
                Column(Modifier.animateItem().cascade(reorder.order.size)) {
                    if (reorder.order.isEmpty()) {
                        BasicText(
                            stringResource(R.string.home_favorites_empty),
                            Modifier.padding(vertical = 12.dp),
                            style = TextStyle(HomeMuted, 17.sp, lineHeight = 24.sp, fontFamily = LocalHomeLook.current.family),
                        )
                    }
                    TextAction(stringResource(R.string.home_show_all), onShowAll)
                }
            }
        }
    }
}

/** Gesto di riordino di un preferito; i valori che cambiano si leggono aggiornati, senza far ripartire il gesto. */
@Composable
private fun rememberFavoriteGesture(
    reorder: ReorderState<AppEntry>,
    key: String,
    favorites: List<AppEntry>,
    actions: HomeActions,
    onMenu: (AppEntry) -> Unit,
): (() -> LayoutCoordinates?) -> Modifier {
    val view = LocalView.current
    val currentFavorites by rememberUpdatedState(favorites)
    val currentActions by rememberUpdatedState(actions)
    val currentOnMenu by rememberUpdatedState(onMenu)
    return remember(reorder, key) {
        { coordinates ->
            Modifier.reorderable(
                reorder,
                key,
                coordinates,
                onStart = {
                    reorder.rowHeight = coordinates()?.size?.height?.toFloat() ?: reorder.rowHeight
                    view.perform(NovaHaptic.LONG_PRESS)
                },
                onSwap = { view.perform(NovaHaptic.SELECTION) },
                onCommit = { order -> currentActions.onReorderFavorites(order.map { it.key }) },
                onLongPress = { currentFavorites.firstOrNull { it.key == key }?.let(currentOnMenu) },
            )
        }
    }
}

@Composable
internal fun AllAppsList(
    rows: List<AllRow>,
    favorites: Set<String>,
    state: LazyListState,
    actions: HomeActions,
    onMenu: (AppEntry) -> Unit,
    onBack: () -> Unit,
) {
    val family = LocalHomeLook.current.family
    LazyColumn(state = state, contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "back") { TextAction(stringResource(R.string.home_back_to_favorites), onBack, leading = true) }
        rows.forEach { row ->
            item(key = row.key) {
                when (row) {
                    is AllRow.Header ->
                        BasicText(
                            row.letter.toString(),
                            Modifier.padding(top = 18.dp, bottom = 4.dp),
                            style = TextStyle(HomeMuted, 15.sp, FontWeight.SemiBold, fontFamily = family),
                        )
                    is AllRow.App ->
                        AppRow(
                            row.entry,
                            big = false,
                            favorite = row.entry.key in favorites,
                            actions,
                            onLongPress = { onMenu(row.entry) },
                            modifier = Modifier.animateItem(),
                        )
                }
            }
        }
    }
}

/** Una app: icona (se attiva) e nome; si rimpicciolisce appena sotto il dito e torna con una molla. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppEntry,
    big: Boolean,
    favorite: Boolean,
    actions: HomeActions,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
    gesture: ((coordinates: () -> LayoutCoordinates?) -> Modifier)? = null,
) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) PRESSED_SCALE else 1f,
        spring(dampingRatio = PRESS_DAMPING, stiffness = PRESS_STIFFNESS),
        label = "app-press",
    )
    val bounds = remember { Rect() }
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val extra = remember(gesture) { gesture?.invoke { coordinates[0]?.takeIf { it.isAttached } } ?: Modifier }
    Row(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { c ->
                coordinates[0] = c
                val r = c.boundsInWindow()
                bounds.set(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
            }.graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = LEFT_CENTRE
            }.combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onLongClick =
                    onLongPress?.let {
                        {
                            view.perform(NovaHaptic.LONG_PRESS)
                            it()
                        }
                    },
            ) {
                view.perform(NovaHaptic.TAP)
                actions.onLaunch(app, Rect(bounds))
            }.then(extra)
            .padding(vertical = if (big) 9.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { RowContent(app, big, favorite, actions) }
}

@Composable
private fun RowScope.RowContent(
    app: AppEntry,
    big: Boolean,
    favorite: Boolean,
    actions: HomeActions,
) {
    val look = LocalHomeLook.current
    if (look.showIcons) {
        AppIcon(app, actions.icon, if (big) BIG_ICON else SMALL_ICON)
        Spacer(Modifier.width(if (big) 18.dp else 14.dp))
    }
    BasicText(
        app.label,
        Modifier.weight(1f, fill = false),
        style =
            TextStyle(
                HomeInk,
                if (big) look.bigLabel else look.smallLabel,
                if (big) FontWeight.Light else FontWeight.Normal,
                fontFamily = look.family,
            ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (favorite && !big) {
        val favoriteMark = stringResource(R.string.home_favorite_mark)
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(6.dp)
                .background(HomeInk.copy(alpha = DOT_ALPHA), CircleShape)
                .semantics { contentDescription = favoriteMark },
        )
    }
}

@Composable
internal fun TextAction(
    text: String,
    onClick: () -> Unit,
    leading: Boolean = false,
) {
    val view = LocalView.current
    BasicText(
        if (leading) "‹  $text" else "$text  ›",
        Modifier
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                view.perform(NovaHaptic.TAP)
                onClick()
            }.padding(vertical = 14.dp),
        style = TextStyle(HomeMuted, 17.sp, FontWeight.Medium, letterSpacing = 0.3.sp, fontFamily = LocalHomeLook.current.family),
    )
}

@Composable
internal fun AppIcon(
    app: AppEntry,
    load: suspend (AppEntry) -> ImageBitmap?,
    size: Dp,
) {
    val image by produceState<ImageBitmap?>(null, app.key) { value = load(app) }
    Box(Modifier.size(size)) {
        val loaded = image
        if (loaded != null) {
            Image(loaded, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(HomeInk.copy(alpha = PLACEHOLDER_ALPHA), SquircleShape(size * PLACEHOLDER_RADIUS)))
        }
    }
}

/** Ingresso a cascata: ogni riga sale e compare poco dopo la precedente. */
@Composable
private fun Modifier.cascade(index: Int): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(CASCADE_STEP_MS * index.coerceAtMost(CASCADE_MAX_STEPS))
        progress.animateTo(1f, spring(dampingRatio = CASCADE_DAMPING, stiffness = CASCADE_STIFFNESS))
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * CASCADE_RISE.toPx()
    }
}

private val BIG_ICON = 44.dp
private val SMALL_ICON = 34.dp
private val CASCADE_RISE = 18.dp
private const val PLACEHOLDER_RADIUS = 0.28f
private const val PLACEHOLDER_ALPHA = 0.1f
private const val PRESSED_SCALE = 0.95f
private const val PRESS_DAMPING = 0.55f
private const val PRESS_STIFFNESS = 700f
private const val PLACEMENT_STIFFNESS = 500f
private const val LIFT_STIFFNESS = 600f
private const val LIFT_SCALE = 0.04f
private const val LIFT_FADE = 0.1f
private const val CASCADE_STEP_MS = 28L
private const val CASCADE_MAX_STEPS = 10
private const val CASCADE_DAMPING = 0.8f
private const val CASCADE_STIFFNESS = 260f
private const val DOT_ALPHA = 0.7f
private const val HALF_HEIGHT = 0.5f
private val LEFT_CENTRE = TransformOrigin(pivotFractionX = 0f, pivotFractionY = HALF_HEIGHT)
