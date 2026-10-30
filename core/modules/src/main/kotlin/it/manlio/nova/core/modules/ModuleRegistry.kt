package it.manlio.nova.core.modules

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Avvia e ferma i moduli secondo la configurazione (`config/modules.json` + override)
 * e i permessi disponibili, rispettando le dipendenze. Un modulo che va in crash
 * [CRASH_LIMIT] volte in [CRASH_WINDOW_MS] viene sospeso (SPEC §3.4).
 */
class ModuleRegistry(
    modules: List<NovaModule>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val byId: Map<String, NovaModule>
    private val order: List<String>
    private val mutex = Mutex()
    private val crashes = mutableMapOf<String, ArrayDeque<Long>>()
    private val suspended = mutableMapOf<String, String>()
    private var lastEnabled: Map<String, Boolean> = emptyMap()
    private var lastCapabilities: Set<Capability> = emptySet()

    private val _states: MutableStateFlow<Map<String, ModuleState>>
    val states: StateFlow<Map<String, ModuleState>>

    init {
        val duplicates = modules.groupBy { it.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Moduli con id duplicato: $duplicates" }
        byId = modules.associateBy { it.id }
        modules.forEach { m ->
            val unknown = m.dependsOn - byId.keys
            require(unknown.isEmpty()) { "Il modulo ${m.id} dipende da moduli inesistenti: $unknown" }
        }
        order = topologicalOrder(byId)
        _states = MutableStateFlow(order.associateWith { ModuleState.Disabled })
        states = _states.asStateFlow()
    }

    /** Applica flag di abilitazione e permessi correnti. Le chiavi sconosciute sono ignorate. */
    suspend fun apply(
        enabled: Map<String, Boolean>,
        capabilities: Set<Capability>,
    ) = mutex.withLock {
        lastEnabled = enabled
        lastCapabilities = capabilities
        reconcile()
    }

    /** Segnala un crash avvenuto mentre il modulo era attivo (es. da un CoroutineExceptionHandler). */
    suspend fun reportCrash(
        id: String,
        error: Throwable,
    ) = mutex.withLock {
        if (state(id) != ModuleState.Running) return@withLock
        safeStop(id)
        update(id, ModuleState.Disabled)
        recordCrash(id, error)
        reconcile()
    }

    /** Riattiva un modulo sospeso (azione dell'utente). */
    suspend fun resume(id: String) =
        mutex.withLock {
            suspended.remove(id)
            crashes.remove(id)
            reconcile()
        }

    fun healthReport(): Map<String, ModuleHealth> =
        order.associateWith { id ->
            if (state(id) == ModuleState.Running) {
                runCatching { byId.getValue(id).healthCheck() }
                    .getOrElse { ModuleHealth.Failed(it.message ?: it::class.simpleName.orEmpty()) }
            } else {
                ModuleHealth.Ok
            }
        }

    private suspend fun reconcile() {
        // 1. Calcola chi deve girare, in ordine di dipendenza.
        val target = mutableSetOf<String>()
        val waiting = mutableMapOf<String, ModuleState.Waiting>()
        for (id in order) {
            val m = byId.getValue(id)
            if (id in suspended || !(lastEnabled[id] ?: m.defaultEnabled)) continue
            val missingCaps = m.requires - lastCapabilities
            val missingDeps = m.dependsOn - target
            if (missingCaps.isEmpty() && missingDeps.isEmpty()) {
                target += id
            } else {
                waiting[id] = ModuleState.Waiting(missingCaps, missingDeps)
            }
        }
        // 2. Ferma prima i dipendenti (ordine inverso).
        for (id in order.asReversed()) {
            if (state(id) == ModuleState.Running && id !in target) safeStop(id)
        }
        // 3. Avvia in ordine di dipendenza; se una dipendenza non parte, il dipendente attende.
        for (id in order) {
            val m = byId.getValue(id)
            when {
                id in suspended -> update(id, ModuleState.Suspended(suspended.getValue(id)))
                id in target -> startIfNeeded(m)
                id in waiting -> update(id, waiting.getValue(id))
                else -> update(id, ModuleState.Disabled)
            }
        }
    }

    private suspend fun startIfNeeded(m: NovaModule) {
        val notRunning = m.dependsOn.filter { state(it) != ModuleState.Running }.toSet()
        if (notRunning.isNotEmpty()) {
            // Una dipendenza non è partita: il dipendente non può restare attivo.
            if (state(m.id) == ModuleState.Running) safeStop(m.id)
            update(m.id, ModuleState.Waiting(emptySet(), notRunning))
            return
        }
        if (state(m.id) == ModuleState.Running) return
        try {
            m.start(ModuleContext(lastCapabilities))
            update(m.id, ModuleState.Running)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            safeStop(m.id)
            recordCrash(m.id, e)
            update(m.id, suspended[m.id]?.let { ModuleState.Suspended(it) } ?: ModuleState.Disabled)
        }
    }

    private fun recordCrash(
        id: String,
        error: Throwable,
    ) {
        val now = clock()
        val times = crashes.getOrPut(id) { ArrayDeque() }
        times.addLast(now)
        while (times.isNotEmpty() && now - times.first() > CRASH_WINDOW_MS) times.removeFirst()
        if (times.size >= CRASH_LIMIT) {
            suspended[id] = error.message ?: error::class.simpleName.orEmpty()
            update(id, ModuleState.Suspended(suspended.getValue(id)))
        }
    }

    private suspend fun safeStop(id: String) {
        try {
            byId.getValue(id).stop()
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
        ) {
            // Uno stop fallito non deve bloccare gli altri moduli: il modulo risulta comunque fermo.
        }
        if (state(id) == ModuleState.Running) update(id, ModuleState.Disabled)
    }

    private fun state(id: String): ModuleState = _states.value.getValue(id)

    private fun update(
        id: String,
        s: ModuleState,
    ) {
        _states.value = _states.value + (id to s)
    }

    companion object {
        const val CRASH_LIMIT = 3
        const val CRASH_WINDOW_MS = 10 * 60 * 1000L

        private fun topologicalOrder(byId: Map<String, NovaModule>): List<String> {
            val result = mutableListOf<String>()
            val visiting = mutableSetOf<String>()
            val done = mutableSetOf<String>()

            fun visit(id: String) {
                if (id in done) return
                require(visiting.add(id)) { "Dipendenza circolare che coinvolge il modulo $id" }
                byId.getValue(id).dependsOn.sorted().forEach(::visit)
                visiting.remove(id)
                done += id
                result += id
            }
            byId.keys.sorted().forEach(::visit)
            return result
        }
    }
}
