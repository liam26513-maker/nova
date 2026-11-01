package it.manlio.nova

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import it.manlio.nova.feature.home.NotificationIndex
import it.manlio.nova.feature.home.NotificationPreview
import it.manlio.nova.feature.home.NotificationSource
import it.manlio.nova.feature.home.RawNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Le notifiche per la Home (SPEC §6.2). Il servizio di sistema [NovaNotificationListener] lo aggiorna; la Home lo legge.
 * Tutto resta in memoria: le notifiche non vengono salvate, registrate né inviate da nessuna parte (§13).
 */
object NotificationHub : NotificationSource {
    private val _previews = MutableStateFlow<Map<String, NotificationPreview>>(emptyMap())
    override val previews: StateFlow<Map<String, NotificationPreview>> = _previews.asStateFlow()
    private var service: NotificationListenerService? = null
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    override fun isEnabled(): Boolean = isListenerEnabled(appContext)

    override fun dismiss(key: String) {
        runCatching { service?.cancelNotification(key) }
    }

    internal fun attach(s: NotificationListenerService?) {
        service = s
        if (s == null) _previews.value = emptyMap() else refresh(s)
    }

    internal fun refresh(s: NotificationListenerService) {
        val raw = runCatching { s.activeNotifications.orEmpty().map(::raw) }.getOrDefault(emptyList())
        _previews.value = NotificationIndex.build(raw)
    }

    private fun raw(sbn: StatusBarNotification): RawNotification {
        val n = sbn.notification
        val extras = n.extras
        return RawNotification(
            key = sbn.key,
            pkg = sbn.packageName,
            title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            whenMs = sbn.postTime,
            ongoing = sbn.isOngoing,
            groupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isMedia = extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true || n.category == Notification.CATEGORY_TRANSPORT,
        )
    }

    /** L'utente ha dato a NOVA l'accesso alle notifiche (Impostazioni di Android → Accesso alle notifiche)? */
    fun isListenerEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        val me = ComponentName(context, NovaNotificationListener::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}

/** Servizio di sistema che riceve le notifiche, attivo solo se l'utente dà l'accesso. Non fa altro che tenere aggiornata la memoria. */
class NovaNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() = NotificationHub.attach(this)

    override fun onListenerDisconnected() = NotificationHub.attach(null)

    override fun onNotificationPosted(sbn: StatusBarNotification) = NotificationHub.refresh(this)

    override fun onNotificationRemoved(sbn: StatusBarNotification) = NotificationHub.refresh(this)
}
