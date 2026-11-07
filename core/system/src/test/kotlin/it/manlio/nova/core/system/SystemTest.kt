package it.manlio.nova.core.system

import it.manlio.nova.core.system.SystemCommand.Namespace.GLOBAL
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

class RestrictedSettingsTest {
    @get:Rule val tmp = TemporaryFolder()

    private class Shell : CommandRunner {
        val sent = mutableListOf<SystemCommand>()
        var fail = false

        override suspend fun run(command: SystemCommand): ShellResult {
            sent += command
            return if (fail) ShellResult(1, "errore") else ShellResult(0, "")
        }
    }

    @Test
    fun `the command allows restricted settings only for NOVA itself`() {
        assertEquals(
            listOf("appops", "set", "it.manlio.nova", "ACCESS_RESTRICTED_SETTINGS", "allow"),
            SystemCommand.SetRestrictedSettings("it.manlio.nova", true).argv,
        )
        assertEquals("default", SystemCommand.SetRestrictedSettings("it.manlio.nova", false).argv.last())
        listOf("com.other.app", "it.manlio.nova.keyboard", "android", "it.manlio.nova allow", "").forEach {
            assertTrue(it, runCatching { SystemCommand.SetRestrictedSettings(it, true) }.isFailure)
        }
    }

    @Test
    fun `unlocking is logged once and reverted to the default`() =
        runTest {
            val shell = Shell()
            val log = SystemChangeLog(File(tmp.root, "restricted.json"), shell)
            assertTrue(log.allowRestrictedSettings("it.manlio.nova").ok)
            assertTrue(log.allowRestrictedSettings("it.manlio.nova").ok) // un secondo tocco non duplica la voce
            assertEquals(1, log.entries().filterIsInstance<SystemChange.RestrictedSettings>().size)
            shell.sent.clear()
            assertEquals(0, log.revertAll())
            assertEquals(listOf<SystemCommand>(SystemCommand.SetRestrictedSettings("it.manlio.nova", false)), shell.sent)
            assertTrue(log.entries().isEmpty())
        }

