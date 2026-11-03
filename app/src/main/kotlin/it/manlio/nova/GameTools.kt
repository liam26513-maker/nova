package it.manlio.nova

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.widget.Toast
import it.manlio.nova.core.system.ShizukuStatus
import it.manlio.nova.core.system.SystemCommand
import it.manlio.nova.feature.home.MoreItem
import kotlinx.coroutines.launch

/**
 * Gli strumenti per i giochi nella tendina (Altro): misurare un gioco, condividere la misura, chiudere il gioco.
 * «Chiudi il gioco» è una delle uscite di emergenza della Gaming (SPEC §8.2, punto 4): `am force-stop` con Shizuku sull'ultimo gioco
 * visto; i pacchetti protetti (NOVA, sistema) il comando stesso li rifiuta.
 */
internal class GameTools(
    private val context: Context,
    private val app: NovaApp,
) {
    fun items(): List<MoreItem> =
        buildList {
            val meter = app.gameMeter
            val state = if (meter.running) context.getString(R.string.meter_running) else meter.lastSummary
            add(
                MoreItem(context.getString(R.string.more_meter), state) { _ ->
                    meter.start()
                    null
                },
            )
            add(
                MoreItem(context.getString(R.string.more_meter_share)) { _ ->
                    meter.lastText?.let(::share) ?: say(R.string.meter_nothing_yet)
                    null
                },
            )
            app.lastGame?.let { game ->
                add(
                    MoreItem(context.getString(R.string.more_close_game, label(game))) { _ ->
                        close(game)
                        null
                    },
                )
            }
        }

    private fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun close(game: String) {
        if (app.shizuku.status.value != ShizukuStatus.READY) return say(R.string.meter_needs_shizuku)
        app.appScope.launch {
            val ok = app.shizuku.run(SystemCommand.ForceStop(game)).ok
            if (ok) app.lastGame = null
            say(if (ok) R.string.game_closed else R.string.game_close_failed)
        }
    }

    private fun label(pkg: String): String =
        runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }
            .getOrElse { pkg }

    private fun say(id: Int) {
        Handler(context.mainLooper).post { Toast.makeText(context, id, Toast.LENGTH_LONG).show() }
    }
}
