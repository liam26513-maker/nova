package it.manlio.nova

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.KeyEvent
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.manlio.nova.core.config.NovaConfig
import it.manlio.nova.core.design.GraphicsLevel
import it.manlio.nova.core.design.NovaColors
import it.manlio.nova.core.design.NovaMotion
import it.manlio.nova.core.design.NovaRadii
import it.manlio.nova.core.design.NovaTheme
import it.manlio.nova.core.design.NovaTokens
import it.manlio.nova.core.design.parseNovaColor
import it.manlio.nova.core.safemode.SafeModeActivity
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var createdAt = 0L

    @Volatile private var benchmark: BenchmarkRunner? = null
    private val frameThread = HandlerThread("nova-frames").apply { start() }
    private val frameListener =
        Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            val app = application as NovaApp
            val duration = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            app.graphicsController.onFrame(metrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP), duration)
            benchmark?.onFrame(duration)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as NovaApp
        createdAt = SystemClock.uptimeMillis()
        if (openSafeModeIfNeeded(app)) return
        setContent {
            val config by app.config.current.collectAsStateWithLifecycle()
            val graphics by app.graphics.collectAsStateWithLifecycle()
            val profile by app.profile.collectAsStateWithLifecycle()
            var measuring by remember { mutableStateOf<GraphicsLevel?>(null) }
            val scope = rememberCoroutineScope()
            NovaTheme(tokensFrom(config, graphics.level)) {
                val current = measuring
                if (current != null) {
                    BenchmarkScreen(current)
                } else {
                    SettingsScreen(
                        brandName = config.brand.displayName,
                        configVersion = config.meta.version,
                        graphics = graphics,
                        profile = profile,
                        onChoose = { choice ->
                            when (choice) {
                                LevelChoice.Automatic -> app.graphicsController.setAutomatic()
                                is LevelChoice.Fixed -> app.graphicsController.setManual(choice.level)
                            }
                        },
                        onMeasure = {
                            val runner = BenchmarkRunner(app.graphicsController).also { benchmark = it }
                            scope.launch {
                                try {
                                    app.applyBenchmark(runner.run { measuring = it })
                                } finally {
                                    benchmark = null
                                    measuring = null
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    /** Volume giù tenuto premuto nei primi secondi: modalità provvisoria (SPEC §14.2). */
    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && SystemClock.uptimeMillis() - createdAt < SAFE_MODE_KEY_WINDOW_MS) {
            event.startTracking()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return super.onKeyLongPress(keyCode, event)
        val app = application as NovaApp
        app.log.i("nova", "modalità provvisoria richiesta col tasto volume")
        app.enterSafeMode()
        openSafeModeIfNeeded(app)
        return true
    }

    private fun openSafeModeIfNeeded(app: NovaApp): Boolean {
        val reason = app.safeModeReason ?: return false
        startActivity(SafeModeActivity.intent(this, reason))
        finish()
        return true
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

internal fun tokensFrom(
    c: NovaConfig,
    level: GraphicsLevel,
) = with(c.theme) {
    NovaTokens(
        dark = dark,
        colors =
            NovaColors(
                background = parseNovaColor(colors.background),
                surface = parseNovaColor(colors.surface),
                onSurface = parseNovaColor(colors.onSurface),
                onSurfaceMuted = parseNovaColor(colors.onSurfaceMuted),
                accent = parseNovaColor(colors.accent),
            ),
        radii = NovaRadii(radii.small.dp, radii.medium.dp, radii.large.dp),
        materialOpacity = materialOpacity,
        motion = NovaMotion(motion.stiffness, motion.dampingRatio),
        graphicsLevel = level,
    )
}

private const val SAFE_MODE_KEY_WINDOW_MS = 3_000L
