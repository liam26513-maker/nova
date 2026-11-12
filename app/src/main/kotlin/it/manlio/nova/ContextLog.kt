package it.manlio.nova

import android.content.Context
import android.content.res.Configuration
import android.media.AudioDeviceInfo
import android.media.AudioManager
import it.manlio.nova.feature.home.Open
import java.io.File

/**
 * La situazione in cui si apre un'app, per i suggerimenti (SPEC §12.5): cuffie collegate, tastiera fisica, modalità accesa. Si
 * annota sul tablet a ogni cambio d'app davanti, solo se c'è una situazione da annotare; niente esce. File piccolo, con un tetto.
 */
internal class ContextLog(private val app: NovaApp) {
    private val file = File(app.filesDir, "contesti.txt")
    private var lastPkg: String? = null

    /** Le etichette di adesso: «cuffie», «tastiera», «mode:<id>». */
    fun tagsNow(): Set<String> =
        buildSet {
            app.modes.engine.active.value
                ?.modeId
                ?.let { add("mode:$it") }
            if (headphones(app)) add("cuffie")
            if (app.resources.configuration.keyboard == Configuration.KEYBOARD_QWERTY) add("tastiera")
        }

    /** Un'app è arrivata davanti: se c'è una situazione, la si annota. */
    fun note(pkg: String) {
        if (pkg == lastPkg) return
        lastPkg = pkg
        val tags = tagsNow()
        if (tags.isEmpty()) return
        runCatching {
            file.appendText("${System.currentTimeMillis()}\t$pkg\t${tags.joinToString(",")}\n")
            if (file.length() > MAX_BYTES) file.writeText(file.readLines().takeLast(KEEP_LINES).joinToString("\n", postfix = "\n"))
        }
    }

    /** Aggiunge alle aperture lette da Android la situazione annotata più vicina nel tempo (entro [MATCH_MS]). */
    fun withTags(opens: List<Open>): List<Open> {
        val notes =
            runCatching { file.readLines() }
                .getOrDefault(emptyList())
                .mapNotNull { line ->
                    val f = line.split('\t')
                    val time = f.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
                    Triple(
                        time,
                        f.getOrNull(1) ?: return@mapNotNull null,
                        f.getOrNull(2).orEmpty().split(',').filter { it.isNotBlank() }.toSet(),
                    )
                }.groupBy { it.second }
        return opens.map { open ->
            val near = notes[open.pkg]?.minByOrNull { kotlin.math.abs(it.first - open.time) }
            if (near != null && kotlin.math.abs(near.first - open.time) <= MATCH_MS) open.copy(tags = near.third) else open
        }
    }

    private companion object {
        const val MAX_BYTES = 200_000L
        const val KEEP_LINES = 2_000
        const val MATCH_MS = 10_000L
        val HEADPHONES =
            setOf(
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
            )

        fun headphones(context: Context): Boolean =
            runCatching {
                context.getSystemService(
                    AudioManager::class.java,
                ).getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in HEADPHONES }
            }.getOrDefault(false)
    }
}
