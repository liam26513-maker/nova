package it.manlio.nova

import it.manlio.nova.core.config.ConfigEngine
import it.manlio.nova.core.config.ModesConfig
import it.manlio.nova.core.modes.Modes
import it.manlio.nova.core.modes.withConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ModesConfigTest {
    private val config =
        ConfigEngine.JSON.decodeFromString(ModesConfig.serializer(), File("../config/modes.json").readText())

    @Test
    fun `the file says exactly what the compiled modes say, so losing it changes nothing`() {
        assertEquals(Modes.all.map { it.id }.toSet(), config.modes.keys)
        Modes.all.forEach { mode ->
            val file = config.modes.getValue(mode.id)
            assertEquals(mode, mode.withConfig(file.actions, file.maxMinutes))
        }
    }
}
