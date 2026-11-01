package it.manlio.nova

import android.content.Intent
import android.content.pm.PackageManager
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
import it.manlio.nova.core.system.ProtectedPackages
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Prestazioni (SPEC §7.3): Turbo, scala delle animazioni, dimensione degli elementi dello schermo con prova,
 * blocco delle pubblicità (DNS) e congelamento delle app. Tutto reversibile e registrato; serve Shizuku.
 */
@Composable
fun PerformanceSection(
    backdrop: ImageBitmap,
    ui: PerformanceUi,
    onShizuku: () -> Unit,
) {
    if (!ui.ready) {
        SettingsSection(
            backdrop,
            header = stringResource(R.string.perf_title),
            footer = stringResource(R.string.perf_needs_shizuku_footer),
        ) {
            SettingsRow(
                stringResource(R.string.perf_needs_shizuku),
                summary = stringResource(R.string.perf_needs_shizuku_summary),
                onClick = onShizuku,
            )
        }
        return
    }
    ui.message?.let { m ->
        SettingsSection(backdrop) { SettingsRow(stringResource(m, ui.turboCount), onClick = ui::dismissMessage) }
    }
    if (ui.trialLeft > 0) TrialSection(backdrop, ui)
    SettingsSection(backdrop, header = stringResource(R.string.perf_turbo), footer = stringResource(R.string.perf_turbo_footer)) {
        SettingsRow(
            stringResource(R.string.perf_turbo_run),
            summary = stringResource(R.string.perf_turbo_summary),
            onClick = ui::turbo,
        )
    }
    SettingsSection(backdrop, header = stringResource(R.string.perf_animations), footer = stringResource(R.string.perf_animations_footer)) {
        Choice(R.string.perf_animations_scale, ANIMATIONS, normalized(ui.animations), {
            stringResource(animationLabel(it))
        }, onSelect = ui::chooseAnimations)
    }
    ScreenSizeSection(backdrop, ui)
    SettingsSection(backdrop, header = stringResource(R.string.perf_dns), footer = stringResource(R.string.perf_dns_footer)) {
        Choice(R.string.perf_dns_adblock, listOf(true, false), ui.adBlock, { onOff(it) }, onSelect = ui::chooseAdBlock)
    }
    FreezeSection(backdrop, ui)
}

@Composable
private fun TrialSection(
    backdrop: ImageBitmap,
    ui: PerformanceUi,
) {
    SettingsSection(
        backdrop,
        header = stringResource(R.string.perf_trial_title),
        footer = stringResource(R.string.perf_trial_footer, ui.trialLeft),
    ) {
        SettingsRow(stringResource(R.string.perf_trial_keep), onClick = ui::keepDensity)
        SettingsRow(stringResource(R.string.perf_trial_revert), showDivider = true, onClick = ui::revertDensity)
    }
}

@Composable
private fun ScreenSizeSection(
    backdrop: ImageBitmap,
    ui: PerformanceUi,
) {
    val d = ui.density ?: return
    val steps = DENSITY_FACTORS.map { (d.physical * it / DENSITY_STEP).roundToInt() * DENSITY_STEP }
    val current = steps.indices.minBy { abs(steps[it] - d.current) }
    SettingsSection(
        backdrop,
        header = stringResource(R.string.perf_screen),
        footer = stringResource(R.string.perf_screen_footer, d.physical, d.current),
    ) {
        Choice(R.string.perf_screen_size, steps.indices.toList(), current, { stringResource(DENSITY_LABELS[it]) }) { i ->
            if (steps[i] != d.current) ui.tryDensity(steps[i])
        }
    }
}

/** Congelamento: le app congelate da NOVA (si riattivano) e, a richiesta, l'elenco di quelle che si possono congelare. */
@Composable
private fun FreezeSection(
    backdrop: ImageBitmap,
    ui: PerformanceUi,
) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    val apps = remember(picking, ui.frozen) { if (picking) freezable(context.packageManager, ui.frozen) else emptyList() }
    SettingsSection(backdrop, header = stringResource(R.string.perf_freeze), footer = stringResource(R.string.perf_freeze_footer)) {
        if (ui.frozen.isEmpty()) SettingsRow(stringResource(R.string.perf_freeze_none))
        ui.frozen.forEachIndexed { i, pkg ->
            SettingsRow(
                labelOf(
                    context.packageManager,
                    pkg,
                ),
                summary = stringResource(R.string.perf_unfreeze),
                showDivider = i > 0,
                onClick = {
                    ui.unfreeze(pkg)
                },
            )
        }
        SettingsRow(
            stringResource(if (picking) R.string.perf_freeze_hide else R.string.perf_freeze_pick),
            showDivider = true,
            onClick = { picking = !picking },
        )
        apps.forEach { (pkg, label) -> SettingsRow(label, summary = pkg, showDivider = true, onClick = { ui.freeze(pkg) }) }
    }
}

