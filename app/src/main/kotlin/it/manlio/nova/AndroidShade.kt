package it.manlio.nova

import it.manlio.nova.core.system.ShizukuStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** La tendina di Android: NOVA la blocca perché dal gesto dall'alto parta solo la sua (SPEC §6.8). Senza Shizuku non si può. */
internal object AndroidShade {
    fun lock(
        app: NovaApp,
        locked: Boolean,
    ) {
        app.appScope.launch { set(app, locked) }
    }

    /** Le righe «Notifiche» e «Impostazioni rapide» di Android: la sbloccano e la aprono. Si riblocca al prossimo gesto. */
    fun open(
        app: NovaApp,
        show: () -> Unit,
    ) {
        app.appScope.launch {
            set(app, locked = false)
            withContext(Dispatchers.Main) { show() }
        }
    }

    private suspend fun set(
        app: NovaApp,
        locked: Boolean,
    ) {
        if (app.shizuku.status.value != ShizukuStatus.READY) return
        if (runCatching { app.systemChanges.setShadeLock(locked).ok }.getOrDefault(false)) app.shadeLocked = locked
    }
}
