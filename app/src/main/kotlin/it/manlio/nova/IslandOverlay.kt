package it.manlio.nova

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import it.manlio.nova.core.design.NovaTheme
import it.manlio.nova.core.modes.Modes
import it.manlio.nova.feature.home.FloatingIsland

/**
 * L'isola dinamica sopra le altre app (SPEC §6.7): la musica che suona, in una pillola in alto al centro, come nella Home. Si tocca
 * per i comandi. Facoltativa (spenta di serie); non c'è sulla Home (che ha la sua), durante la Gaming né a schermo bloccato. Quando
 * non suona niente la pillola sparisce e la finestra non occupa spazio.
 */
internal class IslandOverlay(
    private val service: AccessibilityService,
    private val app: NovaApp,
) {
    private val windows = service.getSystemService(WindowManager::class.java)
    private val media by lazy { MediaHub(service) }
    private var host: NovaControlService.PanelHost? = null

    /** Mostra o toglie l'isola secondo l'app davanti ([onHome]: è la Home di NOVA) e le impostazioni. */
    fun update(onHome: Boolean) {
        val wanted =
            app.homePrefs.prefs.value.islandEverywhere &&
                !onHome &&
                !app.modes.isActive(Modes.GAMING) &&
                !service.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        if (wanted) show() else hide()
    }

    private fun show() {
        if (host != null) return
        media.start()
        val view = NovaControlService.PanelHost(service)
        view.addView(
            ComposeView(service).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                setContent {
                    val config by app.config.current.collectAsState()
                    val graphics by app.graphics.collectAsState()
                    NovaTheme(tokensFrom(config, graphics.level)) { FloatingIsland(media) }
                }
            },
        )
        val density = service.resources.displayMetrics.density
        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (TOP_DP * density).toInt()
            }
        if (runCatching { windows.addView(view, params) }.isSuccess) {
            host = view
            view.resume()
        } else {
            media.stop()
        }
    }

    fun hide() {
        val view = host ?: return
        host = null
        view.destroy()
        runCatching { windows.removeView(view) }
        media.stop()
    }

    private companion object {
        const val TOP_DP = 6
    }
}
