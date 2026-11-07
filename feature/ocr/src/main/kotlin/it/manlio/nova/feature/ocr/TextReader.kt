package it.manlio.nova.feature.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Legge il testo di una foto (SPEC §12.1) con PaddleOCR PP-OCRv6 tiny su ONNX Runtime: tutto sul tablet, nessuna rete, nessuna
 * statistica. I modelli si caricano al primo uso e si liberano con [close]: da chiusa la funzione non occupa memoria.
 */
class TextReader(private val context: Context) : AutoCloseable {
    private val env by lazy { OrtEnvironment.getEnvironment() }
    private var detSession: OrtSession? = null
    private var recSession: OrtSession? = null
    private val det: OrtSession get() = detSession ?: session("ocr/det.onnx").also { detSession = it }
    private val rec: OrtSession get() = recSession ?: session("ocr/rec.onnx").also { recSession = it }

    // Il vocabolario sta dentro il modello: «vuoto» all'inizio e lo spazio in fondo, come in PaddleOCR.
    private val chars by lazy { listOf("blank") + rec.metadata.customMetadata.getValue("character").lines() + " " }

    /** Le righe di testo, dall'alto in basso. Gira fuori dal thread principale. */
    suspend fun read(photo: Bitmap): List<String> =
        withContext(Dispatchers.Default) {
            detect(photo).map { recognize(crop(photo, it)) }.filter { it.isNotBlank() }
        }

    private fun session(asset: String): OrtSession {
        val bytes = context.assets.open(asset).use { it.readBytes() }
        return env.createSession(bytes, OrtSession.SessionOptions())
    }

    private fun detect(photo: Bitmap): List<TextBox> {
        val ratio = minOf(1f, LIMIT / maxOf(photo.width, photo.height).toFloat())
        val w = maxOf(STEP, (photo.width * ratio / STEP).roundToInt() * STEP)
        val h = maxOf(STEP, (photo.height * ratio / STEP).roundToInt() * STEP)
        val input = tensor(Bitmap.createScaledBitmap(photo, w, h, true))
        val prob = input.use { det.run(mapOf(det.inputNames.first() to it)).use { out -> (out[0] as OnnxTensor).floatBuffer.toArray() } }
        return OcrGeometry.boxes(prob, w, h, photo.width / w.toFloat(), photo.height / h.toFloat())
    }

    private fun recognize(line: Bitmap): String {
        val width = maxOf(1, ceil(REC_HEIGHT * line.width / line.height.toFloat()).toInt())
        val input = tensor(Bitmap.createScaledBitmap(line, width, REC_HEIGHT, true))
        return input.use {
            rec.run(mapOf(rec.inputNames.first() to it)).use { out ->
                val result = out[0] as OnnxTensor
                val shape = result.info.shape
                OcrGeometry.ctc(result.floatBuffer.toArray(), shape[1].toInt(), shape[2].toInt(), chars)
            }
        }
    }

    /** Ritaglia la riga raddrizzandola: ogni punto del ritaglio prende il pixel della foto lungo gli assi della riga. */
    private fun crop(
        photo: Bitmap,
        box: TextBox,
    ): Bitmap {
        val out = Bitmap.createBitmap(maxOf(1, box.width.roundToInt()), maxOf(1, box.height.roundToInt()), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val c = cos(box.angle)
        val s = sin(box.angle)
        val x0 = box.cx - box.width / 2 * c + box.height / 2 * s
        val y0 = box.cy - box.width / 2 * s - box.height / 2 * c
        val toPhoto = Matrix().apply { setValues(floatArrayOf(c, -s, x0, s, c, y0, 0f, 0f, 1f)) }
        val toCrop = Matrix().also { toPhoto.invert(it) }
        canvas.drawBitmap(photo, toCrop, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** Pixel in NCHW, ordine BGR come PaddleOCR, valori tra -1 e 1. */
    private fun tensor(bitmap: Bitmap): OnnxTensor {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h).also { bitmap.getPixels(it, 0, w, 0, 0, w, h) }
        val data = FloatArray(CHANNELS * w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            data[i] = norm(p and BYTE)
            data[w * h + i] = norm(p shr GREEN and BYTE)
            data[2 * w * h + i] = norm(p shr RED and BYTE)
        }
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, CHANNELS.toLong(), h.toLong(), w.toLong()))
    }

    private fun norm(v: Int) = v / HALF - 1f

    private fun FloatBuffer.toArray() = FloatArray(remaining()).also { get(it) }

    override fun close() {
        detSession?.close()
        recSession?.close()
        detSession = null
        recSession = null
    }

    private companion object {
        const val LIMIT = 960f
        const val STEP = 32
        const val REC_HEIGHT = 48
        const val CHANNELS = 3
        const val BYTE = 0xFF
        const val GREEN = 8
        const val RED = 16
        const val HALF = 127.5f
    }
}
