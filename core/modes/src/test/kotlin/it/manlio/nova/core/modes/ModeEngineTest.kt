package it.manlio.nova.core.modes

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Un tablet finto: ricorda cosa è stato fatto e annullato, e può rifiutare azioni o annullamenti. */
private class FakeEnv : ModeEnvironment {
    val log = mutableListOf<String>()
    val skip = mutableMapOf<ModeAction, String>()
    val fail = mutableSetOf<ModeAction>()
    val failUndo = mutableSetOf<String>()

    override suspend fun apply(action: ModeAction): ActionOutcome {
        log += "apply ${action.name}"
        skip[action]?.let { return ActionOutcome.Skipped(it) }
        if (action in fail) return ActionOutcome.Failed("errore")
        return if (action == ModeAction.TURBO) ActionOutcome.Applied() else ActionOutcome.Applied(UndoToken(action.name, "prima"))
    }

    override suspend fun undo(token: UndoToken): Boolean {
        log += "undo ${token.kind}"
        return token.kind !in failUndo
    }
}

private class MemoryStore : SessionStore {
    var saved: Session? = null
    var writes = 0

    override fun load() = saved

    override fun save(session: Session?) {
        saved = session
        writes++
    }
}

class ModeEngineTest {
    // Una modalità di prova fissa: i test non devono cambiare quando si aggiungono azioni a Gaming vera.
    private val gaming =
        ModeDefinition(
            id = "gaming",
            actions = listOf(ModeAction.PERFORMANCE_GRAPHICS, ModeAction.DO_NOT_DISTURB, ModeAction.LOCK_ROTATION, ModeAction.TURBO),
            maxMinutes = Modes.GAMING.maxMinutes,
        )

    @Test
    fun `entering applies the actions in order and remembers how to undo each`() =
        runTest {
            val env = FakeEnv()
            val store = MemoryStore()
            val engine = ModeEngine(env, store, clock = { 1000L })
            val report = engine.enter(gaming)
            assertEquals(gaming.actions, report.applied)
            assertEquals(gaming.actions.map { "apply ${it.name}" }, env.log)
            val session = engine.active.value
            assertNotNull(session)
            assertEquals(1000L, session!!.startedAt)
            // il Turbo non si annulla: nessun segnalibro per lui
            assertEquals(gaming.actions.filter { it != ModeAction.TURBO }.map { it.name }, session.tokens.map { it.kind })
            assertEquals(session, store.saved)
        }

    @Test
    fun `exiting undoes everything in reverse order and clears the saved session`() =
        runTest {
            val env = FakeEnv()
            val store = MemoryStore()
            val engine = ModeEngine(env, store)
            engine.enter(gaming)
            env.log.clear()
            val report = engine.exit()
            assertEquals(ExitReport(restored = 3, pending = 0), report)
            assertEquals(
                listOf("undo LOCK_ROTATION", "undo DO_NOT_DISTURB", "undo PERFORMANCE_GRAPHICS"),
                env.log,
            )
            assertNull(engine.active.value)
            assertNull(store.saved)
        }

    @Test
    fun `skipped and failed actions are reported and leave nothing to undo`() =
        runTest {
            val env = FakeEnv()
            env.skip[ModeAction.DO_NOT_DISTURB] = "serve Shizuku"
            env.fail += ModeAction.LOCK_ROTATION
            val engine = ModeEngine(env, MemoryStore())
            val report = engine.enter(gaming)
            assertEquals(listOf(ModeAction.DO_NOT_DISTURB to "serve Shizuku"), report.skipped)
            assertEquals(listOf(ModeAction.LOCK_ROTATION to "errore"), report.failed)
            assertFalse(ModeAction.DO_NOT_DISTURB in report.applied)
            assertEquals(listOf("PERFORMANCE_GRAPHICS"), engine.active.value!!.tokens.map { it.kind })
        }

