package it.manlio.nova.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Le app suggerite (SPEC §12.5), calcolate fuori dal disegno. Si preparano già sulla Home, ogni volta che torna davanti: così
 * la ricerca si apre con i suggerimenti pronti e si costruisce una volta sola (prima arrivavano dopo e la lista si rifaceva).
 */
@Composable
internal fun rememberSuggested(
    apps: List<AppEntry>,
    actions: HomeActions,
): List<Pair<AppEntry, String>> {
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose {}
    }
    val suggested by produceState(emptyList<Pair<AppEntry, String>>(), apps, resumes) {
        val byPackage = apps.associateBy { it.component.packageName }
        value =
            runCatching { actions.suggestions() }
                .getOrDefault(emptyList())
                .mapNotNull { (pkg, why) -> byPackage[pkg]?.let { it to why } }
    }
    return suggested
}
