package it.manlio.nova.feature.home

import android.app.Application
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** «Quando provo a cambiare il nome alle cartelle ora l'app crasha» (26 novembre 2026): il vetro liquido su un riquadro minuscolo. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class LiquidGlassSmallTest {
    @Test
    fun `il vetro liquido su un riquadro piu piccolo del bordo non fa cadere la Home`() {
        listOf(Size(1f, 1f), Size(0f, 40f), Size(3f, 3f)).forEach { size ->
            CanvasDrawScope().draw(Density(2f), LayoutDirection.Ltr, Canvas(ImageBitmap(8, 8)), size) {
                liquidGlass(RectangleShape, dark = true)
            }
        }
    }
}
