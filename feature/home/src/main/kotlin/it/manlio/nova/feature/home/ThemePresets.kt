package it.manlio.nova.feature.home

/** Temi pronti, da applicare con un tocco e poi ritoccare. Dalle impostazioni tengono lo sfondo scelto ([HomePrefs.withPreset]). */
data class ThemePreset(
    val name: String,
    val description: String,
    val prefs: HomePrefs,
)

@Suppress("MagicNumber") // colori d'accento dei temi
object ThemePresets {
    val all =
        listOf(
            ThemePreset(
                "Vetro",
                "Sfondo sfocato e trasparenze lucide, con riflessi sui bordi",
                HomePrefs(
                    theme = ThemeMode.DARK,
                    wallpaper = WallpaperSource.SYSTEM,
                    blurLevel = BlurLevel.STRONG,
                    liquidGlass = true,
                    iconStyle = IconStyle.MONO,
                    accent = 0xFFBFD9FF.toInt(),
                ),
            ),
            ThemePreset(
                "Notte",
                "Sfondo molto sfocato, testo chiaro e sottile",
                HomePrefs(theme = ThemeMode.DARK, wallpaper = WallpaperSource.SYSTEM, blurLevel = BlurLevel.MAX),
            ),
            ThemePreset(
                "Aurora",
                "Icone monocromatiche e rotonde, accento acqua",
                HomePrefs(
                    wallpaper = WallpaperSource.NOVA,
                    novaWallpaper = 1,
                    accent = 0xFF66C2A5.toInt(),
                    iconStyle = IconStyle.MONO,
                    iconShape = IconShape.CIRCLE,
                ),
            ),
            ThemePreset(
                "Carta",
                "Chiaro e caldo, carattere elegante, senza icone",
                HomePrefs(
                    theme = ThemeMode.LIGHT,
                    wallpaper = WallpaperSource.NOVA,
                    novaWallpaper = 3,
                    font = HomeFont.ELEGANT,
                    weight = TextWeight.REGULAR,
                    showIcons = false,
                    scrim = ScrimStrength.LIGHT,
                    accent = 0xFF8A6D4F.toInt(),
                ),
            ),
            ThemePreset(
                "Tramonto",
                "Orologio grande e marcato, accento caldo",
                HomePrefs(
                    wallpaper = WallpaperSource.NOVA,
                    novaWallpaper = 2,
                    clockStyle = ClockStyle.BOLD,
                    weight = TextWeight.REGULAR,
                    iconShape = IconShape.SQUIRCLE,
                    accent = 0xFFFFC58A.toInt(),
                ),
            ),
            ThemePreset(
                "Essenziale",
                "Carattere elegante e sottile, solo testo",
                HomePrefs(
                    wallpaper = WallpaperSource.SOLID,
                    solidColor = 0xFF0D0E11.toInt(),
                    font = HomeFont.ELEGANT,
                    weight = TextWeight.LIGHT,
                    showIcons = false,
                    clockStyle = ClockStyle.THIN,
                    accent = 0xFFD9D4CC.toInt(),
                ),
            ),
        )
}
