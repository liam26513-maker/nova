package helium314.keyboard.latin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** La numerazione di NOVA non deve far rieseguire le vecchie migrazioni di HeliBoard (vedi AppUpgrade.kt). */
class NovaUpgradeTest {
    private val current = NOVA_VERSION_BASE + 340

    @Test fun `same version does nothing`() = assertEquals(UpgradeKind.NONE, upgradeKind(current, current))

    @Test fun `a fresh install goes through the regular path, which recognises it`() = assertEquals(UpgradeKind.REGULAR, upgradeKind(0, current))

    @Test fun `the first NOVA keyboard (330) moves to the new numbering without old migrations`() {
        assertEquals(UpgradeKind.FIRST_NOVA_NUMBERING, upgradeKind(330, current))
        assertEquals(UpgradeKind.FIRST_NOVA_NUMBERING, upgradeKind(NOVA_VERSION_BASE - 1, current))
    }

    @Test fun `later NOVA updates use the regular path, with no old migration thresholds reached`() {
        assertEquals(UpgradeKind.REGULAR, upgradeKind(NOVA_VERSION_BASE + 330, current))
        // le soglie delle vecchie migrazioni di HeliBoard (<= 4201 e simili) non scattano mai sopra la base
        assertTrue(NOVA_VERSION_BASE + 1 > 4300)
    }
}
