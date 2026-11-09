# Modalità (`core/modes`, `app/ModeController`)

Stato: **Fase 3, primo passo**. Motore + Gaming di base, solo manuale. Vedi SPEC §8.

## Il motore (`core/modes`, Kotlin puro, testato senza Android)

- `ModeAction`: insieme **chiuso** di azioni (`PERFORMANCE_GRAPHICS`, `DO_NOT_DISTURB`, `LOCK_ROTATION`, `KEEP_SCREEN_ON`, `PAUSE_SYNC`, `TURBO`). Una modalità (`ModeDefinition`) non può chiedere altro.
- `ModeEngine`: `enter(mode)` applica le azioni nell'ordine dato e, **prima di ogni cambio**, salva su `SessionStore` come annullarlo (`UndoToken`); `exit()` annulla in **ordine inverso**; una sola modalità principale per volta (entrare in un'altra esce prima dalla precedente). Un'azione non applicabile è `Skipped` (con il motivo), una che fallisce è `Failed`: tutte e due vengono riferite, nessuna è finta.
- **Se l'app si chiude a metà:** la sessione è già sul file; `recover()` (all'avvio) la riprende. **Se un annullamento non riesce:** il segnalibro resta nel file (`Session.leftover`) e si riprova a ogni avvio.
- **Rete di sicurezza:** `exitIfExpired()` esce dalla modalità dimenticata (`ModeDefinition.maxMinutes`, 6 ore per Gaming).
- `TextSessionStore`: file di testo semplice (`filesDir/mode-session.txt`), scrittura via file temporaneo.

## Le modalità

`Modes.all`: Gaming, Studio, Lavoro, Creativa, Presentazione, tutte da tendina → Altro. Le quattro nuove sono **di base**: usano solo le azioni di `ModeAction`, senza Turbo. Le scelte di `ModeOptions` valgono solo per la Gaming. Creativa spegne il filtro luce blu (`NIGHT_LIGHT_OFF`), Presentazione porta la luminosità al massimo (`FULL_BRIGHTNESS`). Studio completa di timer: `Pomodoro` e `StudyStore` (durate, minuti per giorno, sessione salvata; `PomodoroAlarm` fa ripartire NOVA a fine fase, sveglia non esatta), `StudyGate` e `StudyGuard` per il blocco delle app (serve il servizio di accessibilità), `StudyPill` in cima alla Home al posto dell'isola con il tasto di uscita. Note rapide: `QuickNotes` e `NotesActivity`, file `note-rapide.txt`, si salvano in `onPause`. Filtro caldo: `NIGHT_LIGHT_ON`, voce facoltativa spenta di serie (`ModeOptions.chosen`). Schermo diviso: `HomeControls.onSplitScreen` apre le app recenti (tasto «Dividi»), solo con il servizio di accessibilità e durante il Lavoro. In Presentazione la riga della trasmissione sale in cima ad Altro. Mancano font per i PDF (§8.4), desktop e finestre flottanti (§8.3), Home portfolio e PIN di uscita (§8.6).

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
Doppio swipe (`EdgeSwipeWindow`, `DoubleSwipe`) e watchdog (`GameWatchdog`, `FreezeDetector`) sono **accesi di serie** dal 9 novembre 2026 per scelta del proprietario (`ModeOptions.isOn`, `EDGE_SWIPE`, `WATCHDOG`), come la Gaming automatica (`AUTO_GAMING`). Costi noti: la fascia del bordo prende qualche tocco al gioco e ogni lettura di `--timestats` ferma un attimo il compositore. Si spengono in Impostazioni → Modalità Gaming.

## Schermo sempre acceso (`app/ScreenAwake`)

Non è una modalità: riga di Altro, spenta di serie. Porta `SCREEN_OFF_TIMEOUT` a `Int.MAX_VALUE` e salva il valore di prima. Una modalità con `KEEP_SCREEN_ON` accesa dopo non lo tocca; se invece l'interruttore si accende durante una modalità, uscendone Android torna al tempo salvato dalla modalità e la riga dice «No».

