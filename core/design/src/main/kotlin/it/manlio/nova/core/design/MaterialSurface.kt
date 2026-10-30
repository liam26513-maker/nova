package it.manlio.nova.core.design

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pannello traslucido (dock, centro di controllo, card) che si adatta al livello grafico
 * corrente (SPEC §5.3). [backdrop] è lo sfondo che sta "dietro" (di solito lo sfondo della
 * home a tutto schermo), disegnato allineato alla posizione del pannello.
 */
@Composable
fun MaterialSurface(
    backdrop: ImageBitmap?,
    modifier: Modifier = Modifier,
    shape: Shape = SquircleShape(Nova.tokens.radii.large),
    tint: Color = Nova.tokens.colors.surface,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = Nova.tokens
    var origin by remember { mutableStateOf(Offset.Zero) }
    val level = if (backdrop == null) GraphicsLevel.PERFORMANCE else tokens.graphicsLevel
    val prepared = if (level == GraphicsLevel.BALANCED) rememberPreBlurred(backdrop) else null

    Box(
        modifier
            .onGloballyPositioned { origin = it.boundsInRoot().topLeft }
            .clip(shape),
    ) {
        when (level) {
            GraphicsLevel.MAXIMUM ->
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { renderEffect = BlurEffect(LIVE_BLUR_PX, LIVE_BLUR_PX, TileMode.Clamp) }
                        .drawBehind { drawAligned(backdrop!!, origin, scale = 1f) },
                )
            GraphicsLevel.BALANCED ->
                if (prepared != null) {
                    Box(Modifier.matchParentSize().drawBehind { drawAligned(prepared, origin, scale = PRE_BLUR_SCALE) })
                }
            GraphicsLevel.PERFORMANCE -> Unit
        }
        val alpha = if (level == GraphicsLevel.PERFORMANCE) PERFORMANCE_OPACITY else tokens.materialOpacity
        Box(Modifier.matchParentSize().drawBehind { drawRect(tint.copy(alpha = alpha)) })
        content()
    }
}

/** Calcola una volta, fuori dal thread principale, la versione rimpicciolita e sfocata dello sfondo. */
@Composable
private fun rememberPreBlurred(source: ImageBitmap?): ImageBitmap? =
    produceState<ImageBitmap?>(null, source) {
        value = source?.let { withContext(Dispatchers.Default) { preBlur(it.asAndroidBitmap()) } }
    }.value

internal fun preBlur(src: Bitmap): ImageBitmap {
    val w = (src.width * PRE_BLUR_SCALE).toInt().coerceAtLeast(1)
    val h = (src.height * PRE_BLUR_SCALE).toInt().coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(src.copy(Bitmap.Config.ARGB_8888, false), w, h, true)
    val px = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
    val blurred = BoxBlur.blur(px, w, h, radius = PRE_BLUR_RADIUS)
    return Bitmap.createBitmap(blurred, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Disegna la porzione di sfondo che sta dietro al pannello. */
private fun DrawScope.drawAligned(
    image: ImageBitmap,
    origin: Offset,
    scale: Float,
) {
    translate(-origin.x, -origin.y) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstSize = IntSize((image.width / scale).toInt(), (image.height / scale).toInt()),
        )
    }
}

private const val LIVE_BLUR_PX = 60f
private const val PRE_BLUR_SCALE = 0.25f
private const val PRE_BLUR_RADIUS = 6
private const val PERFORMANCE_OPACITY = 0.94f
