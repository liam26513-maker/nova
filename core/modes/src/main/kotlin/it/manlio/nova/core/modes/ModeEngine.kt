package it.manlio.nova.core.modes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Come annullare un'azione: un tipo e un valore (ciò che c'era prima), salvati su file per non perderli se l'app si chiude. */
data class UndoToken(
    val kind: String,
    val value: String = "",
)

/** Esito di un'azione: fatta (con il modo di annullarla, se serve), saltata (non si può su questo tablet) o fallita. */
sealed interface ActionOutcome {
    data class Applied(val undo: UndoToken? = null) : ActionOutcome

    data class Skipped(val reason: String) : ActionOutcome

    data class Failed(val reason: String) : ActionOutcome
}

/** Il tablet vero (in produzione, con Android e Shizuku; nei test un finto). */
interface ModeEnvironment {
    suspend fun apply(action: ModeAction): ActionOutcome

    /** @return true se è tornato com'era (o se non c'era più niente da annullare). */
    suspend fun undo(token: UndoToken): Boolean
}

/**
 * Una sessione di modalità. [leftover] vero = la modalità è finita ma qualcosa non si è riusciti ad annullare:
 * [tokens] resta nel registro e si riprova al prossimo avvio.
 */
data class Session(
    val modeId: String,
    val startedAt: Long,
    val tokens: List<UndoToken>,
    val leftover: Boolean = false,
)

interface SessionStore {
    fun load(): Session?

    /** `null` cancella la sessione salvata. */
    fun save(session: Session?)
}

data class EnterReport(
    val applied: List<ModeAction>,
    val skipped: List<Pair<ModeAction, String>>,
    val failed: List<Pair<ModeAction, String>>,
)

/** [pending]: quante azioni non si sono riuscite ad annullare (restano nel registro). */
data class ExitReport(val restored: Int, val pending: Int)

/**
 * Il motore delle modalità (SPEC §8.1): salva lo stato prima di ogni cambio e lo **ripristina esattamente** all'uscita,
 * in ordine inverso. Una sola modalità principale per volta. Ogni passo viene scritto su [store] prima di andare avanti,
 * quindi anche se l'app si chiude a metà si può ripristinare ([recover]). Una modalità scade da sola ([exitIfExpired]).
 * Non conosce Android: si prova con un ambiente finto.
 */
class ModeEngine(
    private val env: ModeEnvironment,
    private val store: SessionStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val catalog: () -> List<ModeDefinition> = { Modes.all },
    /** Una sola volta per sessione: id della modalità e minuti passati dentro. */
    private val onFinished: (String, Int) -> Unit = { _, _ -> },
) {
    private val lock = Mutex()
    private val _active = MutableStateFlow<Session?>(null)

    /** La modalità attiva adesso, o null. */
    val active: StateFlow<Session?> = _active.asStateFlow()

    /** Da chiamare all'avvio: riprende una sessione rimasta a metà e riprova ad annullare ciò che era rimasto. */
    suspend fun recover() {
        lock.withLock<Unit> {
            val saved = store.load()
            if (saved != null && saved.leftover) {
                finish(saved)
            } else if (saved != null) {
                _active.value = saved
            }
        }
    }

    suspend fun enter(mode: ModeDefinition): EnterReport =
        lock.withLock {
            _active.value?.let { finish(it) } // una sola modalità principale
            val tokens = mutableListOf<UndoToken>()
            val applied = mutableListOf<ModeAction>()
            val skipped = mutableListOf<Pair<ModeAction, String>>()
            val failed = mutableListOf<Pair<ModeAction, String>>()
            val startedAt = clock()
            val session = { Session(mode.id, startedAt, tokens.toList()) }
            store.save(session()) // la sessione esiste già prima della prima azione: se qualcosa si ferma, si può annullare
            _active.value = session()
            for (action in mode.actions) {
                when (val outcome = runCatching { env.apply(action) }.getOrElse { ActionOutcome.Failed(it.message.orEmpty()) }) {
                    is ActionOutcome.Applied -> {
                        applied += action
                        outcome.undo?.let { tokens += it }
                        store.save(session())
                        _active.value = session()
                    }
                    is ActionOutcome.Skipped -> skipped += action to outcome.reason
                    is ActionOutcome.Failed -> failed += action to outcome.reason
                }
            }
            EnterReport(applied, skipped, failed)
        }

    /** Esce dalla modalità attiva e rimette tutto com'era. Senza modalità attiva non fa niente. */
    suspend fun exit(): ExitReport =
        lock.withLock {
            val session = _active.value ?: return@withLock ExitReport(0, 0)
            finish(session)
        }

    /** Esce se la modalità attiva ha superato la sua durata massima (la rete di sicurezza contro una modalità dimenticata). */
    suspend fun exitIfExpired(): Boolean =
        lock.withLock {
            val session = _active.value ?: return@withLock false
            val max = catalog().firstOrNull { it.id == session.modeId }?.maxMinutes ?: DEFAULT_MAX_MINUTES
            val expired = clock() - session.startedAt >= max * MINUTE_MS
            if (expired) finish(session)
            expired
        }

    /** Annulla in ordine inverso. Ciò che non si annulla resta nel registro come [Session.leftover] e si riprova a ogni avvio. */
    private suspend fun finish(session: Session): ExitReport {
        val left = mutableListOf<UndoToken>()
        var restored = 0
        for (token in session.tokens.asReversed()) {
            val ok = runCatching { env.undo(token) }.getOrDefault(false)
            if (ok) restored++ else left += token
        }
        _active.value = null
        if (!session.leftover) onFinished(session.modeId, ((clock() - session.startedAt) / MINUTE_MS).toInt())
        if (left.isEmpty()) {
            store.save(null)
        } else {
            store.save(session.copy(tokens = left.asReversed(), leftover = true))
        }
        return ExitReport(restored, left.size)
    }

    private companion object {
        const val MINUTE_MS = 60_000L
        const val DEFAULT_MAX_MINUTES = 6 * 60
    }
}
