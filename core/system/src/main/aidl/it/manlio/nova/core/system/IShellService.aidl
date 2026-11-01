package it.manlio.nova.core.system;

import android.os.ParcelFileDescriptor;

/** Servizio che gira con i privilegi della shell tramite Shizuku (SPEC §7.1). */
interface IShellService {
    /** Codice riservato da Shizuku per chiudere il servizio. */
    void destroy() = 16777114;

    /**
     * Esegue un comando come lista di argomenti (mai una stringa interpretata dalla shell).
     * Restituisce il codice d'uscita sulla prima riga, poi l'output combinato.
     */
    String exec(in String[] argv) = 1;

    /**
     * Come [exec], ma con il contenuto di [input] sullo standard input del comando (per `pm install -S`):
     * la shell non può leggere le cartelle private dell'app, quindi è l'app a darle il file già aperto.
     */
    String execWithInput(in String[] argv, in ParcelFileDescriptor input) = 2;
}
