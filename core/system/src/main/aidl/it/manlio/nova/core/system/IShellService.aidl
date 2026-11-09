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

    /** Le app recenti, una per riga: «id del task, tab, pacchetto, tab, componente in cima» (le App recenti di NOVA, SPEC §12.10). */
    String recentTasks(int max) = 3;

    /** L'anteprima di un task come JPEG, con il lato lungo al più [maxSide] pixel; vuota se Android non ce l'ha. */
    byte[] taskSnapshot(int taskId, int maxSide) = 4;

    /** Riporta davanti un task delle recenti, come toccarne la scheda. */
    boolean openTask(int taskId) = 5;

    /** Toglie un task dalle recenti, come scorrerne via la scheda. */
    boolean removeTask(int taskId) = 6;
}
