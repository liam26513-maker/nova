package it.manlio.nova

import android.content.Context
import it.manlio.nova.core.modes.Modes
import it.manlio.nova.core.system.QuickToggle

/** «Quando colleghi le cuffie → apri Spotify», detto in italiano. */
internal fun describe(
    context: Context,
    rule: Rule,
) = whenLabel(context, rule.trigger, rule.whenArg) + " → " + thenLabel(context, rule.action, rule.thenArg)

internal fun whenLabel(
    context: Context,
    trigger: When,
    arg: String,
): String =
    when (trigger) {
        When.TIME ->
            if (arg.isEmpty()) {
                context.getString(
                    R.string.auto_when_time_any,
                )
            } else {
                context.getString(R.string.auto_when_time, "%02d:00".format(arg.toIntOrNull() ?: 0))
            }
        When.CHARGER_ON -> context.getString(R.string.auto_when_charger_on)
        When.CHARGER_OFF -> context.getString(R.string.auto_when_charger_off)
        When.HEADPHONES_ON -> context.getString(R.string.auto_when_headphones_on)
        When.HEADPHONES_OFF -> context.getString(R.string.auto_when_headphones_off)
        When.BATTERY_BELOW ->
            if (arg.isEmpty()) {
                context.getString(
                    R.string.auto_when_battery_any,
                )
            } else {
                context.getString(R.string.auto_when_battery, arg)
            }
        When.WIFI_ON -> context.getString(R.string.auto_when_wifi_on)
        When.WIFI_OFF -> context.getString(R.string.auto_when_wifi_off)
    }

internal fun thenLabel(
    context: Context,
    action: Then,
    arg: String,
): String =
    when (action) {
        Then.OPEN_APP ->
            if (arg.isEmpty()) {
                context.getString(
                    R.string.auto_then_app_any,
                )
            } else {
                context.getString(R.string.auto_then_app, appName(context, arg))
            }
        Then.MODE_ON ->
            if (arg.isEmpty()) {
                context.getString(
                    R.string.auto_then_mode_any,
                )
            } else {
                context.getString(R.string.auto_then_mode, modeName(context, arg))
            }
        Then.MODE_OFF -> context.getString(R.string.auto_then_mode_off)
        Then.TOGGLE_ON ->
            if (arg.isEmpty()) {
                context.getString(
                    R.string.auto_then_on_any,
                )
            } else {
                context.getString(R.string.auto_then_on, toggleName(context, arg))
            }
        Then.TOGGLE_OFF ->
            if (arg.isEmpty()) {
                context.getString(
                    R.string.auto_then_off_any,
                )
            } else {
                context.getString(R.string.auto_then_off, toggleName(context, arg))
            }
        Then.NOTIFY ->
            if (arg.isEmpty()) {
                context.getString(
                    R.string.auto_then_notify_any,
                )
            } else {
                context.getString(R.string.auto_then_notify, arg)
            }
    }

private fun appName(
    context: Context,
    pkg: String,
) = runCatching {
    context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
}.getOrDefault(pkg)

internal fun modeName(
    context: Context,
    id: String,
) = Modes.all.firstOrNull { it.id == id }?.let { (context.applicationContext as NovaApp).modes.name(it) } ?: id

private fun toggleName(
    context: Context,
    name: String,
) = runCatching { toggleName(context, QuickToggle.valueOf(name)) }.getOrDefault(name)

internal fun toggleName(
    context: Context,
    toggle: QuickToggle,
): String =
    context.getString(
        when (toggle) {
            QuickToggle.DND -> R.string.auto_toggle_dnd
            QuickToggle.NIGHT_LIGHT -> R.string.auto_toggle_night
            QuickToggle.BATTERY_SAVER -> R.string.auto_toggle_saver
            QuickToggle.DARK_THEME -> R.string.auto_toggle_dark
            else -> R.string.auto_toggle_bluetooth
        },
    )
