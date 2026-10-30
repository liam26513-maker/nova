# CLAUDE.md — Regole operative per Claude Code nel progetto NOVA

Leggi questo file e `SPEC.md` all'inizio di **ogni** sessione. `SPEC.md` è la fonte di verità su cosa costruire; questo file dice **come** lavorare. In caso di conflitto tra una richiesta e questi documenti, segnalalo nella PR e chiedi conferma invece di indovinare.

## Chi è il proprietario

Manlio non scrive codice: chiede modifiche in italiano semplice, spesso dal tablet tramite i pulsanti "Migliora" e "Ripara". Le sue richieste arrivano con un blocco di contesto generato da NOVA (versione, profilo dispositivo, elemento selezionato, eventuale report in `reports/`). Rispondi e documenta **in italiano**, in modo chiaro e non tecnico nelle note di rilascio.

## Come lavorare su ogni richiesta

1. **Leggi il contesto:** `SPEC.md` (sezioni pertinenti), `docs/modules/<id>.md` dei moduli toccati, l'eventuale report in `reports/`.
2. **Decidi lo strato:**
   - se basta cambiare `config/` → modifica **solo** `config/` (aggiornamento a caldo, niente APK);
   - altrimenti modifica il codice nel modulo giusto; non toccare il nucleo (`core/`) se non serve.
3. **Lavora su un branch** `richiesta/<slug-breve>` e apri una PR verso `main`.
4. **Scrivi test** per ogni comportamento nuovo e, per ogni bug di "Ripara", **un test che riproduce il problema** prima della correzione.
5. **Esegui localmente** (o lascia eseguire alla CI) lint, test e build release. Non aprire PR rosse di proposito.
6. **Documenta:** aggiorna `CHANGELOG.md` (sezione "Non rilasciato", in italiano semplice, citando la richiesta originale tra virgolette), `docs/modules/<id>.md` se cambia un modulo, `SPEC.md` se cambia il comportamento previsto.
7. **Descrizione PR** con: richiesta originale, cosa è cambiato, strato toccato (config/codice), impatto sulle prestazioni atteso, screenshot se cambia l'aspetto, eventuali punti della checklist manuale (`docs/TEST-MANUALE.md`) da verificare sul tablet.

## Regole non negoziabili

- **Budget di prestazioni (`perf-budget.json`):** mai superarli. Se una richiesta non è realizzabile entro i budget, realizza la versione più vicina possibile e spiega nella PR l'alternativa; non alzare i budget da solo (serve un ADR approvato dal proprietario in `docs/adr/`).
- **Moduli spegnibili:** ogni funzione nuova è un `NovaModule` o si aggancia a uno esistente; da spento non consuma nulla.
- **Nessuna funzione opzionale costosa attiva di default.** Le funzioni "per tablet" della tastiera (divisa, flottante, riga numeri) sono disattivate di default.
- **Modalità provvisoria intoccabile:** `core/safemode` non deve dipendere da nessun modulo `feature/`. Qualsiasi modifica a `core/safemode`, `core/update` o `core/system` richiede test aggiuntivi e una nota evidente nella PR.
- **Uscite di emergenza:** non rimuovere né indebolire le uscite della modalità Gaming (§8.2 di SPEC) né il ripristino dello stato all'uscita dalle modalità.
- **Shizuku:** solo comandi della lista consentita in `core/system`, tipizzati; ogni modifica di sistema è registrata con il valore precedente ed è reversibile. Mai disattivare i package protetti.
- **Nessuna chiave API Anthropic, nessun servizio a pagamento.** "Invia a Claude" = condivisione verso l'app Claude.
- **Tastiera senza permesso `INTERNET`.** Il test sul manifest unito deve restare verde.
- **Privacy:** nessuna telemetria remota; i report escono solo da "Ripara" e passano dai filtri di anonimizzazione; mai segreti nel repo, nei log o nei report.
- **Niente asset proprietari:** nessun marchio, icona, font (es. SF Pro) o grafica di Apple, Niagara Launcher o altri. Ispirazione sì, copia no.
- **Licenze:** il fork della tastiera resta GPL-3.0; registra ogni dipendenza o codice di terzi in `docs/LICENZE.md`.
- **Firma:** non cambiare mai la chiave di firma né i `applicationId` esistenti (romperebbe gli aggiornamenti).
- **Versioni:** `versionCode` sempre crescente (lo gestisce `release.yml`); compatibilità config tramite `schemaVersion` e `minAppVersion`.
- **Niente illegalità:** non implementare aggiramenti di licenze, sblocchi di app/giochi a pagamento o anti-copia.

## Convenzioni di codice

- Kotlin idiomatico, Compose, coroutine strutturate; nessun lavoro sul main thread oltre il disegno.
- Stato UI immutabile; niente allocazioni nei percorsi di disegno e scorrimento; liste con chiavi stabili.
- Nessun polling: usa callback/Flow di sistema. WorkManager per il lavoro differito, raggruppato.
- Nomi, commenti e documentazione tecnica possono essere in inglese; **testi dell'interfaccia in italiano** tramite risorse stringa (con inglese come seconda lingua).
- Ogni JSON in `config/` ha lo schema in `config/schema/` e un test di caricamento.
- Commit piccoli e descrittivi in stile Conventional Commits (`feat(gaming): …`, `fix(home): …`).

## Definizione di "fatto"

Una richiesta è fatta solo se:
- [ ] la funzione fa esattamente quanto richiesto, inclusi i casi limite ragionevoli;
- [ ] test aggiunti/aggiornati e CI verde (lint, unità, UI, screenshot, budget);
- [ ] nessun peggioramento oltre la tolleranza di `perf-budget.json`;
- [ ] documentazione e `CHANGELOG.md` aggiornati;
- [ ] la PR spiega cosa verificare a mano sul tablet, se serve.

## Quando il proprietario chiede qualcosa di impossibile

Spiega in italiano semplice nella PR (o nella risposta) perché, cosa si può fare di più vicino, e realizza quella versione se è chiaramente nello spirito della richiesta. Non promettere ciò che Android 14 senza root non permette (vedi tabella §2.2 di SPEC).
