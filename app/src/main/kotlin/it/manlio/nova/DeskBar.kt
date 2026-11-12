package it.manlio.nova

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import it.manlio.nova.core.modes.Modes
import it.manlio.nova.core.system.RecentTask
import it.manlio.nova.core.system.ShizukuStatus
import it.manlio.nova.core.system.SystemCommand
import it.manlio.nova.feature.home.Favorites
import it.manlio.nova.feature.home.MoreItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * La barra del desktop nel Lavoro (SPEC §8.3): i preferiti della Home in una fila in basso, sopra le altre app; un tocco apre l'app
 * in una finestra flottante. Dopo un separatore, le app aperte (dalle recenti di Android, con Shizuku): un tocco la riporta davanti.
 * Non prende il fuoco. Si accende da tendina → Altro ed esiste solo durante il Lavoro.
 */
internal class DeskBar(
    private val service: AccessibilityService,
    private val app: NovaApp,
) {
    private val windows = service.getSystemService(WindowManager::class.java)
    private var view: View? = null
    private var on = false
    private var open = emptyList<RecentTask>()

    fun set(on: Boolean) {
        this.on = on
        if (on) refresh() else hide()
    }

    /** Un'altra finestra davanti: se sono cambiate le app aperte, la barra si rifà. */
    fun refresh() {
        if (!on || app.shizuku.status.value != ShizukuStatus.READY) {
            if (on && view == null) show()
            return
        }
        app.appScope.launch {
            val favs = favorites().map { it.packageName }.toSet()
            val now =
                app.shizuku
                    .recentTasks(MAX_RECENT)
                    .filter { it.packageName != app.packageName && it.packageName != SYSTEM_LAUNCHER && it.packageName !in favs }
                    .distinctBy { it.packageName }
                    .take(MAX_OPEN)
            withContext(Dispatchers.Main) {
                if (!on) return@withContext
                if (view == null || now.map { it.id } != open.map { it.id }) {
                    open = now
                    hide()
                    show()
                }
            }
        }
    }

    private fun show() {
        if (view != null) return
        val apps = favorites()
        if (apps.isEmpty() && open.isEmpty()) return
        val density = service.resources.displayMetrics.density
        val bar =
            LinearLayout(service).apply {
                orientation = LinearLayout.HORIZONTAL
                val pad = (PAD_DP * density).toInt()
                setPadding(pad, pad, pad, pad)
                background =
                    GradientDrawable().apply {
                        cornerRadius = RADIUS_DP * density
                        setColor(BAR_COLOR)
                    }
                apps.forEach { addView(icon(it, density)) }
                if (open.isNotEmpty()) {
                    if (apps.isNotEmpty()) addView(divider(density))
                    open.forEach { task -> addView(taskIcon(task, density)) }
                }
            }
        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = (BOTTOM_DP * density).toInt()
            }
        if (runCatching { windows.addView(bar, params) }.isSuccess) view = bar
    }

    private fun hide() {
        view?.let { runCatching { windows.removeView(it) } }
        view = null
    }

    /** I preferiti della Home, al massimo [MAX]: la chiave è «componente#utente». */
    private fun favorites(): List<ComponentName> =
        Favorites(File(app.filesDir, "home-favorites.txt"))
            .keys.value
            .mapNotNull { ComponentName.unflattenFromString(it.substringBefore('#')) }
            .take(MAX)

    private fun icon(
        component: ComponentName,
        density: Float,
    ): ImageView {
        val pm = service.packageManager
        val size = (ICON_DP * density).toInt()
        return ImageView(service).apply {
            setImageDrawable(runCatching { pm.getActivityIcon(component) }.getOrNull())
            contentDescription = runCatching { pm.getActivityInfo(component, 0).loadLabel(pm) }.getOrNull()
            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginStart = (GAP_DP * density).toInt() }
            setOnClickListener { open(component) }
        }
    }

    /** Una riga verticale sottile tra preferiti e app aperte, come nel dock dell'iPad. */
    private fun divider(density: Float) =
        View(service).apply {
            setBackgroundColor(DIVIDER_COLOR)
            layoutParams =
                LinearLayout.LayoutParams((1 * density).toInt().coerceAtLeast(1), (ICON_DP * density * DIVIDER_HEIGHT).toInt()).apply {
                    marginStart = (GAP_DP * 2 * density).toInt()
                    marginEnd = (GAP_DP * density).toInt()
                    gravity = Gravity.CENTER_VERTICAL
                }
        }

    private fun taskIcon(
        task: RecentTask,
        density: Float,
    ): ImageView {
        val pm = service.packageManager
        val size = (ICON_DP * density).toInt()
        return ImageView(service).apply {
            setImageDrawable(runCatching { pm.getApplicationIcon(task.packageName) }.getOrNull())
            contentDescription = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(task.packageName, 0)) }.getOrNull()
            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginStart = (GAP_DP * density).toInt() }
            setOnClickListener { app.appScope.launch { app.shizuku.openTask(task.id) } }
        }
    }

    private fun open(component: ComponentName) {
        val command = runCatching { SystemCommand.OpenInWindow(component.packageName, component.className) }.getOrNull() ?: return
        if (app.shizuku.status.value == ShizukuStatus.READY) app.appScope.launch { app.shizuku.run(command) }
    }

    private companion object {
        const val MAX = 6
        const val MAX_OPEN = 4
        const val MAX_RECENT = 12
        const val SYSTEM_LAUNCHER = "com.android.launcher3"
        const val DIVIDER_HEIGHT = 0.7f
        const val DIVIDER_COLOR = 0x55FFFFFF
        const val ICON_DP = 44
        const val GAP_DP = 6
        const val PAD_DP = 8
        const val RADIUS_DP = 24f
        const val BOTTOM_DP = 40
        const val BAR_COLOR = 0xCC1C1C1E.toInt()
    }
}

/** La voce di Altro durante il Lavoro: accende o spegne la barra (ricordato in [ModeOptions.DESK_BAR]). */
internal fun deskBarItem(
    context: Context,
    app: NovaApp,
): MoreItem? {
    if (!app.modes.isActive(Modes.WORK) || app.shizuku.status.value != ShizukuStatus.READY) return null
    val text = { on: Boolean -> context.getString(if (on) R.string.more_on else R.string.more_off) }
    val on = { app.modes.options.values.value[ModeOptions.DESK_BAR] == true }
    return MoreItem(context.getString(R.string.desk_bar), text(on())) {
        app.modes.options.set(ModeOptions.DESK_BAR, !on())
        text(on())
    }
}
