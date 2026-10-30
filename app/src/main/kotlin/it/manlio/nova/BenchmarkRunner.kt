package it.manlio.nova

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.GraphicsLevel
import it.manlio.nova.core.design.GraphicsLevelController
import it.manlio.nova.core.design.MaterialSurface
import it.manlio.nova.core.design.Nova
import it.manlio.nova.core.design.NovaSpacing
import it.manlio.nova.core.design.SettingsScaffold
import it.manlio.nova.core.device.BenchmarkResult
import it.manlio.nova.core.device.FrameSample
import it.manlio.nova.core.device.LevelAdvisor
import kotlinx.coroutines.delay
import java.util.Collections

/**
 * Micro-benchmark (SPEC §3.6): per ogni livello grafico fa scorrere dei pannelli traslucidi
 * e misura i tempi di frame. Circa 12 secondi in tutto.
 */
class BenchmarkRunner(private val controller: GraphicsLevelController) {
    private val frames = Collections.synchronizedList(mutableListOf<FrameSample>())

    @Volatile private var collecting = false

    /** Riceve durata e scadenza dei frame dal listener di FrameMetrics (thread dedicato). */
    fun onFrame(
        durationNanos: Long,
        deadlineNanos: Long,
    ) {
        if (collecting) frames += FrameSample(durationNanos / NANOS_PER_MS, deadlineNanos / NANOS_PER_MS)
    }

    suspend fun run(onProgress: (GraphicsLevel) -> Unit): BenchmarkResult {
        val p90 = mutableMapOf<GraphicsLevel, Float>()
        val jank = mutableMapOf<GraphicsLevel, Float>()
        for (level in GraphicsLevel.entries) {
            onProgress(level)
            controller.setManual(level)
            delay(WARMUP_MS) // lascia assestare la composizione e le cache
            frames.clear()
            collecting = true
            delay(MEASURE_MS)
            collecting = false
            val (p, j) = LevelAdvisor.summarize(synchronized(frames) { frames.toList() })
            p90[level] = p
            jank[level] = j
        }
        return BenchmarkResult(p90, jank)
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000f
        const val WARMUP_MS = 600L
        const val MEASURE_MS = 3_400L
    }
}

/** Schermata mostrata durante la misura: pannelli traslucidi in movimento continuo. */
@Composable
fun BenchmarkScreen(level: GraphicsLevel) {
    val t = Nova.tokens
    val motion = rememberInfiniteTransition(label = "benchmark")
    val shift by motion.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(DRIFT_MS, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    SettingsScaffold(
        title = stringResource(R.string.benchmark_title),
        subtitle = stringResource(R.string.benchmark_subtitle, stringResource(level.label())),
    ) { backdrop ->
        repeat(ROWS) { row ->
            Row(
                Modifier.offset(x = (DRIFT_DP * shift * if (row % 2 == 0) 1 else -1).dp),
                horizontalArrangement = Arrangement.spacedBy(NovaSpacing.m),
            ) {
                repeat(COLUMNS) { col ->
                    MaterialSurface(
                        backdrop,
                        Modifier
                            .size(PANEL_DP.dp)
                            .graphicsLayer { rotationZ = shift * (col - 1) * TILT },
                    ) {}
                }
            }
        }
        BasicText(stringResource(R.string.benchmark_hint), style = TextStyle(t.colors.onSurfaceMuted, 14.sp))
    }
}

private const val ROWS = 3
private const val COLUMNS = 4
private const val PANEL_DP = 120
private const val DRIFT_DP = 60
private const val DRIFT_MS = 1_400
private const val TILT = 4f
