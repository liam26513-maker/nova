package it.manlio.nova.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState

/** Le app suggerite (SPEC §12.5), calcolate fuori dal disegno; finché non arrivano non c'è niente. */
@Composable
internal fun rememberSuggested(
    apps: List<AppEntry>,
    actions: HomeActions,
): List<AppEntry> {
    val suggested by produceState(emptyList<AppEntry>(), apps) {
        val byPackage = apps.associateBy { it.component.packageName }
        value = runCatching { actions.suggestions() }.getOrDefault(emptyList()).mapNotNull(byPackage::get)
    }
    return suggested
}
