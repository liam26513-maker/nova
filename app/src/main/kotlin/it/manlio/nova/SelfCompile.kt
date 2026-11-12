package it.manlio.nova

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Process
import android.os.SystemClock
import it.manlio.nova.core.system.ShizukuStatus
import it.manlio.nova.core.system.SystemCommand
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Codice ottimizzato dopo ogni aggiornamento (SPEC §13): NOVA si installa fuori dal Play Store, quindi Android lascia il codice
 * nuovo in «verify» (interpretato) fino all'ottimizzazione notturna in carica, e tutto scorre peggio. Appena Shizuku risponde
 * facciamo compilare NOVA e la tastiera una volta per versione. Il codice compilato vale dal prossimo avvio del processo: per
 * non aspettare, NOVA si riavvia da sola alla prima volta che lo schermo si spegne (nessuno la sta usando), se non c'è una
 * modalità accesa.
 */
internal class SelfCompile(
    private val app: NovaApp,
) {
    private val prefs = app.getSharedPreferences("compilazione", Context.MODE_PRIVATE)

    fun start() {
        keyboardUpdates()
        if (prefs.getInt(DONE_FOR, 0) == BuildConfig.VERSION_CODE) return
        app.appScope.launch {
            app.shizuku.status.first { it == ShizukuStatus.READY }
            val ok =
                SystemCommand.CompileOwn.OWN_PACKAGES.map { pkg ->
                    runCatching { app.shizuku.run(SystemCommand.CompileOwn(pkg)).ok }.getOrDefault(false)
                }
            // Basta NOVA: la tastiera può non essere installata.
            if (!ok.first()) return@launch
            prefs.edit().putInt(DONE_FOR, BuildConfig.VERSION_CODE).apply()
            restartWhenScreenOff()
        }
    }

    /** NOVA aggiorna la tastiera dopo sé stessa: anche la tastiera nuova parte in «verify», quindi si compila appena installata. */
    private fun keyboardUpdates() {
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    if (intent.data?.schemeSpecificPart != KEYBOARD) return
                    app.appScope.launch { runCatching { app.shizuku.run(SystemCommand.CompileOwn(KEYBOARD)) } }
                }
            }
        app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED) // i messaggi dei pacchetti arrivano dal sistema
    }

    private fun restartWhenScreenOff() {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    if (app.modes.engine.active.value != null) return // una modalità accesa: si aspetta il prossimo spegnimento
                    // Appena avviata NOVA non ha ancora detto «sto bene» (BootGuard): un riavvio adesso conterebbe come avvio fallito.
                    if (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime() < MIN_UPTIME_MS) return
                    Process.killProcess(Process.myPid()) // Android riapre servizio e Home con il codice compilato
                }
            }
        app.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
    }

    private companion object {
        const val DONE_FOR = "compilata_per_versione"
        const val MIN_UPTIME_MS = 60_000L
        const val KEYBOARD = "it.manlio.nova.keyboard"
    }
}
