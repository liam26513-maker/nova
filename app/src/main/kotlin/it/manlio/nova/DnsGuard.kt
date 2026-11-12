package it.manlio.nova

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import it.manlio.nova.core.system.ShizukuStatus
import it.manlio.nova.core.system.SystemTools
import kotlinx.coroutines.launch
import java.io.File

/** Le regole del guardiano del DNS, pure: si provano senza Android. */
internal object DnsGuardRules {
    /**
     * Il DNS di «Blocca pubblicità» è rotto su questa rete: Android usa AdGuard ([privateDnsServer]), la rete ha internet
     * ma Android non riesce a convalidarla perché il DNS privato non risponde. È il caso del messaggio
     * «Impossibile accedere al server DNS privato».
     */
    fun broken(
        privateDnsServer: String?,
        hasInternet: Boolean,
        validated: Boolean,
    ): Boolean = privateDnsServer == SystemTools.AD_BLOCK_HOST && hasInternet && !validated
}

/**
 * Se «Blocca pubblicità» lascia il tablet senza internet (la rete non raggiunge AdGuard), lo sospende da solo dopo
 * [WAIT_MS] e lo rimette alla prossima rete. Un solo tentativo per rete, quindi niente accendi-spegni continuo. Ascolta solo
 * la rete predefinita di Android (callback, niente controlli a intervalli). Lo stato «sospeso» sta in un file.
 */
internal class DnsGuard(private val app: NovaApp) {
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val flag = File(app.filesDir, "dns-sospeso")
    private var lastNetwork: Network? = null
    private var pending: Runnable? = null

    fun start() {
        runCatching { connectivity.registerDefaultNetworkCallback(callback, main) }
    }

    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (network != lastNetwork && flag.exists()) resume()
                lastNetwork = network
            }

            override fun onCapabilitiesChanged(
                network: Network,
                caps: NetworkCapabilities,
            ) = check(network, caps, connectivity.getLinkProperties(network))

            override fun onLinkPropertiesChanged(
                network: Network,
                lp: LinkProperties,
            ) = check(network, connectivity.getNetworkCapabilities(network), lp)
        }

    private fun check(
        network: Network,
        caps: NetworkCapabilities?,
        lp: LinkProperties?,
    ) {
        if (caps == null || lp == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return
        val broken =
            DnsGuardRules.broken(
                lp.privateDnsServerName,
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            )
        pending?.let(main::removeCallbacks)
        pending = null
        if (!broken) return
        // Android ci mette qualche secondo a convalidare una rete nuova: si aspetta prima di decidere.
        pending = Runnable { if (connectivity.activeNetwork == network) suspend() }.also { main.postDelayed(it, WAIT_MS) }
    }

    private fun suspend() {
        if (app.shizuku.status.value != ShizukuStatus.READY) return
        app.appScope.launch {
            if (app.systemTools.setAdBlockDns(false)) {
                flag.writeText("1")
                app.log.w(TAG, "DNS di AdGuard non raggiungibile: Blocca pubblicità sospeso")
                main.post { Toast.makeText(app, R.string.dns_guard_paused, Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun resume() {
        if (app.shizuku.status.value != ShizukuStatus.READY) return
        app.appScope.launch {
            if (app.systemTools.setAdBlockDns(true)) {
                flag.delete()
                app.log.i(TAG, "rete nuova: Blocca pubblicità rimesso")
            }
        }
    }

    companion object {
        /** L'utente sceglie a mano: la sospensione automatica si dimentica, così alla prossima rete non lo riaccende. */
        fun forgetPause(app: NovaApp) {
            File(app.filesDir, "dns-sospeso").delete()
        }

        private const val WAIT_MS = 20_000L
        private const val TAG = "dns"
    }
}
