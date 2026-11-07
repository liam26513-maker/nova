package it.manlio.nova

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import it.manlio.nova.core.modes.Modes
import it.manlio.nova.core.system.ShizukuStatus
import it.manlio.nova.core.system.SystemCommand
import it.manlio.nova.core.system.SystemCommand.KeyCombo.Key
import it.manlio.nova.feature.home.MoreItem
import kotlinx.coroutines.launch

/**
 * La barra Annulla/Ripeti sopra le app di disegno (SPEC §9.4), durante la Creativa: manda all'app davanti le scorciatoie da tastiera
 * con Shizuku. Non prende il fuoco, così i tasti arrivano all'app. Piccola, sul bordo destro, e si accende da tendina → Altro.
 * shortcut: Ctrl+Z e Ctrl+Shift+Z per tutte le app; i profili per app di `config/pen-apps.json` arriveranno quando servono.
 */
internal class PenBar(
    private val service: AccessibilityService,
    private val app: NovaApp,
) {
    private val windows = service.getSystemService(WindowManager::class.java)
    private var view: View? = null

    fun set(on: Boolean) = if (on) show() else hide()

    private fun show() {
        if (view != null) return
        val density = service.resources.displayMetrics.density
        val bar =
            LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                background =
                    GradientDrawable().apply {
                        cornerRadius = RADIUS_DP * density
                        setColor(BAR_COLOR)
                    }
                addView(button(service, "↶", R.string.pen_bar_undo, density) { send(UNDO) })
                addView(button(service, "↷", R.string.pen_bar_redo, density) { send(REDO) })
            }
        val params =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                x = (MARGIN_DP * density).toInt()
            }
        if (runCatching { windows.addView(bar, params) }.isSuccess) view = bar
    }

    private fun hide() {
        view?.let { runCatching { windows.removeView(it) } }
        view = null
    }

    private fun send(keys: List<Key>) {
        if (app.shizuku.status.value == ShizukuStatus.READY) app.appScope.launch { app.shizuku.run(SystemCommand.KeyCombo(keys)) }
    }

    private companion object {
        val UNDO = listOf(Key.CTRL, Key.Z)
        val REDO = listOf(Key.CTRL, Key.SHIFT, Key.Z)
        const val RADIUS_DP = 22f
        const val MARGIN_DP = 8
        const val SIZE_DP = 48
        const val TEXT_SP = 22f
        const val BAR_COLOR = 0xCC1C1C1E.toInt()

        fun button(
            context: Context,
            symbol: String,
            label: Int,
            density: Float,
            onClick: () -> Unit,
        ) = TextView(context).apply {
            text = symbol
            textSize = TEXT_SP
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = context.getString(label)
            val size = (SIZE_DP * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
            setOnClickListener { onClick() }
        }
    }
}

/** La voce di Altro durante la Creativa: accende o spegne la barra (ricordato in [ModeOptions.PEN_BAR]). */
internal fun penBarItem(
    context: Context,
    app: NovaApp,
): MoreItem? {
    if (!app.modes.isActive(Modes.CREATIVE) || app.shizuku.status.value != ShizukuStatus.READY) return null
    val text = { on: Boolean -> context.getString(if (on) R.string.more_on else R.string.more_off) }
    val on = { app.modes.options.values.value[ModeOptions.PEN_BAR] == true }
    return MoreItem(context.getString(R.string.pen_bar), text(on())) {
        app.modes.options.set(ModeOptions.PEN_BAR, !on())
        text(on())
    }
}