    @Test
    fun `an environment that throws counts as a failure and the other actions still run`() =
        runTest {
            val env =
                object : ModeEnvironment {
                    override suspend fun apply(action: ModeAction): ActionOutcome =
                        if (action == ModeAction.TURBO) error("boom") else ActionOutcome.Applied(UndoToken(action.name))

                    override suspend fun undo(token: UndoToken) = true
                }
            val report = ModeEngine(env, MemoryStore()).enter(gaming)
            assertEquals(1, report.failed.size)
            assertEquals(3, report.applied.size)
        }

    @Test
    fun `only one main mode at a time and the previous one is restored first`() =
        runTest {
            val env = FakeEnv()
            val engine = ModeEngine(env, MemoryStore())
            engine.enter(gaming)
            env.log.clear()
            engine.enter(ModeDefinition("studio", listOf(ModeAction.DO_NOT_DISTURB), 60))
            assertEquals(3, env.log.count { it.startsWith("undo") }) // prima ripristina Gaming
            assertEquals("studio", engine.active.value!!.modeId)
        }

    @Test
    fun `what cannot be undone stays saved and is retried at the next start`() =
        runTest {
            val env = FakeEnv()
            env.failUndo += "DO_NOT_DISTURB"
            val store = MemoryStore()
            val engine = ModeEngine(env, store)
            engine.enter(gaming)
            val report = engine.exit()
            assertEquals(ExitReport(restored = 2, pending = 1), report)
            assertNull(engine.active.value)
            assertTrue(store.saved!!.leftover)
            assertEquals(listOf("DO_NOT_DISTURB"), store.saved!!.tokens.map { it.kind })

            env.failUndo.clear() // al prossimo avvio il tablet obbedisce
            env.log.clear()
            ModeEngine(env, store).recover()
            assertEquals(listOf("undo DO_NOT_DISTURB"), env.log)
            assertNull(store.saved)
        }

    @Test
    fun `a session left behind by a closed app is picked up again`() =
        runTest {
            val env = FakeEnv()
            val store = MemoryStore()
            ModeEngine(env, store, clock = { 5L }).enter(gaming)
            val restarted = ModeEngine(env, store)
            assertNull(restarted.active.value)
            restarted.recover()
            assertEquals("gaming", restarted.active.value!!.modeId) // ancora attiva: si può uscire
            restarted.exit()
            assertNull(store.saved)
        }

    @Test
    fun `a mode forgotten for too long ends by itself`() =
        runTest {
            var now = 0L
            val engine = ModeEngine(FakeEnv(), MemoryStore(), clock = { now })
            engine.enter(gaming)
            now = gaming.maxMinutes * 60_000L - 1
            assertFalse(engine.exitIfExpired())
            assertNotNull(engine.active.value)
            now = gaming.maxMinutes * 60_000L
            assertTrue(engine.exitIfExpired())
            assertNull(engine.active.value)
        }

    @Test
    fun `leaving with no active mode does nothing`() =
        runTest {
            val env = FakeEnv()
            val engine = ModeEngine(env, MemoryStore())
            assertEquals(ExitReport(0, 0), engine.exit())
            assertFalse(engine.exitIfExpired())
            assertTrue(env.log.isEmpty())
        }

    @Test
    fun `the session is saved before the first action so a stop in the middle can still be undone`() =
        runTest {
            val store = MemoryStore()
            val seen = mutableListOf<Session?>()
            val env =
                object : ModeEnvironment {
                    override suspend fun apply(action: ModeAction): ActionOutcome {
                        seen += store.saved // com'è il registro nel momento in cui parte l'azione
                        return ActionOutcome.Applied(UndoToken(action.name))
                    }

                    override suspend fun undo(token: UndoToken) = true
                }
            ModeEngine(env, store).enter(gaming)
            assertTrue(seen.all { it != null })
            assertEquals(0, seen.first()!!.tokens.size)
            assertEquals(1, seen[1]!!.tokens.size)
        }
}
