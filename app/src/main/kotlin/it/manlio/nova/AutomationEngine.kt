package it.manlio.nova

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import it.manlio.nova.core.modes.Modes
import it.manlio.nova.core.system.QuickToggle
import java.io.File
import java.time.ZonedDateTime

/** Scatta solo ai cambi di stato, mai sullo stato iniziale: accendendo una regola non parte da sola. Puro. */
internal class Edge {
    private var last: Boolean? = null

    /** @return vero se lo stato è appena cambiato in [now]. */
    fun update(now: Boolean): Boolean = (last != null && last != now).also { last = now }
}

/**
 * Il motore delle automazioni (SPEC §12.4). Ascolta solo gli eventi usati dalle regole accese e smette quando non servono più:
 * senza regole non c'è niente in ascolto. Gli orari usano una sola sveglia non esatta, che si rimette da sola.
 */
@Suppress("TooManyFunctions") // un avvio e uno stop per ogni evento ascoltato: dividerli sparpaglierebbe lo stato
internal class AutomationEngine(private val app: NovaApp) {
    private val store = RuleStore(File(app.filesDir, "automazioni.txt"))
    private val main = Handler(Looper.getMainLooper())
    private val quick by lazy { QuickToggles(app, app, app.appScope) }
    private var rules = store.all()
    private val charger = Edge()
    private val headphones = Edge()
    private val wifi = Edge()
    private var batteryWasAbove: Boolean? = null
    private var listening = emptySet<When>()

    fun rules(): List<Rule> = rules

    /** Salva e riascolta solo ciò che serve alle regole accese. */
    fun update(newRules: List<Rule>) {
        rules = newRules
        store.save(newRules)
        listen()
    }

    fun start() = listen()

    private fun active(trigger: When) = rules.filter { it.enabled && it.trigger == trigger }

    private fun listen() {
        val wanted = rules.filter { it.enabled }.map { it.trigger }.toSet()
        val power = setOf(When.CHARGER_ON, When.CHARGER_OFF, When.BATTERY_BELOW)
        toggle(wanted.any { it in power }, listening.any { it in power }, ::startPower, ::stopPower)
        val audio = setOf(When.HEADPHONES_ON, When.HEADPHONES_OFF)
        toggle(wanted.any { it in audio }, listening.any { it in audio }, ::startAudio, ::stopAudio)
        val net = setOf(When.WIFI_ON, When.WIFI_OFF)
        toggle(wanted.any { it in net }, listening.any { it in net }, ::startWifi, ::stopWifi)
        listening = wanted
        armTime()
    }

    private fun toggle(
        want: Boolean,
        had: Boolean,
        start: () -> Unit,
        stop: () -> Unit,
    ) {
        if (want && !had) {
            start()
        } else if (!want && had) {
            stop()
        }
    }

    // ---- Carica e batteria: un solo annuncio di sistema ----

