package it.manlio.nova.feature.home

import android.graphics.Rect
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.NovaHaptic
import it.manlio.nova.core.design.SquircleShape
import it.manlio.nova.core.design.perform
import kotlinx.coroutines.delay

/**
 * Home (SPEC §6, ispirata a Niagara senza copiarlo): sfondo di sistema con una sfumatura scura e
 * opaca presa dai suoi colori; ora grande; i preferiti come lista di nomi grandi; l'alfabeto a onda
 * sul bordo destro porta all'elenco di tutte le app. Tutto si muove con molle interrompibili.
 * [apps] null = caricamento in corso. Quando [scrollToTop] cambia (tasto Home) si torna ai preferiti.
 */
@Composable
fun HomeScreen(
    apps: List<AppEntry>?,
    favoriteKeys: List<String>,
    nowMillis: Long,
    actions: HomeActions,
    style: HomeStyle = HomeStyle(),
    scrollToTop: Int = 0,
) {
    val favorites = remember(apps, favoriteKeys) { favoritesOf(apps, favoriteKeys) }
    val favoriteSet = remember(favoriteKeys) { favoriteKeys.toSet() }
    val rows = remember(apps) { allRows(apps.orEmpty()) }
    val headerIndex = remember(rows) { headerIndexOf(rows) }
    var mode by rememberSaveable { mutableStateOf(HomeMode.FAVORITES) }
    val favoritesState = rememberLazyListState()
    val allState = rememberLazyListState()
    LaunchedEffect(scrollToTop) {
        if (scrollToTop > 0) {
            mode = HomeMode.FAVORITES
            favoritesState.animateScrollToItem(0)
        }
    }
    BackHandler(enabled = mode == HomeMode.ALL) { mode = HomeMode.FAVORITES }

    Box(Modifier.fillMaxSize()) {
        Scrim(style)
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val wide = maxWidth > maxHeight
            Row(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = if (wide) WIDE_MARGIN else NARROW_MARGIN, top = TOP_MARGIN),
                ) {
                    ClockHeader(nowMillis)
                    Spacer(Modifier.height(32.dp))
                    AnimatedContent(
                        targetState = mode,
                        transitionSpec = { modeTransition(targetState) },
                        modifier = Modifier.weight(1f).widthIn(max = CONTENT_WIDTH),
                        label = "home-mode",
                    ) { shown ->
                        when (shown) {
                            HomeMode.FAVORITES ->
                                FavoritesList(favorites, apps != null, favoritesState, actions, onShowAll = { mode = HomeMode.ALL })
                            HomeMode.ALL -> AllAppsList(rows, favoriteSet, allState, actions, onBack = { mode = HomeMode.FAVORITES })
                        }
                    }
                }
                SettingsButton(actions.onOpenSettings, Modifier.padding(top = TOP_MARGIN, end = 8.dp))
                Alphabet(
                    letters = remember(headerIndex) { headerIndex.keys.toList() },
                    onLetter = { letter ->
                        mode = HomeMode.ALL
                        headerIndex[letter]?.let { allState.requestScrollToItem(it) }
                    },
                    modifier = Modifier.fillMaxHeight().width(ALPHABET_WIDTH).padding(vertical = NARROW_MARGIN),
                )
            }
        }
    }
}

/** Passaggio tra preferiti e tutte le app: dissolvenza e scivolamento con molle. */
private fun modeTransition(target: HomeMode): ContentTransform {
    val direction = if (target == HomeMode.ALL) 1 else -1
    val enter =
        fadeIn(spring(stiffness = FADE_STIFFNESS)) +
            slideInVertically(spring(dampingRatio = SLIDE_DAMPING, stiffness = SLIDE_STIFFNESS)) { it / SLIDE_FRACTION * direction }
    val exit =
        fadeOut(spring(stiffness = FADE_OUT_STIFFNESS)) +
            slideOutVertically(spring(stiffness = FADE_OUT_STIFFNESS)) { -it / SLIDE_FRACTION * direction }
    return ContentTransform(enter, exit, sizeTransform = SizeTransform(clip = false))
}

/** Sfumatura scura e opaca, tinta col colore dello sfondo: più scura a sinistra e in basso, dove c'è il testo. */
@Composable
private fun Scrim(style: HomeStyle) {
    val base = lerp(style.tint, Color.Black, TINT_TO_BLACK)
    val (top, bottom) = if (style.blurred) BLURRED_ALPHA else SOLID_ALPHA
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(base.copy(alpha = top), base.copy(alpha = (top + bottom) / 2), base.copy(alpha = bottom))),
            ),
    )
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(base.copy(alpha = SIDE_ALPHA), Color.Transparent))))
}

