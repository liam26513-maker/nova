package it.manlio.nova.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * Home (SPEC §6, ispirata a Niagara senza copiarlo): sfondo di sistema con una sfumatura scura e
 * opaca presa dai suoi colori; ora grande; i preferiti come lista di nomi grandi; l'alfabeto a onda
 * porta all'elenco di tutte le app. Tutto si muove con molle interrompibili. L'aspetto segue
 * [prefs] (icone, testo, carattere, orologio, lato dell'alfabeto, sfumatura, app nascoste e rinominate).
 * [apps] null = caricamento in corso. Quando [scrollToTop] cambia (tasto Home) si torna ai preferiti.
 */
@Suppress("LongMethod", "CyclomaticComplexMethod") // radice della composizione: coordina stato, sfondo e disposizioni
@Composable
fun HomeScreen(
    apps: List<AppEntry>?,
    favoriteKeys: List<String>,
    nowMillis: Long,
    actions: HomeActions,
    style: HomeStyle = HomeStyle(),
    prefs: HomePrefs = HomePrefs(),
    wallpaper: ImageBitmap? = null,
    scrollToTop: Int = 0,
    /** Disposizione di icone, cartelle e widget dello stile iPad; [onLayoutChange] la modifica (e la salva). */
    layout: HomeLayout = HomeLayout(),
    onLayoutChange: ((HomeLayout) -> HomeLayout) -> Unit = {},
    /** Falso con «Rimuovi animazioni» di Android: niente ondeggiamento in modifica. */
    animate: Boolean = true,
    /** StandBy (SPEC §12.6): non nullo = schermo nero con l'ora grande sopra la Home; [onStandbyExit] lo chiude. */
    standby: StandbyInfo? = null,
    onStandbyExit: () -> Unit = {},
    /** A ogni tocco (senza consumarlo): ricomincia il conto del StandBy automatico. */
    onTouch: () -> Unit = {},
    /** Non nullo = Modalità Studio accesa: pastiglia con il Pomodoro e il tasto di uscita. */
    study: StudyBar? = null,
) {
    val data = rememberHomeData(apps, favoriteKeys, prefs, layout)
    val visible = data.visible
    val favorites = data.favorites
    var mode by rememberSaveable { mutableStateOf(HomeMode.FAVORITES) }
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var control by rememberSaveable { mutableStateOf(false) }
    val sheet = remember { ControlSheetState(scope) }
    val touch = rememberUpdatedState(onTouch)
    // `control` è la scelta (aprire/chiudere dal codice: ricerca, Indietro, un riquadro); la tendina la segue con una molla.
    LaunchedEffect(control) { if (sheet.target != control) sheet.settle(control) }
    val latestData by rememberUpdatedState(data)
    val ipadActions =
        remember(onLayoutChange, favorites) {
            IpadActions(
                onEditing = { editing = it },
                onLayout = { change -> onLayoutChange { current -> change(current.reconcile(latestData.keys, latestData.dockKeys)) } },
                onMenu = { menuFor = it },
                onSearch = { searching = true },
                onReorderDock = { order -> actions.onReorderFavorites(order + favorites.drop(DOCK_MAX).map { it.key }) },
            )
        }
    val favoritesState = rememberLazyListState()
    val allState = rememberLazyListState()
    LaunchedEffect(scrollToTop) {
        if (scrollToTop > 0) {
            menuFor = null
            mode = HomeMode.FAVORITES
            favoritesState.animateScrollToItem(0)
        }
    }
    BackHandler(enabled = control) { control = false }
    BackHandler(enabled = editing && menuFor == null && !control) { editing = false }
    BackHandler(enabled = mode == HomeMode.ALL && menuFor == null && !editing && !searching) { mode = HomeMode.FAVORITES }

    val tint = backgroundTint(prefs, style)
    val systemDark = isSystemInDarkTheme()
    val look = remember(prefs, systemDark, tint) { prefs.look(systemDark, tint) }
    // Scorrere giù apre la nostra tendina (se c'è); solo senza centro di controllo si ripiega sulle notifiche di Android.
    val swipeDown =
        rememberSwipeDown(prefs.swipeDownNotifications) {
            if (actions.controls != null) control = true else actions.onSwipeDown()
        }
    val previews = actions.notifications?.previews?.collectAsState()?.value ?: emptyMap()
    CompositionLocalProvider(LocalHomeLook provides look, LocalNotifications provides previews) {
        // pullDownControl prima di swipeDown: dall'angolo la tendina segue il dito; dal resto della Home si apre con una molla.
        BoxWithConstraints(
            Modifier.fillMaxSize().onAnyTouch(touch).pullDownControl(sheet, actions.controls != null) { control = it }.then(swipeDown),
        ) {
            HomeBackground(prefs, wallpaper)
            val blurred = prefs.blurLevel != BlurLevel.NONE && (wallpaper != null || style.blurred)
            Scrim(tint, blurred, prefs.scrim, look.dark)
            val ipad = prefs.layout == HomeLayoutStyle.IPAD
            val hybrid = prefs.layout == HomeLayoutStyle.HYBRID && maxWidth > maxHeight
            val lists =
                HomeLists(favorites, data.favoriteSet, AllAppsData(data.rows, data.headerIndex, allState), visible != null, favoritesState)
            val appList: @Composable () -> Unit = {
                AppListHome(prefs, nowMillis, actions, lists, mode, {
                    mode = it
                }, { menuFor = it }, showClock = prefs.showClock && !ipad && !hybrid, showSettings = !hybrid)
            }
            val grid: @Composable (Modifier) -> Unit = { m ->
                IpadHome(data.arranged, data.entries, data.dock, nowMillis, prefs, actions, ipadActions, scrollToTop, editing, animate, m)
            }
            // Con la ricerca aperta le icone della Home si dissolvono: la tastiera alza la barra in basso, che altrimenti si vedeva
            // attraverso il riquadro dei suggerimenti. Come sull'iPad, resta solo lo sfondo.
            val homeAlpha by animateFloatAsState(if (searching) 0f else 1f, label = "home-search")
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = homeAlpha }) {
                when {
                    ipad ->
                        AnimatedContent(
                            targetState = mode == HomeMode.FAVORITES,
                            transitionSpec = { gridTransition() },
                            modifier = Modifier.fillMaxSize(),
                            label = "home-ipad",
                        ) { showGrid -> if (showGrid) grid(Modifier) else appList() }
                    hybrid ->
                        Row(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f)) { appList() }
                            grid(Modifier.weight(HYBRID_GRID_WEIGHT))
                        }
                    else -> appList()
                }
            }
            ControlCenterEntry(actions.controls != null, { control = true })
            ControlCenter(
                sheet = sheet,
                controls = actions.controls,
                onSettings = {
                    control = false
                    actions.onOpenSettings()
                },
                onEditHome = {
                    control = false
                    editing = true
                },
                firstView = prefs.controlTiles,
                onClose = { control = false },
                onSettled = { control = it },
            )
            if (searching) {
                SearchScreen(
                    visible.orEmpty(),
                    actions,
                    commands =
                        SearchCommands(
                            onControl =
                                if (actions.controls != null) {
                                    (
                                        {
                                            searching = false
                                            control = true
                                        }
                                    )
                                } else {
                                    null
                                },
                            onEditHome = {
                                searching = false
                                editing = true
                            },
                        ),
                    onShowAll = {
                        searching = false
                        mode = HomeMode.ALL
                    },
                    onClose = { searching = false },
                )
            }
            if (!editing && !searching && !control) {
                val top = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 14.dp)
                if (study != null) StudyPill(study, top) else DynamicIsland(actions.media, top)
            }
            val clear =
                menuFor?.let { previews[it.component.packageName] }?.let { p ->
                    {
                        actions.notifications?.dismiss(p.key)
                        menuFor = null
                    }
                }
            val extras =
                menuExtras(
                    menuFor,
                    data.arranged,
                    ipad || hybrid,
                    ipadActions,
                ) { menuFor = null }.copy(onClearNotification = clear)
            AppMenu(menuFor, menuFor?.key in data.favoriteSet, actions, extras) { menuFor = null }
            standby?.let {
                val music = actions.media?.nowPlaying?.collectAsState()?.value?.takeIf { m -> m.playing }
                val extras =
                    listOfNotNull(
                        music?.let {
                                m ->
                            StandbyRules.musicLine(m.title, m.artist)
                        },
                        study?.text,
                    )
                StandbyScreen(
                    it,
                    nowMillis,
                    onStandbyExit,
                    weatherCity(layout),
                    actions.weather,
                    extras,
                ) {
                    // Le foto scelte, se l'opzione è accesa e ce ne sono; altrimenti lo sfondo della Home.
                    val context = LocalContext.current
                    val photos = remember(it) { if (prefs.standbyPhotos) StandbyPhotos.list(context) else emptyList() }
                    if (photos.isNotEmpty()) StandbyPhotoShow(photos, nowMillis) else HomeBackground(prefs, wallpaper)
                }
            }
        }
    }
}

