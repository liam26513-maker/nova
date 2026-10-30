package it.manlio.nova

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.manlio.nova.core.config.NovaConfig
import it.manlio.nova.core.design.GraphicsLevel
import it.manlio.nova.core.design.NovaColors
import it.manlio.nova.core.design.NovaMotion
import it.manlio.nova.core.design.NovaRadii
import it.manlio.nova.core.design.NovaTheme
import it.manlio.nova.core.design.NovaTokens
import it.manlio.nova.core.design.parseNovaColor
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val frameThread = HandlerThread("nova-frames").apply { start() }
    private val frameListener =
        Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            val app = application as NovaApp
            app.graphicsController.onFrame(
                timestampNanos = metrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP),
                durationNanos = metrics.getMetric(FrameMetrics.TOTAL_DURATION),
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as NovaApp
        setContent {
            val config by app.config.current.collectAsStateWithLifecycle()
            val level by app.graphicsLevel.collectAsStateWithLifecycle()
            NovaTheme(tokensFrom(config, level)) {
                DesignShowcase(
                    brandName = config.brand.displayName,
                    configVersion = config.meta.version,
                    level = level,
                    onPickLevel = { picked, lock -> app.graphicsController.set(picked, lock) },
                )
            }
        }
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