/** Le app avviabili che si possono congelare: niente protette, niente già congelate. */
internal fun freezable(
    pm: PackageManager,
    frozen: List<String>,
): List<Pair<String, String>> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm
        .queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
        .distinctBy { it.first }
        .filter { (pkg, _) -> pkg !in frozen && !ProtectedPackages.isProtected(pkg) }
        .sortedBy { it.second.lowercase() }
}

private fun labelOf(
    pm: PackageManager,
    pkg: String,
): String =
    runCatching {
        pm.getApplicationInfo(
            pkg,
            PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES,
        ).loadLabel(pm).toString()
    }.getOrDefault(pkg)

/** Il valore letto da Android è «1.0», «0.5»…: lo portiamo alla forma delle scelte. */
internal fun normalized(scale: String?): String =
    when (scale?.toFloatOrNull()) {
        null -> "1"
        0f -> "0"
        QUARTER -> "0.25"
        HALF -> "0.5"
        else -> "1"
    }

private fun animationLabel(s: String) =
    when (s) {
        "0.5" -> R.string.perf_anim_fast
        "0.25" -> R.string.perf_anim_faster
        "0" -> R.string.perf_anim_off
        else -> R.string.perf_anim_normal
    }

private val ANIMATIONS = listOf("1", "0.5", "0.25", "0")

/** Piccoli, normali, grandi, molto grandi rispetto alla densità di fabbrica. */
private val DENSITY_FACTORS = listOf(SMALL, 1f, LARGE, XLARGE)
private val DENSITY_LABELS =
    listOf(R.string.perf_screen_small, R.string.perf_screen_normal, R.string.perf_screen_large, R.string.perf_screen_xlarge)
private const val DENSITY_STEP = 5
private const val QUARTER = 0.25f
private const val HALF = 0.5f
private const val SMALL = 0.85f
private const val LARGE = 1.15f
private const val XLARGE = 1.3f

/** Stato del sistema (SPEC §7.4) e salute della batteria (§13.6): memoria, spazio, batteria. */
@Composable
fun HealthSection(
    backdrop: ImageBitmap,
    status: SystemStatus,
) {
    SettingsSection(backdrop, header = stringResource(R.string.health_title), footer = stringResource(R.string.health_footer)) {
        SettingsRow(stringResource(R.string.health_ram), trailing = {
            Muted("${SystemStatusReader.size(status.ramUsedMb)} / ${SystemStatusReader.size(status.ramTotalMb)}")
        })
        SettingsRow(
            stringResource(R.string.health_storage),
            showDivider = true,
            trailing = {
                Muted(
                    stringResource(
                        R.string.health_free,
                        SystemStatusReader.size(status.storageFreeMb),
                        SystemStatusReader.size(status.storageTotalMb),
                    ),
                )
            },
        )
        SettingsRow(
            stringResource(R.string.health_battery),
            showDivider = true,
            trailing = {
                Muted(
                    "${status.batteryPercent}%" + if (status.charging) " · ${stringResource(R.string.health_charging)}" else "",
                )
            },
        )
        SettingsRow(stringResource(R.string.health_battery_state), showDivider = true, trailing = {
            Muted(stringResource(healthLabel(status.batteryHealth)))
        })
        status.batteryTempC?.let {
                t ->
            SettingsRow(stringResource(R.string.health_temp), showDivider = true, trailing = { Muted("%.1f °C".format(t)) })
        }
        status.batteryCycles?.let {
                c ->
            SettingsRow(stringResource(R.string.health_cycles), showDivider = true, trailing = { Muted(c.toString()) })
        }
    }
}

private fun healthLabel(h: BatteryHealth) =
    when (h) {
        BatteryHealth.GOOD -> R.string.health_good
        BatteryHealth.OVERHEAT -> R.string.health_overheat
        BatteryHealth.DEAD -> R.string.health_dead
        BatteryHealth.OVER_VOLTAGE -> R.string.health_over_voltage
        BatteryHealth.COLD -> R.string.health_cold
        BatteryHealth.FAILURE -> R.string.health_failure
        BatteryHealth.UNKNOWN -> R.string.health_unknown
    }
