# Modulo `feature/improve`: Migliora e Ripara (Fase 0, punto 9)

In cima alle Impostazioni, nella sezione **Migliora NOVA**.

## Migliora
1. Scrivi con parole tue cosa vuoi, oppure tocca un esempio.
2. NOVA costruisce la richiesta per Claude Code (`ImprovePrompt.improve`), con:
   - il testo tra virgolette;
   - versione dell'app e della configurazione, dispositivo e stato di Shizuku;
   - il tipo suggerito, leggero (`config/`) o profondo (codice); è solo un suggerimento e decide Claude Code;
   - l'istruzione di rispettare `CLAUDE.md` e `SPEC.md`.
3. Copia la richiesta negli appunti e apre `https://claude.ai/code/new?q=…&repo=liam26513-maker/nova`. È il collegamento ufficiale: apre l'app Claude, o il sito, con il testo già scritto e il repository scelto, e va solo toccato Invia.

## Ripara
1. Scrivi cosa non va. È facoltativo.
2. NOVA raccoglie:
   - gli ultimi 3 crash;
   - gli eventi del registro interno;
   - il log di sistema della **sola NOVA** (`logcat --pid`: un'app legge sempre i propri log, senza permessi né Shizuku);
   - versioni, dispositivo e stato di Shizuku.
3. **Anonimizza** tutto (`Redactor`) e si vede l'anteprima esatta prima dell'invio. Toglie:
   - token GitHub e chiavi lunghe;
   - autorizzazioni;
   - email, numeri di telefono, IP;
   - percorsi e parametri degli indirizzi web;
   - nomi utente nei percorsi.
4. Con il token: carica il report in `reports/AAAA-MM-GG_HHMM_<descrizione>.md` (`GitHubClient.putReport`, solo percorsi `reports/*.md`), poi apre Claude Code con "Leggi `reports/…`, individua la causa, correggi, aggiungi un test che riproduca il problema".
5. Senza token: il report va negli appunti insieme alla richiesta e la schermata spiega di incollarlo.

## Limiti
- Il report pesa al massimo circa 180 KB.
- Non ci sono contenuti delle notifiche, perché NOVA non li raccoglie.
- Non c'è ancora l'"isola" che segue lo stato di Claude Code e della CI (SPEC §6.7, Fase 1).