    @Test
    fun `a refused unlock is not recorded`() =
        runTest {
            val shell = Shell().also { it.fail = true }
            val log = SystemChangeLog(File(tmp.root, "refused.json"), shell)
            assertTrue(!log.allowRestrictedSettings("it.manlio.nova").ok)
            assertTrue(log.entries().isEmpty())
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

class CollapseShadeTest {
    @Test
    fun `it only asks the status bar to close`() {
        assertEquals(listOf("cmd", "statusbar", "collapse"), SystemCommand.CollapseShade.argv)
    }

    @Test
    fun `the lock touches only the expansion of the status bar, and none gives everything back`() {
        assertEquals(listOf("cmd", "statusbar", "send-disable-flag", "statusbar-expansion"), SystemCommand.ShadeLock(true).argv)
        assertEquals(listOf("cmd", "statusbar", "send-disable-flag", "none"), SystemCommand.ShadeLock(false).argv)
    }
}

class GameScaleTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val game = "com.supercell.brawlstars"

    @Test
    fun `game scale commands are fixed argument lists`() {
        assertEquals(listOf("cmd", "game", "set", "--downscale", "0.65", game), SystemCommand.GameScale(game, 65).argv)
        assertEquals(listOf("cmd", "game", "mode", "standard", game), SystemCommand.GameScale(game, null).argv)
    }

    @Test
    fun `scale below the minimum, odd values and protected packages are refused`() {
        listOf(60, 30, 0, 100, 75, -65).forEach { p -> assertTrue("$p", runCatching { SystemCommand.GameScale(game, p) }.isFailure) }
        assertTrue(runCatching { SystemCommand.GameScale("it.manlio.nova", 70) }.isFailure)
        assertTrue(runCatching { SystemCommand.GameScale("com.x; reboot", 70) }.isFailure)
    }

    @Test
    fun `steps go down to the minimum and then back to normal`() {
        val seen = generateSequence(SystemCommand.nextGameScale(null)) { SystemCommand.nextGameScale(it) }.toList()
        assertEquals(listOf(90, 80, 70, 65), seen)
    }

    @Test
    fun `the shade lock is one entry, survives a restart of the app and is undone by reverting everything`() =
        runTest {
            val ran = mutableListOf<SystemCommand>()
            val shell =
                CommandRunner {
                    ran += it
                    ShellResult(0, "")
                }
            val log = SystemChangeLog(File(tmp.root, "shade.json"), shell)
            log.setShadeLock(true)
            log.setShadeLock(true)
            assertEquals(1, log.entries().size)
            val reopened = SystemChangeLog(File(tmp.root, "shade.json"), shell)
            assertTrue(reopened.entries().single() is SystemChange.ShadeLock)

            assertEquals(0, reopened.revertAll())
            assertEquals(SystemCommand.ShadeLock(false), ran.last())
            assertTrue(reopened.entries().isEmpty())

            log.setShadeLock(false)
            assertTrue(log.entries().isEmpty())
        }

    @Test
    fun `the log keeps one entry per game, none at normal, and reverts to normal`() =
        runTest {
            val ran = mutableListOf<SystemCommand>()
            val shell =
                CommandRunner {
                    ran += it
                    ShellResult(0, "")
                }
            val log = SystemChangeLog(File(tmp.root, "scale.json"), shell)
            log.setGameScale(game, 70)
            log.setGameScale(game, 65)
            assertEquals(65, (log.entries().single() as SystemChange.GameScale).applied)
            val reopened = SystemChangeLog(File(tmp.root, "scale.json"), shell)
            assertEquals(65, (reopened.entries().single() as SystemChange.GameScale).applied)
            log.setGameScale(game, null)
            assertTrue(log.entries().isEmpty())

            log.setGameScale(game, 80)
            assertEquals(0, log.revertAll())
            assertTrue(log.entries().isEmpty())
            assertEquals(SystemCommand.GameScale(game, null), ran.last())
        }

    @Test
    fun `a refused scale changes nothing in the log`() =
        runTest {
            val log = SystemChangeLog(File(tmp.root, "no.json"), { ShellResult(1, "no") })
            assertFalse(log.setGameScale(game, 70).ok)
            assertTrue(log.entries().isEmpty())
        }

    @Test
    fun `screen recording stays in Movies and refuses anything but digits`() {
        val record = SystemCommand.ScreenRecord("20261107")
        assertEquals(listOf("screenrecord", "--time-limit", "180", "/sdcard/Movies/nova-20261107.mp4"), record.argv)
        listOf("", "../x", "1 2", "1;rm").forEach { bad ->
            assertTrue(runCatching { SystemCommand.ScreenRecord(bad) }.isFailure)
        }
    }

    @Test
    fun `a floating window opens one app and nothing else`() {
        val open = SystemCommand.OpenInWindow("com.example.app", "com.example.app.Main")
        assertEquals(listOf("am", "start", "--windowingMode", "5", "-n", "com.example.app/com.example.app.Main"), open.argv)
        assertTrue(runCatching { SystemCommand.OpenInWindow("com.example;rm", "com.example.Main") }.isFailure)
        assertTrue(runCatching { SystemCommand.OpenInWindow("com.example", "x y") }.isFailure)
    }

    @Test
    fun `blocking internet turns the filter on, is logged and fully reverted`() =
        runTest {
            val ran = mutableListOf<SystemCommand>()
            val shell =
                CommandRunner {
                    ran += it
                    ShellResult(0, "")
                }
            val log = SystemChangeLog(File(tmp.root, "firewall.json"), shell)

            log.setAppNetwork("com.example.app", blocked = true)
            assertEquals(setOf("com.example.app"), log.offlineApps())
            assertEquals(listOf(SystemCommand.NetFilter(true), SystemCommand.AppNetwork("com.example.app", false)), ran)

            ran.clear()
            log.revertAll()
            assertEquals(listOf(SystemCommand.AppNetwork("com.example.app", true), SystemCommand.NetFilter(false)), ran)
            assertTrue(log.offlineApps().isEmpty())
        }

    @Test
    fun `NOVA and protected packages never lose internet`() {
        assertTrue(runCatching { SystemCommand.AppNetwork(SystemCommand.OWN_PACKAGE, false) }.isFailure)
        assertTrue(runCatching { SystemCommand.AppNetwork("com.android.systemui", false) }.isFailure)
    }

    @Test
    fun `undo is a fixed key combination and a single key is refused`() {
        val undo = SystemCommand.KeyCombo(listOf(SystemCommand.KeyCombo.Key.CTRL, SystemCommand.KeyCombo.Key.Z))
        assertEquals(listOf("input", "keycombination", "KEYCODE_CTRL_LEFT", "KEYCODE_Z"), undo.argv)
        assertTrue(runCatching { SystemCommand.KeyCombo(listOf(SystemCommand.KeyCombo.Key.Z)) }.isFailure)
    }
}
