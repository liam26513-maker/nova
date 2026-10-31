package it.manlio.nova

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import it.manlio.nova.core.design.NovaTheme
import it.manlio.nova.core.safemode.SafeModeActivity
import it.manlio.nova.feature.home.AppCatalog
import it.manlio.nova.feature.home.Favorites
import it.manlio.nova.feature.home.HomeActions
import it.manlio.nova.feature.home.HomeScreen
import it.manlio.nova.feature.home.TILE_ICON
import it.manlio.nova.feature.home.rememberMinuteClock
import kotlinx.coroutines.launch
import java.io.File

/**
 * NOVA come app Home (Fase 0, punto 11). L'elenco delle app vive solo mentre la home è visibile
 * (onStart/onStop); il tasto Indietro non chiude la home; premere Home quando si è già qui
 * riporta la lista in cima.
 */
class HomeActivity : ComponentActivity() {
    private lateinit var catalog: AppCatalog
    private lateinit var favorites: Favorites
    private var homePresses by mutableIntStateOf(0)
    private val frameThread = HandlerThread("nova-home-frames").apply { start() }
    private val frameListener =
        Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            (application as NovaApp).graphicsController.onFrame(
                metrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP),
                metrics.getMetric(FrameMetrics.TOTAL_DURATION),
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as NovaApp
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
                            onLaunch = { entry ->
                                if (!catalog.launch(entry)) {
                                    Toast.makeText(this, getString(R.string.home_launch_failed, entry.label), Toast.LENGTH_SHORT).show()
                                }
                            },
                            onToggleFavorite = { entry -> lifecycleScope.launch { favorites.toggle(entry.key) } },
                            onOpenSettings = { startActivity(Intent(this, MainActivity::class.java)) },
                        ),
                    scrollToTop = homePresses,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses++
    }

    override fun onStart() {
        super.onStart()
        if (::catalog.isInitialized) catalog.start()
    }

    override fun onStop() {
        if (::catalog.isInitialized) catalog.stop()
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
