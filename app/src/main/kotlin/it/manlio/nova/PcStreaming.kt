package it.manlio.nova

import android.content.Context
import android.content.Intent
import it.manlio.nova.feature.home.MoreItem

/**
 * «Gioca dal PC» (SPEC §11.4): apre Moonlight, che si collega a Sunshine sul PC (a casa o, con Tailscale, anche da fuori).
 * NOVA non rifà lo streaming: usa l'app aperta e gratuita. La voce c'è solo se Moonlight è installato.
 * shortcut: apre Moonlight sulla sua lista dei PC; l'avvio diretto di un gioco e l'accensione del PC arriveranno quando servono.
 */
internal object PcStreaming {
    const val MOONLIGHT = "com.limelight"

    fun launchIntent(context: Context): Intent? =
        context.packageManager.getLaunchIntentForPackage(MOONLIGHT)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

internal fun playFromPcItem(context: Context): MoreItem? {
    val launch = PcStreaming.launchIntent(context) ?: return null
    return MoreItem(context.getString(R.string.more_play_from_pc)) {
        runCatching { context.startActivity(launch) }
        null
    }
}
