package it.manlio.nova

import java.io.File

/** Quando scatta una regola (SPEC §12.4). Solo eventi che Android annuncia da solo: niente controlli a intervalli. */
internal enum class When { TIME, CHARGER_ON, CHARGER_OFF, HEADPHONES_ON, HEADPHONES_OFF, BATTERY_BELOW, WIFI_ON, WIFI_OFF }

/** Cosa fa una regola. Le impostazioni di sistema passano da Shizuku e finiscono nel registro, quindi si possono annullare. */
internal enum class Then { OPEN_APP, MODE_ON, MODE_OFF, TOGGLE_ON, TOGGLE_OFF, NOTIFY }

/**
 * Una regola «quando → allora». [whenArg]: l'ora (0–23) per [When.TIME], la percentuale per [When.BATTERY_BELOW]; [thenArg]: il
 * pacchetto, la modalità, l'interruttore o il testo dell'avviso.
 */
internal data class Rule(
    val id: Long,
    val enabled: Boolean,
    val trigger: When,
    val whenArg: String,
    val action: Then,
    val thenArg: String,
)

/** Le regole su file, una per riga, campi separati da un carattere che il testo non può contenere. Puro: si prova senza Android. */
internal object RuleCodec {
    private const val SEP = '\u001F' // separatore di unità ASCII: non si scrive con la tastiera
    private const val FIELDS = 6

    fun encode(rules: List<Rule>): String =
        rules.joinToString("\n") { r ->
            listOf(r.id, r.enabled, r.trigger.name, clean(r.whenArg), r.action.name, clean(r.thenArg)).joinToString(SEP.toString())
        }

    /** Righe rovinate o di una versione futura si saltano: le altre regole restano. */
    @Suppress("MagicNumber") // le posizioni dei campi nella riga
    fun decode(text: String): List<Rule> =
        text.lines().mapNotNull { line ->
            val f = line.split(SEP)
            if (f.size != FIELDS) return@mapNotNull null
            runCatching { Rule(f[0].toLong(), f[1].toBooleanStrict(), When.valueOf(f[2]), f[3], Then.valueOf(f[4]), f[5]) }.getOrNull()
        }

    private fun clean(text: String) = text.replace(SEP, ' ').replace('\n', ' ')
}

/** Il file delle regole, con scrittura sicura (prima un file temporaneo). */
internal class RuleStore(private val file: File) {
    fun all(): List<Rule> = runCatching { RuleCodec.decode(file.readText()) }.getOrDefault(emptyList())

    fun save(rules: List<Rule>) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(RuleCodec.encode(rules))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }
}
