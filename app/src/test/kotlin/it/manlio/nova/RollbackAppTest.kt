package it.manlio.nova

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.manlio.nova.core.design.NovaTheme
import it.manlio.nova.core.design.NovaTokens
import it.manlio.nova.core.system.ShizukuStatus
import it.manlio.nova.core.update.AppUpdate
import it.manlio.nova.core.update.Asset
import it.manlio.nova.core.update.UpdateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Il ritorno a una versione precedente: lista, conferma obbligatoria, esito ed errori. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "it-w1280dp-h800dp-land-mdpi", sdk = [34], application = Application::class)
class RollbackAppTest {
    @get:Rule val compose = createComposeRule()

    private val old =
        AppUpdate(
            versionCode = 101,
            tag = "v0.1.101",
            apk = Asset(1, "nova-101.apk"),
            checksum = Asset(2, "nova-101.apk.sha256"),
            notes = null,
            title = "NOVA 0.1.101",
            publishedAt = "2026-10-30T22:15:00Z",
        )
    private val requested = mutableListOf<AppUpdate>()

    private fun render(
        update: UpdateState = UpdateState.UpToDate(0),
        load: Result<List<AppUpdate>> = Result.success(listOf(old)),
        rollback: String? = null,
    ) {
        val actions =
            SystemActions(
                {},
                {},
                { null },
                {},
                {},
                {},
                onLoadPrevious = { load },
                onRollbackApp = {
                    requested += it
                    rollback
                },
            )
        val state = SystemUiState("0.1.102", update, hasConfigHistory = false, shizuku = ShizukuStatus.READY, systemChanges = 0)
        compose.setContent {
            NovaTheme(NovaTokens.Fallback) { UpdatesSection(ImageBitmap(1, 1), state, actions) }
        }
    }

    @Test
    fun `asks for confirmation, then sends the request`() {
        render()
        compose.onNodeWithText("Torna a una versione precedente").performClick()
        compose.onNodeWithText("v0.1.101").performClick()
        assertEquals(emptyList<AppUpdate>(), requested) // nessuna richiesta senza conferma
        compose.onNodeWithText("Sì, torna a v0.1.101").performClick()
        compose.waitForIdle()
        assertEquals(listOf(old), requested)
        compose.onNodeWithText("Richiesta inviata").assertExists()
    }

    @Test
    fun `cancel goes back without requesting anything`() {
        render()
        compose.onNodeWithText("Torna a una versione precedente").performClick()
        compose.onNodeWithText("v0.1.101").performClick()
        compose.onNodeWithText("Annulla").performClick()
        compose.onNodeWithText("Torna a una versione precedente").assertExists()
        assertEquals(emptyList<AppUpdate>(), requested)
    }

    @Test
    fun `errors are shown in plain words`() {
        render(rollback = "Il token non ha il permesso")
        compose.onNodeWithText("Torna a una versione precedente").performClick()
        compose.onNodeWithText("v0.1.101").performClick()
        compose.onNodeWithText("Sì, torna a v0.1.101").performClick()
        compose.onNodeWithText("Il token non ha il permesso").assertExists()

        compose.onNodeWithText("Chiudi").performClick()
        compose.onNodeWithText("Torna a una versione precedente").assertExists()
    }

    @Test
    fun `no previous versions`() {
        render(load = Result.success(emptyList()))
        compose.onNodeWithText("Torna a una versione precedente").performClick()
        compose.onNodeWithText("Non ci sono versioni precedenti a cui tornare").assertExists()
    }

    @Test
    fun `load failure shows the reason`() {
        render(load = Result.failure(IllegalStateException("Nessuna connessione o GitHub non raggiungibile")))
        compose.onNodeWithText("Torna a una versione precedente").performClick()
        compose.onNodeWithText("Nessuna connessione o GitHub non raggiungibile").assertExists()
    }

    @Test
    fun `hidden without a token`() {
        render(update = UpdateState.NoToken)
        compose.onNodeWithText("Torna a una versione precedente").assertDoesNotExist()
        assertNull(requested.firstOrNull())
    }
}
