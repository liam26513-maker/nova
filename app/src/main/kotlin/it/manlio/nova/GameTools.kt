package it.manlio.nova

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.widget.Toast
import it.manlio.nova.core.system.ShizukuStatus
import it.manlio.nova.core.system.SystemChange
import it.manlio.nova.core.system.SystemCommand
import it.manlio.nova.feature.home.MoreItem
import kotlinx.coroutines.launch

/**
 * Gli strumenti per i giochi nella tendina (Altro): misurare un gioco, condividere la misura, cambiarne la risoluzione, chiuderlo.
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
                    // Col servizio acceso parte da volume su, a partita iniziata; senza, l'unico modo è partire subito.
                    if (NovaControlService.isEnabled(context)) meter.arm() else meter.start()
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
                    MoreItem(context.getString(R.string.more_game_scale, label(game)), scaleText(scale(game))) { fix ->
                        changeScale(game, fix)
                    },
                )
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

    /** La risoluzione scelta per [game], dal registro delle modifiche; null = normale. */
    fun scale(game: String): Int? =
        app.systemChanges.entries().filterIsInstance<SystemChange.GameScale>().firstOrNull { it.packageName == game }?.applied

    private fun scaleText(percent: Int?) = context.getString(R.string.game_scale_percent, percent ?: FULL_SCALE)

    /** Scende di un passo; se Android rifiuta (per esempio l'app non è un gioco) la riga torna a dire com'è davvero. */
    private fun changeScale(
        game: String,
        fix: (String) -> Unit,
        done: () -> Unit = {},
    ): String? {
        val before = scale(game)
        if (app.shizuku.status.value != ShizukuStatus.READY) {
            say(R.string.meter_needs_shizuku)
            return null
        }
        val next = SystemCommand.nextGameScale(before)
        app.appScope.launch {
            val ok = app.systemChanges.setGameScale(game, next).ok
            if (ok) GameFps(context).forget(game) else fix(scaleText(before))
            say(if (ok) R.string.game_scale_done else R.string.game_scale_failed)
            done()
        }
        return scaleText(next)
    }

    /** Dalla libreria dei giochi: un passo in giù, poi [done] rilegge la riga. */
    fun stepScale(
        game: String,
        done: () -> Unit,
    ) {
        changeScale(game, {}, done)
    }

    fun close(game: String) {
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

    private companion object {
        const val FULL_SCALE = 100
    }
}
