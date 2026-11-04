package it.manlio.nova

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import it.manlio.nova.core.design.GraphicsLevel
import it.manlio.nova.core.system.Density
import it.manlio.nova.core.system.ShizukuStatus
import it.manlio.nova.core.system.TurboPlan
import it.manlio.nova.core.system.TurboResult
import it.manlio.nova.feature.home.Favorites
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Stato e azioni degli strumenti di sistema (SPEC §7) per le Impostazioni e per il centro di controllo.
 * Tutto passa da [NovaApp.systemTools], quindi dal registro reversibile; serve Shizuku attivo.
 * La densità dello schermo si prova per [DENSITY_TRIAL_S] secondi: se non la confermi, torna com'era.
 */
@Suppress("TooManyFunctions") // una funzione per ogni strumento della schermata Prestazioni
class PerformanceUi(
    private val app: NovaApp,
    private val scope: CoroutineScope,
) {
    var ready by mutableStateOf(false)
        private set
    var animations by mutableStateOf<String?>(null)
        private set
    var adBlock by mutableStateOf(false)
        private set
    var density by mutableStateOf<Density?>(null)
        private set
    var frozen by mutableStateOf<List<String>>(emptyList())
        private set

    /** Secondi che restano per confermare la nuova densità; 0 = niente da confermare. */
    var trialLeft by mutableIntStateOf(0)
        private set
    var message by mutableStateOf<Int?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    private var trial: Job? = null
    private var turboRestore: Job? = null

    fun dismissMessage() {
        message = null
    }

    /** Rilegge lo stato di sistema (si chiama quando Shizuku cambia o si apre la schermata). */
    fun refresh() {
        ready = app.shizuku.status.value == ShizukuStatus.READY
        frozen = app.systemTools.frozen()
        if (!ready) return
        scope.launch {
            animations = app.systemTools.animationScale()
            adBlock = app.systemTools.adBlockDnsOn()
            density = app.systemTools.density()
        }
    }

    fun chooseAnimations(scale: String) = act { app.systemTools.setAnimationScale(scale).also { if (it) animations = scale } }

    fun chooseAdBlock(on: Boolean) = act { app.systemTools.setAdBlockDns(on).also { if (it) adBlock = on } }

    fun freeze(pkg: String) =
        act {
            app.systemTools.freeze(pkg).also { frozen = app.systemTools.frozen() }
        }

    fun unfreeze(pkg: String) =
        act {
            app.systemTools.unfreeze(pkg).also { frozen = app.systemTools.frozen() }
        }

    /** Prova una nuova densità: [DENSITY_TRIAL_S] secondi per confermarla, poi torna da sola come prima. */
    fun tryDensity(dpi: Int) {
        trial?.cancel()
        scope.launch {
            if (!app.systemTools.setDensity(dpi)) {
                message = R.string.perf_failed
                return@launch
            }
            density = app.systemTools.density()
            trial =
                launch {
                    for (left in DENSITY_TRIAL_S downTo 1) {
                        trialLeft = left
                        delay(MS_PER_SECOND)
                    }
                    revertDensity()
                }
        }
    }

    fun keepDensity() {
        trial?.cancel()
        trialLeft = 0
        app.systemTools.confirmDensity()
    }

    fun revertDensity() {
        trial?.cancel()
        trialLeft = 0
        scope.launch {
            app.systemTools.revertUnconfirmedDensity()
            density = app.systemTools.density()
        }
    }

    /** Turbo: ferma le app in secondo piano che non servono e mette il livello grafico su Prestazioni per [TURBO_MINUTES] minuti. */
    fun turbo() {
        if (!ready || busy) return
        busy = true
        scope.launch {
            val result = runTurbo()
            message = if (result == null) R.string.perf_failed else R.string.perf_turbo_done
            turboCount = result?.stopped ?: 0
            busy = false
        }
    }

    var turboCount by mutableIntStateOf(0)
        private set

    /** Cosa fa la Modalità Gaming, voce per voce (Impostazioni → Modalità Gaming). */
    val modeOptions: ModeOptions get() = app.modes.options
    val modes: ModeController get() = app.modes

    /** [withGraphics] falso: chiude solo le app (lo usa una modalità, che gestisce da sola la grafica e il suo ripristino). */
    suspend fun runTurbo(withGraphics: Boolean = true): TurboResult? {
        if (app.shizuku.status.value != ShizukuStatus.READY) return null
        val result = app.systemTools.turbo(TurboPlan.candidates(launchable(), keep()))
        if (withGraphics) startTurboGraphics()
        return result
    }

    private fun startTurboGraphics() {
        val state = app.graphics.value
        val c = app.graphicsController
        turboRestore?.cancel()
        c.setManual(GraphicsLevel.PERFORMANCE)
        // shortcut: se l'app viene chiusa prima, il livello resta su Prestazioni; si cambia dalle Impostazioni o dal centro di controllo.
        turboRestore =
            scope.launch {
                delay(TURBO_MINUTES * MS_PER_MINUTE)
                if (state.automatic) c.setAutomatic() else c.setManual(state.level)
            }
    }

    private fun launchable(): List<String> {
        val pm = app.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).map { it.activityInfo.packageName }
    }

    /** Da non fermare: i preferiti (anche nel dock), la musica in corso, la tastiera in uso, la Home predefinita. */
    private fun keep(): Set<String> {
        val favorites = Favorites(File(app.filesDir, "home-favorites.txt")).keys.value.map { it.substringBefore('/') }
        val ime = Settings.Secure.getString(app.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')
        val home =
            app.packageManager
                .resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo
                ?.packageName
        return (favorites + listOfNotNull(ime, home, app.nowPlayingPackage)).toSet()
    }

    private fun act(block: suspend () -> Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            val ok = runCatching { block() }.getOrDefault(false)
            if (!ok) message = R.string.perf_failed
            busy = false
        }
    }

    companion object {
        const val DENSITY_TRIAL_S = 15
        const val TURBO_MINUTES = 10
        private const val MS_PER_SECOND = 1000L
        private const val MS_PER_MINUTE = 60_000L
    }
}
