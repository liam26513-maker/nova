package it.manlio.nova.feature.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** «Alle cartelle non si possono modificare i nomi» (26 novembre 2026): il nome scritto deve arrivare alla cartella giusta. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class FolderRenameTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `il nome nuovo si salva quando la cartella si chiude`() {
        var open by mutableStateOf<FolderItem?>(FolderItem("f1", "Vecchia", emptyList()))
        val renamed = mutableListOf<Pair<String, String>>()
        val actions = HomeActions(icon = { null }, onLaunch = { _, _ -> }, onToggleFavorite = {}, onOpenSettings = {})
        compose.setContent {
            FolderOverlay(
                open,
                emptyMap(),
                actions,
                onRename = { id, name -> renamed += id to name },
                onMenu = {},
                onClose = { open = null },
            )
        }
        compose.onNodeWithText("Vecchia").performTextReplacement("Giochi")
        open = null
        compose.waitForIdle()
        assertEquals(listOf("f1" to "Giochi"), renamed)
    }
}
