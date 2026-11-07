package it.manlio.nova

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.manlio.nova.core.design.Nova
import it.manlio.nova.core.design.NovaTheme
import it.manlio.nova.core.modes.Modes
import it.manlio.nova.core.system.ShizukuStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale

/** I numeri del pannello; null = non si sa. */
internal data class GameStats(
    val fps: Float?,
    val tempC: Float,
    val battery: Int,
    val pingMs: Long?,
    val freeMb: Long,
)

/**
 * Il pannello in sovrimpressione della Gaming (SPEC §8.2). Esiste solo mentre è aperto e i numeri si leggono solo allora, una
 * volta al secondo. Gli FPS sono quelli dell'ultima misura: leggerli dal vivo ferma il compositore e farebbe scattare il gioco.
 * La finestra non prende il fuoco, così il gioco non va in pausa; un tocco fuori la chiude.
 */
internal class GamingPanel(
    private val service: AccessibilityService,
    private val app: NovaApp,
    private val scope: CoroutineScope,
) {
    private val windows = service.getSystemService(WindowManager::class.java)
    private val tools = GameTools(service, app)
    private var host: NovaControlService.PanelHost? = null
    val recorder = GameRecorder(service, app)

    fun show() {
        if (host != null || service.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
        val view = NovaControlService.PanelHost(service).apply { addView(content()) }
        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply { setFitInsetsTypes(0) }
        if (runCatching { windows.addView(view, params) }.isSuccess) {
            host = view
            view.resume()
        }
    }

    fun dismiss() {
        val view = host ?: return
        host = null
        view.destroy()
        runCatching { windows.removeView(view) }
    }

    private fun content() =
        ComposeView(service).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setContent {
                val config by app.config.current.collectAsState()
                val graphics by app.graphics.collectAsState()
                NovaTheme(tokensFrom(config, graphics.level)) {
                    Box(Modifier.fillMaxSize()) {
                        val closeLabel = stringResource(R.string.panel_dismiss)
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = SCRIM))
                                .clickable(onClickLabel = closeLabel, onClick = ::dismiss),
                        )
                        Card(Modifier.align(Alignment.CenterStart))
                    }
                }
            }
        }

    @Composable
    private fun Card(modifier: Modifier) {
        val stats by produceState<GameStats?>(null) {
            while (true) {
                value = withContext(Dispatchers.IO) { readStats() }
                delay(REFRESH_MS)
            }
        }
        Column(
            modifier
                .padding(16.dp)
                .width(PANEL_WIDTH_DP.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Nova.tokens.colors.surface)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            stats?.let { Numbers(it) }
            Buttons()
        }
    }

    @Composable
    private fun Numbers(stats: GameStats) {
        val none = "—"
        Stat(stringResource(R.string.panel_fps), stats.fps?.let { String.format(Locale.ROOT, "%.0f", it) } ?: none)
        Stat(stringResource(R.string.panel_temp), String.format(Locale.ROOT, "%.0f °C", stats.tempC))
        Stat(stringResource(R.string.panel_battery), "${stats.battery}%")
        Stat(stringResource(R.string.panel_ping), stats.pingMs?.let { "$it ms" } ?: none)
        Stat(stringResource(R.string.panel_ram), "${stats.freeMb} MB")
    }

    @Composable
    private fun Stat(
        label: String,
        value: String,
    ) {
        val colors = Nova.tokens.colors
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(label, style = TextStyle(color = colors.onSurfaceMuted, fontSize = 14.sp))
            BasicText(value, style = TextStyle(color = colors.onSurface, fontSize = 14.sp))
        }
    }

    @Composable
    private fun Buttons() {
        val status by app.shizuku.status.collectAsState()
        val shizuku = status == ShizukuStatus.READY
        val game = app.lastGame
        Action(stringResource(R.string.panel_screenshot), ::screenshot)
        if (shizuku) {
            val recording by recorder.recording.collectAsState()
            Action(stringResource(if (recording) R.string.panel_record_stop else R.string.panel_record)) {
                dismiss()
                recorder.toggle()
            }
            Action(stringResource(R.string.panel_turbo)) { HomeSystem.turboWithToast(service, scope, PerformanceUi(app, scope)) }
        }
        if (shizuku && game != null && tools.isGame(game)) ScaleAction(game)
        if (shizuku && game != null) {
            Action(stringResource(R.string.panel_close_game)) {
                dismiss()
                tools.close(game)
            }
        }
        Action(stringResource(R.string.panel_exit)) {
            dismiss()
            if (app.modes.isActive(Modes.GAMING)) app.modes.toggle(Modes.GAMING) {}
        }
    }

    @Composable
    private fun ScaleAction(game: String) {
        var scale by remember(game) { mutableStateOf(tools.scale(game)) }
        val text = stringResource(R.string.game_scale_percent, scale ?: FULL_SCALE)
        Action(stringResource(R.string.more_game_scale, text)) { tools.stepScale(game) { scale = tools.scale(game) } }
    }

    @Composable
    private fun Action(
        label: String,
        onClick: () -> Unit,
    ) {
        val colors = Nova.tokens.colors
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.accent.copy(alpha = BUTTON_TINT))
                .clickable(onClick = onClick)
                .padding(12.dp),
        ) {
            BasicText(label, style = TextStyle(color = colors.onSurface, fontSize = 15.sp))
        }
    }

    /** Il pannello si toglie prima, o finirebbe nella foto. */
    private fun screenshot() {
        dismiss()
        Handler(Looper.getMainLooper()).postDelayed(
            { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) },
            SCREENSHOT_WAIT_MS,
        )
    }

    private fun readStats(): GameStats {
        val battery = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val memory = ActivityManager.MemoryInfo().also { app.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        return GameStats(
            fps = app.lastGame?.let(GameFps(app)::of),
            tempC = (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / TENTHS,
            battery = app.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            pingMs = ping(),
            freeMb = memory.availMem / MB,
        )
    }

    /** Quanto ci mette ad aprirsi una connessione, senza inviare niente: Android non permette un vero ping alle app. */
    private fun ping(): Long? =
        runCatching {
            Socket().use {
                val start = System.nanoTime()
                it.connect(InetSocketAddress(PING_HOST, PING_PORT), PING_TIMEOUT_MS)
                (System.nanoTime() - start) / NANOS_PER_MS
            }
        }.getOrNull()

    private companion object {
        const val SCRIM = 0.35f
        const val BUTTON_TINT = 0.18f
        const val PANEL_WIDTH_DP = 300
        const val REFRESH_MS = 1000L
        const val SCREENSHOT_WAIT_MS = 300L
        const val FULL_SCALE = 100
        const val TENTHS = 10f
        const val MB = 1024 * 1024
        const val PING_HOST = "1.1.1.1"
        const val PING_PORT = 443
        const val PING_TIMEOUT_MS = 800
        const val NANOS_PER_MS = 1_000_000L
    }
}
