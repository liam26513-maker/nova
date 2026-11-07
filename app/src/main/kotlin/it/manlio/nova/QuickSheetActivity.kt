package it.manlio.nova

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
                onPdf = ::exportPdf,
                onShare = { export(::share) },
                onClose = ::finish,
            )
        }
    }

    override fun onDestroy() {
        sheet.front?.release()
        super.onDestroy()
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

    /** PDF in Download/NOVA, ritagliato attorno al disegno: la tela è quadrata e quasi tutta vuota. */
    private fun exportPdf() {
        val image = sheet.snapshot()
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { writePdf(SheetCrop.crop(image)) }.getOrDefault(false) }
            Toast.makeText(this@QuickSheetActivity, if (ok) R.string.sheet_pdf_saved else R.string.sheet_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun writePdf(image: Bitmap): Boolean {
        val pdf = PdfDocument()
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(image.width, image.height, 1).create())
        page.canvas.drawBitmap(image, 0f, 0f, null)
        pdf.finishPage(page)
        val values =
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "foglio-${System.currentTimeMillis()}.pdf")
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/NOVA")
            }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        val written = uri?.let { contentResolver.openOutputStream(it)?.use { out -> pdf.writeTo(out) } } != null
        pdf.close()
        return written
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
    onPdf: () -> Unit,
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
            SheetButton(stringResource(R.string.sheet_redo), sheet::redo)
            SheetButton(stringResource(R.string.sheet_clear), sheet::clear)
            SheetButton(stringResource(R.string.sheet_save), onSave)
            SheetButton(stringResource(R.string.notes_share), onShare)
            SheetButton(stringResource(R.string.notes_close), onClose)
        }
        SheetTools(sheet, onPdf)
        AndroidView(
            {
                FrameLayout(it).apply {
                    addView(sheet)
                    // Lo strato veloce solo se il tablet lo permette; altrimenti la tela disegna da sola.
                    if (FrontInk.supported()) addView(FrontInk(it, sheet).also { ink -> sheet.front = ink })
                }
            },
            Modifier.fillMaxSize(),
        )
    }
}

/** Seconda riga della barra: colori, tre spessori e gomma. Lo stato vive nella tela; qui solo cosa è scelto. */
@Composable
private fun SheetTools(
    sheet: QuickSheetView,
    onPdf: () -> Unit,
) {
    var color by remember { mutableIntStateOf(sheet.color) }
    var size by remember { mutableFloatStateOf(sheet.size) }
    var eraser by remember { mutableStateOf(sheet.eraser) }
    val colorLabels = stringArrayResource(R.array.sheet_colors)
    val sizeLabels = stringArrayResource(R.array.sheet_sizes)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        QuickSheetView.COLORS.forEachIndexed { i, c ->
            val chosen = !eraser && c == color
            Box(
                Modifier
                    .size(if (chosen) TOOL_BIG else TOOL_SMALL)
                    .clip(CircleShape)
                    .background(Color(c))
                    // Bordo grigio sempre: il nero sulla barra scura altrimenti non si vede.
                    .border(if (chosen) 2.dp else 1.dp, if (chosen) Color.White else HINT_GRAY, CircleShape)
                    .semantics { contentDescription = colorLabels[i] }
                    .clickable(role = Role.Button) {
                        color = c
                        eraser = false
                        sheet.color = c
                        sheet.eraser = false
                    },
            )
        }
        Spacer(Modifier.width(10.dp))
        QuickSheetView.SIZES.forEachIndexed { i, s ->
            val chosen = s == size
            Box(
                Modifier.size(TOOL_BIG).semantics { contentDescription = sizeLabels[i] }.clickable(role = Role.Button) {
                    size = s
                    sheet.size = s
                },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size((DOT_DP * s).dp).clip(CircleShape).background(if (chosen) Color.White else HINT_GRAY))
            }
        }
        Spacer(Modifier.width(10.dp))
        SheetButton(stringResource(if (eraser) R.string.sheet_pen else R.string.sheet_eraser)) {
            eraser = !eraser
            sheet.eraser = eraser
        }
        SheetButton(stringResource(R.string.sheet_pdf), onPdf)
    }
}

private val TOOL_BIG = 30.dp
private val TOOL_SMALL = 24.dp
private const val DOT_DP = 10f

@Suppress("MagicNumber") // colore
private val HINT_GRAY = Color(0xFF8E8E93)

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

/** Il ritaglio attorno al disegno (con un margine), per il PDF. Pura sui pixel, così si prova. */
@Suppress("MagicNumber") // gli indici del riquadro: sinistra, alto, destra, basso
internal object SheetCrop {
    private const val MARGIN = 32

    /** Il riquadro dei pixel diversi da [paper], o null se il foglio è vuoto: sinistra, alto, destra, basso (esclusi). */
    fun bounds(
        pixels: IntArray,
        width: Int,
        paper: Int,
    ): IntArray? {
        var left = width
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        for (i in pixels.indices) {
            if (pixels[i] == paper) continue
            val x = i % width
            val y = i / width
            left = minOf(left, x)
            right = maxOf(right, x)
            top = minOf(top, y)
            bottom = maxOf(bottom, y)
        }
        return if (right < 0) null else intArrayOf(left, top, right + 1, bottom + 1)
    }

    fun crop(image: Bitmap): Bitmap {
        val pixels = IntArray(image.width * image.height).also { image.getPixels(it, 0, image.width, 0, 0, image.width, image.height) }
        val box = bounds(pixels, image.width, image.getPixel(0, 0)) ?: return image
        val l = maxOf(0, box[0] - MARGIN)
        val t = maxOf(0, box[1] - MARGIN)
        val r = minOf(image.width, box[2] + MARGIN)
        val b = minOf(image.height, box[3] + MARGIN)
        return Bitmap.createBitmap(image, l, t, r - l, b - t)
    }
}
