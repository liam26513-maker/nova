package it.manlio.nova.core.telemetry

/** Perché NOVA si avvia in modalità provvisoria (SPEC §14.2). */
enum class SafeModeReason {
    /** 3 avvii consecutivi senza arrivare a uno stato stabile. */
    REPEATED_START_CRASHES,

    /** 2 crash entro 60 secondi da un aggiornamento. */
    CRASHES_AFTER_UPDATE,

    /** Richiesta dall'utente (volume giù tenuto premuto all'avvio, o dalle impostazioni). */
    REQUESTED,
}

/**
 * Decide se avviare NOVA normalmente o in modalità provvisoria.
 *
 * Ciclo di vita:
 * - [onLaunch] all'avvio del processo: conta gli avvii falliti (quelli che non hanno chiamato [markHealthy]);
 * - [markHealthy] quando NOVA è rimasta in piedi abbastanza (es. 20 s dopo il primo frame);
 * - [onCrash] dal gestore dei crash (sincrono);
 * - [leaveSafeMode] quando l'utente esce dalla modalità provvisoria.
 */
class BootGuard(
    private val store: KeyValueFile,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** @return il motivo per entrare in modalità provvisoria, oppure null per un avvio normale. */
    fun onLaunch(versionCode: Long): SafeModeReason? {
        val now = clock()
        val updated = store.getLong(KEY_VERSION, -1) != versionCode
        val previousWasHealthy = store.getBoolean(KEY_HEALTHY, default = true)
        val failed = if (previousWasHealthy) 0 else store.getLong(KEY_FAILED) + 1
        val forced = store.getLong(KEY_FORCED, -1).toInt()
        // Una volta entrati, si resta in modalità provvisoria finché l'utente non esce (leaveSafeMode).
        val reason =
            when {
                forced in SafeModeReason.entries.indices -> SafeModeReason.entries[forced]
                failed >= START_FAILURES -> SafeModeReason.REPEATED_START_CRASHES
                else -> null
            }
        store.edit {
            put(KEY_VERSION, versionCode)
            put(KEY_HEALTHY, false)
            put(KEY_FAILED, failed)
            if (reason != null) put(KEY_FORCED, reason.ordinal.toLong())
            if (updated) {
                put(KEY_UPDATED_AT, now)
                put(KEY_POST_UPDATE_CRASHES, 0)
            }
        }
        return reason
    }

    fun markHealthy() = store.edit { put(KEY_HEALTHY, true) }

    /** Da chiamare nel gestore dei crash. Deve restare veloce e sincrono. */
    fun onCrash() {
        val now = clock()
        val sinceUpdate = now - store.getLong(KEY_UPDATED_AT, Long.MIN_VALUE / 2)
        if (sinceUpdate !in 0..POST_UPDATE_WINDOW_MS) return
        val crashes = store.getLong(KEY_POST_UPDATE_CRASHES) + 1
        store.edit {
            put(KEY_POST_UPDATE_CRASHES, crashes)
            if (crashes >= POST_UPDATE_CRASHES) put(KEY_FORCED, SafeModeReason.CRASHES_AFTER_UPDATE.ordinal.toLong())
        }
    }

    /** Il prossimo avvio (o quello corrente, se chiamato prima di onLaunch) sarà in modalità provvisoria. */
    fun requestSafeMode() = store.edit { put(KEY_FORCED, SafeModeReason.REQUESTED.ordinal.toLong()) }

    fun leaveSafeMode() =
        store.edit {
            put(KEY_FORCED, -1)
            put(KEY_FAILED, 0)
            put(KEY_POST_UPDATE_CRASHES, 0)
            put(KEY_HEALTHY, true)
        }

    companion object {
        const val START_FAILURES = 3L
        const val POST_UPDATE_CRASHES = 2L
        const val POST_UPDATE_WINDOW_MS = 60_000L

        private const val KEY_VERSION = "version"
        private const val KEY_HEALTHY = "healthy"
        private const val KEY_FAILED = "failedStarts"
        private const val KEY_FORCED = "forcedReason"
        private const val KEY_UPDATED_AT = "updatedAt"
        private const val KEY_POST_UPDATE_CRASHES = "postUpdateCrashes"
    }
}
