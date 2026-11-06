package it.manlio.nova

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import it.manlio.nova.core.config.DebloatApp
import it.manlio.nova.core.design.SettingsRow
import it.manlio.nova.core.design.SettingsSection
import it.manlio.nova.core.system.ProtectedPackages

private const val SAFE = "safe"

/** Cosa proporre: solo ciò che è sul tablet, non è già spento e non è protetto. Prima le app sicure, poi per nome. */
internal fun debloatList(
    apps: Map<String, DebloatApp>,
    installed: Set<String>,
    frozen: List<String>,
): List<Pair<String, DebloatApp>> =
    apps
        .filterKeys { it in installed && it !in frozen && !ProtectedPackages.isProtected(it) }
        .toList()
        .sortedWith(compareBy({ it.second.level != SAFE }, { it.second.name.lowercase() }))

/** Prestazioni → App che si possono spegnere (SPEC §7.2). Spente finiscono tra le congelate, da dove si riaccendono con un tocco. */
@Composable
internal fun DebloatSection(
    backdrop: ImageBitmap,
    ui: PerformanceUi,
) {
    var open by remember { mutableStateOf(false) }
    val apps = remember(open, ui.frozen, ui.installed) { debloatList(ui.debloat, ui.installed, ui.frozen) }
    if (apps.isEmpty()) return
    SettingsSection(backdrop, header = stringResource(R.string.debloat_title), footer = stringResource(R.string.debloat_footer)) {
        SettingsRow(
            stringResource(if (open) R.string.perf_freeze_hide else R.string.debloat_show, apps.size),
            onClick = { open = !open },
        )
        if (open) {
            apps.forEach { (pkg, app) ->
                val level = stringResource(if (app.level == SAFE) R.string.debloat_safe else R.string.debloat_consider)
                SettingsRow(app.name, summary = "$level: ${app.note}", showDivider = true, onClick = { ui.freeze(pkg) })
            }
        }
    }
}
