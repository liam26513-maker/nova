# Modulo `feature/home` — Home minima (Fase 0, punto 11)

La prima schermata del tablet quando NOVA è l'app Home predefinita. Versione minima ma curata; la home completa (layout commutabili, notifiche in linea, widget, dock, cartelle, alfabeto a onda completo) arriva nella Fase 1 (SPEC §6).

## Cosa fa
> Questa sezione descrive la disposizione **Elenco** (stile Niagara). La disposizione predefinita, a icone e dock, è descritta in «Stile iPad» più sotto.

- **Stile** ispirato a Niagara, senza copiarne la grafica:
  - si vede lo **sfondo di sistema**, coperto da una sfumatura scura e opaca tinta col suo colore principale (`WallpaperColors`, letto senza permessi);
  - dove il dispositivo lo supporta (`isCrossWindowBlurEnabled`) e il livello grafico è Massimo o Bilanciato, Android **sfoca** anche lo sfondo (`setBackgroundBlurRadius`: 40 dp oppure 22 dp), e la sfumatura diventa più leggera;
  - con il livello Prestazioni, o senza supporto, resta solo la sfumatura.
- **Preferiti:** ora grande (88 sp, sottile) e data; sotto, i preferiti come lista di nomi grandi con l'icona; "Tutte le app ›" in fondo.
- **Tutte le app:** elenco con le intestazioni di lettera; "‹ Preferiti" in cima, oppure Indietro, per tornare.
- **Alfabeto a onda** sul bordo destro:
  - sotto il dito le lettere si gonfiano e sporgono con una curva continua (gaussiana, larga 52 dp);
  - accanto al dito una bolla mostra la lettera;
  - a ogni cambio di lettera c'è un tic aptico e l'elenco salta lì.

  Le animazioni leggono la posizione del dito solo in fase di disegno, quindi trascinare non ricompone l'interfaccia.
- **Movimento:**
  - i preferiti entrano a cascata;
  - le righe si rimpiccioliscono sotto il dito e tornano con una molla;
  - preferiti e tutte le app si scambiano con una dissolvenza e uno scivolamento a molla;
  - le aggiunte e le rimozioni nelle liste sono animate (`animateItem`);
  - le app si aprono "crescendo" dalla riga toccata (`makeClipRevealAnimation`).
