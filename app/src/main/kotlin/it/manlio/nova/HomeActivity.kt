package it.manlio.nova

import android.app.ActivityOptions
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import it.manlio.nova.core.design.GraphicsLevel
import it.manlio.nova.core.design.NovaTheme
import it.manlio.nova.core.safemode.SafeModeActivity
import it.manlio.nova.feature.home.AppCatalog
import it.manlio.nova.feature.home.AppEntry
import it.manlio.nova.feature.home.Favorites
import it.manlio.nova.feature.home.HomeActions
import it.manlio.nova.feature.home.HomeScreen
import it.manlio.nova.feature.home.HomeStyle
import it.manlio.nova.feature.home.TILE_ICON
import it.manlio.nova.feature.home.rememberMinuteClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.function.Consumer

/**
 * NOVA come app Home (Fase 0, punto 11). L'elenco delle app, i colori dello sfondo e la sfocatura
 * vivono solo mentre la home è visibile (onStart/onStop). Indietro non chiude la home; premere Home
 * quando si è già qui riporta ai preferiti.
 */
class HomeActivity : ComponentActivity() {
    private lateinit var catalog: AppCatalog
    private lateinit var favorites: Favorites
    private var homePresses by mutableIntStateOf(0)
    private var style by mutableStateOf(HomeStyle())
    private var blurSupported = false
    private val app get() = application as NovaApp
    private val wallpapers by lazy { WallpaperManager.getInstance(this) }
    private val frameThread = HandlerThread("nova-home-frames").apply { start() }
    private val frameListener =
        Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            app.graphicsController.onFrame(metrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP), metrics.getMetric(FrameMetrics.TOTAL_DURATION))
        }
    private val blurListener =
        Consumer<Boolean> { enabled ->
            blurSupported = enabled
            applyBlur()
        }
    private val colorsListener =
        WallpaperManager.OnColorsChangedListener { colors, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) applyColors(colors)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        app.safeModeReason?.let { reason ->
            startActivity(SafeModeActivity.intent(this, reason))
            finish()
            return
        }
        catalog = AppCatalog(this)
        favorites = Favorites(File(filesDir, "home-favorites.txt"))
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit // la home è la base: Indietro non la chiude
            },
        )
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { app.graphics.collect { applyBlur() } } }
        setContent {
            val config by app.config.current.collectAsStateWithLifecycle()
            val graphics by app.graphics.collectAsStateWithLifecycle()
            val apps by catalog.apps.collectAsStateWithLifecycle()
            val favoriteKeys by favorites.keys.collectAsStateWithLifecycle()
            val iconPx = with(LocalDensity.current) { TILE_ICON.roundToPx() }
            NovaTheme(tokensFrom(config, graphics.level)) {
                HomeScreen(
                    apps = apps,
                    favoriteKeys = favoriteKeys,
                    nowMillis = rememberMinuteClock(),
                    actions =
                        HomeActions(
                            icon = { catalog.icon(it, iconPx) },
                            onLaunch = ::launch,
                            onToggleFavorite = { entry -> lifecycleScope.launch { favorites.toggle(entry.key) } },
                            onOpenSettings = { startActivity(Intent(this, MainActivity::class.java)) },
                        ),
                    style = style,
                    scrollToTop = homePresses,
                )
            }
        }
    }

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

    /** Sfocatura reale dello sfondo, solo se il dispositivo la supporta e il livello grafico la consente. */
    private fun applyBlur() {
        val radius =
            when {
                !blurSupported -> 0
                app.graphics.value.level == GraphicsLevel.MAXIMUM -> BLUR_MAXIMUM_DP
                app.graphics.value.level == GraphicsLevel.BALANCED -> BLUR_BALANCED_DP
                else -> 0
            }
        window.setBackgroundBlurRadius((radius * resources.displayMetrics.density).toInt())
        style = style.copy(blurred = radius > 0)
    }

    private fun applyColors(colors: WallpaperColors?) {
        val primary = colors?.primaryColor?.toArgb() ?: return
        style = style.copy(tint = Color(primary))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses++
    }

    override fun onStart() {
        super.onStart()
        if (!::catalog.isInitialized) return
        catalog.start()
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

    private companion object {
        const val BLUR_MAXIMUM_DP = 40
        const val BLUR_BALANCED_DP = 22
    }
}
