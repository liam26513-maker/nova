package it.manlio.nova

import it.manlio.nova.core.modes.ModeAction
import it.manlio.nova.core.modes.ModeDefinition
import it.manlio.nova.core.modes.Modes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Properties

/**
 * Cosa fa la Modalità Gaming, voce per voce, scelto dall'utente (Impostazioni → Modalità Gaming). Serve soprattutto a trovare
 * in due prove cosa fa scattare un gioco: si spegne una voce alla volta. Predefiniti prudenti: solo ciò che non può pesare sul gioco.
 * File di proprietà semplice; una voce sconosciuta o rotta torna al predefinito.
 */
class ModeOptions(private val file: File) {
    private val state = MutableStateFlow(load())

    /** Valori scelti (chiave = nome dell'azione o [OVERLAY]); quelli non scelti usano il predefinito. */
    val values: StateFlow<Map<String, Boolean>> = state.asStateFlow()

    fun enabled(action: ModeAction): Boolean = state.value[action.name] ?: (action in DEFAULT_ON)

    /** Le azioni da fare entrando: nella Gaming si sceglie tutto, nelle altre solo le voci facoltative come il filtro caldo. */
    fun chosen(mode: ModeDefinition): List<ModeAction> =
        mode.actions.filter { (mode.id != Modes.GAMING.id && it != ModeAction.NIGHT_LIGHT_ON) || enabled(it) }

    /**
     * La tendina di NOVA dentro i giochi: predefinito acceso, ma durante la Gaming la striscia si riduce a un angolo (in alto a
     * destra) per non rubare i tocchi ai pulsanti del gioco. Spenta, durante la Gaming la striscia non c'è.
     */
    val overlayInGames: Boolean get() = state.value[OVERLAY] ?: true

    fun set(
        key: String,
        on: Boolean,
    ) {
        state.value = state.value + (key to on)
        save()
    }

    private fun load(): Map<String, Boolean> =
        runCatching {
            if (!file.exists()) return emptyMap()
            val p = Properties().apply { file.inputStream().use(::load) }
            p.stringPropertyNames().associateWith { p.getProperty(it) == "true" }
        }.getOrDefault(emptyMap())

    private fun save() {
        file.parentFile?.mkdirs()
        val p = Properties().apply { state.value.forEach { (k, v) -> setProperty(k, v.toString()) } }
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.outputStream().use { p.store(it, "NOVA modalita") }
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    companion object {
        const val OVERLAY = "OVERLAY_IN_GAMES"

        /** Doppio swipe dal bordo e watchdog: spenti di serie, il primo prende tocchi al gioco e il secondo lo disturba. */
        const val EDGE_SWIPE = "EDGE_SWIPE"
        const val WATCHDOG = "WATCHDOG"

        /** Il pannello con uno swipe dal bordo sinistro: spento di serie, la fascia prende tocchi al gioco. */
        const val PANEL_SWIPE = "PANEL_SWIPE"

        /** Gaming da sola quando si apre un gioco (SPEC §8.7): spenta di serie. */
        const val AUTO_GAMING = "AUTO_GAMING"

        /** La barra Annulla/Ripeti nella Creativa (SPEC §9.4): spenta di serie. */
        const val PEN_BAR = "PEN_BAR"

        /** La barra del desktop nel Lavoro (SPEC §8.3): spenta di serie. */
        const val DESK_BAR = "DESK_BAR"

        /** Acceso di serie: cambiano solo NOVA e impostazioni che non pesano sul gioco. Turbo e pausa sincronizzazione li accendi tu. */
        val DEFAULT_ON =
            setOf(ModeAction.PERFORMANCE_GRAPHICS, ModeAction.DO_NOT_DISTURB, ModeAction.LOCK_ROTATION, ModeAction.KEEP_SCREEN_ON)
    }
}
