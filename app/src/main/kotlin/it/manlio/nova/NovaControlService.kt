package it.manlio.nova

import android.accessibilityservice.AccessibilityButtonController
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
import android.os.SystemClock
import android.provider.Settings
import android.view.Choreographer
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.runtime.MonotonicFrameClock
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
import it.manlio.nova.feature.home.ControlActions
import it.manlio.nova.feature.home.ControlCenterOverlay
import it.manlio.nova.feature.home.ControlSheetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Il centro di controllo sopra le altre app (SPEC §2.2, §6.8). Android non lascia sostituire la tendina di sistema:
 * questo servizio di accessibilità, che l'utente accende a mano, disegna soltanto due finestre di NOVA.
 *  - una **striscia** invisibile su tutta la larghezza, sopra la barra di stato: tirarla giù apre il pannello, che **segue il dito**;
 *  - il **pannello**, a tutto schermo, con lo stesso centro di controllo della Home (nasce al gesto e sparisce quando si chiude).
 * Se Android apre la sua tendina insieme alla nostra, il servizio la richiude. Non legge lo schermo
 * (`canRetrieveWindowContent` è falso) e non compie azioni. Da spento non esiste niente. A schermo bloccato non apre nulla.
 */
class NovaControlService : AccessibilityService() {
    private val windows by lazy { getSystemService(WindowManager::class.java) }
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + ChoreographerClock)
    private var strip: View? = null
    private var panel: PanelHost? = null
    private var controls: HomeControls? = null
    private var closing by mutableStateOf(false)
    private var stripTouch: StripTouch? = null
    private var lastShadeHide = 0L
    private val app get() = application as NovaApp

    override fun onServiceConnected() {
        super.onServiceConnected()
        addStrip()
        // Il pulsante di accessibilità di Android (se lo si sceglie come scorciatoia) apre la tendina senza il gesto.
        val button =
            object : AccessibilityButtonController.AccessibilityButtonCallback() {
                override fun onClicked(controller: AccessibilityButtonController) {
                    app.log.i(LOG_TAG, "pulsante di accessibilità")
                    if (panel != null) requestClose() else openPanel()?.settle(true)
                }
            }
        runCatching { accessibilityButtonController.registerAccessibilityButtonCallback(button) }
        app.log.i(LOG_TAG, "servizio collegato, striscia ${if (strip != null) "creata" else "NON creata"}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Cambia la finestra davanti (un'app aperta da una voce, Home, recenti): la tendina non ha più senso. Non vale per la
        // tendina di Android (SystemUI), che si apre insieme alla nostra durante il gesto e che chiudiamo noi: scambiarla per
        // «un'altra finestra davanti» richiudeva la tendina di NOVA un attimo dopo averla aperta.
        val other = event.packageName != packageName && event.packageName != SYSTEM_UI
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && other) requestClose()
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
        stripTouch?.recycle()
        stripTouch = null
        scope.cancel()
        super.onDestroy()
    }

    @SuppressLint("ClickableViewAccessibility") // la striscia è solo un gesto: non ha un'azione da leggere con TalkBack
    private fun addStrip() {
        if (strip != null) return
        val slop = ViewConfiguration.get(this).scaledTouchSlop.toFloat()
        val touch = StripTouch(slop, ::openPanel, ::hideAndroidShade) { app.log.i(LOG_TAG, it) }
        val view = View(this).apply { setOnTouchListener(touch) }
        // Sempre attiva, anche con la barra di stato nascosta (giochi, video): lì il tocco dall'alto è proprio il modo di arrivare qui.
        runCatching { windows.addView(view, stripParams()) }
            .onSuccess {
                strip = view
                stripTouch = touch
            }.onFailure {
                app.log.e(LOG_TAG, "striscia non creata", it)
                Toast.makeText(this, R.string.overlay_strip_failed, Toast.LENGTH_LONG).show()
            }
    }

    /**
     * Il sistema può aprire la sua tendina insieme alla nostra (segue lo stesso tocco): la richiudiamo subito, ogni tanto
     * mentre il dito scende e ancora un paio di volte a gesto finito. Quella di Android non deve restare.
     */
    private fun dismissShade() {
        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
    }

    private fun hideAndroidShade(finished: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (!finished && now - lastShadeHide < SHADE_HIDE_GAP_MS) return
        lastShadeHide = now
        dismissShade()
        if (finished) SHADE_HIDE_AFTER_MS.forEach { handler.postDelayed(::dismissShade, it) }
    }

    private fun stripParams(): WindowManager.LayoutParams {
        val density = resources.displayMetrics.density
        val barId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val bar = if (barId > 0) resources.getDimensionPixelSize(barId) else 0
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            maxOf(bar, (STRIP_MIN_HEIGHT_DP * density).toInt()),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or OVER_SYSTEM_BARS,
            PixelFormat.TRANSPARENT,
        ).apply {
            gravity = Gravity.TOP
            coverSystemBars()
        }
    }

    /**
     * Da Android 11 una finestra, se non dice altro, si ferma sotto barra di stato e di navigazione: la striscia finiva sotto
     * la barra e il tocco sulla barra andava ad Android. Così invece la finestra parte dal bordo vero dello schermo.
     */
    private fun WindowManager.LayoutParams.coverSystemBars() {
        setFitInsetsTypes(0)
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }

    /** Apre la finestra del pannello e restituisce la tendina da trascinare; null se non si può (schermo bloccato, già aperta). */
    private fun openPanel(): ControlSheetState? {
        // In modalità provvisoria l'app non carica i suoi strumenti (Shizuku, registro…): la tendina non si apre.
        if (app.safeModeReason != null || panel != null || getSystemService(KeyguardManager::class.java).isKeyguardLocked) return null
        val actions = newActions()
        closing = false
        val sheet = ControlSheetState(scope).also { it.estimate(PANEL_TRAVEL_DP * resources.displayMetrics.density) }
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
                                    sheet = sheet,
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
                OVER_SYSTEM_BARS,
                PixelFormat.TRANSLUCENT,
            ).apply { coverSystemBars() }
        val added = runCatching { windows.addView(host, params) }
        if (added.isSuccess) {
            panel = host
            host.resume()
        } else {
            app.log.e(LOG_TAG, "finestra del pannello non creata", added.exceptionOrNull())
            controls?.stop()
        }
        return sheet.takeIf { added.isSuccess }
    }

    private fun newActions(): ControlActions =
        HomeControls(
            this,
            app,
            scope,
            onSystemNotifications = { performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS) },
            onSystemQuickSettings = { performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS) },
            onTurbo = { HomeSystem.turboWithToast(this, scope, PerformanceUi(app, scope)) },
        ).also {
            controls = it
            it.start()
        }.actions()

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
        private const val LOG_TAG = "tendina"
        private const val SYSTEM_UI = "com.android.systemui"
        private const val PANEL_TRAVEL_DP = 520
        private const val SHADE_HIDE_GAP_MS = 100L
        private val SHADE_HIDE_AFTER_MS = listOf(120L, 350L)
        private const val OVER_SYSTEM_BARS =
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
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

/**
 * L'orologio dei fotogrammi per le animazioni fuori da una finestra Compose: il servizio non ne ha uno (la Home sì), e senza
 * le animazioni della tendina, che si fermano e si aprono con una molla, lanciano un errore. Si aggancia al ritmo dello schermo.
 */
private object ChoreographerClock : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
        suspendCancellableCoroutine { continuation ->
            val callback = Choreographer.FrameCallback { nanos -> continuation.resumeWith(runCatching { onFrame(nanos) }) }
            Choreographer.getInstance().postFrameCallback(callback)
            continuation.invokeOnCancellation { Choreographer.getInstance().removeFrameCallback(callback) }
        }
}
