package it.manlio.nova

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import it.manlio.nova.core.modes.Modes
import java.time.DayOfWeek
import java.time.ZonedDateTime

/**
 * L'orario di studio (SPEC §8.7): dal lunedì al venerdì, all'ora scelta, la Studio si accende da sola se nessuna modalità è accesa.
 * Spento di serie. La sveglia non è esatta: Android può ritardarla di qualche minuto.
 */
internal object StudySchedule {
    private const val PREFS = "studio-orario"
    private const val HOUR = "hour"
    const val OFF = -1

    @Suppress("MagicNumber") // le ore tra cui scegliere
    val CHOICES = listOf(OFF, 15, 16, 17, 18, 19, 20)

    fun hour(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(HOUR, OFF)

    fun setHour(
        context: Context,
        hour: Int,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(HOUR, hour).apply()
        arm(context)
    }

    /** Il prossimo inizio dopo [now]: oggi se l'ora non è passata, altrimenti il prossimo giorno feriale. */
    fun next(
        now: ZonedDateTime,
        hour: Int,
    ): ZonedDateTime {
        var day = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
        if (!day.isAfter(now)) day = day.plusDays(1)
        while (day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY) day = day.plusDays(1)
        return day
    }

    /** Mette (o toglie) la prossima sveglia. Si richiama all'avvio di NOVA e a ogni sveglia. */
    fun arm(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val wake = PendingIntent.getBroadcast(context, 1, Intent(context, StudyStartAlarm::class.java), flags)
        val hour = hour(context)
        if (hour == OFF) return alarms.cancel(wake)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next(ZonedDateTime.now(), hour).toInstant().toEpochMilli(), wake)
    }
}

/** All'ora di studio: accende la Studio se nessuna modalità è accesa, poi mette la sveglia del giorno dopo. */
class StudyStartAlarm : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val app = context.applicationContext as NovaApp
        if (app.safeModeReason == null && app.modes.engine.active.value == null) app.modes.toggle(Modes.STUDY) {}
        StudySchedule.arm(context)
    }
}
