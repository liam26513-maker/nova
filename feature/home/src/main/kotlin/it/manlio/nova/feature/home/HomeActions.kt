package it.manlio.nova.feature.home

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/** Azioni della home: le fornisce l'app, così la schermata resta provabile con dati finti. */
@Immutable
class HomeActions(
    val icon: suspend (AppEntry) -> ImageBitmap?,
    val onLaunch: (AppEntry) -> Unit,
    val onToggleFavorite: (AppEntry) -> Unit,
    val onOpenSettings: () -> Unit,
)
