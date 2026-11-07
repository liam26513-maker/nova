package it.manlio.nova.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StandbyAndLockTest {
    @Test
    fun `night is from 21 to 7`() {
        listOf(21, 22, 23, 0, 3, 6).forEach { assertTrue("$it", StandbyRules.isNight(it)) }
        listOf(7, 8, 12, 18, 20).forEach { assertFalse("$it", StandbyRules.isNight(it)) }
    }

    @Test
    fun `automatic standby needs all three, switched on and charging and landscape`() {
        assertTrue(StandbyRules.shouldStart(auto = true, charging = true, landscape = true))
        assertFalse(StandbyRules.shouldStart(auto = false, charging = true, landscape = true))
        assertFalse(StandbyRules.shouldStart(auto = true, charging = false, landscape = true))
        assertFalse(StandbyRules.shouldStart(auto = true, charging = true, landscape = false))
    }

    @Test
    fun `the screen is dimmer at night`() = assertTrue(StandbyRules.brightness(true) < StandbyRules.brightness(false))

    @Test
    fun `the lock wallpaper key changes only when the picture changes`() {
        val nova = HomePrefs(wallpaper = WallpaperSource.NOVA, novaWallpaper = 2)
        assertEquals("nova:2", LockWallpaper.key(nova, 0))
        assertEquals("nova:3", LockWallpaper.key(nova.copy(novaWallpaper = 3), 0))
        assertEquals(LockWallpaper.key(nova, 1), LockWallpaper.key(nova.copy(scrim = ScrimStrength.STRONG), 99)) // altro non conta
        val solid = HomePrefs(wallpaper = WallpaperSource.SOLID, solidColor = 0xFF112233.toInt())
        assertEquals("solid:${0xFF112233.toInt()}", LockWallpaper.key(solid, 0))
        assertEquals("photo:42", LockWallpaper.key(HomePrefs(wallpaper = WallpaperSource.PHOTO), 42))
    }

    @Test
    fun `the lock screen is left alone with the system wallpaper or when switched off`() {
        assertNull(LockWallpaper.key(HomePrefs(wallpaper = WallpaperSource.SYSTEM), 0))
        assertNull(LockWallpaper.key(HomePrefs(wallpaper = WallpaperSource.NOVA, lockWallpaper = false), 0))
    }

    @Test
    fun `the new preferences are saved and read, and the defaults are cautious`() {
        val d = HomePrefs()
        assertTrue(d.lockWallpaper)
        assertFalse(d.standbyAuto)
        val read = PrefsCodec.read(PrefsCodec.write(HomePrefs(lockWallpaper = false, standbyAuto = true)))
        assertFalse(read.lockWallpaper)
        assertTrue(read.standbyAuto)
        assertFalse(d.standbyCalendar)
        assertTrue(PrefsCodec.read(PrefsCodec.write(HomePrefs(standbyCalendar = true))).standbyCalendar)
    }

    @Test
    fun `a shared theme does not carry or overwrite the automatic standby`() {
        assertFalse(HomePrefs(standbyAuto = true).appearance().standbyAuto)
        assertTrue(HomePrefs(standbyAuto = true).withLookOf(HomePrefs()).standbyAuto)
    }
}
