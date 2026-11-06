package it.manlio.nova

import it.manlio.nova.core.config.ConfigEngine
import it.manlio.nova.core.config.DebloatApp
import it.manlio.nova.core.config.DebloatConfig
import it.manlio.nova.core.system.ProtectedPackages
import it.manlio.nova.core.system.parsePackages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DebloatTest {
    private fun app(
        name: String,
        level: String,
    ) = DebloatApp(name, level, "nota")

    private val apps =
        mapOf(
            "com.b.consider" to app("Alfa", "consider"),
            "com.a.safe" to app("Zeta", "safe"),
            "com.c.absent" to app("Beta", "safe"),
            "com.d.frozen" to app("Gamma", "safe"),
            "com.google.android.gms" to app("Protetta", "safe"),
        )

    @Test
    fun `only what is on the tablet, not frozen and not protected, safe apps first`() {
        val installed = setOf("com.a.safe", "com.b.consider", "com.d.frozen", "com.google.android.gms")
        assertEquals(listOf("com.a.safe", "com.b.consider"), debloatList(apps, installed, listOf("com.d.frozen")).map { it.first })
    }

    @Test
    fun `the list in the repository never proposes a protected package`() {
        val config = ConfigEngine.JSON.decodeFromString(DebloatConfig.serializer(), File("../config/debloat.json").readText())
        assertTrue(config.apps.isNotEmpty())
        config.apps.forEach { (pkg, app) ->
            assertFalse(pkg, ProtectedPackages.isProtected(pkg))
            assertTrue(pkg, app.level == "safe" || app.level == "consider")
        }
    }

    @Test
    fun `package lines are read, anything else is ignored`() {
        assertEquals(setOf("bnd.com", "com.a.b"), parsePackages("package:bnd.com\npackage:com.a.b\r\nerrore\n"))
    }
}
