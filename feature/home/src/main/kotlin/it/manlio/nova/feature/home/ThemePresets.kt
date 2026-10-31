package it.manlio.nova.feature.home

/** Temi pronti, da applicare con un tocco e poi ritoccare (come i temi pronti di Niagara, ma propri). */
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
                "Notte",
                "Il tuo sfondo molto sfocato, testo chiaro e sottile",
                HomePrefs(theme = ThemeMode.DARK, wallpaper = WallpaperSource.SYSTEM, blurLevel = BlurLevel.MAX),
            ),
            ThemePreset(
                "Aurora",
                "Sfumatura verde-blu, icone monocromatiche, accento acqua",
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
                "Sfumatura calda, orologio grande e marcato",
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
                "Nero pieno, carattere tecnico, solo testo",
                HomePrefs(
                    wallpaper = WallpaperSource.SOLID,
                    solidColor = 0xFF000000.toInt(),
                    font = HomeFont.TECHNICAL,
                    weight = TextWeight.REGULAR,
                    showIcons = false,
                    clockStyle = ClockStyle.STACKED,
                    accent = 0xFFFFFFFF.toInt(),
                ),
            ),
        )
}