/**
 * La home ad elenco (stile Niagara): preferiti e tutte le app con l'alfabeto. Nello stile iPad serve solo
 * per «Cerca app», cioè l'elenco completo, e l'ora sta nella pagina delle icone.
 */
@Composable
private fun AppListHome(
    prefs: HomePrefs,
    nowMillis: Long,
    actions: HomeActions,
    lists: HomeLists,
    mode: HomeMode,
    onMode: (HomeMode) -> Unit,
    onMenu: (AppEntry) -> Unit,
    showClock: Boolean,
    showSettings: Boolean,
) {
    val ipad = prefs.layout == HomeLayoutStyle.IPAD
    HomeLayout(
        prefs = prefs,
        nowMillis = nowMillis,
        onOpenSettings = actions.onOpenSettings,
        letters = remember(lists.headerIndex) { lists.headerIndex.keys.toList() },
        onLetter = { letter ->
            onMode(HomeMode.ALL)
            lists.headerIndex[letter]?.let { lists.allState.requestScrollToItem(it) }
        },
        onDoubleTapAlphabet = actions.onSleep.takeIf { prefs.doubleTapSleep },
        showClock = showClock,
        showSettings = showSettings,
    ) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = { modeTransition(targetState) },
            modifier = Modifier.weight(1f).widthIn(max = CONTENT_WIDTH),
            label = "home-mode",
        ) { shown ->
            when (shown) {
                HomeMode.FAVORITES ->
                    FavoritesList(lists.favorites, lists.loaded, lists.favoritesState, actions, onMenu) { onMode(HomeMode.ALL) }
                HomeMode.ALL ->
                    AllAppsList(lists.rows, lists.favoriteSet, lists.allState, actions, onMenu, backLabel = ipad) {
                        onMode(HomeMode.FAVORITES)
                    }
            }
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
    onDoubleTapAlphabet: (() -> Unit)?,
    showClock: Boolean,
    showSettings: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val margin = if (maxWidth > maxHeight) WIDE_MARGIN else NARROW_MARGIN
        val alphabet: @Composable () -> Unit = {
            Alphabet(
                letters = letters,
                onLetter = onLetter,
                onDoubleTap = onDoubleTapAlphabet,
                side = prefs.alphabetSide,
                modifier = Modifier.fillMaxHeight().width(ALPHABET_WIDTH).padding(vertical = NARROW_MARGIN),
            )
        }
        Row(Modifier.fillMaxSize()) {
            if (prefs.alphabetSide == Side.LEFT) alphabet()
            Column(Modifier.weight(1f).fillMaxHeight().padding(start = margin, top = TOP_MARGIN)) {
                if (showClock) {
                    ClockHeader(nowMillis)
                    Spacer(Modifier.height(32.dp))
                }
                content()
            }
            if (showSettings) {
                Column(Modifier.padding(top = TOP_MARGIN, end = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsButton(onOpenSettings)
                }
            }
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

/** Dalla griglia all'elenco completo (e ritorno): dissolvenza con un leggero ingrandimento, a molla. */
private fun gridTransition(): ContentTransform {
    val grow = spring<Float>(dampingRatio = SLIDE_DAMPING, stiffness = SLIDE_STIFFNESS)
    val enter = fadeIn(spring(stiffness = FADE_STIFFNESS)) + scaleIn(grow, GRID_SCALE)
    val exit = fadeOut(spring(stiffness = FADE_OUT_STIFFNESS)) + scaleOut(spring(stiffness = FADE_OUT_STIFFNESS), 1f / GRID_SCALE)
    return ContentTransform(enter, exit, sizeTransform = SizeTransform(clip = false))
}

/** Colore principale dello sfondo: per la sfumatura e l'accento automatico. */
private fun backgroundTint(
    prefs: HomePrefs,
    style: HomeStyle,
): Color =
    when (prefs.wallpaper) {
        WallpaperSource.NOVA -> NovaWallpapers.all[prefs.novaWallpaper].primary
        WallpaperSource.SOLID -> Color(prefs.solidColor)
        WallpaperSource.SYSTEM, WallpaperSource.PHOTO -> style.tint
    }

/**
 * Sfondo disegnato da NOVA: l'immagine già sfocata (sfondo di sistema letto con il permesso, o foto),
 * una sfumatura della raccolta o un colore. Se non c'è nulla da disegnare si vede lo sfondo di
 * sistema dietro la finestra.
 */
@Composable
private fun HomeBackground(
    prefs: HomePrefs,
    image: ImageBitmap?,
) {
    when (prefs.wallpaper) {
        WallpaperSource.NOVA -> {
            val preset = NovaWallpapers.all[prefs.novaWallpaper]
            Canvas(Modifier.fillMaxSize()) { preset.draw(this) }
        }
        WallpaperSource.SOLID -> Box(Modifier.fillMaxSize().background(Color(prefs.solidColor)))
        WallpaperSource.SYSTEM, WallpaperSource.PHOTO ->
            if (image != null) Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/**
 * Sfumatura opaca, tinta col colore dello sfondo: scura con il tema scuro, chiara con quello chiaro;
 * più marcata a sinistra e in basso, dove c'è il testo. Più leggera se lo sfondo è già sfocato.
 */
@Composable
private fun Scrim(
    tint: Color,
    blurred: Boolean,
    strength: ScrimStrength,
    dark: Boolean,
) {
    val base = lerp(tint, if (dark) Color.Black else Color.White, if (dark) TINT_TO_BLACK else TINT_TO_WHITE)
    val factor =
        when (strength) {
            ScrimStrength.LIGHT -> LIGHT_FACTOR
            ScrimStrength.MEDIUM -> 1f
            ScrimStrength.STRONG -> STRONG_FACTOR
        }
    val (top, bottom) = (if (blurred) BLURRED_ALPHA else SOLID_ALPHA).let { (a, b) -> a * factor to (b * factor).coerceAtMost(MAX_ALPHA) }
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(base.copy(alpha = top), base.copy(alpha = (top + bottom) / 2), base.copy(alpha = bottom))),
            ),
    )
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(base.copy(alpha = SIDE_ALPHA * factor), Color.Transparent))))
}

