package it.manlio.nova

import it.manlio.nova.core.modes.ModeAction
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

    /**
     * La tendina di NOVA sopra gli altri giochi (la striscia trasparente sul bordo alto): predefinito spento durante la Gaming,
     * perché è un livello in più sopra il gioco e il tocco in quella fascia andrebbe a NOVA.
     */
    val overlayInGames: Boolean get() = state.value[OVERLAY] ?: false

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
        tmp.renameTo(file)
    }

    companion object {
        const val OVERLAY = "OVERLAY_IN_GAMES"

        /** Acceso di serie: cambiano solo NOVA e impostazioni che non pesano sul gioco. Turbo e pausa sincronizzazione li accendi tu. */
        val DEFAULT_ON =
            setOf(ModeAction.PERFORMANCE_GRAPHICS, ModeAction.DO_NOT_DISTURB, ModeAction.LOCK_ROTATION, ModeAction.KEEP_SCREEN_ON)
    }
}
