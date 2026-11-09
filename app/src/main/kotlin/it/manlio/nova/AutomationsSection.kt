package it.manlio.nova

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.widget.EditText
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import it.manlio.nova.core.design.SettingsRow
import it.manlio.nova.core.design.SettingsSection
import it.manlio.nova.core.modes.Modes

@Suppress("MagicNumber") // le ore del giorno
private val HOURS = (0..23).toList()

@Suppress("MagicNumber") // le soglie tra cui scegliere
private val BATTERY_LIMITS = listOf(10, 15, 20, 30, 50)

/** Il margine laterale dei dialoghi di Android, uguale a quello del titolo. */
private const val DIALOG_SIDE_DP = 24

/**
 * Impostazioni → Automazioni (SPEC §12.4): regole «quando → allora» composte scegliendo da elenchi, una domanda alla volta.
 * Un tocco su una regola la accende, la spegne o la elimina. shortcut: niente blocchi trascinabili né condizioni «se»; si aggiungono
 * quando una regola semplice non basta.
 */
@Composable
internal fun AutomationsSection(backdrop: ImageBitmap) {
    val context = LocalContext.current
    val engine = (context.applicationContext as NovaApp).automations
    var rules by remember { mutableStateOf(engine.rules()) }
    val save = { list: List<Rule> ->
        engine.update(list)
        rules = list
    }
    SettingsSection(backdrop, header = stringResource(R.string.auto_rules_title), footer = stringResource(R.string.auto_rules_footer)) {
        rules.forEachIndexed { index, rule ->
            SettingsRow(
                describe(context, rule),
                summary = stringResource(if (rule.enabled) R.string.auto_rule_on else R.string.auto_rule_off),
                showDivider = index > 0,
                onClick = { manage(context, rule, rules, save) },
                trailing = { if (rule.enabled) Check() },
            )
        }
        SettingsRow(
            stringResource(R.string.auto_rule_new),
            showDivider = rules.isNotEmpty(),
            onClick = { create(context) { save(rules + it) } },
        )
    }
}

private fun manage(
    context: Context,
    rule: Rule,
    rules: List<Rule>,
    save: (List<Rule>) -> Unit,
) {
    val choices =
        arrayOf(
            context.getString(if (rule.enabled) R.string.auto_rule_turn_off else R.string.auto_rule_turn_on),
            context.getString(R.string.auto_rule_delete),
        )
    pick(context, describe(context, rule), choices.toList()) { which ->
        save(
            if (which == 0) {
                rules.map {
                    if (it.id == rule.id) {
                        it.copy(
                            enabled = !it.enabled,
                        )
                    } else {
                        it
                    }
                }
            } else {
                rules.filter { it.id != rule.id }
            },
        )
    }
}

/** Le domande della regola nuova: quando, con che valore, allora, con che valore. */
private fun create(
    context: Context,
    onDone: (Rule) -> Unit,
) {
    val triggers = When.entries
    pick(context, context.getString(R.string.auto_ask_when), triggers.map { whenLabel(context, it, "") }) { t ->
        val trigger = triggers[t]
        askWhenArg(context, trigger) { whenArg ->
            val actions = Then.entries
            pick(context, context.getString(R.string.auto_ask_then), actions.map { thenLabel(context, it, "") }) { a ->
                askThenArg(context, actions[a]) { thenArg ->
                    askIf(context) { condition, ifArg ->
                        onDone(Rule(System.currentTimeMillis(), true, trigger, whenArg, actions[a], thenArg, condition, ifArg))
                    }
                }
            }
        }
    }
}

/** L'ultima domanda: «Solo se…», con «Sempre» per prima. Tra due orari chiede l'ora d'inizio e quella di fine. */
private fun askIf(
    context: Context,
    next: (If, String) -> Unit,
) {
    val conditions = If.entries
    pick(
        context,
        context.getString(R.string.auto_ask_if),
        conditions.map { ifLabel(context, it, "").replaceFirstChar(Char::uppercase) },
    ) { c ->
        if (conditions[c] != If.BETWEEN_HOURS) return@pick next(conditions[c], "")
        val hours = HOURS.map { "%02d:00".format(it) }
        pick(context, context.getString(R.string.auto_ask_from), hours) { from ->
            pick(context, context.getString(R.string.auto_ask_to), hours) { to -> next(If.BETWEEN_HOURS, "${HOURS[from]}-${HOURS[to]}") }
        }
    }
}

private fun askWhenArg(
    context: Context,
    trigger: When,
    next: (String) -> Unit,
) = when (trigger) {
    When.TIME -> pick(context, context.getString(R.string.auto_ask_hour), HOURS.map { "%02d:00".format(it) }) { next(HOURS[it].toString()) }
    When.BATTERY_BELOW ->
        pick(
            context,
            context.getString(R.string.auto_ask_battery),
            BATTERY_LIMITS.map {
                "$it%"
            },
        ) { next(BATTERY_LIMITS[it].toString()) }
    else -> next("")
}

private fun askThenArg(
    context: Context,
    action: Then,
    next: (String) -> Unit,
) {
    when (action) {
        Then.OPEN_APP -> {
            val pm = context.packageManager
            val apps =
                pm
                    .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                    .distinctBy { it.first }
                    .sortedBy { it.second.lowercase() }
            pick(context, context.getString(R.string.auto_ask_app), apps.map { it.second }) { next(apps[it].first) }
        }
        Then.MODE_ON ->
            pick(
                context,
                context.getString(R.string.auto_ask_mode),
                Modes.all.map {
                    modeName(context, it.id)
                },
            ) { next(Modes.all[it].id) }
        Then.TOGGLE_ON, Then.TOGGLE_OFF -> {
            val toggles = AutomationEngine.TOGGLES
            pick(context, context.getString(R.string.auto_ask_setting), toggles.map { toggleName(context, it) }) { next(toggles[it].name) }
        }
        Then.NOTIFY -> {
            val field = EditText(context).apply { isSingleLine = true }
            val side = (DIALOG_SIDE_DP * context.resources.displayMetrics.density).toInt()
            val box = FrameLayout(context).apply { setPadding(side, 0, side, 0) }
            box.addView(field)
            AlertDialog
                .Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.auto_ask_text)
                .setView(box)
                .setPositiveButton(android.R.string.ok) { _, _ -> field.text.toString().trim().takeIf { it.isNotEmpty() }?.let(next) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        Then.MODE_OFF -> next("")
    }
}

private fun pick(
    context: Context,
    title: String,
    items: List<String>,
    onPick: (Int) -> Unit,
) {
    AlertDialog
        .Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        .setTitle(title)
        .setItems(items.toTypedArray<CharSequence>()) { _, which -> onPick(which) }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}
