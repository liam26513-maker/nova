package it.manlio.nova

import android.content.Context
import android.os.Handler
import android.widget.Toast
import it.manlio.nova.core.modes.Modes
import it.manlio.nova.core.system.RecentsParser
import it.manlio.nova.core.system.ShizukuStatus
import it.manlio.nova.core.system.SystemCommand
import it.manlio.nova.feature.home.MoreItem
import kotlinx.coroutines.launch
import java.io.File

/** I set di finestre salvati: una riga per set, le app separate da «;». Si tengono gli ultimi [MAX]. Puro, si prova senza Android. */
internal class WindowSetStore(private val file: File) {
    fun all(): List<List<String>> =
        runCatching { file.readLines() }.getOrDefault(
            emptyList(),
        ).map { it.split(';').filter(String::isNotBlank) }.filter { it.isNotEmpty() }

    fun add(set: List<String>) {
        val sets = (listOf(set) + all().filter { it != set }).take(MAX)
        file.parentFile?.mkdirs()
        file.writeText(sets.joinToString("\n") { it.joinToString(";") })
    }

    companion object {
        const val MAX = 3
    }
}

/**
 * Set di finestre del Lavoro (SPEC §8.3, stile Stage Manager): «Salva le finestre aperte» ricorda le app aperte in una finestra
 * flottante, «Riapri» le riapre tutte. Android non permette di rimettere posizione e misura: le finestre si dispongono da sole.
 */
internal class WindowSets(
    private val context: Context,
    private val app: NovaApp,
) {
    private val store = WindowSetStore(File(app.filesDir, "set-finestre.txt"))

    fun items(): List<MoreItem> {
        if (!app.modes.isActive(Modes.WORK) || app.shizuku.status.value != ShizukuStatus.READY) return emptyList()
        val save =
            MoreItem(context.getString(R.string.window_set_save)) { _ ->
                save()
                null
            }
        return listOf(save) +
            store.all().map { set ->
                MoreItem(context.getString(R.string.window_set_open, set.joinToString(" + ", transform = ::label))) { _ ->
                    open(set)
                    null
                }
            }
    }

    private fun save() {
        app.appScope.launch {
            val open = RecentsParser.openWindows(app.shizuku.run(SystemCommand.ListRecents).output)
            if (open.isNotEmpty()) store.add(open)
            say(if (open.isEmpty()) R.string.window_set_none else R.string.window_set_saved)
        }
    }

    private fun open(set: List<String>) {
        app.appScope.launch {
            set.reversed().forEach { component ->
                val (pkg, activity) = component.split('/', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                runCatching { SystemCommand.OpenInWindow(pkg, activity) }.getOrNull()?.let { app.shizuku.run(it) }
            }
        }
    }

    private fun label(component: String): String {
        val pkg = component.substringBefore('/')
        return runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }
            .getOrDefault(pkg)
    }

    private fun say(id: Int) {
        Handler(context.mainLooper).post { Toast.makeText(context, id, Toast.LENGTH_LONG).show() }
    }
}
