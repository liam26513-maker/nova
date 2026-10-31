package it.manlio.nova.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * Home (SPEC §6, ispirata a Niagara senza copiarlo): sfondo di sistema con una sfumatura scura e
 * opaca presa dai suoi colori; ora grande; i preferiti come lista di nomi grandi; l'alfabeto a onda
 * porta all'elenco di tutte le app. Tutto si muove con molle interrompibili. L'aspetto segue
 * [prefs] (icone, testo, carattere, orologio, lato dell'alfabeto, sfumatura, app nascoste e rinominate).
 * [apps] null = caricamento in corso. Quando [scrollToTop] cambia (tasto Home) si torna ai preferiti.
 */
@Composable
fun HomeScreen(
    apps: List<AppEntry>?,
    favoriteKeys: List<String>,
    nowMillis: Long,
    actions: HomeActions,
    style: HomeStyle = HomeStyle(),
    prefs: HomePrefs = HomePrefs(),
    scrollToTop: Int = 0,
) {
    val visible = remember(apps, prefs.hidden, prefs.renamed) { prefs.apply(apps) }
    val favorites = remember(visible, favoriteKeys) { favoritesOf(visible, favoriteKeys) }
    val favoriteSet = remember(favoriteKeys) { favoriteKeys.toSet() }
    val rows = remember(visible) { allRows(visible.orEmpty()) }
    val headerIndex = remember(rows) { headerIndexOf(rows) }
    var mode by rememberSaveable { mutableStateOf(HomeMode.FAVORITES) }
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    val favoritesState = rememberLazyListState()
    val allState = rememberLazyListState()
    LaunchedEffect(scrollToTop) {
        if (scrollToTop > 0) {
            menuFor = null
            mode = HomeMode.FAVORITES
            favoritesState.animateScrollToItem(0)
        }
    }
    BackHandler(enabled = mode == HomeMode.ALL && menuFor == null) { mode = HomeMode.FAVORITES }

    CompositionLocalProvider(LocalHomeLook provides remember(prefs) { prefs.look() }) {
        Box(Modifier.fillMaxSize()) {
            Scrim(style, prefs.scrim)
            HomeLayout(
                prefs = prefs,
                nowMillis = nowMillis,
                onOpenSettings = actions.onOpenSettings,
                letters = remember(headerIndex) { headerIndex.keys.toList() },
                onLetter = { letter ->
                    mode = HomeMode.ALL
                    headerIndex[letter]?.let { allState.requestScrollToItem(it) }
                },
            ) {
                AnimatedContent(
                    targetState = mode,
                    transitionSpec = { modeTransition(targetState) },
                    modifier = Modifier.weight(1f).widthIn(max = CONTENT_WIDTH),
                    label = "home-mode",
                ) { shown ->
                    when (shown) {
                        HomeMode.FAVORITES ->
                            FavoritesList(favorites, visible != null, favoritesState, actions, { menuFor = it }) { mode = HomeMode.ALL }
                        HomeMode.ALL ->
                            AllAppsList(rows, favoriteSet, allState, actions, { menuFor = it }) { mode = HomeMode.FAVORITES }
                    }
                }
            }
            AppMenu(menuFor, menuFor?.key in favoriteSet, actions) { menuFor = null }
        }
    }
}

/** Disposizione: ora e data, contenuto, pulsante impostazioni e alfabeto sul lato scelto. */
@Composable
private fun HomeLayout(
    prefs: HomePrefs,
    nowMillis: Long,
    onOpenSettings: () -> Unit,
    letters: List<Char>,
    onLetter: (Char) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val margin = if (maxWidth > maxHeight) WIDE_MARGIN else NARROW_MARGIN
        val alphabet: @Composable () -> Unit = {
            Alphabet(
                letters = letters,
                onLetter = onLetter,
                side = prefs.alphabetSide,
                modifier = Modifier.fillMaxHeight().width(ALPHABET_WIDTH).padding(vertical = NARROW_MARGIN),
            )
        }
        Row(Modifier.fillMaxSize()) {
            if (prefs.alphabetSide == Side.LEFT) alphabet()
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = margin, top = TOP_MARGIN)) {
                if (prefs.showClock) {
                    ClockHeader(nowMillis)
                    Spacer(Modifier.height(32.dp))
                }
                content()
            }
            SettingsButton(onOpenSettings, Modifier.padding(top = TOP_MARGIN, end = 8.dp))
            if (prefs.alphabetSide == Side.RIGHT) alphabet()
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
private fun Scrim(
    style: HomeStyle,
    strength: ScrimStrength,
) {
    val base = lerp(style.tint, Color.Black, TINT_TO_BLACK)
    val factor =
        when (strength) {
            ScrimStrength.LIGHT -> LIGHT_FACTOR
            ScrimStrength.MEDIUM -> 1f
            ScrimStrength.STRONG -> STRONG_FACTOR
        }
    val (top, bottom) =
        (if (style.blurred) BLURRED_ALPHA else SOLID_ALPHA).let {
                (a, b) ->
            a * factor to (b * factor).coerceAtMost(MAX_ALPHA)
        }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(base.copy(alpha = top), base.copy(alpha = (top + bottom) / 2), base.copy(alpha = bottom))),
            ),
    )
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(base.copy(alpha = SIDE_ALPHA * factor), Color.Transparent))))
}

/** Colori del testo sulla sfumatura scura. */
internal val HomeInk = Color.White
internal val HomeMuted = Color.White.copy(alpha = 0.64f)

/** Le icone si caricano una sola volta, alla misura più grande. */
val TILE_ICON = 56.dp
private val CONTENT_WIDTH = 560.dp
private val WIDE_MARGIN = 72.dp
private val NARROW_MARGIN = 28.dp
private val TOP_MARGIN = 28.dp
private val ALPHABET_WIDTH = 44.dp
private const val FADE_STIFFNESS = 380f
private const val FADE_OUT_STIFFNESS = 900f
private const val SLIDE_DAMPING = 0.86f
private const val SLIDE_STIFFNESS = 360f
private const val SLIDE_FRACTION = 10
private const val TINT_TO_BLACK = 0.7f
private const val SIDE_ALPHA = 0.35f
private const val LIGHT_FACTOR = 0.6f
private const val STRONG_FACTOR = 1.25f
private const val MAX_ALPHA = 0.96f

@Suppress("MagicNumber") // opacità in alto e in basso della sfumatura
private val BLURRED_ALPHA = 0.30f to 0.62f

@Suppress("MagicNumber")
private val SOLID_ALPHA = 0.46f to 0.82f
