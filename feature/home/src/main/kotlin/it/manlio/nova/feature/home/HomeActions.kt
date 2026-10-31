package it.manlio.nova.feature.home

import android.graphics.Rect
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

/** Azioni della home: le fornisce l'app, così la schermata resta provabile con dati finti. */
@Immutable
class HomeActions(
    val icon: suspend (AppEntry) -> ImageBitmap?,
    /** [bounds]: dove si trova l'app sullo schermo, per farla "aprire" da lì. */
    val onLaunch: (entry: AppEntry, bounds: Rect?) -> Unit,
    val onToggleFavorite: (AppEntry) -> Unit,
    val onOpenSettings: () -> Unit,
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