@Composable
private fun FavoritesList(
    favorites: List<AppEntry>,
    loaded: Boolean,
    state: LazyListState,
    actions: HomeActions,
    onShowAll: () -> Unit,
) {
    LazyColumn(state = state, contentPadding = PaddingValues(bottom = 24.dp)) {
        itemsIndexed(favorites, key = { _, app -> app.key }) { index, app ->
            AppRow(app, big = true, favorite = true, actions, Modifier.animateItem().cascade(index))
        }
        if (loaded) {
            item(key = "show-all") {
                Column(Modifier.animateItem().cascade(favorites.size)) {
                    if (favorites.isEmpty()) {
                        BasicText(
                            stringResource(R.string.home_favorites_empty),
                            Modifier.padding(vertical = 12.dp),
                            style = TextStyle(HomeMuted, 17.sp, lineHeight = 24.sp),
                        )
                    }
                    TextAction(stringResource(R.string.home_show_all), onShowAll)
                }
            }
        }
    }
}

@Composable
private fun AllAppsList(
    rows: List<AllRow>,
    favorites: Set<String>,
    state: LazyListState,
    actions: HomeActions,
    onBack: () -> Unit,
) {
    LazyColumn(state = state, contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "back") { TextAction(stringResource(R.string.home_back_to_favorites), onBack, leading = true) }
        rows.forEach { row ->
            item(key = row.key) {
                when (row) {
                    is AllRow.Header ->
                        BasicText(
                            row.letter.toString(),
                            Modifier.padding(top = 18.dp, bottom = 4.dp),
                            style = TextStyle(HomeMuted, 15.sp, FontWeight.SemiBold),
                        )
                    is AllRow.App -> AppRow(row.entry, big = false, favorite = row.entry.key in favorites, actions, Modifier.animateItem())
                }
            }
        }
    }
}

/** Una app: icona e nome grande; si rimpicciolisce appena sotto il dito e torna con una molla. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppEntry,
    big: Boolean,
    favorite: Boolean,
    actions: HomeActions,
    modifier: Modifier = Modifier,
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
    val favoriteMark = stringResource(R.string.home_favorite_mark)
    Row(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { c ->
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
                onLongClick = {
                    view.perform(NovaHaptic.LONG_PRESS)
                    actions.onToggleFavorite(app)
                },
            ) {
                view.perform(NovaHaptic.TAP)
                actions.onLaunch(app, Rect(bounds))
            }.padding(vertical = if (big) 9.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app, actions.icon, if (big) BIG_ICON else SMALL_ICON)
        Spacer(Modifier.width(if (big) 18.dp else 14.dp))
        BasicText(
            app.label,
            Modifier.weight(1f, fill = false),
            style = TextStyle(HomeInk, if (big) BIG_LABEL else SMALL_LABEL, if (big) FontWeight.Light else FontWeight.Normal),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (favorite && !big) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(6.dp)
                    .background(HomeInk.copy(alpha = DOT_ALPHA), CircleShape)
                    .semantics { contentDescription = favoriteMark },
            )
        }
    }
}

@Composable
private fun TextAction(
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
        style = TextStyle(HomeMuted, 17.sp, FontWeight.Medium, letterSpacing = 0.3.sp),
    )
}

@Composable
private fun AppIcon(
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
            Box(Modifier.fillMaxSize().background(HomeInk.copy(alpha = 0.1f), SquircleShape(size * PLACEHOLDER_RADIUS)))
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

/** Colori del testo sulla sfumatura scura. */
internal val HomeInk = Color.White
internal val HomeMuted = Color.White.copy(alpha = 0.64f)

/** Le icone si caricano una sola volta, alla misura più grande. */
val TILE_ICON = 56.dp
private val BIG_ICON = 44.dp
private val SMALL_ICON = 34.dp
private val BIG_LABEL: TextUnit = 28.sp
private val SMALL_LABEL: TextUnit = 20.sp
private val CONTENT_WIDTH = 560.dp
private val WIDE_MARGIN = 72.dp
private val NARROW_MARGIN = 28.dp
private val TOP_MARGIN = 28.dp
private val ALPHABET_WIDTH = 44.dp
private val CASCADE_RISE = 18.dp
private const val PLACEHOLDER_RADIUS = 0.28f
private const val PRESSED_SCALE = 0.95f
private const val PRESS_DAMPING = 0.55f
private const val PRESS_STIFFNESS = 700f
private const val FADE_STIFFNESS = 380f
private const val FADE_OUT_STIFFNESS = 900f
private const val SLIDE_DAMPING = 0.86f
private const val SLIDE_STIFFNESS = 360f
private const val SLIDE_FRACTION = 10
private const val CASCADE_STEP_MS = 28L
private const val CASCADE_MAX_STEPS = 10
private const val CASCADE_DAMPING = 0.8f
private const val CASCADE_STIFFNESS = 260f
private const val TINT_TO_BLACK = 0.7f
private const val SIDE_ALPHA = 0.35f
private const val DOT_ALPHA = 0.7f
private const val HALF_HEIGHT = 0.5f
private val LEFT_CENTRE = TransformOrigin(pivotFractionX = 0f, pivotFractionY = HALF_HEIGHT)

@Suppress("MagicNumber") // opacità in alto e in basso della sfumatura
private val BLURRED_ALPHA = 0.30f to 0.62f

@Suppress("MagicNumber")
private val SOLID_ALPHA = 0.46f to 0.82f
