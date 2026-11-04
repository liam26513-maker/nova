# Modalità (`core/modes`, `app/ModeController`)

Stato: **Fase 3, primo passo**. Motore + Gaming di base, solo manuale. Vedi SPEC §8.

## Il motore (`core/modes`, Kotlin puro, testato senza Android)

- `ModeAction`: insieme **chiuso** di azioni (`PERFORMANCE_GRAPHICS`, `DO_NOT_DISTURB`, `LOCK_ROTATION`, `KEEP_SCREEN_ON`, `PAUSE_SYNC`, `TURBO`). Una modalità (`ModeDefinition`) non può chiedere altro.
- `ModeEngine`: `enter(mode)` applica le azioni nell'ordine dato e, **prima di ogni cambio**, salva su `SessionStore` come annullarlo (`UndoToken`); `exit()` annulla in **ordine inverso**; una sola modalità principale per volta (entrare in un'altra esce prima dalla precedente). Un'azione non applicabile è `Skipped` (con il motivo), una che fallisce è `Failed`: tutte e due vengono riferite, nessuna è finta.
- **Se l'app si chiude a metà:** la sessione è già sul file; `recover()` (all'avvio) la riprende. **Se un annullamento non riesce:** il segnalibro resta nel file (`Session.leftover`) e si riprova a ogni avvio.
- **Rete di sicurezza:** `exitIfExpired()` esce dalla modalità dimenticata (`ModeDefinition.maxMinutes`, 6 ore per Gaming).
- `TextSessionStore`: file di testo semplice (`filesDir/mode-session.txt`), scrittura via file temporaneo.

## Le modalità

`Modes.all`: Gaming, Studio, Lavoro, Creativa, Presentazione, tutte da tendina → Altro. Le quattro nuove sono **di base**: usano solo le azioni di `ModeAction`, senza Turbo. Le scelte di `ModeOptions` valgono solo per la Gaming. Mancano Pomodoro e blocco distrazioni (§8.4), desktop e finestre flottanti (§8.3), filtro luce blu (§8.5), Home portfolio (§8.6).

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
Volume giù tenuto 3 s: `VolumeKeys` e `GamingExitMenu` (`GamingExit.kt`), con il servizio di accessibilità acceso; il tasto non viene mai consumato. «Chiudi il gioco» è in Altro e nel menu.
**Da fare (SPEC §8.2):** doppio swipe dal bordo, watchdog del gioco bloccato (8 s). Finché mancano, la Gaming resta **solo manuale**.

## Scelte e limiti

- **Wi-Fi a bassa latenza non incluso:** `WIFI_MODE_FULL_LOW_LATENCY` ha effetto solo se l'app che tiene il blocco è in primo piano; durante il gioco NOVA non lo è.
- **Modalità definite nel codice:** `shortcut:` in `Modes.kt`; passare a `config/modes/<nome>.json` (con schema e test di caricamento, come chiede CLAUDE.md) quando le modalità saranno più d'una.
- **Modalità provvisoria:** il registro delle modalità non è ancora azzerato da `core/safemode` (lo fa «Annulla tutte le modifiche» solo per gli interruttori di sistema); da aggiungere.

## Strumenti per i giochi (Altro)
«Misura il gioco (2 minuti)» campiona ogni 2 s l'ultimo gioco in primo piano (solo su richiesta, Shizuku) e salva `diagnosi-gaming.txt`; «Condividi l'ultima misura» la manda via condivisione; «Chiudi il gioco» fa `force-stop` dell'ultimo gioco (uscita di emergenza n. 4 di SPEC §8.2).

«Risoluzione: <gioco>» (Altro, solo con Shizuku e un gioco già visto) scende di un passo a ogni tocco: 100 → 90 → 80 → 70 → 65 → 100 (`SystemCommand.GameScale`, `cmd game set --downscale`; il ritorno a 100 è `cmd game mode standard`). Il minimo è il 65%: il comando rifiuta valori più bassi. Ogni scelta è nel registro (`SystemChange.GameScale`, una voce per gioco) e «Annulla tutte le modifiche» la toglie. Non dipende dalla Gaming, vale da quando il gioco si riapre e Android la rifiuta per le app che non sono giochi. Misura su U6 con Brawl Stars: 46,3 FPS e 32,4% di fotogrammi lenti al 100%, 61,5 FPS e 0,5% al 70%.

La striscia della tendina (`StripShape`): `FULL` fuori dalla Gaming, `CORNER` (angolo in alto a destra, 200 dp) in Gaming con «Tendina sopra i giochi» acceso (predefinito), `NONE` se spenta. «Condividi l'ultima misura» è sempre in Altro; una misura fallita salva un testo diagnostico.
