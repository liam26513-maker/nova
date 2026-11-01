package it.manlio.nova.feature.home

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.StateFlow

/** I simboli dei riquadri del centro di controllo, disegnati da NOVA. */
internal enum class ControlGlyph { WIFI, BLUETOOTH, MOON, TORCH, LAYERS, SETTINGS, EDIT, VOLUME, SUN }

/** Le schermate di sistema che il centro di controllo può aprire quando non si può agire direttamente. */
enum class SystemPanel { INTERNET, BLUETOOTH, VOLUME, DISPLAY, DND }

/** La torcia: [on] segue lo stato reale (anche se la cambia un'altra app); null se il tablet non ha il flash. */
interface Torch {
    val on: StateFlow<Boolean>

    fun toggle()
}

/** Un valore regolabile da 0 a 1 (volume, luminosità). */
class Level(
    val read: () -> Float,
    val write: (Float) -> Unit,
)

/**
 * Ciò che il centro di controllo (SPEC §6.8) sa fare, fornito dall'app così la Home resta provabile.
 * Dove Android non lascia a un'app normale cambiare un'impostazione (Wi-Fi, Bluetooth, non disturbare)
 * si apre il pannello di sistema giusto; torcia, volume e luminosità si regolano direttamente.
 * [brightness] è null finché non si concede il permesso di modificare le impostazioni: [onGrantBrightness] lo chiede.
 */
@Immutable
@Suppress("LongParameterList") // raccolta di azioni: ognuna è una funzione distinta del centro di controllo
class ControlActions(
    val openPanel: (SystemPanel) -> Unit,
    val torch: Torch? = null,
    val volume: Level? = null,
    val brightness: Level? = null,
    val onGrantBrightness: () -> Unit = {},
    val graphicsLabel: () -> String = { "" },
    val onCycleGraphics: () -> Unit = {},
)

/** Per far arrivare il pulsante del centro di controllo alle intestazioni senza passarlo ovunque (null = non c'è). */
internal val LocalOpenControl = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }
