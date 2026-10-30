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
                listOf(app("v1", 9, draft = true), app("v2", 10, prerelease = true), app("v3", 11, withChecksum = false), config("9.9.9", false)),
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
