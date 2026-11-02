package it.manlio.nova

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import it.manlio.nova.core.system.ShizukuStatus
import it.manlio.nova.feature.home.HomePrefs
import it.manlio.nova.feature.home.HomePrefsStore
import it.manlio.nova.feature.home.IconSpec
import it.manlio.nova.feature.home.IconStyle
import it.manlio.nova.feature.home.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Aggiorna le preferenze della Home senza bloccare chi chiama. */
internal fun HomePrefsStore.updateIn(
    scope: CoroutineScope,
    change: (HomePrefs) -> HomePrefs,
) {
    scope.launch { update(change) }
}

/** Piccole parti della Home che dipendono dal sistema: tema, stile delle icone, notifiche. */
internal object HomeSystem {
    fun isDark(
        context: Context,
        prefs: HomePrefs,
    ): Boolean =
        when (prefs.theme) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.AUTO -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }

    fun iconSpec(
        context: Context,
        prefs: HomePrefs,
    ): IconSpec {
        val ink = if (isDark(context, prefs)) Color.White else Color(INK_LIGHT_THEME)
        val plate = ink.copy(alpha = if (prefs.iconStyle == IconStyle.MONO) PLATE_MONO else PLATE_COLOR)
        return IconSpec(prefs.iconShape, prefs.iconStyle, prefs.iconPack, ink.toArgb(), plate.toArgb())
    }

    /** Apre le notifiche (come lo scorrimento dalla barra di stato). API non pubblica: se manca, non succede nulla. */
    @SuppressLint("WrongConstant")
    fun expandNotifications(context: Context) {
        runCatching {
            val service = context.getSystemService("statusbar")
            service.javaClass.getMethod("expandNotificationsPanel").invoke(service)
        }
    }

    /** Turbo da un tasto (centro di controllo): lo esegue e dice a schermo quante app ha chiuso. */
    fun turboWithToast(
        context: Context,
        scope: CoroutineScope,
        performance: PerformanceUi,
    ) {
        scope.launch {
            val done = performance.runTurbo()
            val text =
                if (done == null) context.getString(R.string.perf_failed) else context.getString(R.string.perf_turbo_done, done.stopped)
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Prepara l'accensione del servizio di accessibilità: con Shizuku pronto toglie da sola il blocco «impostazione con
     * restrizioni» di Android (una volta sola, senza passare dai tre puntini), poi apre le impostazioni di accessibilità.
     * Se non riesce, lo dice e indica la strada a mano.
     */
    fun unlockAndOpenAccessibility(
        app: NovaApp,
        scope: CoroutineScope,
    ) {
        scope.launch {
            if (app.shizuku.status.value == ShizukuStatus.READY) {
                val ok = runCatching { app.systemChanges.allowRestrictedSettings(app.packageName).ok }.getOrDefault(false)
                val text = if (ok) R.string.overlay_unlocked else R.string.overlay_unlock_failed
                Toast.makeText(app, text, Toast.LENGTH_LONG).show()
            }
            openAccessibilitySettings(app)
        }
    }

    /** Apre le impostazioni di accessibilità di Android (dove si accende il centro di controllo sopra le altre app). */
    fun openAccessibilitySettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Apre le Impostazioni rapide di Android (API non pubblica: se manca, non succede nulla). */
    @SuppressLint("WrongConstant")
    fun expandQuickSettings(context: Context) {
        runCatching {
            val service = context.getSystemService("statusbar")
            service.javaClass.getMethod("expandSettingsPanel").invoke(service)
        }
    }

    /** «Rimuovi animazioni» di Android (scala 0): niente ondeggiamento in modifica. */
    fun animationsOn(context: Context): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

    /** «Cerca sul web» nel browser: NOVA non ha accesso a Internet. */
    fun webSearch(
        context: Context,
        query: String,
    ) {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private const val INK_LIGHT_THEME = 0xFF15171A
    private const val PLATE_MONO = 0.14f
    private const val PLATE_COLOR = 0.2f
}
