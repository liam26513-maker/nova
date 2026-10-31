package it.manlio.nova

import it.manlio.nova.feature.home.HomePrefs
import it.manlio.nova.feature.home.IconPackInfo

/** Cosa serve alla personalizzazione oltre alle preferenze: azioni di sistema e dati del dispositivo. */
@Suppress("LongParameterList") // raccolta di azioni e dati, ognuno distinto
class CustomizeActions(
    val onChange: ((HomePrefs) -> HomePrefs) -> Unit,
    val onDone: () -> Unit,
    val onPickPhoto: () -> Unit = {},
    val onExportTheme: () -> Unit = {},
    val onImportTheme: () -> Unit = {},
    val onGrantWallpaperAccess: () -> Unit = {},
    val iconPacks: List<IconPackInfo> = emptyList(),
    val canReadWallpaper: Boolean = false,
)
