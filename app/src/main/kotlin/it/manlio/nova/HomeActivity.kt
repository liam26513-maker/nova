package it.manlio.nova

import android.app.ActivityOptions
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import it.manlio.nova.core.design.NovaTheme
import it.manlio.nova.core.safemode.SafeModeActivity
import it.manlio.nova.core.system.ShizukuStatus
import it.manlio.nova.core.system.SystemCommand
import it.manlio.nova.feature.home.AppCatalog
import it.manlio.nova.feature.home.AppEntry
import it.manlio.nova.feature.home.Favorites
import it.manlio.nova.feature.home.HomeActions
import it.manlio.nova.feature.home.HomeLayoutStore
import it.manlio.nova.feature.home.HomeScreen
import it.manlio.nova.feature.home.HomeStyle
import it.manlio.nova.feature.home.PreparedWallpaper
import it.manlio.nova.feature.home.TILE_ICON
import it.manlio.nova.feature.home.WallpaperLoader
import it.manlio.nova.feature.home.WallpaperSource
import it.manlio.nova.feature.home.rememberMinuteClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.function.Consumer

/**
 * NOVA come app Home (Fase 0, punto 11). Elenco delle app, sfondo, colori e sfocatura vivono solo
 * mentre la home è visibile (onStart/onStop). Indietro non chiude la home; premere Home quando si è
 * già qui riporta ai preferiti.
 */
