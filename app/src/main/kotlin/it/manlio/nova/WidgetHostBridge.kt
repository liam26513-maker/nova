package it.manlio.nova

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import it.manlio.nova.feature.home.ExternalWidgets
import it.manlio.nova.feature.home.WidgetProvider

/**
 * Widget di altre app (SPEC §6.5) tramite `AppWidgetHost`. Il widget si aggiunge così: Android chiede il permesso
 * di collegarlo (una volta per widget), poi l'app del widget può mostrare la sua schermata di configurazione.
 * Ascolta gli aggiornamenti solo tra [start] e [stop], cioè mentre la Home è visibile.
 * Ogni chiamata ad Android è protetta: un widget difettoso viene scartato, non fa cadere la Home.
 */
class WidgetHostBridge(private val activity: ComponentActivity) : ExternalWidgets {
    private val manager = AppWidgetManager.getInstance(activity)
    private val host = AppWidgetHost(activity, HOST_ID)
    private var pending: Pending? = null
    private var infos = emptyMap<String, AppWidgetProviderInfo>()

    private class Pending(
        val id: Int,
        val info: AppWidgetProviderInfo,
        val onAdded: (Int, Int, Int) -> Unit,
    )

    private val bindLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val p = pending
            if (p != null && result.resultCode == Activity.RESULT_OK) configure(p) else discard()
        }
    private val configureLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val p = pending
            if (p != null && result.resultCode == Activity.RESULT_OK) finish(p) else discard()
        }

    fun start() = runCatching { host.startListening() }

    fun stop() = runCatching { host.stopListening() }

    override fun providers(): List<WidgetProvider> {
        val found =
            runCatching { manager.installedProviders }.getOrDefault(
                emptyList(),
            ).filter { it.provider.packageName != activity.packageName }
        infos = found.associateBy { it.provider.flattenToString() }
        val pm = activity.packageManager
        return found
            .map { info ->
                val (cols, rows) = cellsOf(info)
                WidgetProvider(
                    info.provider.flattenToString(),
                    runCatching { info.loadLabel(pm) }.getOrDefault(info.provider.packageName),
                    cols,
                    rows,
                )
            }.sortedBy { it.label.lowercase() }
    }

    override fun view(
        context: Context,
        ref: Int,
    ): View? =
        runCatching {
            val info = manager.getAppWidgetInfo(ref) ?: return null
            host.createView(activity, ref, info)
        }.getOrNull()

    override fun add(
        provider: WidgetProvider,
        onAdded: (ref: Int, cols: Int, rows: Int) -> Unit,
    ) {
        val info = infos[provider.id] ?: return
        runCatching {
            val id = host.allocateAppWidgetId()
            val p = Pending(id, info, onAdded)
            pending = p
            if (manager.bindAppWidgetIdIfAllowed(id, info.provider)) {
                configure(p)
            } else {
                val bind =
                    Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                bindLauncher.launch(bind)
            }
        }.onFailure { discard() }
    }

    override fun delete(ref: Int) {
        runCatching { host.deleteAppWidgetId(ref) }
    }

    private fun configure(p: Pending) {
        val component = p.info.configure
        if (component == null) {
            finish(p)
            return
        }
        runCatching {
            configureLauncher.launch(
                Intent(
                    AppWidgetManager.ACTION_APPWIDGET_CONFIGURE,
                ).setComponent(component).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, p.id),
            )
        }.onFailure { discard() }
    }

    private fun finish(p: Pending) {
        pending = null
        val (cols, rows) = cellsOf(p.info)
        p.onAdded(p.id, cols, rows)
    }

    private fun discard() {
        pending?.let { runCatching { host.deleteAppWidgetId(it.id) } }
        pending = null
    }

    /** Misura in caselle del launcher: la formula di Android, (dp + 30) / 70, al minimo 1 e al massimo 4. */
    private fun cellsOf(info: AppWidgetProviderInfo): Pair<Int, Int> {
        val density = activity.resources.displayMetrics.density

        fun cells(px: Int) = (((px / density) + CELL_SLACK) / CELL_DP).toInt().coerceIn(1, MAX_CELLS)
        return cells(info.minWidth) to cells(info.minHeight)
    }

    private companion object {
        const val HOST_ID = 0x4E4F5641 // "NOVA"
        const val CELL_DP = 70
        const val CELL_SLACK = 30
        const val MAX_CELLS = 4
    }
}
