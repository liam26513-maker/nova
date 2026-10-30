package it.manlio.nova.core.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BootGuardTest {
    @get:Rule val tmp = TemporaryFolder()
    private var now = 1_000_000L

    private fun guard(file: File = File(tmp.root, "boot.properties")) = BootGuard(KeyValueFile(file)) { now }

    @Test
    fun `healthy launches never enter safe mode`() {
        repeat(10) {
            val g = guard()
            assertNull(g.onLaunch(1))
            g.markHealthy()
        }
    }

    @Test
    fun `three failed starts in a row enter safe mode on the fourth and stay until the user leaves`() {
        repeat(3) { assertNull(guard().onLaunch(1)) } // nessuno arriva a markHealthy
        assertEquals(SafeModeReason.REPEATED_START_CRASHES, guard().onLaunch(1))
        val g = guard()
        assertEquals(SafeModeReason.REPEATED_START_CRASHES, g.onLaunch(1))
        g.markHealthy() // anche se la modalità provvisoria funziona bene…
        assertEquals(SafeModeReason.REPEATED_START_CRASHES, guard().onLaunch(1)) // …si resta dentro
        guard().leaveSafeMode()
        assertNull(guard().onLaunch(1))
    }

    @Test
    fun `a healthy start resets the failure count`() {
        repeat(2) { guard().onLaunch(1) }
        val g = guard()
        g.onLaunch(1)
        g.markHealthy()
        repeat(2) { assertNull(guard().onLaunch(1)) }
    }

    @Test
    fun `two crashes within a minute of an update force safe mode`() {
        guard().apply {
            onLaunch(1)
            markHealthy()
        }
        val g = guard()
        assertNull(g.onLaunch(2)) // aggiornamento
        now += 10_000
        g.onCrash()
        now += 20_000
        guard().onCrash()
        assertEquals(SafeModeReason.CRASHES_AFTER_UPDATE, guard().onLaunch(2))
    }

    @Test
    fun `crashes long after an update do not force safe mode`() {
        guard().onLaunch(1)
        guard().leaveSafeMode()
        guard().onLaunch(2)
        now += 120_000
        repeat(2) { guard().onCrash() }
        val g = guard()
        assertTrue(g.onLaunch(2) == null)
    }

    @Test
    fun `manual request is honoured`() {
        guard().apply {
            onLaunch(1)
            markHealthy()
        }
        guard().requestSafeMode()
        assertEquals(SafeModeReason.REQUESTED, guard().onLaunch(1))
    }

    @Test
    fun `corrupted state file is treated as empty`() {
        val f = File(tmp.root, "boot.properties").apply { writeBytes(byteArrayOf(0, 1, 2, -1, 92, 117)) }
        assertNull(guard(f).onLaunch(1))
    }
}

class CrashReporterTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `report contains version, stack trace and recent log, and old reports are pruned`() {
        val log = LocalLog(capacity = 3)
        repeat(5) { log.i("test", "evento $it") }
        val guard = BootGuard(KeyValueFile(tmp.newFile("b.properties")))
        var t = 0L
        val reporter = CrashReporter(tmp.newFolder("crash"), log, guard, "0.1.0") { t++ }
        val file = reporter.write(Thread.currentThread(), IllegalStateException("boom"))
        val text = file.readText()
        assertTrue(text.contains("NOVA 0.1.0"))
        assertTrue(text.contains("IllegalStateException: boom"))
        assertTrue(text.contains("evento 4"))
        assertTrue(!text.contains("evento 1")) // fuori dalla capacità del registro
        repeat(CrashReporter.KEEP + 5) { reporter.write(Thread.currentThread(), RuntimeException()) }
        assertEquals(CrashReporter.KEEP, reporter.pending().size)
        reporter.clear()
        assertEquals(0, reporter.pending().size)
    }

    @Test
    fun `uncaught exception is recorded and forwarded to the previous handler`() {
        val dir = tmp.newFolder("c")
        val guard = BootGuard(KeyValueFile(File(tmp.root, "g.properties")))
        var forwarded: Throwable? = null
        val old = Thread.getDefaultUncaughtExceptionHandler()
        try {
            Thread.setDefaultUncaughtExceptionHandler { _, e -> forwarded = e }
            val reporter = CrashReporter(dir, LocalLog(), guard, "0.1.0")
            reporter.install()
            val error = IllegalArgumentException("x")
            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), error)
            assertEquals(error, forwarded)
            assertEquals(1, reporter.pending().size)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(old)
        }
    }
}

class LocalLogTest {
    @Test
    fun `keeps only the most recent entries`() {
        val log = LocalLog(capacity = 2) { 0 }
        log.i("a", "1")
        log.w("a", "2")
        log.e("a", "3", RuntimeException("r"))
        assertEquals(listOf("2", "3: RuntimeException: r"), log.snapshot().map { it.message })
    }
}
