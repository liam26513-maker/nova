package it.manlio.nova

import android.annotation.SuppressLint
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import it.manlio.nova.core.design.GraphicsLevel
import it.manlio.nova.feature.home.BlurLevel
import it.manlio.nova.feature.home.HomePrefs
import it.manlio.nova.feature.home.WallpaperSource

/**
 * Finestra della home: quando NOVA disegna da sé lo sfondo la finestra è **opaca** (così nelle app
 * recenti non traspare mai l'app aperta prima); solo se deve mostrare lo sfondo di sistema senza
 * poterlo leggere usa lo sfondo di Android dietro, eventualmente sfocato da Android.
 */
internal object HomeWindow {
    /** @return true se dietro c'è la sfocatura di Android. */
    fun apply(
        activity: ComponentActivity,
        prefs: HomePrefs,
        ownBackground: Boolean,
        blurSupported: Boolean,
        level: GraphicsLevel,
        dark: Boolean,
    ): Boolean {
        val window = activity.window
        val showSystemWallpaper = prefs.wallpaper == WallpaperSource.SYSTEM && !ownBackground
        if (showSystemWallpaper) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.BLACK))
        }
        val radiusDp = if (showSystemWallpaper && blurSupported) blurDp(prefs.blurLevel, level) else 0
        window.setBackgroundBlurRadius((radiusDp * activity.resources.displayMetrics.density).toInt())
        systemBars(activity, window, prefs.showStatusBar, dark)
        return radiusDp > 0
    }

    private fun blurDp(
        blur: BlurLevel,
        level: GraphicsLevel,
    ): Int {
        // La sfocatura di Android pesa sulla GPU: con il livello Prestazioni si spegne.
        if (level == GraphicsLevel.PERFORMANCE) return 0
        return when (blur) {
            BlurLevel.NONE -> 0
            BlurLevel.LIGHT -> BLUR_LIGHT
            BlurLevel.STRONG -> BLUR_STRONG
            BlurLevel.MAX -> BLUR_MAX
        }
    }

    @SuppressLint("WrongConstant")
    private fun systemBars(
        activity: ComponentActivity,
        window: Window,
        showStatusBar: Boolean,
        dark: Boolean,
    ) {
        val transparent = android.graphics.Color.TRANSPARENT
        val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        activity.enableEdgeToEdge(style, style)
        window.insetsController?.let { controller ->
            if (showStatusBar) {
                controller.show(WindowInsets.Type.statusBars())
            } else {
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsets.Type.statusBars())
            }
        }
    }

    private const val BLUR_LIGHT = 16
    private const val BLUR_STRONG = 32
    private const val BLUR_MAX = 56
}