class HomeActivity : ComponentActivity() {
    private lateinit var catalog: AppCatalog
    private lateinit var favorites: Favorites
    private lateinit var layoutStore: HomeLayoutStore
    private lateinit var widgets: WidgetHostBridge
    private lateinit var controls: HomeControls
    private val standby by lazy { StandbyController(this, app, lifecycleScope) }
    private lateinit var performance: PerformanceUi
    private lateinit var mediaHub: MediaHub
    private val weather = WeatherClient()
    private var controlsTick by mutableIntStateOf(0)
    private lateinit var wallpaperLoader: WallpaperLoader
    private var homePresses by mutableIntStateOf(0)
    private var style by mutableStateOf(HomeStyle())
    private var wallpaper by mutableStateOf<PreparedWallpaper?>(null)
    private var blurSupported = false
    private val app get() = application as NovaApp
    private val prefs get() = app.homePrefs.prefs.value
    private val wallpapers by lazy { WallpaperManager.getInstance(this) }
    private val frameThread = HandlerThread("nova-home-frames").apply { start() }
    private val frameListener =
        Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            app.graphicsController.onFrame(metrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP), metrics.getMetric(FrameMetrics.TOTAL_DURATION))
        }
    private val blurListener =
        Consumer<Boolean> { enabled ->
            blurSupported = enabled
            applyWindow()
        }
    private val colorsListener =
        WallpaperManager.OnColorsChangedListener { colors, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) {
                applyColors(colors)
                if (prefs.wallpaper == WallpaperSource.SYSTEM) reloadWallpaper()
            }
        }

    @Suppress("LongMethod") // radice della composizione della Home
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.safeModeReason?.let { reason ->
            startActivity(SafeModeActivity.intent(this, reason))
            finish()
            return
        }
        catalog = AppCatalog(this)
        favorites = Favorites(File(filesDir, "home-favorites.txt"))
        layoutStore = HomeLayoutStore(File(filesDir, "home-layout.txt"))
        widgets = WidgetHostBridge(this)
        performance = PerformanceUi(app, lifecycleScope)
        controls =
            HomeControls(
                this,
                app,
                lifecycleScope,
                onSystemNotifications = { HomeSystem.expandNotifications(this) },
                onSystemQuickSettings = { HomeSystem.expandQuickSettings(this) },
            ) { HomeSystem.turboWithToast(this, lifecycleScope, performance) }
        NotificationHub.init(this)
        mediaHub = MediaHub(this)
        wallpaperLoader = WallpaperLoader(this)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit // la home è la base: Indietro non la chiude
            },
        )
        observe()
        setContent {
            val config by app.config.current.collectAsStateWithLifecycle()
            val graphics by app.graphics.collectAsStateWithLifecycle()
            val apps by catalog.apps.collectAsStateWithLifecycle()
            val favoriteKeys by favorites.keys.collectAsStateWithLifecycle()
            val prefs by app.homePrefs.prefs.collectAsStateWithLifecycle()
            val layout by layoutStore.layout.collectAsStateWithLifecycle()
            controlsTick // leggerlo fa ricomporre la Home al ritorno, così il centro di controllo rilegge i permessi
            val iconPx = with(LocalDensity.current) { TILE_ICON.roundToPx() }
            val spec = HomeSystem.iconSpec(this, prefs)
            NovaTheme(tokensFrom(config, graphics.level)) {
                HomeScreen(
                    apps = apps,
                    favoriteKeys = favoriteKeys,
                    nowMillis = rememberMinuteClock(),
                    actions = actions { catalog.icon(it, iconPx, spec) },
                    style = style,
                    prefs = prefs,
                    wallpaper = wallpaper?.image,
                    scrollToTop = homePresses,
                    layout = layout,
                    onLayoutChange = { change -> lifecycleScope.launch { layoutStore.update(change) } },
                    animate = HomeSystem.animationsOn(this),
                    standby = standby.info(),
                    onStandbyExit = standby::hide,
                    study = rememberStudyBar(this, app),
                    onTouch = standby::touched,
                )
            }
        }
    }

    /** Riapplica finestra e sfondo quando cambiano livello grafico o preferenze (solo mentre la home è visibile). */
    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(app.graphics, app.homePrefs.prefs) { _, _ -> }.collect { applyWindow() }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Riparte a ogni ritorno nella home: così lo sfondo si rilegge anche dopo aver scelto
                // una foto o concesso il permesso nelle Impostazioni.
                app.homePrefs.prefs
                    .map { it.wallpaper to it.blurLevel }
                    .distinctUntilChanged()
                    .collect { reloadWallpaper() }
            }
        }
    }

    private fun actions(icon: suspend (AppEntry) -> androidx.compose.ui.graphics.ImageBitmap?) =
        HomeActions(
            icon = icon,
            onLaunch = ::launch,
            onToggleFavorite = { entry -> lifecycleScope.launch { favorites.toggle(entry.key) } },
            onOpenSettings = { startActivity(Intent(this, MainActivity::class.java)) },
            onReorderFavorites = { order -> lifecycleScope.launch { favorites.reorder(order) } },
            onHide = { entry -> app.homePrefs.updateIn(lifecycleScope) { it.copy(hidden = it.hidden + (entry.key to entry.label)) } },
            onRename = { entry, name ->
                app.homePrefs.updateIn(lifecycleScope) {
                    it.copy(renamed = if (name.isBlank()) it.renamed - entry.key else it.renamed + (entry.key to name))
                }
            },
            onAppInfo = { entry -> catalog.openInfo(entry) },
            onUninstall = { entry ->
                runCatching { startActivity(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", entry.component.packageName, null))) }
            },
            onSwipeDown = { HomeSystem.expandNotifications(this) },
            onSleep = ::sleep,
            onWebSearch = { HomeSystem.webSearch(this, it) },
            widgets = widgets,
            weather = weather::load,
            controls = controls.actions(),
            notifications = NotificationHub,
            media = mediaHub,
        )

    /** Apre l'app facendola "crescere" dal punto toccato. */
    private fun launch(
        entry: AppEntry,
        bounds: Rect?,
    ) {
        val options =
            bounds?.takeIf { !it.isEmpty }?.let {
                ActivityOptions.makeClipRevealAnimation(window.decorView, it.left, it.top, it.width(), it.height()).toBundle()
            }
        if (!catalog.launch(entry, bounds, options)) {
            Toast.makeText(this, getString(R.string.home_launch_failed, entry.label), Toast.LENGTH_SHORT).show()
        }
    }

    /** Spegne lo schermo tramite Shizuku (senza Shizuku, Android non lo permette a un'app normale). */
    private fun sleep() {
        if (app.shizuku.status.value != ShizukuStatus.READY) {
            Toast.makeText(this, R.string.home_sleep_needs_shizuku, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch { app.shizuku.run(SystemCommand.Sleep) }
    }

    private fun applyWindow() {
        val dark = HomeSystem.isDark(this, prefs)
        val blurred = HomeWindow.apply(this, prefs, wallpaper != null, blurSupported, app.graphics.value.level, dark)
        style = style.copy(blurred = blurred)
    }

    /** Prepara fuori dal thread principale lo sfondo che la home disegna da sé (sfondo di sistema o foto). */
    private fun reloadWallpaper() {
        val current = prefs
        lifecycleScope.launch {
            val prepared =
                when (current.wallpaper) {
                    WallpaperSource.SYSTEM -> wallpaperLoader.system(current.blurLevel)
                    WallpaperSource.PHOTO -> wallpaperLoader.photo(current.blurLevel)
                    else -> null
                }
            wallpaper = prepared
            prepared?.primaryColor?.let { style = style.copy(tint = Color(it)) }
            applyWindow()
        }
    }

    private fun applyColors(colors: WallpaperColors?) {
        val primary = colors?.primaryColor?.toArgb() ?: return
        if (prefs.wallpaper == WallpaperSource.SYSTEM) style = style.copy(tint = Color(primary))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses++
    }

    override fun onStart() {
        super.onStart()
        if (!::catalog.isInitialized) return
        catalog.start()
        widgets.start()
        controls.start()
        standby.start()
        app.modes.start() // esce da una modalità dimenticata e riprende una rimasta a metà
        mediaHub.start()
        controlsTick++ // il permesso della luminosità può essere cambiato mentre la Home non c'era
        getSystemService(WindowManager::class.java).addCrossWindowBlurEnabledListener(mainExecutor, blurListener)
        wallpapers.addOnColorsChangedListener(colorsListener, Handler(mainLooper))
        lifecycleScope.launch {
            val colors =
                withContext(Dispatchers.IO) { runCatching { wallpapers.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull() }
            applyColors(colors)
        }
    }

    override fun onStop() {
        if (::catalog.isInitialized) {
            catalog.stop()
            widgets.stop()
            controls.stop()
            standby.stop()
            mediaHub.stop()
            getSystemService(WindowManager::class.java).removeCrossWindowBlurEnabledListener(blurListener)
            wallpapers.removeOnColorsChangedListener(colorsListener)
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        window.addOnFrameMetricsAvailableListener(frameListener, Handler(frameThread.looper))
    }

    override fun onPause() {
        window.removeOnFrameMetricsAvailableListener(frameListener)
        super.onPause()
    }

    override fun onDestroy() {
        frameThread.quitSafely()
        super.onDestroy()
    }
}
