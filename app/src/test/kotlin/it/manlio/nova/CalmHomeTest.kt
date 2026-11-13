package it.manlio.nova

import it.manlio.nova.feature.home.BlurLevel
import it.manlio.nova.feature.home.HomeLayoutStyle
import it.manlio.nova.feature.home.HomePrefs
import it.manlio.nova.feature.home.IconStyle
import it.manlio.nova.feature.home.WallpaperSource
import org.junit.Assert.assertEquals
import org.junit.Test

class CalmHomeTest {
    @Test
    fun `the study home keeps your layout, apps and wallpaper, only quieter`() {
        val mine =
            HomePrefs(
                wallpaper = WallpaperSource.PHOTO,
                layout = HomeLayoutStyle.IPAD,
                hidden = mapOf("a" to "A"),
                renamed = mapOf("b" to "Bi"),
            )
        val calm = CalmHome.calmLook(mine)
        assertEquals(WallpaperSource.PHOTO, calm.wallpaper)
        assertEquals(HomeLayoutStyle.IPAD, calm.layout)
        assertEquals(mine.hidden, calm.hidden)
        assertEquals(mine.renamed, calm.renamed)
        assertEquals(true, calm.showIcons)
        assertEquals(BlurLevel.MAX, calm.blurLevel)
        assertEquals(IconStyle.MONO, calm.iconStyle)
    }
}
