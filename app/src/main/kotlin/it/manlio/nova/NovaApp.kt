package it.manlio.nova

import android.app.Application
import android.content.Context
import it.manlio.nova.core.config.ConfigEngine
import it.manlio.nova.core.design.GraphicsLevel
import it.manlio.nova.core.design.GraphicsLevelController
import it.manlio.nova.core.design.GraphicsState
import it.manlio.nova.core.modules.ModuleRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/** Assemblaggio del nucleo: configurazione, registro moduli, livello grafico. */
class NovaApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var config: ConfigEngine
        private set

    // Nessun modulo funzionale ancora: arriveranno dalla Fase 1 in poi.
    val modules = ModuleRegistry(emptyList())

    /**
     * Livello iniziale atteso sul tablet di riferimento (SPEC §2.1): Bilanciato.
     * shortcut: tetto fisso a MAXIMUM, finché il profilatore dispositivo (Fase 0, punto 6) non lo calcola.
     */
    private val _graphics = MutableStateFlow(GraphicsState(GraphicsLevel.BALANCED, automatic = true))
    val graphics: StateFlow<GraphicsState> = _graphics.asStateFlow()

    val graphicsController: GraphicsLevelController =
        GraphicsLevelController(GraphicsLevel.BALANCED) { _graphics.value = it }

    override fun onCreate() {
        super.onCreate()
        config = ConfigEngine(BuildConfig.VERSION_CODE, loadDefaults(this))
        overridesFile(this).takeIf { it.exists() }?.readText()?.let(config::setOverrides)
        appScope.launch {
            config.current.collect { c -> modules.apply(c.modules.modules, capabilities = emptySet()) }
        }
    }

    companion object {
        fun overridesFile(context: Context) = File(context.filesDir, "config-overrides.json")

        /** I default compilati sono i file di `config/` del repository, inclusi negli asset. */
        fun loadDefaults(context: Context) =
            with(context.assets) {
                fun read(path: String) = open(path).bufferedReader().use { it.readText() }
                val theme = if (isNightMode(context)) "theme/dark.json" else "theme/light.json"
                ConfigEngine.assemble(read("meta.json"), read("brand.json"), read(theme), read("modules.json"))
            }

        private fun isNightMode(context: Context) =
            (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}