    private val powerReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                if (charger.update(plugged)) fire(if (plugged) When.CHARGER_ON else When.CHARGER_OFF)
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                if (level >= 0) battery(level)
            }
        }

    private fun battery(level: Int) {
        active(When.BATTERY_BELOW).forEach { rule ->
            val limit = rule.whenArg.toIntOrNull() ?: return@forEach
            if (batteryWasAbove == true && level < limit) run(rule)
        }
        val limits = active(When.BATTERY_BELOW).mapNotNull { it.whenArg.toIntOrNull() }
        batteryWasAbove = limits.isEmpty() || limits.any { level >= it }
    }

    private fun startPower() {
        ContextCompat.registerReceiver(app, powerReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun stopPower() = runCatching { app.unregisterReceiver(powerReceiver) }.let { }

    // ---- Cuffie (con filo, USB o Bluetooth) ----

    private val audio by lazy { app.getSystemService(AudioManager::class.java) }
    private val audioCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = checkHeadphones()

            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = checkHeadphones()
        }

    private fun checkHeadphones() {
        val on = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in HEADPHONES }
        if (headphones.update(on)) fire(if (on) When.HEADPHONES_ON else When.HEADPHONES_OFF)
    }

    private fun startAudio() = audio.registerAudioDeviceCallback(audioCallback, main)

    private fun stopAudio() = audio.unregisterAudioDeviceCallback(audioCallback)

    // ---- Wi-Fi ----

    private val connectivity by lazy { app.getSystemService(ConnectivityManager::class.java) }
    private val wifiCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = main.post { if (wifi.update(true)) fire(When.WIFI_ON) }.let { }

            override fun onLost(network: Network) = main.post { if (wifi.update(false)) fire(When.WIFI_OFF) }.let { }
        }

    private fun startWifi() {
        // Lo stato di partenza: senza, la prima notifica di Android sembrerebbe un cambio.
        wifi.update(
            connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
        )
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        runCatching { connectivity.registerNetworkCallback(request, wifiCallback) }
    }

    private fun stopWifi() = runCatching { connectivity.unregisterNetworkCallback(wifiCallback) }.let { }

    // ---- Orari ----

    private fun armTime() {
        val alarms = app.getSystemService(AlarmManager::class.java)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val wake = PendingIntent.getBroadcast(app, 2, Intent(app, AutomationAlarm::class.java), flags)
        val hours = active(When.TIME).mapNotNull { it.whenArg.toIntOrNull() }
        val next = hours.minOfOrNull { nextAt(ZonedDateTime.now(), it).toInstant().toEpochMilli() }
        if (next == null) alarms.cancel(wake) else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, wake)
    }

    /** Dalla sveglia: le regole di quest'ora, poi la sveglia successiva. */
    fun onAlarm() {
        val hour = ZonedDateTime.now().hour
        active(When.TIME).filter { it.whenArg.toIntOrNull() == hour }.forEach(::run)
        armTime()
    }

    // ---- Azioni ----

    private fun fire(trigger: When) = active(trigger).forEach(::run)

    private fun run(rule: Rule) {
        main.post {
            when (rule.action) {
                Then.OPEN_APP -> openApp(rule.thenArg)
                Then.MODE_ON ->
                    Modes.all.firstOrNull { it.id == rule.thenArg }?.let {
                        if (app.modes.engine.active.value == null) {
                            app.modes.toggle(
                                it,
                            ) {}
                        }
                    }
                Then.MODE_OFF ->
                    app.modes.engine.active.value?.modeId?.let {
                            id ->
                        Modes.all.firstOrNull { it.id == id }
                    }?.let { app.modes.toggle(it) {} }
                Then.TOGGLE_ON, Then.TOGGLE_OFF -> toggleSetting(rule)
                Then.NOTIFY -> Toast.makeText(app, rule.thenArg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openApp(pkg: String) {
        val launch = app.packageManager.getLaunchIntentForPackage(pkg) ?: return
        runCatching { app.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun toggleSetting(rule: Rule) {
        val toggle = runCatching { QuickToggle.valueOf(rule.thenArg) }.getOrNull()?.takeIf { it in TOGGLES } ?: return
        if (quick.available) quick.set(toggle, rule.action == Then.TOGGLE_ON) {}
    }

    companion object {
        /** Gli interruttori che una regola può cambiare: quelli che non tolgono la connessione a NOVA o Shizuku. */
        val TOGGLES =
            listOf(QuickToggle.DND, QuickToggle.NIGHT_LIGHT, QuickToggle.BATTERY_SAVER, QuickToggle.DARK_THEME, QuickToggle.BLUETOOTH)
        private val HEADPHONES =
            setOf(
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
            )

        /** Oggi all'ora [hour] se non è ancora passata, altrimenti domani. */
        fun nextAt(
            now: ZonedDateTime,
            hour: Int,
        ): ZonedDateTime {
            val today = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
            return if (today.isAfter(now)) today else today.plusDays(1)
        }
    }
}

/** La sveglia delle regole a orario: sveglia NOVA e passa la mano al motore. */
class AutomationAlarm : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val app = context.applicationContext as NovaApp
        if (app.safeModeReason == null) app.automations.onAlarm()
    }
}
