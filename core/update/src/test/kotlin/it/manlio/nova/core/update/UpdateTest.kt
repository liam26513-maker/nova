package it.manlio.nova.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdatePlannerTest {
    private var nextId = 1L

    private fun asset(name: String) = Asset(nextId++, name)

    private fun app(
        tag: String,
        code: Int,
        withChecksum: Boolean = true,
        draft: Boolean = false,
        prerelease: Boolean = false,
    ) = Release(
        tag = tag,
        draft = draft,
        prerelease = prerelease,
        assets = listOfNotNull(asset("nova-$code.apk"), if (withChecksum) asset("nova-$code.apk.sha256") else null),
    )

    private fun config(
        version: String,
        withChecksum: Boolean = true,
    ) = Release(
        tag = "config-v$version",
        assets = listOfNotNull(asset("nova-config.json"), if (withChecksum) asset("nova-config.json.sha256") else null),
    )

    @Test
    fun `picks the newest app release above the installed version`() {
        val plan = UpdatePlanner.plan(listOf(app("v0.2.0", 3), app("v0.4.0", 7), app("v0.1.0", 1)), installedVersionCode = 3, "0.1.0")
        assertEquals(7, plan.app?.versionCode)
        assertEquals("v0.4.0", plan.app?.tag)
    }

    @Test
    fun `nothing newer means no app update`() {
        assertNull(UpdatePlanner.plan(listOf(app("v0.2.0", 3)), installedVersionCode = 3, "0.1.0").app)
    }

    @Test
    fun `drafts, pre-releases and assets without checksum are ignored`() {
        val plan =
            UpdatePlanner.plan(
                listOf(
                    app("v1", 9, draft = true),
                    app("v2", 10, prerelease = true),
                    app("v3", 11, withChecksum = false),
                    config("9.9.9", false),
                ),
                installedVersionCode = 1,
                installedConfigVersion = "0.1.0",
            )
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `config updates compare versions numerically`() {
        val plan = UpdatePlanner.plan(listOf(config("0.9.0"), config("0.10.0"), config("0.1.0")), 1, installedConfigVersion = "0.2.0")
        assertEquals("0.10.0", plan.config?.version)
        assertNull(UpdatePlanner.plan(listOf(config("0.2.0")), 1, "0.2.0").config)
    }

    @Test
    fun `previous versions are older ones only, newest first, without drafts or missing checksums`() {
        val releases =
            listOf(
                app("v0.1.101", 101),
                app("v0.1.103", 103),
                app("v0.1.104", 104),
                app("v0.1.102", 102, withChecksum = false),
                app("v0.1.100", 100, draft = true),
                config("0.2.0"),
            )
        val previous = UpdatePlanner.previousVersions(releases, installedVersionCode = 104)
        assertEquals(listOf(103, 101), previous.map { it.update.versionCode })
        assertTrue(previous.all { it.standby == null })
        assertEquals(listOf(103), UpdatePlanner.previousVersions(releases, 104, limit = 1).map { it.update.versionCode })
        assertTrue(UpdatePlanner.previousVersions(releases, 101).isEmpty())
    }

    @Test
    fun `the standby APK of the installed release makes going back to that version instant`() {
        fun withStandby(
            release: Release,
            name: String,
            checksum: Boolean = true,
        ) = release.copy(assets = release.assets + listOfNotNull(asset(name), if (checksum) asset("$name.sha256") else null))
        val installed = withStandby(app("v0.1.110", 110), "nova-ripristino-111-v0.1.102.apk")
        val releases = listOf(app("v0.1.101", 101), app("v0.1.102", 102), installed)

        val previous = UpdatePlanner.previousVersions(releases, installedVersionCode = 110)
        assertEquals(listOf("v0.1.102", "v0.1.101"), previous.map { it.update.tag })
        val standby = previous.first().standby!!
        assertEquals(111, standby.versionCode)
        assertEquals("nova-ripristino-111-v0.1.102.apk", standby.apk.name)
        assertNull(previous[1].standby)

        // Il ripristino non diventa mai un aggiornamento automatico.
        assertNull(UpdatePlanner.plan(releases, 110, "0.0.0").app)
        // Senza impronta non si usa.
        val noChecksum =
            listOf(app("v0.1.102", 102), withStandby(app("v0.1.110", 110), "nova-ripristino-111-v0.1.102.apk", checksum = false))
        assertNull(UpdatePlanner.previousVersions(noChecksum, 110).first().standby)
        // Il ripristino allegato a un'altra release (non quella installata) non conta.
        assertNull(UpdatePlanner.previousVersions(releases + app("v0.1.120", 120), 120).first { it.update.tag == "v0.1.102" }.standby)
    }

    @Test
    fun `semantic version comparison`() {
        assertTrue(UpdatePlanner.compareVersions("1.10.0", "1.9.3") > 0)
        assertTrue(UpdatePlanner.compareVersions("0.1", "0.1.0") == 0)
        assertTrue(UpdatePlanner.compareVersions("0.1.0", "0.1.1") < 0)
    }
}

class ChecksumsTest {
    @Test
    fun `sha256 and sha256sum format`() {
        val hash = Checksums.sha256("abc".toByteArray())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash)
        assertEquals(hash, Checksums.parse("${hash.uppercase()}  nova-12.apk\n"))
        assertNull(Checksums.parse("non è un'impronta"))
        assertNull(Checksums.parse(""))
    }
}

class LocalHistoryTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `config bundles keep the active one and allow stepping back`() {
        val h = LocalHistory(tmp.root)
        assertNull(h.activeBundle())
        h.saveBundle("0.2.0", "{\"a\":2}")
        h.saveBundle("0.10.0", "{\"a\":10}")
        assertEquals("0.10.0", h.activeVersion())
        assertEquals("{\"a\":10}", h.activeBundle())
        assertEquals("0.2.0" to "{\"a\":2}", h.previousBundle())
        h.setActive("0.2.0")
        assertNull(h.previousBundle()) // prima di 0.2.0 ci sono solo i default
    }

    @Test
    fun `only the last ten bundles and restore points are kept`() {
        val h = LocalHistory(tmp.root)
        repeat(15) { i ->
            h.saveBundle("0.$i.0", "{}")
            h.addRestorePoint(RestorePoint(i.toLong(), "test", 1, "0.1.0", "0.$i.0"))
        }
        assertEquals(10, h.bundleVersions().size)
        assertEquals("0.5.0", h.bundleVersions().first())
        assertEquals(10, h.restorePoints().size)
        assertEquals(14L, h.restorePoints().last().time)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bundle versions are validated (no paths in file names)`() {
        LocalHistory(tmp.root).saveBundle("../../evil", "{}")
    }
}
