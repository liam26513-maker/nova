package it.manlio.nova.core.system

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Shell finta con impostazioni, pacchetti, densità e comandi eseguiti, per provare gli strumenti senza Android. */
private class FakeSystem : CommandRunner {
    val settings = mutableMapOf<String, String>()
    val disabled = mutableSetOf<String>()
    val stopped = mutableListOf<String>()
    var physical = 160
    var override: Int? = null
    val failPackages = mutableSetOf<String>()
    var failAll = false

    override suspend fun run(command: SystemCommand): ShellResult {
        if (failAll) return ShellResult(1, "errore")
        return when (command) {
            is SystemCommand.GetSetting -> ShellResult(0, settings["${command.namespace.value}.${command.key}"] ?: "null")
            is SystemCommand.PutSetting -> {
                val k = "${command.namespace.value}.${command.key}"
                if (command.value == null) settings.remove(k) else settings[k] = command.value
                ShellResult(0, "")
            }
            is SystemCommand.DisablePackage -> ShellResult(0, "").also { disabled += command.packageName }
            is SystemCommand.EnablePackage -> ShellResult(0, "").also { disabled -= command.packageName }
            is SystemCommand.ForceStop ->
                if (command.packageName in failPackages) {
                    ShellResult(1, "no")
                } else {
                    ShellResult(0, "").also {
                        stopped += command.packageName
                    }
                }
            SystemCommand.GetDensity ->
                ShellResult(0, "Physical density: $physical\n" + (override?.let { "Override density: $it\n" } ?: ""))
            is SystemCommand.SetDensity -> ShellResult(0, "").also { override = command.dpi?.takeIf { d -> d != physical } }
            else -> ShellResult(0, "")
        }
    }
}

class SystemToolsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun tools(shell: FakeSystem = FakeSystem()): Pair<SystemTools, FakeSystem> {
        val log = SystemChangeLog(File(tmp.root, "changes.json"), shell)
        return SystemTools(shell, log) to shell
    }

    @Test
    fun `animation scale sets all three settings, and reset puts the originals back`() =
        runTest {
            val shell = FakeSystem().apply { SystemTools.ANIMATOR_KEYS.forEach { settings["global.$it"] = "1.0" } }
            val (t, _) = tools(shell)
            assertEquals("1.0", t.animationScale())
            assertTrue(t.setAnimationScale("0.5"))
            SystemTools.ANIMATOR_KEYS.forEach { assertEquals("0.5", shell.settings["global.$it"]) }
            assertTrue(t.setAnimationScale("0"))
            assertTrue(t.resetAnimationScale())
            SystemTools.ANIMATOR_KEYS.forEach { assertEquals("1.0", shell.settings["global.$it"]) }
        }

    @Test
    fun `only the known animation scales are accepted`() =
        runTest {
            val (t, _) = tools()
            listOf("5", "abc", "0.5; reboot", "").forEach { assertTrue(it, runCatching { t.setAnimationScale(it) }.isFailure) }
        }

    @Test
    fun `ad blocking dns turns on and off and off restores what was there`() =
        runTest {
            val shell = FakeSystem().apply { settings["global.private_dns_mode"] = "opportunistic" }
            val (t, _) = tools(shell)
            assertFalse(t.adBlockDnsOn())
            assertTrue(t.setAdBlockDns(true))
            assertTrue(t.adBlockDnsOn())
            assertEquals("hostname", shell.settings["global.private_dns_mode"])
            assertEquals("dns.adguard.com", shell.settings["global.private_dns_specifier"])
            assertTrue(t.setAdBlockDns(false))
            assertEquals("opportunistic", shell.settings["global.private_dns_mode"]) // com'era
            assertNull(shell.settings["global.private_dns_specifier"]) // non esisteva
            assertFalse(t.adBlockDnsOn())
        }

    @Test
    fun `turbo stops the apps it is given, skips protected ones and counts failures`() =
        runTest {
            val shell = FakeSystem().apply { failPackages += "com.rotta" }
            val (t, _) = tools(shell)
            val r = t.turbo(listOf("com.gioco", "com.android.systemui", "com.rotta", "it.manlio.nova", "com.gioco", "com.chat"))
            assertEquals(listOf("com.gioco", "com.chat"), shell.stopped)
            assertEquals(2, r.stopped)
            assertEquals(3, r.failed) // systemui e nova protetti (rifiutati dal comando), com.rotta fallito
        }

    @Test
    fun `density is read, changed with a way back, and unconfirmed changes revert themselves`() =
        runTest {
            val (t, shell) = tools()
            assertEquals(Density(160, 160), t.density())
            assertTrue(t.setDensity(240))
            assertEquals(240, shell.override)
            assertTrue(t.revertUnconfirmedDensity()) // l'utente non ha confermato
            assertNull(shell.override)
            assertTrue(t.setDensity(200))
            t.confirmDensity()
            assertTrue(t.revertUnconfirmedDensity()) // niente da annullare
            assertEquals(200, shell.override)
        }

    @Test
    fun `a second change reverts to the original, not to the first attempt`() =
        runTest {
            val (t, shell) = tools()
            t.setDensity(240)
            t.setDensity(280)
            assertTrue(t.revertUnconfirmedDensity())
            assertNull(shell.override)
        }

    @Test
    fun `the density log survives a restart, so a crash still gets undone`() =
        runTest {
            val shell = FakeSystem()
            val file = File(tmp.root, "changes.json")
            SystemTools(shell, SystemChangeLog(file, shell)).setDensity(260)
            val again = SystemTools(shell, SystemChangeLog(file, shell)) // dopo il riavvio dell'app
            assertTrue(again.revertUnconfirmedDensity())
            assertNull(shell.override)
        }

    @Test
    fun `revert all also puts the density back`() =
        runTest {
            val shell = FakeSystem()
            val log = SystemChangeLog(File(tmp.root, "changes.json"), shell)
            SystemTools(shell, log).setDensity(300)
            assertEquals(0, log.revertAll())
            assertNull(shell.override)
        }

    @Test
    fun `density limits are enforced by the command itself`() {
        listOf(0, 100, 119, 641, 5000, -160).forEach { assertTrue("$it", runCatching { SystemCommand.SetDensity(it) }.isFailure) }
        assertEquals(listOf("wm", "density", "240"), SystemCommand.SetDensity(240).argv)
        assertEquals(listOf("wm", "density", "reset"), SystemCommand.SetDensity(null).argv)
        SystemCommand.SetDensity(SystemCommand.MIN_DPI)
        SystemCommand.SetDensity(SystemCommand.MAX_DPI)
    }

    @Test
    fun `wm output is parsed, with and without an override`() {
        assertEquals(Density(160, 160), SystemTools.parseDensity("Physical density: 160\n"))
        assertEquals(Density(160, 240), SystemTools.parseDensity("Physical density: 160\nOverride density: 240\n"))
        assertNull(SystemTools.parseDensity("errore"))
        assertNull(SystemTools.parseDensity(""))
    }

    @Test
    fun `freezing is logged, listed and undone, and protected apps cannot be frozen`() =
        runTest {
            val (t, shell) = tools()
            assertTrue(t.freeze("com.vendor.bloat"))
            assertEquals(listOf("com.vendor.bloat"), t.frozen())
            assertTrue(shell.disabled.contains("com.vendor.bloat"))
            assertTrue(runCatching { t.freeze("com.android.settings") }.isFailure)
            assertTrue(runCatching { t.freeze("it.manlio.nova") }.isFailure)
            assertTrue(t.unfreeze("com.vendor.bloat"))
            assertTrue(t.frozen().isEmpty())
            assertFalse(shell.disabled.contains("com.vendor.bloat"))
        }

    @Test
    fun `when the shell fails nothing is recorded as done`() =
        runTest {
            val shell = FakeSystem().apply { failAll = true }
            val (t, _) = tools(shell)
            assertFalse(t.freeze("com.vendor.bloat"))
            assertTrue(t.frozen().isEmpty())
            assertFalse(t.setAnimationScale("0.5"))
            assertNull(t.density())
            assertFalse(t.setDensity(240))
        }
}

class TurboPlanTest {
    @Test
    fun `turbo keeps favorites, music, the keyboard and protected apps`() {
        val all = listOf("com.gioco", "com.chat", "com.musica", "com.tastiera", "com.android.settings", "it.manlio.nova", "com.gioco")
        assertEquals(listOf("com.gioco", "com.chat"), TurboPlan.candidates(all, keep = setOf("com.musica", "com.tastiera")))
        assertEquals(emptyList<String>(), TurboPlan.candidates(emptyList(), emptySet()))
    }
}
