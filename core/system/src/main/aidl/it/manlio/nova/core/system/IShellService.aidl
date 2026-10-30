package it.manlio.nova.core.system;

/** Servizio che gira con i privilegi della shell tramite Shizuku (SPEC §7.1). */
interface IShellService {
    /** Codice riservato da Shizuku per chiudere il servizio. */
    void destroy() = 16777114;

    /**
     * Esegue un comando come lista di argomenti (mai una stringa interpretata dalla shell).
     * Restituisce il codice d'uscita sulla prima riga, poi l'output combinato.
     */
    String exec(in String[] argv) = 1;
}
