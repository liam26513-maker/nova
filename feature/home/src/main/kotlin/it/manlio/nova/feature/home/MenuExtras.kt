package it.manlio.nova.feature.home

/** Voci del menu che esistono solo nello stile a icone (null = la voce non c'è). */
internal data class MenuExtras(
    val onEdit: (() -> Unit)? = null,
    val onTakeOut: (() -> Unit)? = null,
    val onRemove: (() -> Unit)? = null,
    val onAddToHome: (() -> Unit)? = null,
    /** Chiude la notifica di quest'app (solo se ce n'è una). */
    val onClearNotification: (() -> Unit)? = null,
    /** Risponde alla notifica di quest'app (solo se l'app lo permette); [replyTo] è a chi, per il titolo. */
    val onReply: ((String) -> Unit)? = null,
    val replyTo: String = "",
)
