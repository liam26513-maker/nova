package it.manlio.nova.core.modules

/** Permessi o servizi di sistema di cui un modulo può avere bisogno (SPEC §3.4). */
enum class Capability { SHIZUKU, ACCESSIBILITY, NOTIFICATION_ACCESS, OVERLAY }

/** Ciò che il registro passa a un modulo all'avvio. */
data class ModuleContext(val grantedCapabilities: Set<Capability>)

sealed interface ModuleHealth {
    data object Ok : ModuleHealth

    data class Degraded(val reason: String) : ModuleHealth

    data class Failed(val reason: String) : ModuleHealth
}

/**
 * Una funzione di NOVA. Da spento non deve consumare nulla: [stop] rilascia
 * listener, servizi, wake-lock e cache (SPEC §3.4, CLAUDE.md).
 */
interface NovaModule {
    val id: String
    val displayName: String
    val defaultEnabled: Boolean
    val requires: Set<Capability> get() = emptySet()
    val dependsOn: Set<String> get() = emptySet()

    suspend fun start(context: ModuleContext)

    suspend fun stop()

    fun healthCheck(): ModuleHealth = ModuleHealth.Ok
}

/** Stato di un modulo, mostrato in "Stato del sistema" e usato da "Ripara". */
sealed interface ModuleState {
    data object Disabled : ModuleState

    data object Running : ModuleState

    /** Abilitato ma in attesa di permessi o di altri moduli. */
    data class Waiting(
        val missingCapabilities: Set<Capability>,
        val missingModules: Set<String>,
    ) : ModuleState

    /** Disattivato automaticamente dopo troppi crash; si riattiva solo con [ModuleRegistry.resume]. */
    data class Suspended(val lastError: String) : ModuleState
}
