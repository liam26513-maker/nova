package it.manlio.nova

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import it.manlio.nova.core.safemode.SafeModeActivity
import it.manlio.nova.core.system.ShizukuStatus
import it.manlio.nova.feature.improve.ImproveContext
import it.manlio.nova.feature.improve.ImproveMode
import it.manlio.nova.feature.improve.ImprovePrompt
import it.manlio.nova.feature.improve.ImproveScreen
import it.manlio.nova.feature.improve.ImproveUiState
import it.manlio.nova.feature.improve.RepairData
import it.manlio.nova.feature.improve.RepairReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Collega Migliora/Ripara al tablet: contesto reale, raccolta del report (crash, log di NOVA, log
 * di sistema della sola app), caricamento in `reports/` se c'è il token, appunti e apertura di Claude Code.
 */
@Composable
fun ImproveRoute(
    app: NovaApp,
    mode: ImproveMode,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(ImproveUiState()) }
    LaunchedEffect(Unit) {
        val data = withContext(Dispatchers.IO) { ImproveSupport.collect(app, context, description = "") }
        state =
            state.copy(
                includes = ImproveSupport.includes(context, data),
                reportPreview = RepairReport.build(data),
            )
    }
    ImproveScreen(
        initialMode = mode,
        state = state,
        onSend = { chosen, text ->
            scope.launch {
                state = state.copy(sending = true, message = null)
                val message = ImproveSupport.send(app, context, chosen, text)
                state = state.copy(sending = false, message = message)
            }
        },
        onDone = onDone,
    )
}

/** Le parti che parlano con il sistema, separate dalla schermata. */
internal object ImproveSupport {
    fun contextOf(app: NovaApp): ImproveContext {
        val p = app.profile.value
        val device =
            p?.let {
                "${it.manufacturer} ${it.model}, Android SDK ${it.androidSdk}, ${it.ramMb / MB} GB RAM, " +
                    "${it.screenWidthPx}×${it.screenHeightPx} ${it.densityDpi} dpi, livello grafico ${app.graphics.value.level}"
            } ?: "profilo non ancora letto"
        val shizuku =
            when (app.shizuku.status.value) {
                ShizukuStatus.READY -> "attivo"
                ShizukuStatus.NEEDS_PERMISSION -> "attivo, senza permesso per NOVA"
                ShizukuStatus.NOT_RUNNING -> "non attivo"
            }
        return ImproveContext(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, app.config.current.value.meta.version, device, shizuku)
    }

    fun collect(
        app: NovaApp,
        context: Context,
        description: String,
    ): RepairData {
        val crashes =
            SafeModeActivity
                .crashDir(context)
                .listFiles { f -> f.name.startsWith("crash-") && f.name.endsWith(".txt") }
                ?.sortedByDescending { it.name }
                ?.take(MAX_CRASHES)
                ?.map { it.readText() }
                .orEmpty()
        return RepairData(
            description = description,
            context = contextOf(app),
            crashes = crashes,
            appLog = app.log.snapshot().map { it.toString() },
            systemLog = ownLogcat(),
        )
    }

    fun includes(
        context: Context,
        data: RepairData,
    ): List<String> =
        listOf(
            context.getString(R.string.repair_includes_crashes, data.crashes.size),
            context.getString(R.string.repair_includes_log, data.appLog.size),
            context.getString(R.string.repair_includes_system, data.systemLog.size),
            context.getString(R.string.repair_includes_context),
        )

    /** Log di sistema della sola NOVA: un'app può sempre leggere i propri, senza permessi né Shizuku. */
    private fun ownLogcat(): List<String> =
        runCatching {
            val process =
                ProcessBuilder("logcat", "-d", "-t", LOGCAT_LINES.toString(), "--pid=${Process.myPid()}")
                    .redirectErrorStream(true)
                    .start()
            val lines = process.inputStream.bufferedReader().readLines()
            process.waitFor(LOGCAT_TIMEOUT_S, TimeUnit.SECONDS)
            lines
        }.getOrDefault(emptyList())

    /** @return l'esito, in parole semplici. */
    suspend fun send(
        app: NovaApp,
        context: Context,
        mode: ImproveMode,
        text: String,
    ): String {
        val ctx = contextOf(app)
        val prompt: String
        val extra: String?
        if (mode == ImproveMode.IMPROVE) {
            if (text.isBlank()) return context.getString(R.string.improve_empty)
            prompt = ImprovePrompt.improve(text, ctx)
            extra = null
        } else {
            val report = withContext(Dispatchers.IO) { RepairReport.build(collect(app, context, text)) }
            val path = ImprovePrompt.reportPath(text, System.currentTimeMillis())
            val uploaded =
                app.updater.hasToken() &&
                    runCatching { app.github.putReport(path, report, "Ripara: ${ImprovePrompt.slug(text)}") }
                        .onFailure { app.log.w("improve", "report non caricato: ${it.message}") }
                        .isSuccess
            prompt = ImprovePrompt.repair(text, if (uploaded) path else null, ctx)
            extra = if (uploaded) null else report
        }
        // Appunti: richiesta completa (e, se non caricato, anche il report) come riserva.
        val clip = if (extra == null) prompt else "$prompt\n\n$extra"
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("NOVA", clip))
        val opened =
            runCatching {
                val link = ImprovePrompt.claudeLink(prompt, BuildConfig.GITHUB_REPO)
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
        return when {
            !opened -> context.getString(R.string.improve_not_opened)
            mode == ImproveMode.REPAIR && extra != null -> context.getString(R.string.repair_sent_clipboard)
            mode == ImproveMode.REPAIR -> context.getString(R.string.repair_sent)
            else -> context.getString(R.string.improve_sent)
        }
    }

    private const val MB = 1024
    private const val MAX_CRASHES = 3
    private const val LOGCAT_LINES = 400
    private const val LOGCAT_TIMEOUT_S = 3L
}
