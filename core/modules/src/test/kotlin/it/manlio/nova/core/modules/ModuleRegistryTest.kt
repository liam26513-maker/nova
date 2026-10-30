package it.manlio.nova.core.modules

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleRegistryTest {
    private class FakeModule(
        override val id: String,
        override val defaultEnabled: Boolean = true,
        override val requires: Set<Capability> = emptySet(),
        override val dependsOn: Set<String> = emptySet(),
        var failStart: Boolean = false,
        val log: MutableList<String> = mutableListOf(),
    ) : NovaModule {
        override val displayName = id

        override suspend fun start(context: ModuleContext) {
            log += "start:$id"
            if (failStart) error("boom $id")
        }

        override suspend fun stop() {
            log += "stop:$id"
        }
    }

    @Test
    fun `default enabled modules start, disabled ones do not`() =
        runTest {
            val a = FakeModule("a")
            val b = FakeModule("b", defaultEnabled = false)
            val r = ModuleRegistry(listOf(a, b))
            r.apply(emptyMap(), emptySet())
            assertEquals(ModuleState.Running, r.states.value["a"])
            assertEquals(ModuleState.Disabled, r.states.value["b"])
        }

    @Test
    fun `config flags override defaults and unknown keys are ignored`() =
        runTest {
            val a = FakeModule("a")
            val r = ModuleRegistry(listOf(a))
            r.apply(mapOf("a" to false, "ghost" to true), emptySet())
            assertEquals(ModuleState.Disabled, r.states.value["a"])
        }

    @Test
    fun `module waits for missing capability and starts once granted`() =
        runTest {
            val a = FakeModule("a", requires = setOf(Capability.SHIZUKU))
            val r = ModuleRegistry(listOf(a))
            r.apply(emptyMap(), emptySet())
            assertEquals(ModuleState.Waiting(setOf(Capability.SHIZUKU), emptySet()), r.states.value["a"])
            r.apply(emptyMap(), setOf(Capability.SHIZUKU))
            assertEquals(ModuleState.Running, r.states.value["a"])
        }

    @Test
    fun `dependencies start first and stop last`() =
        runTest {
            val log = mutableListOf<String>()
            val base = FakeModule("base", log = log)
            val top = FakeModule("top", dependsOn = setOf("base"), log = log)
            val r = ModuleRegistry(listOf(top, base))
            r.apply(emptyMap(), emptySet())
            r.apply(mapOf("base" to false), emptySet())
            assertEquals(listOf("start:base", "start:top", "stop:top", "stop:base"), log)
            assertEquals(ModuleState.Waiting(emptySet(), setOf("base")), r.states.value["top"])
        }

    @Test
    fun `dependent is stopped when its dependency fails to restart`() =
        runTest {
            val base = FakeModule("base")
            val top = FakeModule("top", dependsOn = setOf("base"))
            val r = ModuleRegistry(listOf(base, top))
            r.apply(emptyMap(), emptySet())
            base.failStart = true
            r.reportCrash("base", IllegalStateException("crash"))
            assertTrue(r.states.value["top"] is ModuleState.Waiting)
        }

    @Test
    fun `three crashes within ten minutes suspend the module`() =
        runTest {
            var now = 0L
            val a = FakeModule("a")
            val r = ModuleRegistry(listOf(a)) { now }
            r.apply(emptyMap(), emptySet())
            repeat(2) {
                now += 60_000
                r.reportCrash("a", IllegalStateException("crash"))
                assertEquals(ModuleState.Running, r.states.value["a"]) // riavviato
            }
            now += 60_000
            r.reportCrash("a", IllegalStateException("crash"))
            assertEquals(ModuleState.Suspended("crash"), r.states.value["a"])
            // La configurazione non lo riattiva da sola…
            r.apply(mapOf("a" to true), emptySet())
            assertTrue(r.states.value["a"] is ModuleState.Suspended)
            // …l'utente sì.
            r.resume("a")
            assertEquals(ModuleState.Running, r.states.value["a"])
        }

    @Test
    fun `crashes spread over more than ten minutes do not suspend`() =
        runTest {
            var now = 0L
            val r = ModuleRegistry(listOf(FakeModule("a"))) { now }
            r.apply(emptyMap(), emptySet())
            repeat(3) {
                now += 6 * 60_000
                r.reportCrash("a", IllegalStateException("crash"))
            }
            assertEquals(ModuleState.Running, r.states.value["a"])
        }

    @Test
    fun `failing start counts as crash and leads to suspension`() =
        runTest {
            val a = FakeModule("a", failStart = true)
            val r = ModuleRegistry(listOf(a)) { 0L }
            repeat(3) { r.apply(emptyMap(), emptySet()) }
            assertTrue(r.states.value["a"] is ModuleState.Suspended)
        }

    @Test(expected = IllegalArgumentException::class)
    fun `circular dependencies are rejected`() {
        ModuleRegistry(listOf(FakeModule("a", dependsOn = setOf("b")), FakeModule("b", dependsOn = setOf("a"))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown dependency is rejected`() {
        ModuleRegistry(listOf(FakeModule("a", dependsOn = setOf("missing"))))
    }
}
