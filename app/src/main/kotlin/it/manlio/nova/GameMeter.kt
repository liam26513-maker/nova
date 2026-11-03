package it.manlio.nova

import android.app.ActivityManager
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.widget.Toast
import it.manlio.nova.core.system.FrameCollector
import it.manlio.nova.core.system.FrameReport
import it.manlio.nova.core.system.LayerPicker
import it.manlio.nova.core.system.ProtectedPackages
import it.manlio.nova.core.system.ShizukuStatus
import it.manlio.nova.core.system.SystemCommand
import it.manlio.nova.core.system.SystemCommand.TimeStats.Step
import it.manlio.nova.core.system.TimeStatsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Misura un gioco mentre ci si gioca (con Shizuku): ogni 2 secondi, per 2 minuti, legge i tempi dei fotogrammi del gioco in primo
 * piano (`dumpsys SurfaceFlinger --latency`, solo lettura) e ne ricava FPS, scatti, temperatura e memoria. Parte solo se lo chiede
 * l'utente e dura al massimo [DURATION_S] secondi: non c'è nessun controllo continuo. Il risultato è un testo che si può condividere
 * (con l'app Claude, per esempio) per capire cosa fa scattare un gioco con dati veri invece di ipotesi.
 */
class GameMeter(
    private val app: NovaApp,
    private val scope: CoroutineScope,
) {
    private val main = Handler(Looper.getMainLooper())
    private val file = File(app.filesDir, "diagnosi-gaming.txt")
    private var job: Job? = null

    /** Il testo dell'ultima misura (anche dopo un riavvio), o null. */
    var lastText: String? = file.takeIf { it.exists() }?.readText()
        private set

    /** Riga breve dell'ultima misura, per la tendina («41 FPS · 12% lenti»). */
    var lastSummary: String? = lastText?.lineSequence()?.firstOrNull { it.startsWith(SUMMARY_PREFIX) }?.removePrefix(SUMMARY_PREFIX)
        private set

    val running: Boolean get() = job?.isActive == true

    /** Avvia la misura. Serve Shizuku e un gioco in primo piano (lo riconosce il servizio di accessibilità). */
    fun start() {
        if (running) return
        if (app.shizuku.status.value != ShizukuStatus.READY) return say(app.getString(R.string.meter_needs_shizuku))
        say(app.getString(R.string.meter_started, DURATION_S / SECONDS_PER_MINUTE))
        job = scope.launch { measure() }
    }

    private suspend fun measure() {
        val collectors = mutableMapOf<String, FrameCollector>()
        val layers = mutableMapOf<String, String>()
        val why = Why()
        val seen = mutableMapOf<String, Int>()
        app.shizuku.run(SystemCommand.TimeStats(Step.ENABLE))
        app.shizuku.run(SystemCommand.TimeStats(Step.CLEAR))
        repeat(DURATION_S / STEP_S) {
            delay(STEP_S * MS_PER_S)
            val pkg = app.foreground.value?.takeIf(app::isGamePackage)
            if (pkg == null) {
                why.noGame++
                return@repeat
            }
            seen.merge(pkg, 1, Int::plus)
            val layer = layers[pkg] ?: findLayer(pkg, why)?.also { layers[pkg] = it }
            if (layer != null) sample(pkg, layer, collectors, why)
        }
        val dump = app.shizuku.run(SystemCommand.TimeStats(Step.DUMP)).output
        app.shizuku.run(SystemCommand.TimeStats(Step.DISABLE))
        val best = collectors.entries.maxByOrNull { it.value.report()?.frames ?: 0 }
        val game = best?.key ?: seen.maxByOrNull { it.value }?.key
        // --latency non dà più nulla per i livelli BLAST di Android 12+: si ripiega sulle statistiche di SurfaceFlinger.
        val report = best?.value?.report() ?: game?.let { TimeStatsParser.parse(dump, it) }
        if (game == null || report == null) return fail(why, game ?: app.lastGame, dump)
        save(text(game, report), summary(report))
        say(app.getString(R.string.meter_done, lastSummary))
    }

    private suspend fun sample(
        pkg: String,
        layer: String,
        collectors: MutableMap<String, FrameCollector>,
        why: Why,
    ) {
        val out = app.shizuku.run(SystemCommand.SurfaceLatency(layer))
        if (why.latency.isEmpty()) why.latency = out.output.take(DIAG_CHARS)
        if (!out.ok) why.failed = out.output.take(DIAG_CHARS)
        if (out.ok) why.read += collectors.getOrPut(pkg) { FrameCollector() }.add(out.output)
    }

    private fun save(
        text: String,
        short: String?,
    ) {
        file.writeText(text)
        lastText = text
        lastSummary = short
    }

    /** Anche quando non si legge niente il testo si può condividere: dice cosa è andato storto, così lo si corregge. */
    private suspend fun fail(
        why: Why,
        game: String?,
        dump: String,
    ) {
        why.game = game
        why.timeline = app.shizuku.run(SystemCommand.FrameTimeline).output
        val text =
            buildString {
                appendLine(SUMMARY_PREFIX + app.getString(R.string.meter_no_data))
                appendLine("Misura non riuscita. Letture di fotogrammi: ${why.read}")
                appendLine("Controlli senza un gioco davanti: ${why.noGame}")
                appendLine("Gioco davanti all'ultimo controllo: ${app.foreground.value ?: "nessuno"}")
                appendLine("Livello grafico trovato: ${why.layer ?: "nessuno"}")
                appendLine("Elenco livelli (parte): ${why.layers}")
                appendLine("Errore del comando: ${why.failed ?: "nessuno"}")
                appendLine("Risposta di --latency (inizio): ${why.latency.replace('\n', '|')}")
                val game = why.game.orEmpty()
                appendLine("Gioco misurato: $game")
                appendLine("Statistiche: ${dump.length} caratteri; punto del gioco: ${around(dump, game)}")
                appendLine("Statistiche (primo livello): ${around(dump, "layerName")}")
                appendLine("Linea temporale: ${around(why.timeline, game)} || inizio: ${why.timeline.take(DIAG_DUMP).replace('\n', '|')}")
                appendLine("Dispositivo: ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE}")
            }
        save(text, app.getString(R.string.meter_no_data))
        say(app.getString(R.string.meter_none))
    }

    private fun around(
        text: String,
        key: String,
    ): String {
        val at = if (key.isEmpty()) -1 else text.indexOf(key)
        if (at < 0) return "(non trovato)"
        return text.substring(maxOf(0, at - DIAG_DUMP / 2), minOf(text.length, at + DIAG_DUMP)).replace('\n', '|')
    }

    private class Why {
        var game: String? = null
        var timeline: String = ""
        var noGame = 0
        var read = 0
        var layer: String? = null
        var layers: String = ""
        var failed: String? = null
        var latency: String = ""
    }

    private suspend fun findLayer(
        pkg: String,
        why: Why,
    ): String? {
        val list = app.shizuku.run(SystemCommand.ListSurfaceLayers)
        if (!list.ok) why.failed = list.output.take(DIAG_CHARS)
        why.layers = list.output.lineSequence().filter { pkg.substringBefore('.') in it }.take(DIAG_LINES).joinToString(" | ")
        return if (list.ok) LayerPicker.pick(list.output, pkg)?.also { why.layer = it } else null
    }

    private fun summary(r: FrameReport) = format("%.0f FPS · 1%% basso %.0f · %.0f%% lenti", r.fps, r.lowFps, r.slowPercent)

    private fun format(
        pattern: String,
        vararg args: Any,
    ): String = String.format(Locale.ROOT, pattern, *args)

    /** Il testo da condividere: i numeri e il contesto (temperatura, memoria, stato termico), niente di personale. */
    private fun text(
        pkg: String,
        r: FrameReport,
    ): String {
        val battery = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = (battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / TENTHS
        val memory = ActivityManager.MemoryInfo().also { app.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val thermal = app.getSystemService(PowerManager::class.java).currentThermalStatus
        return buildString {
            appendLine(SUMMARY_PREFIX + summary(r))
            appendLine("Misura del gioco: $pkg")
            appendLine(format("Durata: %.0f s, %d fotogrammi, %d stop ignorati (caricamenti/menu)", r.seconds, r.frames, r.pauses))
            appendLine(format("FPS medi: %.1f, peggiore 1%%: %.1f FPS, fotogrammi lenti: %.1f%%", r.fps, r.lowFps, r.slowPercent))
            appendLine(format("Fotogramma più lungo: %.0f ms", r.worstMs))
            appendLine(format("Batteria: %.1f °C, memoria libera: %d MB", temp, memory.availMem / MB))
            appendLine("Stato termico: $thermal (0 = nessuno)")
            appendLine("Dispositivo: ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE}")
            appendLine("Modalità Gaming: ${if (app.modes.engine.active.value != null) "attiva" else "spenta"}")
        }
    }

    private fun say(text: String) {
        main.post { Toast.makeText(app, text, Toast.LENGTH_LONG).show() }
    }

    companion object {
        const val DURATION_S = 120
        private const val STEP_S = 2
        private const val DIAG_CHARS = 200
        private const val DIAG_LINES = 6
        private const val DIAG_DUMP = 700
        private const val MS_PER_S = 1000L
        private const val SECONDS_PER_MINUTE = 60
        private const val TENTHS = 10f
        private const val MB = 1024 * 1024
        private const val SUMMARY_PREFIX = "Riepilogo: "
    }
}

/** Un'app che può essere un gioco in primo piano: non NOVA, non la Home, non il sistema. */
internal fun NovaApp.isGamePackage(pkg: String): Boolean {
    val home =
        packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
    return !ProtectedPackages.isProtected(pkg) && pkg != home
}

/** Il servizio di accessibilità dice quale app è comparsa davanti: serve a sapere quale gioco misurare o chiudere. */
internal fun NovaApp.noteForeground(pkg: String) {
    if (isGamePackage(pkg)) {
        foreground.value = pkg
        lastGame = pkg
    } else {
        foreground.value = null
    }
}
