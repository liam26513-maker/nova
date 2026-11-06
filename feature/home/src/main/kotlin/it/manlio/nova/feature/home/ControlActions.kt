package it.manlio.nova.feature.home

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** I simboli dei riquadri del centro di controllo, disegnati da NOVA. */
internal enum class ControlGlyph { WIFI, BLUETOOTH, MOON, TORCH, LAYERS, SETTINGS, EDIT, VOLUME, SUN, BOLT }

/**
 * I riquadri che il centro di controllo può mostrare a prima vista (si scelgono in Personalizza la Home).
 * Quelli non scelti stanno sotto «Altro»; un riquadro non disponibile (torcia senza flash, Turbo senza Shizuku) non c'è mai.
 */
enum class ControlTileId {
    WIFI,
    BLUETOOTH,
    DND,
    TORCH,
    GRAPHICS,
    TURBO,
    EDIT_HOME,
    SETTINGS,
    ;

    companion object {
        /** A prima vista, finché non si sceglie altro. */
        val DEFAULT: List<ControlTileId> = listOf(WIFI, BLUETOOTH, DND, TORCH)
    }
}

/** Le schermate di sistema che il centro di controllo può aprire quando non si può agire direttamente. */
enum class SystemPanel { INTERNET, BLUETOOTH, VOLUME, DISPLAY, DND }

/** La torcia: [on] segue lo stato reale (anche se la cambia un'altra app); null se il tablet non ha il flash. */
interface Torch {
    val on: StateFlow<Boolean>

    fun toggle()
}

/**
 * Un valore regolabile da 0 a 1 (volume, luminosità). [steps] > 0 se il sistema ha solo quei gradini (il volume):
 * il cursore si aggancia ai gradini e scrive solo quando ne cambia uno.
 */
class Level(
    val read: () -> Float,
    val write: (Float) -> Unit,
    val steps: Int = 0,
    /** Emette quando il valore cambia da fuori (una modalità, Android): il cursore lo rilegge, se non lo stai trascinando. */
    val changes: Flow<Unit>? = null,
)

/**
 * Una voce della sezione «Altro» (le impostazioni di Android che non stanno a prima vista).
 * [onClick] restituisce il nuovo [state] se la voce è un interruttore che resta lì (la tendina resta aperta);
 * restituisce null se porta fuori (impostazioni di Android, notifiche): la tendina si chiude.
 */
@Immutable
class MoreItem(
    val label: String,
    val state: String? = null,
    /** Lo stato quando può cambiare senza toccare questa voce (una modalità si spegne se ne accendi un'altra). */
    val live: Flow<String>? = null,
    /** Riceve una funzione per correggere lo stato se Android non ha obbedito (mostra com'è davvero). */
    val onClick: ((String) -> Unit) -> String? = { null },
)

/** La pastiglia della Modalità Studio in cima alla Home: prende il posto dell'isola finché la modalità è accesa. */
@Immutable
@Suppress("LongParameterList") // testo e tre pulsanti, ognuno con etichetta e azione
class StudyBar(
    val text: String,
    val timerLabel: String,
    val onTimer: () -> Unit,
    val notesLabel: String,
    val onNotes: () -> Unit,
    val exitLabel: String,
    val onExit: () -> Unit,
)

/**
 * Un interruttore che si cambia subito dalla tendina (Wi-Fi, Bluetooth, non disturbare): [read] dice com'è adesso,
 * [set] lo cambia subito e chiama il secondo argomento solo se Android non ha obbedito, così la tendina rilegge lo stato vero.
 * Esiste solo se il sistema lo permette (Shizuku attivo); senza, il riquadro apre la schermata di Android.
 */
@Immutable
class Toggle(
    val read: () -> Boolean,
    val set: (Boolean, () -> Unit) -> Unit,
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
    /** Turbo (chiude le app in secondo piano); null = senza Shizuku o non disponibile. */
    val onTurbo: (() -> Unit)? = null,
    /** Le altre impostazioni di Android, sotto «Altro». */
    val more: List<MoreItem> = emptyList(),
    /** Interruttori diretti per i riquadri che altrimenti aprirebbero un pannello di Android. */
    val toggles: Map<SystemPanel, Toggle> = emptyMap(),
)
