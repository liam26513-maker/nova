package it.manlio.nova.core.config

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ConfigEngineTest {
    private val configDir = File(System.getProperty("nova.configDir") ?: "../../config")

    private fun read(path: String) = File(configDir, path).readText()

    private fun repoDefaults(theme: String = "theme/light.json"): JsonObject =
        ConfigEngine.assemble(read("meta.json"), read("brand.json"), read(theme), read("modules.json"))

    @Test
    fun `every config file in the repository loads and validates`() {
        val themes = File(configDir, "theme").listFiles { f -> f.extension == "json" }!!.map { "theme/${it.name}" }
        assertTrue("nessun tema trovato", themes.isNotEmpty())
        themes.forEach { theme ->
            val config = ConfigEngine(appVersionCode = 1, defaults = repoDefaults(theme)).current.value
            assertEquals(emptyList<String>(), ConfigValidator.validate(config))
        }
    }

    @Test
    fun `bundle overrides only the keys it contains`() {
        val engine = ConfigEngine(1, repoDefaults())
        val r = engine.applyBundle("""{"theme":{"colors":{"accent":"#AF52DE"}}}""")
        assertTrue(r is ApplyResult.Applied)
        val c = engine.current.value
        assertEquals("#AF52DE", c.theme.colors.accent)
        assertEquals("#FFFFFF", c.theme.colors.surface) // non toccato
        assertEquals("NOVA", c.brand.displayName)
    }

    @Test
    fun `local overrides win over the bundle and survive a new bundle`() {
        val engine = ConfigEngine(1, repoDefaults())
        engine.setOverrides("""{"brand":{"displayName":"Il mio tablet"}}""")
        engine.applyBundle("""{"brand":{"displayName":"NOVA 2","accentColor":"#FF9500"}}""")
        val c = engine.current.value
        assertEquals("Il mio tablet", c.brand.displayName)
        assertEquals("#FF9500", c.brand.accentColor)
        engine.clearOverrides()
        assertEquals("NOVA 2", engine.current.value.brand.displayName)
    }

    @Test
    fun `invalid bundle is rejected and the previous config is kept`() {
        val engine = ConfigEngine(1, repoDefaults())
        val before = engine.current.value
        val cases =
            listOf(
                "non è json",
                """{"theme":{"colors":{"accent":"viola"}}}""",
                """{"theme":{"materialOpacity":3}}""",
                """{"theme":{"radii":{"small":30}}}""",
                """{"brand":{"campoSconosciuto":1}}""",
                """{"modules":{"schemaVersion":2}}""",
            )
        cases.forEach { json ->
            val r = engine.applyBundle(json)
            assertTrue("doveva essere rifiutato: $json", r is ApplyResult.Rejected)
            assertEquals(before, engine.current.value)
        }
    }

    @Test
    fun `bundle requiring a newer app is rejected`() {
        val engine = ConfigEngine(appVersionCode = 1, defaults = repoDefaults())
        val r = engine.applyBundle("""{"meta":{"version":"0.2.0","minAppVersion":5}}""")
        assertTrue(r is ApplyResult.Rejected)
        assertTrue((r as ApplyResult.Rejected).reason.contains("versione 5"))
    }

    @Test
    fun `current emits the new value after a change (hot reload)`() {
        val engine = ConfigEngine(1, repoDefaults())
        engine.applyBundle("""{"modules":{"modules":{"island":false}}}""")
        assertEquals(mapOf("island" to false), engine.current.value.modules.modules)
    }

    @Test(expected = IllegalStateException::class)
    fun `invalid compiled defaults fail fast`() {
        ConfigEngine(1, ConfigEngine.deepMerge(repoDefaults(), ConfigEngine.JSON.parseToJsonElement("""{"brand":{"accentColor":"x"}}""") as JsonObject))
    }
}
