package it.manlio.nova.core.system

import it.manlio.nova.core.system.SystemCommand.Namespace.GLOBAL
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SystemCommandTest {
    @Test
    fun `commands become fixed argument lists`() {
        assertEquals(
            listOf("settings", "put", "global", "animator_duration_scale", "0.5"),
            SystemCommand.PutSetting(GLOBAL, "animator_duration_scale", "0.5").argv,
        )
        assertEquals(listOf("settings", "delete", "global", "x_y"), SystemCommand.PutSetting(GLOBAL, "x_y", null).argv)
        assertEquals(listOf("pm", "disable-user", "--user", "0", "com.vendor.bloat"), SystemCommand.DisablePackage("com.vendor.bloat").argv)
    }

    @Test
    fun `sleep only sends the sleep key, with nothing to revert`() {
        assertEquals(listOf("input", "keyevent", "KEYCODE_SLEEP"), SystemCommand.Sleep.argv)
    }

    @Test
    fun `injection attempts are rejected before reaching the shell`() {
        val bad =
            listOf(
                { SystemCommand.PutSetting(GLOBAL, "a; rm -rf /", "1") },
                { SystemCommand.PutSetting(GLOBAL, "ok_key", "1; reboot") },
                { SystemCommand.PutSetting(GLOBAL, "ok_key", "\$(id)") },
                { SystemCommand.DisablePackage("com.x && reboot") },
                { SystemCommand.ForceStop("../etc") },
                { SystemCommand.InstallApk("/sdcard/Download/evil.apk", "it.manlio.nova") },
                { SystemCommand.InstallApk("/sdcard/Android/data/it.manlio.nova/cache/../../x.apk", "it.manlio.nova") },
            )
        bad.forEach { make -> assertTrue(runCatching(make).isFailure) }
    }

    @Test
    fun `protected packages can never be disabled or stopped`() {
        val protectedOnes =
            listOf(
                "com.android.systemui",
                "com.android.settings",
                "it.manlio.nova",
                "com.android.providers.media",
                "moe.shizuku.privileged.api",
            )
        protectedOnes
            .forEach { pkg ->
                assertTrue(pkg, runCatching { SystemCommand.DisablePackage(pkg) }.isFailure)
                assertTrue(pkg, runCatching { SystemCommand.ForceStop(pkg) }.isFailure)
            }
        SystemCommand.EnablePackage("com.android.settings") // riabilitare è sempre permesso
    }

    @Test
    fun `apk install is allowed only from the app's own external folder`() {
        val ok = SystemCommand.InstallApk("/storage/emulated/0/Android/data/it.manlio.nova/cache/nova-12.apk", "it.manlio.nova")
        assertEquals("install", ok.argv[1])
    }
}

class SystemChangeLogTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Finta shell con impostazioni e pacchetti in memoria. */
    private class FakeShell : CommandRunner {
        val settings = mutableMapOf("global.animator_duration_scale" to "1.0")
        val disabled = mutableSetOf<String>()
        var failNext = false

        override suspend fun run(command: SystemCommand): ShellResult {
            if (failNext) return ShellResult(1, "errore").also { failNext = false }
            return when (command) {
                is SystemCommand.GetSetting -> ShellResult(0, settings["${command.namespace.value}.${command.key}"] ?: "null")
                is SystemCommand.PutSetting -> {
                    val k = "${command.namespace.value}.${command.key}"
                    if (command.value == null) settings.remove(k) else settings[k] = command.value
                    ShellResult(0, "")
                }
                is SystemCommand.DisablePackage -> ShellResult(0, "").also { disabled += command.packageName }
                is SystemCommand.EnablePackage -> ShellResult(0, "").also { disabled -= command.packageName }
                else -> ShellResult(0, "")
            }
        }
    }

    @Test
    fun `revert restores every original value, even after repeated changes`() =
        runTest {
            val shell = FakeShell()
            val log = SystemChangeLog(File(tmp.root, "changes.json"), shell)
            log.putSetting(GLOBAL, "animator_duration_scale", "0.5")
            log.putSetting(GLOBAL, "animator_duration_scale", "0.0")
            log.putSetting(GLOBAL, "new_key", "1")
            log.disablePackage("com.vendor.bloat")
            assertEquals(4, log.entries().size)
            assertEquals(0, log.revertAll())
            assertEquals(mapOf("global.animator_duration_scale" to "1.0"), shell.settings)
            assertTrue(shell.disabled.isEmpty())
            assertTrue(log.entries().isEmpty())
        }

    @Test
    fun `the log survives a restart`() =
        runTest {
            val file = File(tmp.root, "changes.json")
            val shell = FakeShell()
            SystemChangeLog(file, shell).putSetting(GLOBAL, "animator_duration_scale", "0.5")
            val reloaded = SystemChangeLog(file, shell)
            assertEquals(1, reloaded.entries().size)
            reloaded.revertAll()
            assertEquals("1.0", shell.settings["global.animator_duration_scale"])
        }

    @Test
    fun `failed changes are not recorded and failed reverts are kept`() =
        runTest {
            val shell = FakeShell()
            val log = SystemChangeLog(File(tmp.root, "c.json"), shell)
            shell.failNext = true
            assertTrue(!log.putSetting(GLOBAL, "animator_duration_scale", "0.5").ok)
            assertTrue(log.entries().isEmpty())
            log.disablePackage("com.vendor.bloat")
            shell.failNext = true
            assertEquals(1, log.revertAll())
            assertEquals(1, log.entries().size)
        }
}
