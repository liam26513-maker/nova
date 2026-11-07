package it.manlio.nova

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.widget.Toast
import androidx.core.content.ContextCompat

/** Dice quando avvisare: una volta sola a [HOT_C], e di nuovo solo dopo essere scesi sotto [COOL_C]. */
internal class HeatAlarm {
    private var warned = false

    fun update(celsius: Float): Boolean {
        if (celsius < COOL_C) warned = false
        val warn = celsius >= HOT_C && !warned
        if (warn) warned = true
        return warn
    }

    private companion object {
        const val HOT_C = 45f
        const val COOL_C = 42f
    }
}

/** L'avviso di temperatura alta in Gaming (SPEC §13.6). Nessun polling: ascolta l'annuncio della batteria, solo durante la modalità. */
internal class GameHeatWatch(private val context: Context) {
    private var alarm = HeatAlarm()
    private var listening = false
    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                val tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                if (alarm.update(tenths / TENTHS)) Toast.makeText(context, R.string.gaming_hot, Toast.LENGTH_LONG).show()
            }
        }

    fun set(on: Boolean) {
        if (on == listening) return
        listening = on
        if (on) {
            alarm = HeatAlarm()
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    private companion object {
        const val TENTHS = 10f
    }
}