## Libreria dei giochi (`app/GameLibrary`)

In Impostazioni → Modalità Gaming: le app con categoria gioco, aperte con un tocco, con la percentuale che scende di un passo (`GameTools.stepScale`). `GameFps` tiene gli FPS dell'ultima misura e li dimentica se la risoluzione cambia; `suggestedScale` propone un passo sotto 55 FPS. La percentuale viene dal registro delle modifiche: una scala impostata fuori da NOVA, con adb, qui si vede come 100%.

## Il tuo tempo (`app/TimeSection`, `core/modes/ModeMinutes`)

`ModeEngine.onFinished` dà i minuti una volta per sessione e `ModeController` li somma al giorno di fine (`mode-time.properties`). Non contarli da `start()`: gira a ogni ritorno sulla Home. Le app vengono da `UsageStatsManager`, solo con l'accesso concesso dall'utente; NOVA non compare nell'elenco.

## Doppio swipe e gesti di Android

Con la navigazione a gesti Android ruba lo swipe dal bordo e manda `ACTION_CANCEL`: `EdgeGesture` conta anche quello. `systemGestureExclusionRects` libera 200 dp al centro del bordo (il massimo che Android concede). Provato sul tablet: al centro funziona, fuori dal centro scatta solo «Indietro» e il menu non si apre. I tasti inviati con `adb input` non arrivano al filtro del servizio: volume giù si prova solo col tasto vero.

## Riavvio di NOVA durante una modalità

`ModeController.start`, una volta per processo, rimette grafica e rotazione dai segnalibri della sessione, senza cambiarli. Causa del cambio della rotazione non trovata: si è visto dopo un'installazione a Gaming accesa.

## Scelte e limiti

- **Wi-Fi a bassa latenza non incluso:** `WIFI_MODE_FULL_LOW_LATENCY` ha effetto solo se l'app che tiene il blocco è in primo piano; durante il gioco NOVA non lo è.
- **Modalità in `config/modes.json`:** azioni e durata massima, lette a ogni ingresso da `ModeController.configured`. `Modes.kt` resta come ripiego e `ModesConfigTest` li tiene uguali. Un nome sconosciuto si ignora e il Turbo resta solo alla Gaming (`withConfig`). Una versione vecchia dell'app rifiuta un pacchetto che contiene `modes` e resta sulla configurazione di prima.
- **Modalità provvisoria:** il registro delle modalità non è ancora azzerato da `core/safemode` (lo fa «Annulla tutte le modifiche» solo per gli interruttori di sistema); da aggiungere.

## Strumenti per i giochi (Altro)
«Misura il gioco (2 minuti)» campiona ogni 2 s l'ultimo gioco in primo piano (solo su richiesta, Shizuku) e salva `diagnosi-gaming.txt`; «Condividi l'ultima misura» la manda via condivisione; «Chiudi il gioco» fa `force-stop` dell'ultimo gioco (uscita di emergenza n. 4 di SPEC §8.2).

«Risoluzione: <gioco>» (Altro, solo con Shizuku e un gioco già visto) scende di un passo a ogni tocco: 100 → 90 → 80 → 70 → 65 → 100 (`SystemCommand.GameScale`, `cmd game set --downscale`; il ritorno a 100 è `cmd game mode standard`). Il minimo è il 65%: il comando rifiuta valori più bassi. Ogni scelta è nel registro (`SystemChange.GameScale`, una voce per gioco) e «Annulla tutte le modifiche» la toglie. Non dipende dalla Gaming, vale da quando il gioco si riapre e Android la rifiuta per le app che non sono giochi. Misura su U6 con Brawl Stars: 46,3 FPS e 32,4% di fotogrammi lenti al 100%, 61,5 FPS e 0,5% al 70%.

La striscia della tendina (`StripShape`): `FULL` fuori dalla Gaming, `CORNER` (angolo in alto a destra, 200 dp) in Gaming con «Tendina sopra i giochi» acceso (predefinito), `NONE` se spenta. «Condividi l'ultima misura» è sempre in Altro; una misura fallita salva un testo diagnostico.
