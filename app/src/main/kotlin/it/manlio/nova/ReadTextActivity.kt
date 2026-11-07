package it.manlio.nova

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import it.manlio.nova.feature.home.MoreItem
import it.manlio.nova.feature.ocr.TextReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Testo dalle immagini (SPEC §12.1): una foto condivisa a NOVA, o scelta dalla Galleria, diventa testo da copiare o mandare all'app
 * Claude. Legge tutto sul tablet; i modelli si caricano solo qui e si liberano alla chiusura.
 */
class ReadTextActivity : ComponentActivity() {
    private val reader by lazy { TextReader(this) }
    private var text by mutableStateOf<String?>(null)
    private val pick =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri == null) finish() else read(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ReadScreen(text, onCopy = ::copy, onShare = {
                share(it, toClaude = false)
            }, onClaude = { share(it, toClaude = true) }, onClose = ::finish)
        }
        if (savedInstanceState != null) return
        val shared = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        if (shared != null) read(shared) else pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    override fun onDestroy() {
        reader.close()
        super.onDestroy()
    }

    private fun read(uri: Uri) {
        lifecycleScope.launch {
            val photo = withContext(Dispatchers.IO) { runCatching { decode(uri) }.getOrNull() }
            val lines = photo?.let { runCatching { reader.read(it) }.getOrNull() }
            text = lines?.joinToString("\n") ?: ""
        }
    }

    /** Ridotta a ~2000 pixel di lato: basta per leggere e non riempie la memoria con le foto della fotocamera. */
    private fun decode(uri: Uri): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val side = maxOf(info.size.width, info.size.height)
            if (side > MAX_SIDE) decoder.setTargetSampleSize((side + MAX_SIDE - 1) / MAX_SIDE)
        }

    private fun copy(value: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(getString(R.string.read_title), value))
        Toast.makeText(this, R.string.read_copied, Toast.LENGTH_SHORT).show()
    }

    private fun share(
        value: String,
        toClaude: Boolean,
    ) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, value)
        // «Invia a Claude» è una condivisione verso l'app Claude, se c'è; altrimenti si sceglie dove mandarlo.
        val claude = Intent(send).setPackage(CLAUDE).takeIf { toClaude && it.resolveActivity(packageManager) != null }
        startActivity(claude ?: Intent.createChooser(send, getString(R.string.notes_share)))
    }

    private companion object {
        const val MAX_SIDE = 2000
        const val CLAUDE = "com.anthropic.claude"
    }
}

internal fun readTextItem(context: Context) =
    MoreItem(context.getString(R.string.read_title)) {
        context.startActivity(Intent(context, ReadTextActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        null
    }

@Composable
private fun ReadScreen(
    text: String?,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onClaude: (String) -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(BACKGROUND).systemBarsPadding().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            BasicText(
                stringResource(R.string.read_title),
                Modifier.weight(1f),
                style = TextStyle(Color.White, 22.sp, fontWeight = FontWeight.SemiBold),
            )
            if (!text.isNullOrBlank()) {
                ReadButton(stringResource(R.string.read_copy)) { onCopy(text) }
                ReadButton(stringResource(R.string.notes_share)) { onShare(text) }
                ReadButton(stringResource(R.string.read_claude)) { onClaude(text) }
            }
            ReadButton(stringResource(R.string.notes_close), onClose)
        }
        val shown =
            when {
                text == null -> stringResource(R.string.read_working)
                text.isBlank() -> stringResource(R.string.read_none)
                else -> text
            }
        SelectionContainer(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
            BasicText(shown, style = TextStyle(if (text.isNullOrBlank()) HINT else Color.White, 18.sp))
        }
    }
}

@Composable
private fun ReadButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        BasicText(label, style = TextStyle(Color.White, 16.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Suppress("MagicNumber") // colori
private val BACKGROUND = Color(0xFF1C1C1E)

@Suppress("MagicNumber")
private val HINT = Color(0xFF8E8E93)
