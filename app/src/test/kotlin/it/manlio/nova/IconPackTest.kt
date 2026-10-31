package it.manlio.nova

import android.app.Application
import android.util.Xml
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.manlio.nova.feature.home.IconPackParserAccess
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.StringReader

/** Lettura di un appfilter.xml come quelli dei pacchetti di icone reali. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class IconPackTest {
    @Test
    fun appfilter_maps_components_to_drawables_and_ignores_the_rest() {
        val xml =
            """
            <resources>
                <iconback img1="iconback" />
                <item component="ComponentInfo{com.whatsapp/com.whatsapp.Main}" drawable="whatsapp" />
                <item component="ComponentInfo{org.krita/.MainActivity}" drawable="krita" />
                <item component="ComponentInfo{org.krita/.MainActivity}" drawable="krita_alt" />
                <item component=":BROWSER" drawable="browser" />
                <item component="ComponentInfo{com.empty/.X}" drawable="" />
            </resources>
            """.trimIndent()
        val parser = Xml.newPullParser().apply { setInput(StringReader(xml)) }
        assertEquals(
            mapOf("com.whatsapp/com.whatsapp.Main" to "whatsapp", "org.krita/org.krita.MainActivity" to "krita"),
            IconPackParserAccess.parse(parser),
        )
    }
}
