package it.manlio.nova

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import it.manlio.nova.core.design.NovaTheme
import it.manlio.nova.feature.home.ControlCenterOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Il centro di controllo sopra le altre app (SPEC §2.2, §6.8). Android non lascia sostituire la tendina di sistema:
 * questo servizio di accessibilità, che l'utente accende a mano, disegna soltanto due finestre di NOVA.
 *  - una **striscia** sottile e invisibile sull'angolo in alto a destra, sopra la barra di stato: tirarla giù apre il pannello;
 *  - il **pannello**, a tutto schermo, con lo stesso centro di controllo della Home (nasce al gesto e sparisce quando si chiude).
 * Non legge lo schermo (`canRetrieveWindowContent` è falso) e non compie azioni. Da spento non esiste niente.
 * La striscia si spegne sulla Home di NOVA, che ha già il suo gesto, e non apre nulla a schermo bloccato.
 */
class NovaControlService : AccessibilityService() {
    private val windows by lazy { getSystemService(WindowManager::class.java) }
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var strip: View? = null
    private var panel: PanelHost? = null
    private var controls: HomeControls? = null
    private var closing by mutableStateOf(false)
    private val app get() = application as NovaApp

    override fun onServiceConnected() {
        super.onServiceConnected()
        addStrip()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Cambia la finestra davanti (un'app aperta da una voce, Home, recenti): la tendina non ha più senso.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.packageName != packageName) requestClose()
    }

    override fun onInterrupt() = requestClose()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        strip?.let { runCatching { windows.updateViewLayout(it, stripParams()) } }
    }

    override fun onDestroy() {
        removePanel()
        strip?.let { runCatching { windows.removeView(it) } }
        strip = null
        scope.cancel()
        super.onDestroy()
    }

    @SuppressLint("ClickableViewAccessibility") // la striscia è solo un gesto: non ha un'azione da leggere con TalkBack
    private fun addStrip() {
        if (strip != null) return
        val detector = PullDetector(ViewConfiguration.get(this).scaledTouchSlop.toFloat())
        val view =
            View(this).apply {
                setOnTouchListener { _, e ->
                    when (e.actionMasked) {
                        MotionEvent.ACTION_DOWN -> detector.down(e.rawX, e.rawY)
                        MotionEvent.ACTION_MOVE -> if (detector.move(e.rawX, e.rawY)) openPanel()
                        MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> detector.cancel()
                    }
                    true
                }
            }
        // Sempre attiva, anche con la barra di stato nascosta (giochi, video): lì il tocco dall'alto è proprio il modo di arrivare qui.
        runCatching { windows.addView(view, stripParams()) }.onSuccess { strip = view }
    }

    private fun stripParams(): WindowManager.LayoutParams {
        val density = resources.displayMetrics.density
        val barId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val bar = if (barId > 0) resources.getDimensionPixelSize(barId) else 0
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            maxOf(bar, (STRIP_MIN_HEIGHT_DP * density).toInt()),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSPARENT,
        ).apply { gravity = Gravity.TOP }
    }

    private fun openPanel() {
        if (panel != null || getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
        val actions =
            HomeControls(
                this,
                app,
                onSystemNotifications = { performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) },
                onSystemQuickSettings = { performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS) },
                onTurbo = { HomeSystem.turboWithToast(this, scope, PerformanceUi(app, scope)) },
            ).also {
                controls = it
                it.start()
            }.actions()
        closing = false
        val host =
            PanelHost(this).apply {
                onBack = ::requestClose
                addView(
                    ComposeView(context).apply {
                        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                        setContent {
                            val config by app.config.current.collectAsState()
                            val graphics by app.graphics.collectAsState()
                            val prefs by app.homePrefs.prefs.collectAsState()
                            NovaTheme(tokensFrom(config, graphics.level)) {
                                ControlCenterOverlay(
                                    controls = actions,
                                    prefs = prefs,
                                    systemDark = HomeSystem.isDark(this@NovaControlService, prefs),
                                    closing = closing,
                                    onSettings = {
                                        val open = Intent(this@NovaControlService, MainActivity::class.java)
                                        startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    },
                                    onDismissed = ::removePanel,
                                )
                            }
                        }
                    },
                )
            }
        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                // Senza FLAG_NOT_FOCUSABLE: la finestra ha il fuoco e Indietro arriva a noi.
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            )
        runCatching { windows.addView(host, params) }
            .onSuccess {
                panel = host
                host.resume()
            }.onFailure { controls?.stop() }
    }

    /** Chiude con l'animazione; se per qualunque motivo non finisse, la finestra viene tolta comunque. */
    private fun requestClose() {
        if (panel == null) return
        closing = true
        handler.removeCallbacks(forceRemove)
        handler.postDelayed(forceRemove, FORCE_REMOVE_MS)
    }

    private val forceRemove = Runnable { removePanel() }

    private fun removePanel() {
        handler.removeCallbacks(forceRemove)
        controls?.stop()
        controls = null
        val host = panel ?: return
        panel = null
        host.destroy()
        runCatching { windows.removeView(host) }
    }

    /** La radice della finestra del pannello: dà a Compose ciclo di vita e stato salvato (non c'è un'Activity) e riceve Indietro. */
    private class PanelHost(
        context: Context,
    ) : FrameLayout(context),
        LifecycleOwner,
        SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        var onBack: () -> Unit = {}

        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

        init {
            saved.performAttach()
            saved.performRestore(null)
            registry.currentState = Lifecycle.State.CREATED
            setViewTreeLifecycleOwner(this)
            setViewTreeSavedStateRegistryOwner(this)
        }

        fun resume() {
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
            if (event.action == KeyEvent.ACTION_UP) onBack()
            return true
        }
    }

    companion object {
        private const val STRIP_MIN_HEIGHT_DP = 24
        private const val FORCE_REMOVE_MS = 700L

        /** L'utente ha acceso il servizio nelle impostazioni di accessibilità di Android? */
        fun isEnabled(context: Context): Boolean {
            val key = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            val enabled = Settings.Secure.getString(context.contentResolver, key).orEmpty()
            val me = ComponentName(context, NovaControlService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
