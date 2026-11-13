package it.manlio.nova

import android.app.ActivityOptions
import android.app.Notification
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
    private val _all = MutableStateFlow<List<NotificationPreview>>(emptyList())
    override val all: StateFlow<List<NotificationPreview>> = _all.asStateFlow()
    private var service: NotificationListenerService? = null
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    override fun isEnabled(): Boolean = isListenerEnabled(appContext)

    override fun dismiss(key: String) {
        runCatching { service?.cancelNotification(key) }
    }

    /** Usa l'azione di risposta dell'app (RemoteInput), come fa la tendina di Android: il testo va solo all'app della notifica. */
    override fun reply(
        key: String,
        text: String,
    ): Boolean =
        runCatching {
            val sbn = service?.activeNotifications?.firstOrNull { it.key == key } ?: return false
            val action = replyAction(sbn.notification) ?: return false
            val input = action.remoteInputs.first()
            val intent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            RemoteInput.addResultsToIntent(action.remoteInputs, intent, Bundle().apply { putCharSequence(input.resultKey, text) })
            action.actionIntent.send(appContext, 0, intent)
            true
        }.getOrDefault(false)

    override fun open(key: String) {
        runCatching {
            val sbn = service?.activeNotifications?.firstOrNull { it.key == key } ?: return
            val target = sbn.notification.contentIntent ?: return
            // Da Android 14 chi manda il PendingIntent deve permettere esplicitamente di aprire un'attività da dietro.
            val options =
                ActivityOptions.makeBasic().apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    }
                }
            target.send(appContext, 0, null, null, null, null, options.toBundle())
            if (sbn.notification.flags and Notification.FLAG_AUTO_CANCEL != 0) service?.cancelNotification(key)
        }
    }

    override fun askReply(preview: NotificationPreview) {
        val intent =
            Intent(appContext, ReplyActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(ReplyActivity.KEY, preview.key)
                .putExtra(ReplyActivity.TO, preview.title)
        runCatching { appContext.startActivity(intent) }
    }

    internal fun attach(s: NotificationListenerService?) {
        service = s
        if (s == null) {
            _previews.value = emptyMap()
            _all.value = emptyList()
        } else {
            refresh(s)
        }
    }

    internal fun refresh(s: NotificationListenerService) {
        val raw = runCatching { s.activeNotifications.orEmpty().map(::raw) }.getOrDefault(emptyList())
        _previews.value = NotificationIndex.build(raw)
        _all.value = NotificationIndex.list(raw)
    }

    /** L'utente ha dato a NOVA l'accesso alle notifiche (Impostazioni di Android → Accesso alle notifiche)? */
    fun isListenerEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        val me = ComponentName(context, NovaNotificationListener::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }
}

private fun replyAction(n: Notification): Notification.Action? =
    n.actions.orEmpty().firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }

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
        canReply = replyAction(n) != null,
    )
}

/** Servizio di sistema che riceve le notifiche, attivo solo se l'utente dà l'accesso. Non fa altro che tenere aggiornata la memoria. */
class NovaNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() = NotificationHub.attach(this)

    override fun onListenerDisconnected() = NotificationHub.attach(null)

    override fun onNotificationPosted(sbn: StatusBarNotification) = NotificationHub.refresh(this)

    override fun onNotificationRemoved(sbn: StatusBarNotification) = NotificationHub.refresh(this)
}
