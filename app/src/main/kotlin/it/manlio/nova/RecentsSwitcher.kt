package it.manlio.nova

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.graphics.drawable.toBitmap
import it.manlio.nova.core.system.ShizukuStatus
import it.manlio.nova.feature.home.LockWallpaper
import it.manlio.nova.feature.home.WallpaperLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Una scheda delle App recenti di NOVA. */
internal class RecentCard(
    val id: Int,
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
)

/** Cosa mostra lo switcher: le schede (null mentre si leggono) e le anteprime man mano che arrivano. */
internal class SwitcherState {
    var cards by mutableStateOf<List<RecentCard>?>(null)
    val shots = mutableStateMapOf<Int, ImageBitmap>()
}

/**
 * Le App recenti di NOVA in stile iPad (SPEC §12.10). Le App recenti di Android le disegna il launcher di sistema e un launcher di
 * terze parti non le può sostituire: quando si aprono, NOVA ci mette sopra questa finestra, con le schede delle app, le anteprime
 * vere e lo sfondo di NOVA sfocato. Elenco, anteprime, apertura e chiusura passano da Shizuku ([it.manlio.nova.core.system.TaskTools]):
 * senza Shizuku restano quelle di Android. Esiste solo mentre è aperta.
 */
internal class RecentsSwitcher(
    private val service: AccessibilityService,
    private val app: NovaApp,
    private val scope: CoroutineScope,
) {
    private val windows = service.getSystemService(WindowManager::class.java)
    private var host: NovaControlService.PanelHost? = null
    private var state: SwitcherState? = null
    private var backdrop: Pair<String, ImageBitmap>? = null

    fun show() {
        if (host != null || !app.homePrefs.prefs.value.ipadRecents) return
        if (app.shizuku.status.value != ShizukuStatus.READY) return
        if (service.getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
        val current = SwitcherState()
        val view = NovaControlService.PanelHost(service).apply { onBack = ::goHome }
        view.addView(content(current))
        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                setFitInsetsTypes(0)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        if (runCatching { windows.addView(view, params) }.isFailure) return
        host = view
        state = current
        view.resume()
        scope.launch { load(current) }
    }

    fun dismiss() {
        val view = host ?: return
        host = null
        state = null
        view.destroy()
        runCatching { windows.removeView(view) }
    }

    /** Fuori dalle schede o Indietro: alla Home, come nelle App recenti dell'iPad. */
    private fun goHome() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        dismiss()
    }

    private fun open(card: RecentCard) {
        scope.launch {
            val ok = app.shizuku.openTask(card.id)
            if (!ok) service.packageManager.getLaunchIntentForPackage(card.packageName)?.let { runCatching { service.startActivity(it) } }
            dismiss()
        }
    }

    private fun close(card: RecentCard) {
        val current = state ?: return
        val left = current.cards.orEmpty().filter { it.id != card.id }
        current.cards = left
        scope.launch { app.shizuku.removeTask(card.id) }
        if (left.isEmpty()) goHome()
    }

    private fun closeAll() {
        val cards = state?.cards.orEmpty()
        scope.launch { cards.forEach { app.shizuku.removeTask(it.id) } }
        goHome()
    }

    @OptIn(ExperimentalCoroutinesApi::class) // getCompleted: si legge solo dopo isCompleted
    private suspend fun load(current: SwitcherState) {
        val home = ComponentName(service, HomeActivity::class.java).flattenToString()
        val tasks = app.shizuku.recentTasks(MAX_TASKS).filter { it.packageName != SYSTEM_LAUNCHER && it.component != home }
        val cards = withContext(Dispatchers.IO) { tasks.map { card(it.id, it.packageName) } }
        // Le anteprime arrivano in parallelo; le schede compaiono quando ci sono tutte (o dopo poco), così la schermata appare
        // una volta sola, già completa, invece di cambiare sotto gli occhi.
        val shots =
            tasks.map { task ->
                scope.async {
                    val bytes = app.shizuku.taskSnapshot(task.id, SHOT_SIDE_PX)
                    if (bytes.isEmpty()) null else withContext(Dispatchers.Default) { decode(bytes) }?.let { task.id to it }
                }
            }
        withTimeoutOrNull(WAIT_SHOTS_MS) { shots.awaitAll() }
        shots.filter { it.isCompleted }.forEach { done -> done.getCompleted()?.let { current.shots[it.first] = it.second } }
        current.cards = cards
        shots.filterNot { it.isCompleted }.forEach { late ->
            scope.launch { late.await()?.let { if (state === current) current.shots[it.first] = it.second } }
        }
    }

    private fun card(
        id: Int,
        pkg: String,
    ): RecentCard {
        val pm = service.packageManager
        val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        val icon = runCatching { pm.getApplicationIcon(pkg).toBitmap(ICON_PX, ICON_PX).asImageBitmap() }.getOrNull()
        return RecentCard(id, pkg, label, icon)
    }

    private fun decode(bytes: ByteArray): ImageBitmap? {
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }

    /**
     * Lo sfondo di NOVA disegnato piccolo (256 px) e poi sfocato dalla scheda video: morbido e leggero. Si rifà solo se cambia lo
     * sfondo; con lo sfondo di sistema non c'è (resta il colore scuro).
     */
    private fun backdrop(): ImageBitmap? {
        val prefs = app.homePrefs.prefs.value
        val key = LockWallpaper.key(prefs.copy(lockWallpaper = true), WallpaperLoader.photoFile(app).lastModified()) ?: return null
        backdrop?.takeIf { it.first == key }?.let { return it.second }
        val full = LockWallpaper.render(app, prefs, BACKDROP_PX) ?: return null
        val small = Bitmap.createScaledBitmap(full, BACKDROP_PX, BACKDROP_PX, true)
        if (small !== full) full.recycle()
        return small.asImageBitmap().also { backdrop = key to it }
    }

    private fun content(current: SwitcherState) =
        ComposeView(service).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            val image = backdrop()
            setContent {
                SwitcherScreen(
                    state = current,
                    backdrop = image,
                    onOpen = ::open,
                    onClose = ::close,
                    onCloseAll = ::closeAll,
                    onHome = ::goHome,
                )
            }
        }

    companion object {
        /** Il launcher di sistema: le sue App recenti sono quelle che copriamo. */
        const val SYSTEM_LAUNCHER = "com.android.launcher3"
        const val RECENTS_ACTIVITY = "com.android.quickstep.RecentsActivity"
        private const val MAX_TASKS = 16
        private const val SHOT_SIDE_PX = 720
        private const val ICON_PX = 96
        private const val BACKDROP_PX = 256
        private const val WAIT_SHOTS_MS = 450L
    }
}
