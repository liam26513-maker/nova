# Modalità (`core/modes`, `app/ModeController`)

Stato: **Fase 3, primo passo**. Motore + Gaming di base, solo manuale. Vedi SPEC §8.

## Il motore (`core/modes`, Kotlin puro, testato senza Android)

- `ModeAction`: insieme **chiuso** di azioni (`PERFORMANCE_GRAPHICS`, `DO_NOT_DISTURB`, `LOCK_ROTATION`, `KEEP_SCREEN_ON`, `PAUSE_SYNC`, `TURBO`). Una modalità (`ModeDefinition`) non può chiedere altro.
- `ModeEngine`: `enter(mode)` applica le azioni nell'ordine dato e, **prima di ogni cambio**, salva su `SessionStore` come annullarlo (`UndoToken`); `exit()` annulla in **ordine inverso**; una sola modalità principale per volta (entrare in un'altra esce prima dalla precedente). Un'azione non applicabile è `Skipped` (con il motivo), una che fallisce è `Failed`: tutte e due vengono riferite, nessuna è finta.
- **Se l'app si chiude a metà:** la sessione è già sul file; `recover()` (all'avvio) la riprende. **Se un annullamento non riesce:** il segnalibro resta nel file (`Session.leftover`) e si riprova a ogni avvio.
- **Rete di sicurezza:** `exitIfExpired()` esce dalla modalità dimenticata (`ModeDefinition.maxMinutes`, 6 ore per Gaming).
- `TextSessionStore`: file di testo semplice (`filesDir/mode-session.txt`), scrittura via file temporaneo.

## Sul tablet (`app/ModeController`)

| Azione | Cosa fa | Come torna com'era | Se non si può |
|---|---|---|---|
| `PERFORMANCE_GRAPHICS` | `graphicsController.setManual(PERFORMANCE)` | automatico o il livello di prima | — |
| `DO_NOT_DISTURB` | `QuickToggle.DND` via Shizuku (registrato in `SystemChangeLog`) | lo spegne; se era già acceso non lo tocca | «serve Shizuku attivo» |
| `LOCK_ROTATION` | salva l'orientamento **attuale** in `USER_ROTATION` e poi `ACCELEROMETER_ROTATION = 0` (senza il primo passo Android torna all'orientamento salvato, di solito verticale: era il difetto della 0.1.350) | tutti e due i valori di prima (segnalibro «automatica\|orientamento») | «serve il permesso di modificare le impostazioni» |
| `KEEP_SCREEN_ON` | `SCREEN_OFF_TIMEOUT` a 30 minuti (se era già più lungo non si tocca) | il valore di prima | «serve il permesso di modificare le impostazioni» |
| `PAUSE_SYNC` | `ContentResolver.setMasterSyncAutomatically(false)` (permessi normali `READ/WRITE_SYNC_SETTINGS`) | la riaccende; se l'utente l'aveva già spenta non la tocca | — |
| `TURBO` | `PerformanceUi.runTurbo(withGraphics = false)` | niente da annullare (le app si riaprono quando servono) | «serve Shizuku attivo» |

Le azioni che toccano le impostazioni sono in `app/ModeSettings`. L'avviso all'ingresso dice quante app ha chiuso il Turbo. Il display di riferimento è a 60 Hz: nessuna azione sulla frequenza dello schermo.

Entrata e uscita: tendina di controllo → **Altro → Modalità Gaming** (`HomeControls.gaming()`). Un avviso a schermo dice com'è andata. `ModeController.start()` gira all'avvio dell'app e a ogni `HomeActivity.onStart`.

## Cosa fa la modalità, voce per voce (`ModeOptions`)

`app/ModeOptions` (file `filesDir/mode-options.properties`) e la schermata `GamingSection` (Impostazioni → Modalità Gaming) permettono di accendere/spegnere ogni `ModeAction` e la «tendina sopra i giochi». Predefiniti prudenti (`DEFAULT_ON`): grafica di NOVA, Non disturbare, rotazione, schermo acceso. **Spenti di serie:** `TURBO`, `PAUSE_SYNC`. `ModeController.toggle` passa al motore solo le azioni accese. Serve a isolare in poche prove cosa fa scattare un gioco (segnalazione su Brawl Stars con la 0.1.360).

**Striscia della tendina durante la Gaming:** `NovaControlService.watchGaming` toglie la finestra trasparente del bordo alto quando la Gaming è attiva e `overlayInGames` è falso, e la rimette all'uscita. Ipotesi (non misurata): un livello in più sopra il gioco costa composizione e ruba il tocco nella fascia alta. L'uscita resta dalla tendina della Home (angolo in alto a destra).

## Uscite di emergenza (stato)

Fatte: tendina → Altro → Modalità Gaming (si raggiunge dalla barra di stato o dall'angolo); spegnimento automatico dopo 6 ore; ripristino al riavvio dell'app; tasto di accensione tenuto ~10 s (riavvio hardware, sempre disponibile).
**Da fare (SPEC §8.2):** volume giù tenuto 3 s (AccessibilityService, `FLAG_REQUEST_FILTER_KEY_EVENTS`), doppio swipe dal bordo, watchdog del gioco bloccato (8 s), pulsante «Chiudi gioco» (`am force-stop`). Finché mancano, la Gaming resta **solo manuale**.

## Scelte e limiti

- **Wi-Fi a bassa latenza non incluso:** `WIFI_MODE_FULL_LOW_LATENCY` ha effetto solo se l'app che tiene il blocco è in primo piano; durante il gioco NOVA non lo è.
- **Modalità definite nel codice:** `shortcut:` in `Modes.kt`; passare a `config/modes/<nome>.json` (con schema e test di caricamento, come chiede CLAUDE.md) quando le modalità saranno più d'una.
- **Modalità provvisoria:** il registro delle modalità non è ancora azzerato da `core/safemode` (lo fa «Annulla tutte le modifiche» solo per gli interruttori di sistema); da aggiungere.
