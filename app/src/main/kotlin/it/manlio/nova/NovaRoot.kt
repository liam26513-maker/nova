package it.manlio.nova

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.manlio.nova.core.design.GraphicsLevel
import it.manlio.nova.core.design.NovaTheme
import it.manlio.nova.core.system.ShizukuStatus
import it.manlio.nova.core.update.RollbackResult
import it.manlio.nova.core.update.UpdateState
import it.manlio.nova.core.update.UpdateWorker
import it.manlio.nova.feature.improve.ImproveMode
import it.manlio.nova.feature.onboarding.OnboardingStep
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
    var customizingHome by remember { mutableStateOf(false) }
    val update by app.updater.state.collectAsStateWithLifecycle()
    val shizukuStatus by app.shizuku.status.collectAsStateWithLifecycle()
    var changesVersion by remember { mutableIntStateOf(0) }
    val system = rememberSystemUiState(app, update, shizukuStatus, changesVersion)
    val scope = rememberCoroutineScope()
    val performance = remember { PerformanceUi(app, scope) }
    LaunchedEffect(shizukuStatus) { performance.refresh() }
    var improving by rememberSaveable { mutableStateOf<ImproveMode?>(null) }
    var onboardingStep by rememberSaveable { mutableStateOf(if (app.onboardingDone) null else OnboardingStep.WELCOME) }
    val actions =
        systemActions(app, update, scope, onChanged = { changesVersion++ }, openShizuku)
            .copy(
                onCustomizeHome = { customizingHome = true },
                onRedoOnboarding = { onboardingStep = OnboardingStep.WELCOME },
                onNotificationAccess = {
                    app.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                onImprove = { improving = ImproveMode.IMPROVE },
                onRepair = { improving = ImproveMode.REPAIR },
            )
    val measure = { startMeasure(app, scope, setBenchmark, onLevel = { measuring = it }, onDone = { showReport = true }) }
    NovaTheme(tokensFrom(config, graphics.level)) {
        val current = measuring
        val measured = profile?.takeIf { it.benchmark != null }
        val step = onboardingStep
        val improve = improving
        when {
            improve != null -> ImproveRoute(app, improve, onDone = { improving = null })
            current != null -> BenchmarkScreen(current)
            showReport && measured != null -> BenchmarkReportScreen(measured, onDone = { showReport = false })
            customizingHome -> HomeCustomizeRoute(app, onDone = { customizingHome = false })
            step != null ->
                OnboardingRoute(app, step, onStep = { onboardingStep = it }, system, actions, onMeasure = measure) {
                    onboardingStep = null
                }
            else ->
                SettingsScreen(
                    brandName = config.brand.displayName,
                    configVersion = config.meta.version,
                    graphics = graphics,
                    profile = profile,
                    onChoose = { choice -> chooseLevel(app, choice) },
                    onMeasure = measure,
                    onShowReport = { showReport = true },
                    system = system,
                    systemActions = actions,
                    performance = performance,
                )
        }
    }
}

/** Avvia la misura delle prestazioni; al termine mostra il resoconto. */
private fun startMeasure(
    app: NovaApp,
    scope: CoroutineScope,
    setBenchmark: (BenchmarkRunner?) -> Unit,
    onLevel: (GraphicsLevel?) -> Unit,
    onDone: () -> Unit,
) {
    val runner = BenchmarkRunner(app.graphicsController).also(setBenchmark)
    scope.launch {
        try {
            app.applyBenchmark(runner.run { onLevel(it) })
            onDone()
        } finally {
            setBenchmark(null)
            onLevel(null)
        }
    }
}

private fun chooseLevel(
    app: NovaApp,
    choice: LevelChoice,
) = when (choice) {
    LevelChoice.Automatic -> app.graphicsController.setAutomatic()
    is LevelChoice.Fixed -> app.graphicsController.setManual(choice.level)
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
    onChooseHome = {
        app.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    },
    onLoadPrevious = {
        app.updater.previousVersions().recoverCatching { throw IllegalStateException(app.updater.reasonFor(it)) }
    },
    onRollbackApp = { target ->
        app.updater.requestRollback(target).also { if (it == RollbackResult.Requested) UpdateWorker.watchRollback(app) }
    },
    onRevertSystem = {
        scope.launch {
            app.systemChanges.revertAll()
            onChanged()
        }
    },
)

@Composable
private fun rememberSystemUiState(
    app: NovaApp,
    update: UpdateState,
    shizukuStatus: ShizukuStatus,
    changesVersion: Int,
): SystemUiState {
    // Ricalcolato a ogni ritorno sulle Impostazioni (es. dopo aver scelto l'app Home in Android).
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsState()
    return remember(update, shizukuStatus, changesVersion, lifecycle) {
        SystemUiState(
            versionName = BuildConfig.VERSION_NAME,
            update = update,
            hasConfigHistory = app.updateHistory.activeVersion() != null,
            shizuku = shizukuStatus,
            systemChanges = app.systemChanges.entries().size,
            novaIsHome = isDefaultHome(app),
            notificationAccess = NotificationHub.isListenerEnabled(app),
        )
    }
}

/** NOVA è l'app Home predefinita? */
private fun isDefaultHome(app: NovaApp): Boolean =
    app.packageManager
        .resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo
        ?.packageName == app.packageName
