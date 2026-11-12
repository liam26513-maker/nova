package it.manlio.nova.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState

/** Le app suggerite (SPEC §12.5), calcolate fuori dal disegno; finché non arrivano non c'è niente. */
@Composable
internal fun rememberSuggested(
    apps: List<AppEntry>,
    actions: HomeActions,
): List<Pair<AppEntry, String>> {
    val suggested by produceState(emptyList<Pair<AppEntry, String>>(), apps) {
        val byPackage = apps.associateBy { it.component.packageName }
        value =
            runCatching { actions.suggestions() }
                .getOrDefault(emptyList())
                .mapNotNull { (pkg, why) -> byPackage[pkg]?.let { it to why } }
    }
    return suggested
}
