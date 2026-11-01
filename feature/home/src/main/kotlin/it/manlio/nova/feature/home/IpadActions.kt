package it.manlio.nova.feature.home

/** Tutto ciò che la Home a icone decide o cambia: la disposizione, gli elementi e le azioni su di essi. */
internal class IpadActions(
    val onEditing: (Boolean) -> Unit,
    val onLayout: ((HomeLayout) -> HomeLayout) -> Unit,
    val onMenu: (AppEntry) -> Unit,
    val onSearch: () -> Unit,
    val onReorderDock: (List<String>) -> Unit,
)
