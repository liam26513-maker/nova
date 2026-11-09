package it.manlio.nova

import java.io.File

/** Quando scatta una regola (SPEC §12.4). Solo eventi che Android annuncia da solo: niente controlli a intervalli. */
internal enum class When { TIME, CHARGER_ON, CHARGER_OFF, HEADPHONES_ON, HEADPHONES_OFF, BATTERY_BELOW, WIFI_ON, WIFI_OFF }

/** Cosa fa una regola. Le impostazioni di sistema passano da Shizuku e finiscono nel registro, quindi si possono annullare. */
internal enum class Then { OPEN_APP, MODE_ON, MODE_OFF, TOGGLE_ON, TOGGLE_OFF, NOTIFY }

/**
 * La condizione «solo se» di una regola, controllata quando l'evento arriva: niente da ascoltare in più. [Rule.ifArg] per
 * [If.BETWEEN_HOURS] è «da-a» in ore (es. «8-20»; «22-7» passa la mezzanotte).
 */
internal enum class If { ALWAYS, BETWEEN_HOURS, WEEKDAYS, WEEKEND, CHARGING, NOT_CHARGING }

/** Lo stato del momento in cui scatta la regola: ora, giorno (1 = lunedì … 7 = domenica), carica. Puro, per le prove. */
internal data class Moment(
    val hour: Int,
    val dayOfWeek: Int,
    val charging: Boolean,
)

internal object Conditions {
    private const val SATURDAY = 6

    fun holds(
        condition: If,
        arg: String,
        now: Moment,
    ): Boolean =
        when (condition) {
            If.ALWAYS -> true
            If.BETWEEN_HOURS -> between(arg, now.hour)
            If.WEEKDAYS -> now.dayOfWeek < SATURDAY
            If.WEEKEND -> now.dayOfWeek >= SATURDAY
            If.CHARGING -> now.charging
            If.NOT_CHARGING -> !now.charging
        }

    /** «8-20»: dalle 8 alle 19:59. «22-7»: dalle 22 alle 6:59. Un valore rotto non blocca la regola. */
    private fun between(
        arg: String,
        hour: Int,
    ): Boolean {
        val from = arg.substringBefore('-').toIntOrNull() ?: return true
        val to = arg.substringAfter('-').toIntOrNull() ?: return true
        return if (from <= to) hour in from until to else hour >= from || hour < to
    }
}

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
    val condition: If = If.ALWAYS,
    val ifArg: String = "",
)

/** Le regole su file, una per riga, campi separati da un carattere che il testo non può contenere. Puro: si prova senza Android. */
internal object RuleCodec {
    private const val SEP = '\u001F' // separatore di unità ASCII: non si scrive con la tastiera
    private const val FIELDS = 6 // le regole di prima, senza condizione
    private const val FIELDS_WITH_IF = 8

    fun encode(rules: List<Rule>): String =
        rules.joinToString("\n") { r ->
            listOf(r.id, r.enabled, r.trigger.name, clean(r.whenArg), r.action.name, clean(r.thenArg), r.condition.name, clean(r.ifArg))
                .joinToString(SEP.toString())
        }

    /** Righe rovinate o di una versione futura si saltano: le altre regole restano. */
    @Suppress("MagicNumber") // le posizioni dei campi nella riga
    fun decode(text: String): List<Rule> =
        text.lines().mapNotNull { line ->
            val f = line.split(SEP)
            if (f.size != FIELDS && f.size != FIELDS_WITH_IF) return@mapNotNull null
            runCatching {
                val condition = if (f.size == FIELDS_WITH_IF) If.valueOf(f[6]) else If.ALWAYS
                val ifArg = if (f.size == FIELDS_WITH_IF) f[7] else ""
                Rule(f[0].toLong(), f[1].toBooleanStrict(), When.valueOf(f[2]), f[3], Then.valueOf(f[4]), f[5], condition, ifArg)
            }.getOrNull()
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
