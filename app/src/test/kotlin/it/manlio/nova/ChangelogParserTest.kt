package it.manlio.nova

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ChangelogParserTest {
    @Test
    fun `reads titles and lines of the unreleased section only`() {
        val text =
            """
            # Novità di NOVA

            ## Non rilasciato

            ### Foglio rapido
            - Richiesta: "continua".
            - Una tela per disegnare.

            ### Privacy
            - Chi ha usato il microfono.

            ## 0.1.0
            ### Vecchia
            - non va mostrata
            """.trimIndent()

        assertEquals(
            listOf(
                NewsEntry("Foglio rapido", listOf("Richiesta: \"continua\".", "Una tela per disegnare.")),
                NewsEntry("Privacy", listOf("Chi ha usato il microfono.")),
            ),
            ChangelogParser.entries(text),
        )
    }

    @Test
    fun `the real changelog has entries`() {
        val real = File("../CHANGELOG.md").readText()
        assert(ChangelogParser.entries(real).isNotEmpty())
    }
}
