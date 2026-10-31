package it.manlio.nova

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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

    private const val INK_LIGHT_THEME = 0xFF15171A
    private const val PLATE_MONO = 0.14f
    private const val PLATE_COLOR = 0.2f
}