/**
 * Scorrere verso il basso apre le notifiche (come in Niagara): sia sopra l'ora e le zone vuote, sia
 * tirando giù una lista già in cima (in quel caso lo spostamento non serve alla lista).
 */
@Composable
private fun rememberSwipeDown(
    enabled: Boolean,
    onSwipe: () -> Unit,
): Modifier {
    if (!enabled) return Modifier
    val threshold = with(LocalDensity.current) { SWIPE_DISTANCE.toPx() }
    val current by rememberUpdatedState(onSwipe)
    val connection =
        remember(threshold) {
            object : NestedScrollConnection {
                var pulled = 0f

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                    pulled += available.y
                    if (pulled > threshold) {
                        pulled = Float.NEGATIVE_INFINITY // una volta sola per gesto
                        current()
                    }
                    return Offset(0f, available.y)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    pulled = 0f
                    return Velocity.Zero
                }
            }
        }
    return Modifier.nestedScroll(connection).pointerInput(threshold) {
        var pulled = 0f
        detectVerticalDragGestures(
            onDragStart = { pulled = 0f },
            onVerticalDrag = { change, amount ->
                pulled += amount
                if (pulled > threshold) {
                    pulled = Float.NEGATIVE_INFINITY
                    change.consume()
                    current()
                }
            },
        )
    }
}

/** Le icone si caricano una sola volta, alla misura più grande. */
val TILE_ICON = 88.dp
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
private const val GRID_SCALE = 0.94f
private const val HYBRID_GRID_WEIGHT = 2f
private const val TINT_TO_BLACK = 0.7f
private const val TINT_TO_WHITE = 0.78f
private val SWIPE_DISTANCE = 72.dp
private const val SIDE_ALPHA = 0.35f
private const val LIGHT_FACTOR = 0.6f
private const val STRONG_FACTOR = 1.25f
private const val MAX_ALPHA = 0.96f

@Suppress("MagicNumber") // opacità in alto e in basso della sfumatura
private val BLURRED_ALPHA = 0.30f to 0.62f

@Suppress("MagicNumber")
private val SOLID_ALPHA = 0.46f to 0.82f

/** La città del primo widget Meteo della Home, se c'è: lo StandBy mostra lo stesso tempo. */
private fun weatherCity(layout: HomeLayout): String? =
    layout.items.filterIsInstance<WidgetItem>().firstOrNull { it.kind == WidgetKind.WEATHER && it.text.isNotBlank() }?.text
