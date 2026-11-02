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
                { SystemCommand.InstallApk(0, "it.manlio.nova") },
                { SystemCommand.InstallApk(1, "it.manlio.nova; reboot") },
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
    fun `apk install reads the file from standard input with its exact size`() {
        val ok = SystemCommand.InstallApk(2_954_000, "it.manlio.nova")
        assertEquals(listOf("pm", "install", "-r", "-i", "it.manlio.nova", "-S", "2954000"), ok.argv)
    }

    @Test
    fun `apk install refuses impossible sizes and bad package names`() {
        listOf(0L, -1L, SystemCommand.MAX_APK_BYTES + 1, Long.MIN_VALUE).forEach {
            assertTrue("$it", runCatching { SystemCommand.InstallApk(it, "it.manlio.nova") }.isFailure)
        }
        listOf("", "x", "it.manlio.nova -S 1", "../x").forEach {
            assertTrue(it, runCatching { SystemCommand.InstallApk(1000, it) }.isFailure)
        }
        SystemCommand.InstallApk(SystemCommand.MAX_APK_BYTES, "it.manlio.nova")
    }
}

class QuickToggleCommandTest {
    @Test
    fun `every toggle has a fixed command for on and for off`() {
        val expected =
            mapOf(
                QuickToggle.WIFI to ("svc wifi enable" to "svc wifi disable"),
                QuickToggle.BLUETOOTH to ("svc bluetooth enable" to "svc bluetooth disable"),
                QuickToggle.AIRPLANE to ("cmd connectivity airplane-mode enable" to "cmd connectivity airplane-mode disable"),
                QuickToggle.DND to ("cmd notification set_dnd priority" to "cmd notification set_dnd off"),
                QuickToggle.DARK_THEME to ("cmd uimode night yes" to "cmd uimode night no"),
                QuickToggle.LOCATION to ("cmd location set-location-enabled true" to "cmd location set-location-enabled false"),
                QuickToggle.BATTERY_SAVER to ("settings put global low_power 1" to "settings put global low_power 0"),
                QuickToggle.DATA_SAVER to ("cmd netpolicy set restrict-background true" to "cmd netpolicy set restrict-background false"),
                QuickToggle.NIGHT_LIGHT to
                    ("settings put secure night_display_activated 1" to "settings put secure night_display_activated 0"),
                QuickToggle.INVERSION to
                    (
                        "settings put secure accessibility_display_inversion_enabled 1" to
                            "settings put secure accessibility_display_inversion_enabled 0"
                    ),
            )
        assertEquals(QuickToggle.entries.toSet(), expected.keys) // un comando per ogni interruttore, nessuno dimenticato
        expected.forEach { (toggle, pair) ->
            assertEquals(pair.first.split(' '), SystemCommand.SetToggle(toggle, true).argv)
            assertEquals(pair.second.split(' '), SystemCommand.SetToggle(toggle, false).argv)
        }
    }

    @Test
    fun `no toggle command contains shell metacharacters`() {
        QuickToggle.entries.forEach { t ->
            listOf(true, false).forEach { on ->
                SystemCommand.SetToggle(t, on).argv.forEach { assertTrue(it, Regex("^[A-Za-z0-9_.-]+$").matches(it)) }
            }
        }
    }
}

class SystemChangeLogTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Finta shell con impostazioni e pacchetti in memoria. */
    private class FakeShell : CommandRunner {
        val settings = mutableMapOf("global.animator_duration_scale" to "1.0")
        val disabled = mutableSetOf<String>()
        val sent = mutableListOf<SystemCommand>()
        var failNext = false

        override suspend fun run(command: SystemCommand): ShellResult {
            if (failNext) return ShellResult(1, "errore").also { failNext = false }
            sent += command
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
    fun `a toggle changed many times keeps one entry with the original state, and revert restores it`() =
        runTest {
            val shell = FakeShell()
            val log = SystemChangeLog(File(tmp.root, "toggles.json"), shell)
            log.setToggle(QuickToggle.WIFI, on = false, wasOn = true)
            log.setToggle(QuickToggle.WIFI, on = true, wasOn = false)
            log.setToggle(QuickToggle.WIFI, on = false, wasOn = true)
            log.setToggle(QuickToggle.AIRPLANE, on = true, wasOn = false)
            val toggles = log.entries().filterIsInstance<SystemChange.Toggle>()
            assertEquals(2, toggles.size)
            assertEquals(true, toggles.first { it.toggle == QuickToggle.WIFI }.previous) // originale: acceso
            shell.sent.clear()
            assertEquals(0, log.revertAll())
            assertTrue(SystemCommand.SetToggle(QuickToggle.WIFI, true) in shell.sent)
            assertTrue(SystemCommand.SetToggle(QuickToggle.AIRPLANE, false) in shell.sent)
            assertTrue(log.entries().isEmpty())
        }

    @Test
    fun `turning a toggle back to its original state leaves nothing to undo`() =
        runTest {
            val log = SystemChangeLog(File(tmp.root, "back.json"), FakeShell())
            log.setToggle(QuickToggle.BLUETOOTH, on = false, wasOn = true)
            assertEquals(1, log.entries().size)
            log.setToggle(QuickToggle.BLUETOOTH, on = true, wasOn = false)
            assertTrue(log.entries().isEmpty())
        }

    @Test
    fun `a toggle that Android refuses is not recorded`() =
        runTest {
            val shell = FakeShell()
            val log = SystemChangeLog(File(tmp.root, "refused.json"), shell)
            shell.failNext = true
            assertTrue(!log.setToggle(QuickToggle.WIFI, on = false, wasOn = true).ok)
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
