package it.manlio.nova.core.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

sealed interface ApplyResult {
    data class Applied(val config: NovaConfig) : ApplyResult

    data class Rejected(val reason: String) : ApplyResult
}

/**
 * Motore della configurazione a tre strati (SPEC §3.5):
 * default compilati → pacchetto dal repository → override locali.
 * Ogni modifica viene fusa, decodificata e validata; se non è valida si resta
 * sulla configurazione precedente e il motivo è restituito.
 * [current] emette a ogni cambio: è il meccanismo di ricarica a caldo.
 */
class ConfigEngine(
    private val appVersionCode: Int,
    private val defaults: JsonObject,
) {
    private var bundle: JsonObject = JsonObject(emptyMap())
    private var overrides: JsonObject = JsonObject(emptyMap())
    private val _current = MutableStateFlow(build(defaults, bundle, overrides).orThrow())
    val current: StateFlow<NovaConfig> = _current.asStateFlow()

    /** Applica un nuovo pacchetto di configurazione (testo JSON). */
    fun applyBundle(json: String): ApplyResult {
        val parsed = parse(json) ?: return ApplyResult.Rejected("JSON non valido")
        return commit(parsed, overrides)
    }

    /** Sostituisce gli override locali (es. dall'editor temi o da "Ritocco visivo"). */
    fun setOverrides(json: String): ApplyResult {
        val parsed = parse(json) ?: return ApplyResult.Rejected("JSON non valido")
        return commit(bundle, parsed)
    }

    /** Torna ai soli default + pacchetto (es. "Ripristina" in modalità provvisoria). */
    fun clearOverrides(): ApplyResult = setOverrides("{}")

    private fun commit(
        newBundle: JsonObject,
        newOverrides: JsonObject,
    ): ApplyResult {
        val result = build(defaults, newBundle, newOverrides)
        if (result is ApplyResult.Applied) {
            bundle = newBundle
            overrides = newOverrides
            _current.value = result.config
        }
        return result
    }

    private fun build(vararg layers: JsonObject): ApplyResult {
        val merged = layers.reduce(::deepMerge)
        val config =
            try {
                JSON.decodeFromJsonElement<NovaConfig>(merged)
            } catch (e: SerializationException) {
                return ApplyResult.Rejected("Struttura non valida: ${e.message}")
            } catch (e: IllegalArgumentException) {
                return ApplyResult.Rejected("Struttura non valida: ${e.message}")
            }
        if (config.meta.minAppVersion > appVersionCode) {
            return ApplyResult.Rejected(
                "Il pacchetto ${config.meta.version} richiede l'app versione ${config.meta.minAppVersion} " +
                    "(installata: $appVersionCode)",
            )
        }
        val errors = ConfigValidator.validate(config)
        return if (errors.isEmpty()) ApplyResult.Applied(config) else ApplyResult.Rejected(errors.joinToString("; "))
    }

    companion object {
        val JSON = Json { ignoreUnknownKeys = false }

        private fun parse(json: String): JsonObject? = runCatching { JSON.parseToJsonElement(json).jsonObject }.getOrNull()

        private fun ApplyResult.orThrow(): NovaConfig =
            when (this) {
                is ApplyResult.Applied -> config
                is ApplyResult.Rejected -> error("Default di configurazione non validi: $reason")
            }

        /** Fusione profonda: gli oggetti si fondono chiave per chiave, ogni altro valore viene sostituito. */
        fun deepMerge(
            base: JsonObject,
            top: JsonObject,
        ): JsonObject {
            val out = base.toMutableMap()
            for ((k, v) in top) {
                val b: JsonElement? = out[k]
                out[k] = if (b is JsonObject && v is JsonObject) deepMerge(b, v) else v
            }
            return JsonObject(out)
        }

        /**
         * Compone il documento dei default a partire dai file di `config/`
         * (letti dagli asset dell'app o, nei test, dal repository).
         */
        fun assemble(
            meta: String,
            brand: String,
            theme: String,
            modules: String,
            modes: String,
            debloat: String,
        ): JsonObject =
            JsonObject(
                mapOf(
                    "meta" to JSON.parseToJsonElement(meta),
                    "brand" to JSON.parseToJsonElement(brand),
                    "theme" to JSON.parseToJsonElement(theme),
                    "modules" to JSON.parseToJsonElement(modules),
                    "modes" to JSON.parseToJsonElement(modes),
                    "debloat" to JSON.parseToJsonElement(debloat),
                ),
            )
    }
}
