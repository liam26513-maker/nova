package it.manlio.nova.core.system

/**
 * Legge `dumpsys activity recents` e restituisce le app aperte ora in una finestra flottante, come «pacchetto/activity», dalla più
 * recente. Ogni voce è un blocco «RecentTaskInfo #»: conta solo se il task esiste ancora (`hasTask=true`) ed è `freeform`. Pura.
 */
object RecentsParser {
    private val ACTIVITY = Regex("realActivity=\\{([^/}]+)/([^}]+)\\}")

    fun openWindows(text: String): List<String> =
        text
            .split("RecentTaskInfo #")
            .drop(1)
            .filter { "hasTask=true" in it && "windowingMode=freeform" in it }
            .mapNotNull { block -> ACTIVITY.find(block)?.let { "${it.groupValues[1]}/${it.groupValues[2]}" } }
            .distinct()
}
