package it.manlio.nova

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import it.manlio.nova.feature.home.StandbyInfo
import it.manlio.nova.feature.home.StandbyRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var event by mutableStateOf<String?>(null)
    private var request: Job? = null
    private val handler = Handler(Looper.getMainLooper())
    private val idle = Runnable { if (wantedAutomatically()) show() }
    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                val was = charging
                readBattery(intent)
                // Android manda questo annuncio di continuo in carica (livello, temperatura): solo attaccare o staccare riavvia il conto.
                if (charging != was) arm()
            }
        }

    /** Da mostrare sopra la Home, o null. Legge stati di Compose: si ricompone da solo quando cambiano. */
    fun info(): StandbyInfo? =
        if (shown) {
            val night = StandbyRules.isNight(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
            StandbyInfo(battery, charging, night, alarm, event)
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
        scope.launch { event = withContext(Dispatchers.IO) { nextEvent() } }
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

    /** Il prossimo evento con un orario nelle 24 ore, se l'utente lo vuole e ha dato il permesso. Resta sul tablet. */
    private fun nextEvent(): String? {
        val granted = activity.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (!app.homePrefs.prefs.value.standbyCalendar || !granted) return null
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uri, now)
        ContentUris.appendId(uri, now + DAY_MS)
        val columns = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN)
        val timed = "${CalendarContract.Instances.ALL_DAY} = 0 AND ${CalendarContract.Instances.BEGIN} >= $now"
        return runCatching {
            activity.contentResolver.query(uri.build(), columns, timed, null, "${CalendarContract.Instances.BEGIN} ASC")?.use {
                if (it.moveToFirst()) {
                    "${SimpleDateFormat(
                        "HH:mm",
                        Locale.getDefault(),
                    ).format(Date(it.getLong(1)))} · ${it.getString(0)}"
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private companion object {
        const val FULL = 100
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
