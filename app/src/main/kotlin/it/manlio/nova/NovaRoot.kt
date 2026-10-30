package it.manlio.nova

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.manlio.nova.core.design.GraphicsLevel
import it.manlio.nova.core.design.NovaTheme
import it.manlio.nova.core.update.UpdateState
import it.manlio.nova.core.update.UpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Radice dell'interfaccia: Impostazioni, misura delle prestazioni e resoconto. */
@Composable
fun NovaRoot(
    app: NovaApp,
    setBenchmark: (BenchmarkRunner?) -> Unit,
    openShizuku: () -> Unit,
) {
    val config by app.config.current.collectAsStateWithLifecycle()
    val graphics by app.graphics.collectAsStateWithLifecycle()
    val profile by app.profile.collectAsStateWithLifecycle()
    var measuring by remember { mutableStateOf<GraphicsLevel?>(null) }
    var showReport by remember { mutableStateOf(false) }
    val update by app.updater.state.collectAsStateWithLifecycle()
    val shizukuStatus by app.shizuku.status.collectAsStateWithLifecycle()
    var changesVersion by remember { mutableIntStateOf(0) }
    val system =
        remember(update, shizukuStatus, changesVersion) {
            SystemUiState(
                versionName = BuildConfig.VERSION_NAME,
                update = update,
                hasConfigHistory = app.updateHistory.activeVersion() != null,
                shizuku = shizukuStatus,
                systemChanges = app.systemChanges.entries().size,
            )
        }
    val scope = rememberCoroutineScope()
    NovaTheme(tokensFrom(config, graphics.level)) {
        val current = measuring
        val measured = profile?.takeIf { it.benchmark != null }
        if (current != null) {
            BenchmarkScreen(current)
        } else if (showReport && measured != null) {
            BenchmarkReportScreen(measured, onDone = { showReport = false })
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
                    val runner = BenchmarkRunner(app.graphicsController).also(setBenchmark)
                    scope.launch {
                        try {
                            app.applyBenchmark(runner.run { measuring = it })
                            showReport = true
                        } finally {
                            setBenchmark(null)
                            measuring = null
                        }
                    }
                },
                onShowReport = { showReport = true },
                system = system,
                systemActions = systemActions(app, update, scope, onChanged = { changesVersion++ }, openShizuku),
            )
        }
    }
}

/** Azioni delle sezioni Aggiornamenti e Strumenti di sistema. */
private fun systemActions(
    app: NovaApp,
    update: UpdateState,
    scope: CoroutineScope,
    onChanged: () -> Unit,
    openShizuku: () -> Unit,
) = SystemActions(
    onCheck = { scope.launch { app.updater.check(automatic = false) } },
    onInstall = {
        (update as? UpdateState.AppAvailable)?.let { u -> scope.launch { app.updater.install(u.update) } }
    },
    onSaveToken = { token ->
        app.updater.setToken(token).also { error ->
            if (error == null) scope.launch { app.updater.check(automatic = false) }
        }
    },
    onRollbackConfig = {
        app.updater.rollbackConfig()
        onChanged()
    },
    onShizukuAction = openShizuku,
    onLoadPrevious = {
        app.updater.previousVersions().recoverCatching { throw IllegalStateException(app.updater.reasonFor(it)) }
    },
    onRollbackApp = { target ->
        app.updater.requestRollback(target).also { if (it == null) UpdateWorker.checkSoon(app) }
    },
    onRevertSystem = {
        scope.launch {
            app.systemChanges.revertAll()
            onChanged()
        }
    },
)