- **Gesti:**
  - tocco → apre l'app;
  - pressione lunga → aggiunge o toglie dai preferiti (nell'elenco completo i preferiti hanno un puntino);
  - tasto Home → torna ai preferiti e in cima;
  - Indietro → torna ai preferiti, oppure non fa nulla se sei già lì.
- **Ordine** italiano (`Collator`): maiuscole e accenti ignorati, "É" sotto "E", numeri e simboli in fondo sotto "#".

## Personalizzazione ("studio" sul modello di Niagara)
Analisi di partenza: le pagine di aiuto di Niagara (funzioni Pro, temi) e gli articoli sull'aggiornamento "Studio". NOVA riprende le **funzioni**, non la grafica, i nomi o le icone.

**Impostazioni → Home → Personalizza la Home.** Ogni scelta si applica subito:

| Area | Opzioni | Come in Niagara |
|---|---|---|
| **Temi** | 5 temi pronti (Notte, Aurora, Carta, Tramonto, Essenziale); salva il tema in un file `.novatheme` e aprilo su un altro tablet. Il tema porta solo l'aspetto, non le app nascoste o i nomi | temi pronti, condivisione dei temi |
| **Sfondo** | Il mio (sfondo di sistema), NOVA (8 sfumature originali), Foto (dalla galleria), Colore (8 tinte); sfocatura No, Leggera, Forte o Massima; sfumatura Leggera, Media o Forte | raccolta di sfondi, sfondo nel tema |
| **Colori** | Tema scuro, chiaro o automatico; colore d'accento preso dallo sfondo o scelto (8) | colore del tema |
| **Testo e orologio** | Misura, carattere (Moderno, Elegante, Tecnico: solo caratteri di sistema), spessore; orologio Sottile, Marcato o Su due righe; data sì o no | caratteri, stili dell'orologio |
| **Icone** | Sì o no; misura; forma (Originale, Cerchio, Morbido = superellisse, Quadrato); a colori o **monocromatiche** (icona tematica di Android 13+, altrimenti in grigio); **pacchetti di icone** di terzi (`appfilter.xml`) | pacchetti di icone, stili delle icone |
| **Disposizione e gesti** | Alfabeto a destra o a sinistra; barra di stato sì o no; **scorri giù per le notifiche**; **doppio tocco sull'alfabeto per spegnere lo schermo** (tramite Shizuku) | nascondi la barra di stato, doppio tocco per spegnere |
| **App** | App nascoste (con un tocco tornano) e ripristino dei nomi | nascondi o rinomina app |

**Dalla Home, tenendo premuta un'app:** un menu che compare con una molla.
- Aggiungi ai preferiti o togli dai preferiti.
- Rinomina, oppure torna al nome originale.
- Nascondi.
- Informazioni sull'app.
- Disinstalla, con la conferma di Android (`REQUEST_DELETE_PACKAGES`).

**Riordino dei preferiti:** tieni premuto un preferito e trascinalo. La logica degli scambi è in `ReorderState`.

**Non incluso:** notifiche sotto le app, meteo e agenda, timer d'uso, icone generate automaticamente per le app senza icona nel pacchetto (widget, cartelle e disposizione libera sono ora inclusi: vedi sotto).*

## Stile iPad (predefinito)
La Home ha due disposizioni, scelte in **Personalizza → Disposizione e gesti → Stile della Home** (`HomePrefs.layout`, non fa parte del tema: cambiare tema non la cambia).

| Stile | Cosa si vede |
|---|---|
| **Icone e dock** (`IPAD`, predefinito) | ora e data compatte in alto, pagine di icone con il nome sotto (`HorizontalPager`), puntini della pagina, capsula «Cerca app» e dock |
| **Ibrido** (`HYBRID`) | in orizzontale elenco dei preferiti a sinistra (un terzo) e Home a icone a destra (due terzi); in verticale come l'elenco |
| **Elenco** (`LIST`) | la Home in stile Niagara descritta sopra: ora grande, preferiti in lista, alfabeto a onda |

**Come funziona lo stile iPad**
- **Griglia:** colonne e righe si calcolano dallo spazio disponibile (`GridMetrics.of`: celle di almeno 150 × 128 dp, da 4 a 8 colonne e da 2 a 6 righe). Sul tablet 1280×800 sono 7 × 4 icone per pagina; in verticale 4 × 6. Le app in più passano alla pagina successiva (`paginate`).
- **Ordine:** quello scelto da te (`HomeLayout`); le app nuove vanno in fondo in ordine alfabetico italiano. Le app del dock non compaiono anche nella griglia, come sull'iPad.
- **Dock:** i primi `DOCK_MAX` = 6 preferiti, nell'ordine dei preferiti, senza nomi (restano nella descrizione per TalkBack). Senza preferiti mostra come aggiungerli. Con il livello grafico **Prestazioni** il pannello è più opaco e senza bordo luminoso; con gli altri è traslucido con un filo di luce.
- **Gesti:** tocco = apre l'app, con l'animazione dal rettangolo dell'icona; pressione lunga = il solito menu; scorrere di lato = pagina; scorrere giù = notifiche. L'icona si rimpicciolisce sotto il dito con una molla, entro un frame.
- **Cerca:** apre la ricerca universale (vedi sotto); da lì «Tutte le app ›» porta all'elenco completo con l'alfabeto a onda; Indietro o «‹ Home» tornano alla griglia. Il tasto Home riporta alla prima pagina.
- **Icone:** nello stile iPad contano la misura (piccola 56, media 72, grande 88 dp), la forma e lo stile; «Icone sì/no» vale solo per l'elenco.
- **Icone morbide di default:** `iconShape` ora parte da «Morbido». Un file di preferenze scritto prima dello stile iPad, che aveva la vecchia forma predefinita «Originale», passa a «Morbido» una sola volta (`PrefsCodec`: manca la chiave `layout`); dal primo salvataggio la scelta resta esplicita.

**Consumi:** lo stile iPad non aggiunge sfocature in tempo reale: il dock e la capsula sono rettangoli traslucidi disegnati con un colore. Il pager compone solo la pagina visibile. Le icone si caricano una sola volta alla misura più grande (88 dp, 31 KB l'una a 1×), con la cache LRU di sempre.


### Disposizione libera, cartelle e widget (`HomeLayout`)
- **Modello** (Kotlin puro, provato con test unitari): `HomeLayout` = elementi in ordine di lettura (`AppItem`, `FolderItem`, `WidgetItem`) + app tolte dalla Home (`offHome`). Salvato in `files/home-layout.txt` (`HomeLayoutStore`, scrittura atomica fuori dal thread principale, righe illeggibili ignorate).
- **`reconcile`:** a ogni disegno allinea la disposizione alle app che esistono (tolte le disinstallate, nascoste o nel dock; cartelle con una sola app sciolte; app nuove in fondo). Le modifiche partono sempre dalla versione allineata.
- **`pack`:** mette gli elementi in pagine di colonne×righe, in ordine e senza tornare indietro; un widget occupa le sue caselle e non entra se non c'è posto (passa alla pagina dopo).
- **Modifica:** si entra con la pressione lunga sullo sfondo o dal menu di un'app. Il gesto (`gridDrag`) sta sopra il pager e guarda gli eventi per primo: parte solo da un elemento e solo oltre la soglia, così scorrere di pagina sullo sfondo funziona ancora. Al rilascio `dropAction` decide: al centro di un'altra app o di una cartella = unisce; altrimenti inserisce (dopo il bersaglio se si va avanti, prima se si torna indietro); su una casella vuota = in fondo alla pagina. Sul dock = preferito. Al bordo, dopo 650 ms, si cambia pagina. L'ondeggiamento è una sola animazione condivisa, letta in fase di disegno, e si spegne con «Rimuovi animazioni» di Android.
- **Cartelle:** anteprima 3×3 nell'icona; aperta è una scheda di vetro con il nome modificabile (si salva alla chiusura).
- **Widget di NOVA** (`NovaWidgets`): Orologio analogico (2×2), Calendario (2×2), Batteria (2×1, il ricevitore `ACTION_BATTERY_CHANGED` esiste solo mentre la Home è visibile), Nota rapida (2×2, si salva da sola 0,7 s dopo l'ultima lettera, al massimo 500 caratteri).
- **Widget di altre app:** `WidgetHostBridge` (nell'app) usa `AppWidgetHost`: `bindAppWidgetIdIfAllowed`, altrimenti la schermata di Android per autorizzare, poi la configurazione del widget se la prevede. Ascolta solo tra `onStart` e `onStop`; ogni chiamata ad Android è protetta, un widget difettoso viene scartato. **Non provato su un dispositivo vero** (non si può simulare in test): vedi `docs/TEST-MANUALE.md`.
- **Vetro:** `Modifier.glass` (`Glass.kt`): riempimento a gradiente e bordo luminoso, solo disegno. Con il livello Prestazioni, riempimento pieno e niente bordo.

### Ricerca universale
`AppSearch`: senza maiuscole né accenti; prima chi inizia con il testo, poi una parola che inizia così, poi chi lo contiene. Risultati: app, «Impostazioni di NOVA» (se la ricerca le riguarda) e «Cerca sul web» (`ACTION_WEB_SEARCH`: apre il browser, NOVA non ha il permesso Internet). Non c'è polling né indice: si filtra l'elenco già in memoria.

**Non incluso:** Meteo (serve Internet e la posizione), widget Stato PC, Musica, Pomodoro, Galleria (SPEC §6.5, altre fasi), notifiche sotto le app.


### Meteo, Pomodoro, centro di controllo, calcolatrice
- **Meteo** (`WeatherParser`, `WeatherWidget`, `app/WeatherClient`): ricerca del luogo per nome (`geocoding-api.open-meteo.com`) e previsione (`api.open-meteo.com`), senza chiave né account. **Non si legge la posizione del tablet**: si scrive la città; verso Internet viaggiano solo il nome e, dopo la ricerca, le sue coordinate. Risposta in cache per 30 minuti (in memoria); se la rete manca si mostra l'ultimo valore. Il codice WMO diventa uno di otto simboli disegnati da NOVA.
- **Pomodoro** (`Pomodoro`, `PomodoroWidget`): macchina a stati pura (25′ / 5′), salvata come testo nel widget. Il tic di un secondo parte solo mentre va ed è visibile.
- **Centro di controllo** (`ControlActions`, `ControlCenter`, `app/HomeControls`): la Home riceve le azioni dall'app. Torcia (`CameraManager.setTorchMode`, ascolto solo mentre la Home è visibile), volume (`AudioManager`), luminosità (`Settings.System`, serve `WRITE_SETTINGS`), livello grafico; Wi-Fi, Bluetooth, non disturbare e schermo aprono il pannello di sistema (`Settings.Panel`/intent): da Android 10 un'app normale non può cambiarli direttamente (con Shizuku si potrà in Fase 2).
- **Cerca** (`Calc`, `SearchCommands`): parser a discesa ricorsiva (niente `eval`) per i conti, tabella di unità per le conversioni, comandi di NOVA.

**Permessi aggiunti:** `WRITE_SETTINGS` (facoltativo, solo per la luminosità; si concede da `Settings.ACTION_MANAGE_WRITE_SETTINGS`). Nessun altro: `INTERNET` c'era già (aggiornamenti) e la torcia non ne richiede.


### Notifiche in linea, isola dinamica, widget Musica
- **Notifiche** (`RawNotification`, `NotificationIndex`, `NotificationSource`; app: `NotificationHub`, `NovaNotificationListener`): il servizio di sistema `NotificationListenerService` si attiva **solo se l'utente dà l'accesso** (Impostazioni → Home → «Notifiche e musica sulla Home» apre `ACTION_NOTIFICATION_LISTENER_SETTINGS`). Da ogni notifica attiva si tiene un'anteprima per app (la più recente + conteggio), scartando quelle in corso, i riassunti di gruppo, i comandi della musica e quelle vuote. Tutto in memoria: **mai salvato, registrato o inviato** (SPEC §13). Su Android 13+ per un'app installata fuori dal Play Store l'impostazione può risultare «con restrizioni»: si sblocca da Informazioni sull'app → ⋮ → «Consenti impostazioni con restrizioni».
- **Distintivi** (`CountBadge`): in griglia, dock e (con il nome sotto) nelle righe grandi dell'elenco. Il menu dell'app ha «Cancella la notifica» (`cancelNotification`).
- **Musica** (`MediaSource`, `NowPlaying`; app: `MediaHub`): sessioni multimediali (`MediaSessionManager`, serve lo stesso accesso). Si ascolta solo tra `onStart` e `onStop` della Home; la copertina si riduce a 256 px. Sceglie la sessione che sta suonando, altrimenti la più recente.
- **Isola dinamica** (`DynamicIsland`): pillola in alto, visibile **solo mentre suona**; tocco = comandi. È una vista della Home (una finestra sopra le altre app richiederebbe `SYSTEM_ALERT_WINDOW`: per ora no).
- **Widget Musica** (`MusicWidget`): sempre disponibile, anche in pausa.
- **Permessi:** nessuno nuovo nel manifest oltre al servizio (`BIND_NOTIFICATION_LISTENER_SERVICE`, che è del sistema). Il servizio, se l'accesso è dato, resta in memoria: pesa pochi KB (una mappa di anteprime).

## Sfondo e app recenti
- **Problema risolto:** nelle app recenti a volte traspariva l'app aperta prima. La finestra della home era traslucida; ora **non lo è più**.
- **Sfondo disegnato da NOVA:** quando NOVA disegna lo sfondo da sé (foto, NOVA, colore, oppure il tuo sfondo se le consenti di leggerlo), la finestra è opaca.
- **Preparazione dell'immagine:** `WallpaperLoader` rimpicciolisce l'immagine e la sfoca una volta sola, fuori dal thread principale. Più sfocatura significa un'immagine più piccola, quindi anche più leggera. Il colore principale si ricava con `WallpaperColors.fromBitmap`.
- **Leggere il tuo sfondo:** da Android 13 un'app può leggerlo solo con l'accesso a tutti i file (`MANAGE_EXTERNAL_STORAGE`). È **facoltativo**: lo concedi tu, da una voce chiara, e serve solo a quello. Senza il permesso resta lo sfondo di Android dietro la finestra, sfocato da Android se il tablet lo supporta (`setBackgroundBlurRadius`, spento a Prestazioni). Con «Foto» la sfocatura funziona sempre senza permessi.

## Come è fatto
| File | Ruolo |
|---|---|
| `AppCatalog` | elenco delle app da `LauncherApps` (tutti i profili utente), aggiornato dai callback di sistema su installazione, rimozione e modifica: niente polling. Icone caricate fuori dal thread principale, una sola misura (88 dp, la più grande della griglia), cache LRU di 300. Attivo solo tra `start()` e `stop()`. |
| `AppIndex` | lettera e ordinamento italiano (testato). |
| `Favorites` | preferiti in `files/home-favorites.txt`, una chiave per riga, scrittura atomica fuori dal thread principale (testato). |
| `HomeScreen`, `IpadHome`, `GridMetrics`, `HomeLists`, `HomeRows`, `AppMenu`, `ReorderState`, `HomePrefs` (+ `PrefsCodec`), `HomeLook`, `ThemePresets`, `NovaWallpapers`, `WallpaperLoader`, `IconStyler`, `IconPacks`, `Alphabet`, `Clock` | interfaccia Compose; l'ora si aggiorna col tic di sistema di ogni minuto, solo mentre la home è visibile. |
| `app/HomeActivity` | attività `HOME` (`singleTask`, tema traslucido con lo sfondo di sistema); collega catalogo, preferiti, colori dello sfondo, sfocatura e livello grafico; passa i tempi dei fotogrammi al livello automatico. |
| `app/HomeSection`, `app/HomeCustomizeScreen` (+ `HomeCustomizeParts`, `HomeCustomizeRoute`), `app/HomeWindow`, `app/HomeSystem` | in Impostazioni: quale app fa da Home, il pulsante per cambiarla (`Settings.ACTION_HOME_SETTINGS`) e la personalizzazione. |

**Chiave di un'app:** `pacchetto/attività#seriale-utente`, stabile tra un riavvio e l'altro.

## Consumi
Lista, icone, colori dello sfondo e sfocatura esistono solo mentre la home è visibile: in `onStop` si annullano il callback di `LauncherApps`, gli ascoltatori dei colori e della sfocatura, il ricevitore dell'ora e le coroutine. La sfocatura reale costa GPU: si spegne con il livello Prestazioni. Le liste usano chiavi stabili e non allocano durante lo scorrimento.

## Note sulle regole
La home è il guscio del launcher (`app/` assembla i moduli, SPEC §3.3), quindi non è un `NovaModule` spegnibile: senza home non c'è launcher. Rispetta comunque la regola "da spento non consuma nulla", perché non consuma niente quando non è visibile. Il launcher di sistema resta installato e si può tornare a usarlo dalle Impostazioni o dalla modalità provvisoria.

### Centro di controllo a tendina (`ControlCenter.kt`)

- **Stato:** `ControlSheetState.progress` (0 chiusa … 1 aperta) è un `mutableFloatStateOf` letto **solo nella fase di disegno** (`graphicsLayer`/`drawBehind`): trascinare o animare non ricompone niente. `control` in `HomeScreen` resta la *scelta* (ricerca, Indietro, un riquadro); un `LaunchedEffect` porta lo stato lì con una molla (`animate`), partendo dalla velocità del dito quando c'è.
- **Apertura:** `Modifier.pullDownControl` sul contenitore della Home, nel passaggio `Initial`: reagisce solo a un gesto che parte nell'angolo in alto a destra (280×120 dp) e va in giù; ogni altro tocco passa intatto, quindi non c'è nessuna zona che blocchi icone o liste. Messo **prima** di `swipeDown`: l'angolo apre il centro di controllo, il resto della Home continua ad aprire le notifiche.
- **Chiusura:** `closeByDragging` sul pannello (soglia di tocco, velocità con `VelocityTracker`), tocco sullo sfondo, Indietro. `ControlSheetMath.shouldOpen` decide dove si ferma (metà o scatto > 1,2 pannelli/s).
- **Da chiuso** il pannello non è nella composizione (`derivedStateOf { progress > 0 }`): costo zero. Le letture (volume, luminosità) si rifanno a ogni apertura.
- **Cursori:** gesto proprio (`awaitEachGesture`), senza soglia di partenza, solo asse x, consuma i movimenti (il pannello non li scambia per «chiudi»). Il valore è uno stato locale; la scrittura al sistema (`Level.write`) passa da `snapshotFlow` con pausa di 40 ms (≈25 Hz, sempre l'ultimo valore) e un `commit` finale al rilascio, solo se il valore è cambiato. `Level.steps` aggancia ai gradini (volume = massimo di `AudioManager`).
- **Accessibilità:** `ControlCenterEntry` (1 dp, invisibile) espone l'azione «Centro di controllo» a TalkBack; i cursori espongono `setProgress`.

#### Sopra le altre app (`app/NovaControlService`, `PullDetector`, `ControlCenterOverlay`)

- **Servizio di accessibilità** acceso dall'utente (Impostazioni di Android → Accessibilità); `accessibility_service.xml`: solo `typeWindowStateChanged`, `canRetrieveWindowContent=false`. Non legge lo schermo, non esegue gesti. Se non è acceso, nessuna finestra, nessun costo.
- **Striscia:** `View` trasparente `TYPE_ACCESSIBILITY_OVERLAY` su **tutta la larghezza** in alto (altezza barra di stato, min 24 dp), sopra la barra di stato: sostituisce il gesto della tendina di Android. `PullDetector` (pura, testata) riconosce «giù più che di lato» oltre la soglia di tocco e apre il pannello. È **sempre attiva**: un tentativo di spegnerla a barra di stato nascosta (listener degli inset) la lasciava morta sul tablet reale ed è stato tolto; nei giochi a schermo intero il tiro dal bordo alto è proprio l'accesso alla tendina.
- **Pannello:** finestra a tutto schermo con `ComposeView`; `PanelHost` fornisce ciclo di vita e stato salvato (non c'è un'Activity) e riceve Indietro (finestra col fuoco). Contenuto: `ControlCenterOverlay` → lo stesso `ControlCenter` della Home (senza «Modifica la Home»). `HomeControls` è lo stesso della Home: torcia, volume, luminosità, Turbo; `start()`/`stop()` solo a pannello aperto.
- **Chiusura:** tiro su, tocco fuori, Indietro, cambio di finestra di un'altra app (evento), `onInterrupt`; un timer di sicurezza (700 ms) toglie la finestra anche se l'animazione non finisse. A schermo bloccato (`KeyguardManager`) non si apre.
- **Limite:** la finestra della striscia non può passare il tocco al pannello, quindi sopra le altre app la tendina **non segue il dito** nel tiro: si apre con una molla al riconoscimento del gesto.

#### Prima vista e «Altro» (`ControlCenter.kt`, `HomeControls.more()`)

- **Prima vista:** `essentials` (Wi-Fi, Bluetooth, Non disturbare, torcia) + cursori + `MoreToggle`. Tutto il resto è in `AnimatedVisibility` sotto «Altro» (non è nella composizione finché chiuso): i riquadri NOVA (`nova`) e `ControlActions.more` (`MoreItem`), in una colonna che scorre (max 300 dp).
- **`MoreItem(label, state, onClick: () -> String?)`:** `onClick` dà il nuovo stato se è un interruttore (resta aperta) o `null` se porta fuori (chiude). La logica Android sta in `app/HomeControls`: rotazione con `Settings.System.ACCELEROMETER_ROTATION` (serve `WRITE_SETTINGS`, altrimenti apre lo schermo), le altre voci aprono l'azione di impostazioni giusta; alcune azioni sono stringhe perché non sono costanti pubbliche in tutte le versioni (se mancano, `runCatching` non fa nulla).
- **Rete di sicurezza:** «Notifiche di Android» e «Impostazioni rapide di Android» (Home: `expandNotificationsPanel`/`expandSettingsPanel` via `statusbar`; servizio: `GLOBAL_ACTION_NOTIFICATIONS`/`GLOBAL_ACTION_QUICK_SETTINGS`). Sono ciò che rende sicuro sostituire la tendina: ogni riquadro di Android resta raggiungibile.
- **Tema:** la scheda è `lerp(CARD|CARD_LIGHT, accento, 0.1)`; testo, simboli e riquadri attivi usano `HomeLook` (quindi chiaro/scuro, accento personalizzato e sfondo scelti in Personalizza la Home).

#### Riquadri a prima vista scelti dall'utente

- `HomePrefs.controlTiles: List<ControlTileId>` (predefinito `ControlTileId.DEFAULT`), salvato come `controlTiles=A,B,C` in `PrefsCodec`: chiave assente = predefiniti, chiave vuota = nessuno, nomi ignoti o doppi scartati, ordine sempre quello dell'enum. Non fa parte dell'aspetto dei temi (`appearance()` lo azzera, `withLookOf()` conserva quello dell'utente).
- `ControlCenter(firstView = …)`: costruisce i riquadri disponibili (`available`: torcia solo con flash, Turbo solo con Shizuku, Modifica la Home solo sulla Home) e li divide tra prima vista e «Altro». Il servizio passa `prefs.controlTiles` tramite `ControlCenterOverlay`.
- Schermata: categoria «Centro di controllo» in `HomeCustomizeScreen` (`ControlSection`), una riga con spunta per riquadro.

#### Tendina: solo la nostra, che segue il dito, con interruttori diretti

- **Sopra le altre app il pannello segue il dito:** `StripTouch` (listener della striscia) tiene il tocco per tutto il gesto; oltre la soglia chiede `openPanel()` (che crea la finestra e restituisce un `ControlSheetState`) e da lì chiama `sheet.drag(dy)` a ogni movimento e `sheet.release(velocità)` al rilascio. `ControlCenterOverlay` non si apre più da solo: lo guida chi lo chiama (`sheet`), `closing` lo richiude.
- **Android non deve comparire:** mentre il dito scende (≥100 ms tra una chiamata e l'altra) e a gesto finito (due volte, dopo 120 e 350 ms) il servizio chiama `performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)`: se SystemUI apre la sua tendina in parallelo (osserva lo stesso tocco), viene richiusa. Sulla Home, `rememberSwipeDown` apre `control = true`; `actions.onSwipeDown` (notifiche di Android) è usato solo senza centro di controllo.
- **Sensazione:** `OFFSCREEN_MARGIN` 120 → 48 dp (niente zona morta: il pannello compare quasi subito), `SHEET_DAMPING` 1 (nessun rimbalzo), `SHEET_STIFFNESS` 1500.
- **Interruttori diretti:** `ControlActions.toggles: Map<SystemPanel, Toggle>` (Wi-Fi, Bluetooth, Non disturbare). `Toggle(read, set(on, onFailed))`: il riquadro cambia stato **subito** (ottimistico) e, solo se il comando fallisce, rilegge lo stato vero. Senza `toggles` il riquadro apre Android come prima. `MoreItem.onClick(report)` restituisce lo stato sperato e può correggerlo più tardi con `report`.
- **App:** `QuickToggles` legge lo stato dalle API pubbliche (nessun permesso speciale; `ACCESS_WIFI_STATE` per il Wi-Fi) e cambia con `app.systemTools.setToggle(...)` solo se Shizuku è pronto.
- **core/system (nota per la revisione):** nuovo `SystemCommand.SetToggle(QuickToggle, on)` + enum chiuso `QuickToggle` con un `argv` fisso per ogni interruttore (`svc wifi|bluetooth`, `cmd connectivity airplane-mode`, `cmd notification set_dnd`, `cmd uimode night`, `cmd location set-location-enabled`, `cmd netpolicy set restrict-background`, `settings put global low_power`, `settings put secure night_display_activated|accessibility_display_inversion_enabled`); `SystemChange.Toggle` nel registro (una sola voce per interruttore, con lo stato originale; sparisce se si torna allo stato originale); `revertAll` lo annulla. Test: `QuickToggleCommandTest`, `SystemChangeLogTest`.
