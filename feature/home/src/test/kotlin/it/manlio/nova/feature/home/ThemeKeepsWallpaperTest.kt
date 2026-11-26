package it.manlio.nova.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** «Quando si cambia il tema deve rimanere lo sfondo» (26 novembre 2026). */
class ThemeKeepsWallpaperTest {
    @Test
    fun `un tema pronto tiene lo sfondo e prende il resto`() {
        val mine = HomePrefs(wallpaper = WallpaperSource.PHOTO, novaWallpaper = 2, solidColor = 0xFF112233.toInt(), lockWallpaper = false)
        ThemePresets.all.forEach { preset ->
            val applied = mine.withPreset(preset.prefs)
            assertEquals(WallpaperSource.PHOTO, applied.wallpaper)
            assertEquals(2, applied.novaWallpaper)
            assertEquals(0xFF112233.toInt(), applied.solidColor)
            assertEquals(false, applied.lockWallpaper)
            assertEquals(preset.prefs.font, applied.font)
            assertEquals(preset.prefs.liquidGlass, applied.liquidGlass)
        }
    }
}
