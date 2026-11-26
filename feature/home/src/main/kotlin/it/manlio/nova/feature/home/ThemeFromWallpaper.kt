package it.manlio.nova.feature.home

import android.graphics.Color as AndroidColor

/**
 * Il tema che nasce dallo sfondo (richiesta del 26 novembre 2026): sfondo chiaro → tema chiaro, scuro → scuro; l'accento è il
 * colore dello sfondo reso vivace e leggibile (chiaro sul tema scuro, scuro su quello chiaro). Tocca solo tema e accento:
 * tutto il resto resta come l'ha scelto l'utente, che può poi ritoccare anche questi due.
 */
@Suppress("MagicNumber") // soglie del colore
fun HomePrefs.themedFrom(wallpaperColor: Int): HomePrefs {
    val hsv = FloatArray(3).also { AndroidColor.colorToHSV(wallpaperColor, it) }
    val r = AndroidColor.red(wallpaperColor) / 255f
    val g = AndroidColor.green(wallpaperColor) / 255f
    val b = AndroidColor.blue(wallpaperColor) / 255f
    val light = 0.2126f * r + 0.7152f * g + 0.0722f * b > 0.55f
    // Uno sfondo quasi grigio non ha un colore da prendere: l'accento resta neutro.
    val saturation = if (hsv[1] < 0.08f) 0f else hsv[1].coerceIn(0.35f, 0.65f)
    val value = if (light) 0.42f else 0.95f
    val accent = AndroidColor.HSVToColor(floatArrayOf(hsv[0], saturation, value))
    return copy(theme = if (light) ThemeMode.LIGHT else ThemeMode.DARK, accent = accent)
}
