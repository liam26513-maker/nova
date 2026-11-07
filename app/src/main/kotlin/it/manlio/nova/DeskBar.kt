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
import it.manlio.nova.core.system.ShizukuStatus
import it.manlio.nova.core.system.SystemCommand
import it.manlio.nova.feature.home.Favorites
import it.manlio.nova.feature.home.MoreItem
import kotlinx.coroutines.launch
import java.io.File

/**
 * La barra del desktop nel Lavoro (SPEC §8.3): i preferiti della Home in una fila in basso, sopra le altre app; un tocco apre l'app
 * in una finestra flottante. Non prende il fuoco. Si accende da tendina → Altro ed esiste solo durante il Lavoro.
 */
internal class DeskBar(
    private val service: AccessibilityService,
    private val app: NovaApp,
) {
    private val windows = service.getSystemService(WindowManager::class.java)
    private var view: View? = null

    fun set(on: Boolean) = if (on) show() else hide()

    private fun show() {
        if (view != null) return
        val apps = favorites()
        if (apps.isEmpty()) return
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

    private fun open(component: ComponentName) {
        val command = runCatching { SystemCommand.OpenInWindow(component.packageName, component.className) }.getOrNull() ?: return
        if (app.shizuku.status.value == ShizukuStatus.READY) app.appScope.launch { app.shizuku.run(command) }
    }

    private companion object {
        const val MAX = 6
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
