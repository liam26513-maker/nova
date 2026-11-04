package it.manlio.nova

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import it.manlio.nova.core.modes.QuickNotes
import it.manlio.nova.feature.home.MoreItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Le note rapide (SPEC §8.4): si aprono da tendina → Altro e dalla pastiglia della Studio, e si salvano da sole uscendo. */
class NotesActivity : ComponentActivity() {
    private val notes by lazy { QuickNotes(File(filesDir, "note-rapide.txt")) }

    /** Null finché il file non è letto: così non si può salvare sopra note mai caricate. */
    private var text by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) { runCatching { notes.read() }.getOrNull() }
            if (saved == null) finish() else text = saved
        }
        setContent {
            text?.let { NotesScreen(it, onChange = { new -> text = new }, onShare = { share(it) }, onClose = ::finish) }
        }
    }

    override fun onPause() {
        super.onPause()
        val now = text ?: return
        (application as NovaApp).appScope.launch(Dispatchers.IO) { runCatching { notes.write(now) } }
    }

    private fun share(note: String) {
        if (note.isBlank()) return
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, note)
        startActivity(Intent.createChooser(send, getString(R.string.notes_share)))
    }
}

internal fun notesItem(context: Context) =
    MoreItem(context.getString(R.string.notes_title)) {
        openNotes(context)
        null
    }

internal fun openNotes(context: Context) {
    context.startActivity(Intent(context, NotesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
private fun NotesScreen(
    text: String,
    onChange: (String) -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val title = stringResource(R.string.notes_title)
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxSize().background(NOTES_BACKGROUND).systemBarsPadding().imePadding().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            BasicText(title, Modifier.weight(1f), style = TextStyle(Color.White, 22.sp, fontWeight = FontWeight.SemiBold))
            NotesButton(stringResource(R.string.notes_share), onShare)
            NotesButton(stringResource(R.string.notes_close), onClose)
        }
        Box(Modifier.fillMaxSize().padding(vertical = 8.dp)) {
            if (text.isEmpty()) BasicText(stringResource(R.string.notes_hint), style = TextStyle(NOTES_HINT, 18.sp))
            BasicTextField(
                text,
                onChange,
                Modifier.fillMaxSize().focusRequester(focus).semantics { contentDescription = title },
                textStyle = TextStyle(Color.White, 18.sp),
                cursorBrush = SolidColor(Color.White),
            )
        }
    }
}

@Composable
private fun NotesButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        BasicText(label, style = TextStyle(Color.White, 16.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Suppress("MagicNumber") // colori
private val NOTES_BACKGROUND = Color(0xFF1C1C1E)

@Suppress("MagicNumber")
private val NOTES_HINT = Color(0xFF8E8E93)
