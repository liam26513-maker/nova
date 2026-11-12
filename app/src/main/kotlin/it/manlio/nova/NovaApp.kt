package it.manlio.nova

import android.app.Application
import android.content.Context
import it.manlio.nova.core.config.ApplyResult
import it.manlio.nova.core.config.ConfigEngine
import it.manlio.nova.core.design.GraphicsLevel
import it.manlio.nova.core.design.GraphicsLevelController
import it.manlio.nova.core.design.GraphicsState
import it.manlio.nova.core.device.BenchmarkResult
import it.manlio.nova.core.device.DeviceProfile
import it.manlio.nova.core.device.DeviceProfiler
import it.manlio.nova.core.device.LevelAdvisor
import it.manlio.nova.core.modules.ModuleRegistry
import it.manlio.nova.core.safemode.SafeModeActivity
import it.manlio.nova.core.system.ShizukuBridge
import it.manlio.nova.core.system.ShizukuStatus
import it.manlio.nova.core.system.SystemChangeLog
import it.manlio.nova.core.system.SystemTools
import it.manlio.nova.core.telemetry.BootGuard
import it.manlio.nova.core.telemetry.CrashReporter
import it.manlio.nova.core.telemetry.KeyValueFile
import it.manlio.nova.core.telemetry.LocalLog
import it.manlio.nova.core.telemetry.SafeModeReason
import it.manlio.nova.core.update.ApkInstaller
import it.manlio.nova.core.update.ConfigTarget
import it.manlio.nova.core.update.GitHubClient
import it.manlio.nova.core.update.LocalHistory
import it.manlio.nova.core.update.TokenStore
import it.manlio.nova.core.update.UpdateWorker
import it.manlio.nova.core.update.Updater
import it.manlio.nova.core.update.UpdaterProvider
import it.manlio.nova.feature.home.HomePrefsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Assemblaggio del nucleo: affidabilità, configurazione, registro moduli, livello grafico. */
class NovaApp :
    Application(),
    UpdaterProvider {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Vero se la tendina di Android è bloccata da NOVA: il gesto dall'alto non deve più combatterla. */
    @Volatile var shadeLocked = false

    val log = LocalLog()

    lateinit var bootGuard: BootGuard
        private set

    /** Se non è null, NOVA deve aprirsi in modalità provvisoria (SPEC §14.2). */
    var safeModeReason: SafeModeReason? = null
        private set

    lateinit var config: ConfigEngine
        private set

    private val firstRun by lazy { KeyValueFile(File(filesDir, "onboarding.properties")) }

    /** La procedura guidata è stata completata (o chiusa) almeno una volta. */
    var onboardingDone: Boolean
        get() = firstRun.getBoolean(ONBOARDING_DONE)
        set(value) = firstRun.edit { put(ONBOARDING_DONE, value) }

    /** Personalizzazione della home: la legge la Home e la modificano le Impostazioni. */
    val homePrefs by lazy { HomePrefsStore(File(filesDir, "home-prefs.properties")) }

    // Nessun modulo funzionale ancora: arriveranno dalla Fase 1 in poi.
    val modules = ModuleRegistry(emptyList())

    /**
     * Livello iniziale atteso sul tablet di riferimento (SPEC §2.1): Bilanciato.
     * Dopo il benchmark, partenza e tetto arrivano dal profilo del dispositivo.
     */
    private val _graphics = MutableStateFlow(GraphicsState(GraphicsLevel.BALANCED, automatic = true))
    val graphics: StateFlow<GraphicsState> = _graphics.asStateFlow()

    val graphicsController: GraphicsLevelController =
        GraphicsLevelController(GraphicsLevel.BALANCED) { _graphics.value = it }

    lateinit var profiler: DeviceProfiler
        private set

    lateinit var shizuku: ShizukuBridge
        private set

    /** Modifiche di sistema fatte da NOVA, tutte annullabili (SPEC §7.1). */
    lateinit var systemChanges: SystemChangeLog
    lateinit var systemTools: SystemTools
    lateinit var modes: ModeController

    /** Qualcuno (la tendina, anche sopra le altre app) chiede il StandBy: la Home lo mostra appena è visibile. */
    val standbyRequest = MutableStateFlow(false)

    /** L'app in primo piano se può essere un gioco (la riconosce il servizio di accessibilità), o null. */
    val foreground = MutableStateFlow<String?>(null)

    /** L'ultimo gioco visto in primo piano: serve al tasto «Chiudi il gioco» anche quando si è tornati alla Home. */
    @Volatile var lastGame: String? = null

    /** Le regole «quando → allora» dell'utente (SPEC §12.4). */
    internal lateinit var automations: AutomationEngine

    /** La situazione in cui si aprono le app, per i suggerimenti (SPEC §12.5). */
    internal val contexts by lazy { ContextLog(this) }
    lateinit var gameMeter: GameMeter

    /** App che sta suonando musica (la aggiorna `MediaHub`): il Turbo non la chiude. */
    @Volatile var nowPlayingPackage: String? = null

    /** Accesso al repository (aggiornamenti e report di Ripara), con il token cifrato. */
    lateinit var github: GitHubClient
        private set

    override lateinit var updater: Updater
        private set

    lateinit var updateHistory: LocalHistory
        private set

    private val _profile = MutableStateFlow<DeviceProfile?>(null)
    val profile: StateFlow<DeviceProfile?> = _profile.asStateFlow()

    /** Salva un nuovo esito del benchmark e applica livello di partenza e tetto (SPEC §3.6). */
    suspend fun applyBenchmark(result: BenchmarkResult) =
        withContext(Dispatchers.IO) {
            val base = _profile.value ?: profiler.readHardware()
            val (start, ceiling) = LevelAdvisor.advise(result, base.ramMb, base.supportsRealtimeBlur)
            val updated =
                base.copy(
                    benchmark = result,
                    startLevel = start,
                    ceiling = ceiling,
                    measuredAt = System.currentTimeMillis(),
                    benchmarkMethod = LevelAdvisor.METHOD,
                )
            profiler.save(updated)
            _profile.value = updated
            graphicsController.configure(start, ceiling)
            graphicsController.setAutomatic()
            log.i(TAG, "benchmark: partenza $start, tetto $ceiling, p90 ${result.p90MsByLevel}")
        }

    override fun onCreate() {
        super.onCreate()
        // La lettura del testo gira in un processo a parte (`:ocr`): lì NOVA non avvia niente, e se il motore nativo cade la Home
        // e la tendina restano su. Non conta nemmeno come avvio per la modalità provvisoria.
        if (getProcessName().endsWith(OCR_PROCESS)) return
        // Per primi: il gestore dei crash deve coprire anche gli errori dell'avvio.
        bootGuard = BootGuard(KeyValueFile(SafeModeActivity.bootFile(this)))
        CrashReporter(SafeModeActivity.crashDir(this), log, bootGuard, BuildConfig.VERSION_NAME).install()
        safeModeReason = bootGuard.onLaunch(BuildConfig.VERSION_CODE.toLong())
        log.i(TAG, "avvio ${BuildConfig.VERSION_NAME}, modalità provvisoria: ${safeModeReason ?: "no"}")
        if (safeModeReason != null) return // in modalità provvisoria non si carica niente altro

        config = ConfigEngine(BuildConfig.VERSION_CODE, loadDefaults(this))
        overridesFile(this).takeIf { it.exists() }?.readText()?.let { json ->
            (config.setOverrides(json) as? ApplyResult.Rejected)?.let { log.w(TAG, "override locali ignorati: ${it.reason}") }
        }
        appScope.launch {
            config.current.collect { c -> modules.apply(c.modules.modules, capabilities = emptySet()) }
        }
        shizuku = ShizukuBridge(this).also { it.start() }
        systemChanges = SystemChangeLog(SystemChangeLog.fileIn(filesDir), shizuku)
        systemTools = SystemTools(shizuku, systemChanges)
        appScope.launch {
            // Una dimensione dello schermo provata e mai confermata (l'app si è chiusa a metà) torna com'era appena Shizuku risponde.
            shizuku.status.first { it == ShizukuStatus.READY }
            systemTools.revertUnconfirmedDensity()
            systemChanges.reapplyFirewall()
        }
        setUpUpdates()
        modes = ModeController(this, appScope).also { it.start() }
        StudySchedule.arm(this)
        automations = AutomationEngine(this).also { it.start() }
        DnsGuard(this).start() // «Blocca pubblicità» non deve lasciare il tablet senza internet
        File(filesDir, "foglio-rapido.png").delete() // il Foglio rapido è stato tolto: niente file rimasti
        announceAccentChanges()
        gameMeter = GameMeter(this, appScope)
        LockScreenWallpaper.start(this) // lo sfondo di NOVA anche sulla schermata di blocco

        profiler = DeviceProfiler(this)
        appScope.launch {
            // Profilo salvato, oppure prima lettura dell'hardware (senza benchmark).
            // Un benchmark misurato col vecchio metodo (troppo severo) viene scartato: va rifatto.
            val profile = (profiler.load() ?: profiler.readHardware()).withoutStaleBenchmark().also(profiler::save)
            _profile.value = profile
            if (profile.benchmark != null) graphicsController.configure(profile.startLevel, profile.ceiling)
        }
        appScope.launch {
            delay(HEALTHY_AFTER_MS)
            bootGuard.markHealthy()
            log.i(TAG, "avvio stabile")
        }
    }

    private fun setUpUpdates() {
        updateHistory = LocalHistory(File(filesDir, "updates"))
        // La configurazione scaricata vive su file: va riapplicata a ogni avvio.
        updateHistory.activeBundle()?.let { json ->
            (config.applyBundle(json) as? ApplyResult.Rejected)?.let {
                log.w(TAG, "pacchetto di configurazione salvato non valido, uso i default: ${it.reason}")
                updateHistory.setActive(null)
            }
        }
        val tokens = TokenStore(File(filesDir, "github-token.bin"))
        github = GitHubClient(BuildConfig.GITHUB_REPO, tokens::get)
        updater =
            Updater(
                context = this,
                versionCode = BuildConfig.VERSION_CODE,
                versionName = BuildConfig.VERSION_NAME,
                client = github,
                tokens = tokens,
                installer = ApkInstaller(this, shizuku),
                shizuku = shizuku,
                config =
                    object : ConfigTarget {
                        override fun currentVersion() = config.current.value.meta.version

                        override fun apply(json: String) = (config.applyBundle(json) as? ApplyResult.Rejected)?.reason

                        override fun reset() {
                            config.applyBundle("{}")
                        }
                    },
                history = updateHistory,
                log = log,
            )
        UpdateWorker.schedule(this)
        appScope.launch { updater.check(automatic = true) }
    }

    /** Volume giù tenuto premuto all'avvio, o richiesta dalle impostazioni. */
    fun enterSafeMode() {
        bootGuard.requestSafeMode()
        safeModeReason = SafeModeReason.REQUESTED
    }

    companion object {
        const val OCR_PROCESS = ":ocr"

        private const val TAG = "nova"
        private const val ONBOARDING_DONE = "done"

        /** Dopo questo tempo senza crash l'avvio è considerato riuscito. */
        private const val HEALTHY_AFTER_MS = 20_000L

        fun overridesFile(context: Context) = File(context.filesDir, "config-overrides.json")

        /** I default compilati sono i file di `config/` del repository, inclusi negli asset. */
        fun loadDefaults(context: Context) =
            with(context.assets) {
                fun read(path: String) = open(path).bufferedReader().use { it.readText() }
                val theme = if (isNightMode(context)) "theme/dark.json" else "theme/light.json"
                ConfigEngine.assemble(
                    read("meta.json"),
                    read("brand.json"),
                    read(theme),
                    read("modules.json"),
                    read("modes.json"),
                    read("debloat.json"),
                )
            }

        private fun isNightMode(context: Context) =
            (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}
