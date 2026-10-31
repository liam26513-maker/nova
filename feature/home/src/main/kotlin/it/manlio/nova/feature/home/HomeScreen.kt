package it.manlio.nova.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import it.manlio.nova.core.design.MaterialSurface
import it.manlio.nova.core.design.Nova
import it.manlio.nova.core.design.NovaHaptic
import it.manlio.nova.core.design.NovaSpacing
import it.manlio.nova.core.design.SquircleShape
import it.manlio.nova.core.design.perform
import it.manlio.nova.core.design.rememberCalmBackdrop

/**
 * Home minima (SPEC §6, Fase 0): in orizzontale lista di tutte le app stile Niagara a sinistra
 * (circa 1/3) con l'alfabeto sul bordo, a destra ora, data e griglia dei preferiti; in verticale
 * gli stessi blocchi impilati. [apps] null = caricamento in corso. Ogni volta che [scrollToTop]
 * cambia (tasto Home premuto di nuovo) la lista torna in cima.
 */
@Composable
fun HomeScreen(
    apps: List<AppEntry>?,
    favoriteKeys: List<String>,
    nowMillis: Long,
    actions: HomeActions,
    scrollToTop: Int = 0,
) {
    val t = Nova.tokens
    val backdrop = rememberCalmBackdrop()
    val favorites =
        remember(apps, favoriteKeys) {
            apps.orEmpty().associateBy { it.key }.let {
                    byKey ->
                favoriteKeys.mapNotNull(byKey::get)
            }
        }
    val favoriteSet = remember(favoriteKeys) { favoriteKeys.toSet() }
    BoxWithConstraints(Modifier.fillMaxSize().background(t.colors.background)) {
        Image(backdrop, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        val padding = Modifier.fillMaxSize().safeDrawingPadding().padding(NovaSpacing.l)
        if (maxWidth > maxHeight) {
            Row(padding, horizontalArrangement = Arrangement.spacedBy(NovaSpacing.l)) {
                AppListPanel(backdrop, apps, favoriteSet, actions, scrollToTop, Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(2f).fillMaxHeight()) {
                    ClockHeader(nowMillis, actions.onOpenSettings)
                    Spacer(Modifier.height(NovaSpacing.xl))
                    FavoritesPanel(backdrop, favorites, actions, columns = LANDSCAPE_COLUMNS)
                }
            }
        } else {
            Column(padding) {
                ClockHeader(nowMillis, actions.onOpenSettings)
                Spacer(Modifier.height(NovaSpacing.l))
                FavoritesPanel(backdrop, favorites, actions, columns = PORTRAIT_COLUMNS)
                Spacer(Modifier.height(NovaSpacing.l))
                AppListPanel(backdrop, apps, favoriteSet, actions, scrollToTop, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun AppListPanel(
    backdrop: ImageBitmap,
    apps: List<AppEntry>?,
    favorites: Set<String>,
    actions: HomeActions,
    scrollToTop: Int,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTop) { if (scrollToTop > 0) listState.animateScrollToItem(0) }
    MaterialSurface(backdrop, modifier) {
        if (apps == null) {
            BasicText(stringResource(R.string.home_loading), Modifier.align(Alignment.Center), style = mutedStyle())
        } else {
            Row(Modifier.fillMaxSize()) {
                LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(vertical = NovaSpacing.s)) {
                    item(key = HEADER_KEY) {
                        SectionLabel(
                            stringResource(R.string.home_all_apps),
                            Modifier.padding(start = NovaSpacing.m, bottom = NovaSpacing.xs),
                        )
                    }
                    items(apps, key = { it.key }) { app -> AppRow(app, app.key in favorites, actions) }
                }
                LetterStrip(apps, onLetter = { index -> listState.scrollToItem(index + 1) }) // +1: l'intestazione
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppEntry,
    favorite: Boolean,
    actions: HomeActions,
) {
    val t = Nova.tokens
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val highlight by animateColorAsState(
        if (pressed) t.colors.onSurface.copy(alpha = 0.06f) else Color.Transparent,
        t.motion.spring(),
        label = "app-press",
    )
    val favoriteMark = stringResource(R.string.home_favorite_mark)
    Row(
        Modifier
            .fillMaxWidth()
            .background(highlight)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onLongClick = {
                    view.perform(NovaHaptic.LONG_PRESS)
                    actions.onToggleFavorite(app)
                },
            ) {
                view.perform(NovaHaptic.TAP)
                actions.onLaunch(app)
            }.padding(horizontal = NovaSpacing.m, vertical = NovaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app, actions.icon, LIST_ICON)
        Spacer(Modifier.width(NovaSpacing.s))
        BasicText(
            app.label,
            Modifier.weight(1f),
            style = TextStyle(t.colors.onSurface, 17.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (favorite) {
            Box(Modifier.size(6.dp).background(t.colors.accent, CircleShape).semantics { contentDescription = favoriteMark })
        }
    }
}

@Composable
private fun FavoritesPanel(
    backdrop: ImageBitmap,
    favorites: List<AppEntry>,
    actions: HomeActions,
    columns: Int,
) {
    Column(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.home_favorites), Modifier.padding(start = NovaSpacing.m, bottom = NovaSpacing.xs))
        MaterialSurface(backdrop, Modifier.fillMaxWidth()) {
            if (favorites.isEmpty()) {
                BasicText(stringResource(R.string.home_favorites_empty), Modifier.padding(NovaSpacing.l), style = mutedStyle())
            } else {
                Column(Modifier.padding(NovaSpacing.s), verticalArrangement = Arrangement.spacedBy(NovaSpacing.s)) {
                    favorites.chunked(columns).forEach { row ->
                        Row {
                            row.forEach { app -> FavoriteTile(app, actions, Modifier.weight(1f)) }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteTile(
    app: AppEntry,
    actions: HomeActions,
    modifier: Modifier,
) {
    val t = Nova.tokens
    val view = LocalView.current
    Column(
        modifier
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onLongClick = {
                    view.perform(NovaHaptic.LONG_PRESS)
                    actions.onToggleFavorite(app)
                },
            ) {
                view.perform(NovaHaptic.TAP)
                actions.onLaunch(app)
            }.padding(vertical = NovaSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(app, actions.icon, TILE_ICON)
        Spacer(Modifier.height(NovaSpacing.xs))
        BasicText(
            app.label,
            Modifier.padding(horizontal = NovaSpacing.xxs),
            style = TextStyle(t.colors.onSurface, 13.sp, textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AppIcon(
    app: AppEntry,
    load: suspend (AppEntry) -> ImageBitmap?,
    size: Dp,
) {
    val image by produceState<ImageBitmap?>(null, app.key) { value = load(app) }
    val placeholder = Nova.tokens.colors.onSurface.copy(alpha = 0.06f)
    Box(Modifier.size(size)) {
        val loaded = image
        if (loaded != null) {
            Image(loaded, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(placeholder, SquircleShape(size * PLACEHOLDER_RADIUS)))
        }
    }
}

@Composable
internal fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text.uppercase(),
        modifier,
        style = TextStyle(Nova.tokens.colors.onSurfaceMuted, 12.sp, FontWeight.Medium, letterSpacing = 1.2.sp),
    )
}

@Composable
internal fun mutedStyle() = TextStyle(Nova.tokens.colors.onSurfaceMuted, 15.sp, lineHeight = 21.sp)

private const val HEADER_KEY = "header"
private const val LANDSCAPE_COLUMNS = 6
private const val PORTRAIT_COLUMNS = 4
private const val PLACEHOLDER_RADIUS = 0.28f

/** Le icone si caricano una sola volta, alla misura più grande, e si rimpiccioliscono nella lista. */
val TILE_ICON = 56.dp
private val LIST_ICON = 40.dp
