package it.manlio.nova.feature.home

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import android.graphics.Color as AndroidColor

/** Tema dallo sfondo e colori liberi (26 novembre 2026). */
@RunWith(RobolectricTestRunner::class)
class ThemeFromWallpaperTest {
    private fun hsv(argb: Int) = FloatArray(3).also { AndroidColor.colorToHSV(argb, it) }

    @Test
    fun `a light background gives a light theme with a dark accent of the same hue`() {
        val p = HomePrefs(font = HomeFont.ELEGANT).themedFrom(0xFFF2E3B0.toInt())
        assertEquals(ThemeMode.LIGHT, p.theme)
        assertEquals(HomeFont.ELEGANT, p.font) // il resto resta com'era
        val a = hsv(p.accent!!)
        assertTrue(a[2] < 0.5f)
        assertEquals(hsv(0xFFF2E3B0.toInt())[0], a[0], 2f)
    }

    @Test
    fun `a dark background gives a dark theme with a bright accent, and a grey one a neutral accent`() {
        val p = HomePrefs(theme = ThemeMode.LIGHT).themedFrom(0xFF102040.toInt())
        assertEquals(ThemeMode.DARK, p.theme)
        assertTrue(hsv(p.accent!!)[2] > 0.9f)
        assertEquals(0f, hsv(HomePrefs().themedFrom(0xFF333333.toInt()).accent!!)[1], 0.01f)
    }

    @Test
    fun `text colour and the automatic theme are saved, and the text colour reaches the look`() {
        val p = HomePrefs(inkColor = 0xFFFFD9A8.toInt(), autoTheme = true)
        val back = PrefsCodec.read(PrefsCodec.write(p))
        assertEquals(p.inkColor, back.inkColor)
        assertTrue(back.autoTheme)
        assertEquals(Color(0xFFFFD9A8), p.look().ink)
        assertEquals(Color.White, HomePrefs().look().ink)
    }
}
