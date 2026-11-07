package it.manlio.nova

import android.content.Context
import android.media.MediaScannerConnection
import android.widget.Toast
import it.manlio.nova.core.system.SystemCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** La registrazione dello schermo dal pannello Gaming, con Shizuku: niente permessi nuovi né richieste di Android sopra il gioco. */
internal class GameRecorder(
    private val context: Context,
    private val app: NovaApp,
) {
    private val running = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = running.asStateFlow()

    fun toggle() = if (running.value) stop() else start()

    fun stop() {
        if (running.value) app.appScope.launch { app.shizuku.run(SystemCommand.ScreenRecordStop) }
    }

    private fun start() {
        running.value = true
        Toast.makeText(context, R.string.record_started, Toast.LENGTH_LONG).show()
        app.appScope.launch(Dispatchers.Main) {
            val record = SystemCommand.ScreenRecord(System.currentTimeMillis().toString())
            val ok = app.shizuku.run(record).ok
            running.value = false
            // Il file l'ha scritto la shell: senza avvisare Android la Galleria non lo vedrebbe.
            if (ok) MediaScannerConnection.scanFile(context, arrayOf(record.path), null, null)
            Toast.makeText(context, if (ok) R.string.record_done else R.string.record_failed, Toast.LENGTH_LONG).show()
        }
    }
}
