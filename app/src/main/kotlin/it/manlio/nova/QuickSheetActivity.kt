package it.manlio.nova

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import it.manlio.nova.feature.home.MoreItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Il foglio rapido (SPEC §9.4): una tela sempre pronta, con penna o dito, che si salva da sola uscendo. */
class QuickSheetActivity : ComponentActivity() {
    private val file by lazy { File(filesDir, "foglio-rapido.png") }
    private lateinit var sheet: QuickSheetView

    /** Falso finché il foglio salvato non è caricato: così non si salva un foglio bianco sopra il disegno. */
    private var loaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Quadrata e grande quanto il lato lungo: girando il tablet il disegno non si taglia.
        val bounds = windowManager.currentWindowMetrics.bounds
        sheet = QuickSheetView(this, maxOf(bounds.width(), bounds.height()))
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.path) }?.let(sheet::load)
            loaded = true
        }
        setContent {
            SheetScreen(
                sheet,
                onSave = { export { Toast.makeText(this, R.string.sheet_saved, Toast.LENGTH_LONG).show() } },
                onShare = { export(::share) },
                onClose = ::finish,
            )
        }
    }

    override fun onPause() {
        super.onPause()
        if (!loaded) return
        val image = sheet.snapshot()
        (application as NovaApp).appScope.launch(Dispatchers.IO) {
            val temp = File(file.path + ".tmp")
            val written = runCatching { temp.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, FULL_QUALITY, it) } }
            if (written.getOrDefault(false)) temp.renameTo(file)
        }
    }

    /** Salva una copia in Galleria (Pictures/NOVA): dal disco di Android non serve nessun permesso. */
    private fun export(done: (Uri) -> Unit) {
        val image = sheet.snapshot()
        lifecycleScope.launch {
            val uri = withContext(Dispatchers.IO) { runCatching { writeToGallery(image) }.getOrNull() }
            if (uri != null) done(uri) else Toast.makeText(this@QuickSheetActivity, R.string.sheet_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun writeToGallery(image: Bitmap): Uri? {
        val values =
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "foglio-${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NOVA")
            }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        contentResolver.openOutputStream(uri)?.use { image.compress(Bitmap.CompressFormat.PNG, FULL_QUALITY, it) }
        return uri
    }

    private fun share(uri: Uri) {
        val send =
            Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, getString(R.string.notes_share)))
    }

    private companion object {
        const val FULL_QUALITY = 100
    }
}

internal fun quickSheetItem(context: Context) =
    MoreItem(context.getString(R.string.sheet_title)) {
        context.startActivity(Intent(context, QuickSheetActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        null
    }

@Composable
private fun SheetScreen(
    sheet: QuickSheetView,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(BAR).systemBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            BasicText(
                stringResource(R.string.sheet_title),
                Modifier.weight(1f),
                style = TextStyle(Color.White, 22.sp, fontWeight = FontWeight.SemiBold),
            )
            SheetButton(stringResource(R.string.sheet_undo), sheet::undo)
            SheetButton(stringResource(R.string.sheet_clear), sheet::clear)
            SheetButton(stringResource(R.string.sheet_save), onSave)
            SheetButton(stringResource(R.string.notes_share), onShare)
            SheetButton(stringResource(R.string.notes_close), onClose)
        }
        AndroidView({ sheet }, Modifier.fillMaxSize())
    }
}

@Composable
private fun SheetButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        BasicText(label, style = TextStyle(Color.White, 16.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Suppress("MagicNumber") // colore
private val BAR = Color(0xFF1C1C1E)
