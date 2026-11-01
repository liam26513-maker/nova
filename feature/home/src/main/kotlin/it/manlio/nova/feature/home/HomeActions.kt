package it.manlio.nova.feature.home

import android.graphics.Rect
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

/** Azioni della home: le fornisce l'app, così la schermata resta provabile con dati finti. */
@Suppress("LongParameterList") // raccolta di callback: ognuna è un'azione distinta della home
@Immutable
class HomeActions(
    val icon: suspend (AppEntry) -> ImageBitmap?,
    /** [bounds]: dove si trova l'app sullo schermo, per farla "aprire" da lì. */
    val onLaunch: (entry: AppEntry, bounds: Rect?) -> Unit,
    val onToggleFavorite: (AppEntry) -> Unit,
    val onOpenSettings: () -> Unit,
    /** Nuovo ordine dei preferiti visibili, dopo averli riordinati trascinando. */
    val onReorderFavorites: (List<String>) -> Unit = {},
    val onHide: (AppEntry) -> Unit = {},
    /** Nome vuoto = torna al nome originale. */
    val onRename: (AppEntry, String) -> Unit = { _, _ -> },
    val onAppInfo: (AppEntry) -> Unit = {},
    val onUninstall: (AppEntry) -> Unit = {},
    /** Scorrimento verso il basso sulla home: apre le notifiche. */
    val onSwipeDown: () -> Unit = {},
    /** Doppio tocco sull'alfabeto: spegne lo schermo. */
    val onSleep: () -> Unit = {},
    /** «Cerca sul web» dalla ricerca universale (apre il browser: NOVA non ha accesso a Internet). */
    val onWebSearch: (String) -> Unit = {},
    /** Widget di altre app; null se il dispositivo non li permette. */
    val widgets: ExternalWidgets? = null,
    /** Tempo di una città (Open-Meteo); null = senza Meteo. L'app tiene la risposta in cache. */
    val weather: (suspend (city: String) -> Result<Weather>)? = null,
    /** Azioni del centro di controllo; null = senza centro di controllo. */
    val controls: ControlActions? = null,
)

/**
 * Aspetto dello sfondo: [tint] è il colore principale dello sfondo di sistema (Android lo fornisce
 * senza permessi); [blurred] dice se dietro la home c'è la sfocatura reale, così la sfumatura
 * può essere più leggera.
 */
@Immutable
data class HomeStyle(
    val tint: Color = DEFAULT_TINT,
    val blurred: Boolean = false,
) {
    companion object {
        val DEFAULT_TINT = Color(0xFF26303D)
    }
}
