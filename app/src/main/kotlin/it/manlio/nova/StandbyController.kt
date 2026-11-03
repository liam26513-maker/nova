package it.manlio.nova

import android.app.Activity
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import it.manlio.nova.feature.home.StandbyInfo
import it.manlio.nova.feature.home.StandbyRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * StandBy (SPEC §12.6) dentro la Home. Si apre a mano (tendina → Altro → StandBy) o, se acceso nelle impostazioni, da solo quando il
 * tablet è in carica, in orizzontale e fermo da [StandbyRules.IDLE_MS]. Mentre è aperto tiene lo schermo acceso e lo abbassa
 * (notte: quasi al minimo); all'uscita rimette tutto. Nessun polling: la batteria arriva da un annuncio di sistema, il conto
 * dell'inattività è un solo timer che parte solo se serve.
 */
class StandbyController(
    private val activity: Activity,
    private val app: NovaApp,
    private val scope: CoroutineScope,
) {
    var shown by mutableStateOf(false)
        private set
    private var battery by mutableIntStateOf(FULL)
    private var charging by mutableStateOf(false)
    private var alarm: String? = null
    private var request: Job? = null
    private val handler = Handler(Looper.getMainLooper())
    private val idle = Runnable { if (wantedAutomatically()) show() }
    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                readBattery(intent)
                arm()
            }
        }

    /** Da mostrare sopra la Home, o null. Legge stati di Compose: si ricompone da solo quando cambiano. */
    fun info(): StandbyInfo? =
        if (shown) {
            val night = StandbyRules.isNight(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
            StandbyInfo(battery, charging, night, alarm)
        } else {
            null
        }

    fun start() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ContextCompat.registerReceiver(activity, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)?.let(::readBattery)
        request = scope.launch { app.standbyRequest.collect { if (it) fromRequest() } }
        arm()
    }

    fun stop() {
        runCatching { activity.unregisterReceiver(receiver) }
        request?.cancel()
        handler.removeCallbacks(idle)
        hide()
    }

    /** A ogni tocco: il conto dell'inattività riparte. */
    fun touched() = arm()

    fun hide() {
        if (!shown) return
        shown = false
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setBrightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        arm()
    }

    private fun fromRequest() {
        app.standbyRequest.value = false
        show()
    }

    private fun show() {
        if (shown) return
        handler.removeCallbacks(idle)
        alarm = nextAlarm()
        shown = true
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setBrightness(StandbyRules.brightness(StandbyRules.isNight(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))))
    }

    private fun arm() {
        handler.removeCallbacks(idle)
        if (!shown && app.homePrefs.prefs.value.standbyAuto && charging) handler.postDelayed(idle, StandbyRules.IDLE_MS)
    }

    private fun wantedAutomatically(): Boolean {
        val landscape = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return StandbyRules.shouldStart(app.homePrefs.prefs.value.standbyAuto, charging, landscape)
    }

    private fun readBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, FULL)
        if (level >= 0 && scale > 0) battery = level * FULL / scale
        val status = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        charging = status != 0
    }

    private fun setBrightness(value: Float) {
        val attributes = activity.window.attributes
        attributes.screenBrightness = value
        activity.window.attributes = attributes
    }

    /** La prossima sveglia, se ce n'è una (non serve nessun permesso). */
    private fun nextAlarm(): String? =
        activity.getSystemService(AlarmManager::class.java).nextAlarmClock?.let {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.triggerTime))
        }

    private companion object {
        const val FULL = 100
    }
}
