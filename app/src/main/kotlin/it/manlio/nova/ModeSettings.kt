package it.manlio.nova

import android.content.ContentResolver
import android.content.Context
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.Display
import it.manlio.nova.core.modes.ActionOutcome
import it.manlio.nova.core.modes.UndoToken

/**
 * Le azioni delle modalità che toccano le impostazioni di Android (rotazione, spegnimento dello schermo, sincronizzazione):
 * ognuna salva ciò che c'era prima in un [UndoToken] e sa tornare indietro. Separate da [ModeController] per tenerlo piccolo.
 */
internal class ModeSettings(private val context: Context) {
    /**
     * Blocca la rotazione **com'è adesso**. Spegnere solo la rotazione automatica non basta: Android torna all'orientamento salvato
     * a parte (`USER_ROTATION`, di solito verticale), ed è quello che faceva mettere lo schermo in verticale. Si salva l'orientamento
     * attuale in `USER_ROTATION` e poi si spegne la rotazione automatica; all'uscita tornano tutti e due i valori di prima.
     */
    fun lockRotation(): ActionOutcome {
        if (!Settings.System.canWrite(context)) return ActionOutcome.Skipped(context.getString(R.string.mode_reason_write))
        val resolver = context.contentResolver
        val auto = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1)
        val user = Settings.System.getInt(resolver, Settings.System.USER_ROTATION, 0)
        val now = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: user
        val ok =
            runCatching {
                Settings.System.putInt(resolver, Settings.System.USER_ROTATION, now)
                Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 0)
            }.isSuccess
        if (!ok) return ActionOutcome.Failed("impostazione non scrivibile")
        return ActionOutcome.Applied(UndoToken(KIND_ROTATION, "$auto|$user"))
    }

    /** [value] è «automatica|orientamento»; un segnalibro vecchio ha solo la rotazione automatica. */
    fun restoreRotation(value: String): Boolean {
        if (!Settings.System.canWrite(context)) return false
        val parts = value.split('|')
        val auto = parts[0].toIntOrNull() ?: 1
        val user = parts.getOrNull(1)?.toIntOrNull()
        val resolver = context.contentResolver
        return runCatching {
            user?.let { Settings.System.putInt(resolver, Settings.System.USER_ROTATION, it) }
            Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, auto)
        }.isSuccess
    }

    /** Lo schermo resta acceso fino a 30 minuti di inattività; se era già più lungo non si tocca. */
    fun keepScreenOn(): ActionOutcome {
        if (!Settings.System.canWrite(context)) return ActionOutcome.Skipped(context.getString(R.string.mode_reason_write))
        val resolver = context.contentResolver
        val before = Settings.System.getInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, DEFAULT_SCREEN_OFF_MS)
        if (before >= KEEP_AWAKE_MS) return ActionOutcome.Applied()
        val ok = runCatching { Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, KEEP_AWAKE_MS) }.isSuccess
        if (!ok) return ActionOutcome.Failed("impostazione non scrivibile")
        return ActionOutcome.Applied(UndoToken(KIND_SCREEN_ON, before.toString()))
    }

    fun restoreScreenTimeout(value: String): Boolean {
        if (!Settings.System.canWrite(context)) return false
        val before = value.toIntOrNull() ?: DEFAULT_SCREEN_OFF_MS
        return runCatching { Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, before) }.isSuccess
    }

    /** Luminosità al massimo e regolazione automatica spenta, altrimenti Android la riabbassa. */
    fun fullBrightness(): ActionOutcome {
        if (!Settings.System.canWrite(context)) return ActionOutcome.Skipped(context.getString(R.string.mode_reason_write))
        val resolver = context.contentResolver
        val mode = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        val level = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, MAX_BRIGHTNESS)
        val ok =
            runCatching {
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, MAX_BRIGHTNESS)
            }.isSuccess
        if (!ok) return ActionOutcome.Failed("impostazione non scrivibile")
        return ActionOutcome.Applied(UndoToken(KIND_BRIGHTNESS, "$mode|$level"))
    }

    /** [value] è «regolazione|luminosità». */
    fun restoreBrightness(value: String): Boolean {
        if (!Settings.System.canWrite(context)) return false
        val parts = value.split('|')
        val resolver = context.contentResolver
        return runCatching {
            parts.getOrNull(1)?.toIntOrNull()?.let { Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, it) }
            parts[0].toIntOrNull()?.let { Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, it) }
        }.isSuccess
    }

    /** Sincronizzazione automatica degli account in pausa; se l'utente l'aveva già spenta non la riaccende all'uscita. */
    fun pauseSync(): ActionOutcome {
        if (!ContentResolver.getMasterSyncAutomatically()) return ActionOutcome.Applied()
        return runCatching { ContentResolver.setMasterSyncAutomatically(false) }.fold(
            onSuccess = { ActionOutcome.Applied(UndoToken(KIND_SYNC)) },
            onFailure = { ActionOutcome.Failed("sincronizzazione non modificabile") },
        )
    }

    fun resumeSync(): Boolean = runCatching { ContentResolver.setMasterSyncAutomatically(true) }.isSuccess

    companion object {
        const val KIND_ROTATION = "rotation"
        const val KIND_SCREEN_ON = "screen_on"
        const val KIND_SYNC = "sync"
        const val KIND_BRIGHTNESS = "brightness"
        private const val MAX_BRIGHTNESS = 255
        private const val DEFAULT_SCREEN_OFF_MS = 60_000
        private const val KEEP_AWAKE_MS = 30 * 60_000
    }
}
