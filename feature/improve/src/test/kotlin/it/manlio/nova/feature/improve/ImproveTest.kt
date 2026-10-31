package it.manlio.nova.feature.improve

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class RedactorTest {
    @Test
    fun `secrets and personal data never leave the tablet`() {
        val text =
            """
            token github_pat_11ABCDEFG0123456789_abcdefghijklmnopqrstuvwxyz012345
            classic ghp_abcdefghijklmnopqrstuvwxyz0123456789
            Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload.sig
            mail manliogalano@gmail.com, tel +39 333 123 4567 e 0774-123456
            host 192.168.1.20, url https://api.github.com/repos/x/y?token=abc123
            C:\Users\Manlio\Documents\file.txt e /home/manlio/nova
            """.trimIndent()
        val out = Redactor.redact(text)
        val leaks =
            listOf("github_pat_", "ghp_", "eyJhbG", "manliogalano", "333 123", "0774", "192.168", "token=abc", "Manlio\\", "/home/manlio")
        leaks.forEach {
            assertFalse("Resta nel report: $it\n$out", out.contains(it))
        }
        assertTrue(out.contains("https://api.github.com/…"))
    }

    @Test
    fun `log timestamps, process ids and dates are not mistaken for phone numbers`() {
        val line = "10-31 14:22:01.123  4321  4350 I nova: avvio 2026-10-31 versione 0.1.150 (150)"
        assertEquals(line, Redactor.redact(line))
    }

    @Test
    fun `stack traces stay readable`() {
        val trace = "java.lang.IllegalStateException: boom\n\tat it.manlio.nova.feature.home.HomeScreen.render(HomeScreen.kt:120)"
        assertEquals(trace, Redactor.redact(trace))
    }
}

class ImprovePromptTest {
    private val context = ImproveContext("0.1.150", 150, "0.1.0", "UJJ U6_EEA, Android 14", "attivo")

    @Test
    fun `light requests are only about look, deep ones add or fix things`() {
        assertEquals(RequestKind.LIGHT, ImprovePrompt.classify("cambia il colore d'accento in viola"))
        assertEquals(RequestKind.DEEP, ImprovePrompt.classify("aggiungi un widget orologio"))
        assertEquals(RequestKind.DEEP, ImprovePrompt.classify("il colore non funziona più"))
    }

    @Test
    fun `prompts carry request, context and the project rules`() {
        val p = ImprovePrompt.improve("rendi il dock più trasparente", context)
        assertTrue(p.contains("\"rendi il dock più trasparente\""))
        assertTrue(p.contains("App 0.1.150 (150)"))
        assertTrue(p.contains("CLAUDE.md"))
        val r = ImprovePrompt.repair("si chiude da sola", "reports/2026-10-31_1530_si-chiude-da-sola.md", context)
        assertTrue(r.contains("Leggi `reports/2026-10-31_1530_si-chiude-da-sola.md`"))
        assertTrue(r.contains("test che riproduca"))
    }

    @Test
    fun `the link opens a new Claude Code session on the repository with the text encoded`() {
        val link = ImprovePrompt.claudeLink("ciao & è\nriga", "liam26513-maker/nova")
        assertTrue(link.startsWith("https://claude.ai/code/new?q="))
        val q = link.substringAfter("q=").substringBefore("&repo=")
        assertEquals("ciao & è\nriga", URLDecoder.decode(q, Charsets.UTF_8))
        assertTrue(link.endsWith("&repo=liam26513-maker%2Fnova"))
        assertTrue(ImprovePrompt.claudeLink("x".repeat(10_000), "a/b").length < 9_000)
    }

    @Test
    fun `report names are safe and dated`() {
        assertEquals("la-home-si-blocca-quando-apro", ImprovePrompt.slug("La Home si blocca quando apro Krita!!"))
        assertEquals("perche-cosi", ImprovePrompt.slug("Perché così?"))
        assertEquals("segnalazione", ImprovePrompt.slug("???"))
        assertTrue(ImprovePrompt.reportPath("prova", 0).matches(Regex("reports/\\d{4}-\\d{2}-\\d{2}_\\d{4}_prova\\.md")))
    }
}

class RepairReportTest {
    @Test
    fun `report is anonymised, sectioned and bounded`() {
        val data =
            RepairData(
                description = "scrivimi a manlio@example.com",
                context = ImproveContext("0.1.150", 150, "0.1.0", "tablet", "attivo"),
                crashes = listOf("java.lang.RuntimeException: x"),
                appLog = List(1_000) { "riga $it token github_pat_ABCDEFGHIJKLMNOPQRSTUVWXYZ" },
                systemLog = listOf("I/nova: ok", "```injection```"),
            )
        val report = RepairReport.build(data)
        assertFalse(report.contains("manlio@example.com"))
        assertFalse(report.contains("github_pat_"))
        assertTrue(report.contains("## Ultimi crash"))
        assertTrue(report.contains("riga 999"))
        assertFalse(report.contains("riga 0 "))
        assertFalse(report.contains("```injection```"))
        assertTrue(report.length <= 181_000)
    }
}
