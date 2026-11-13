# Stato del piano e come ripartire

Aggiornato a: release 0.1.1620. Per riprendere il lavoro in una chat nuova basta scrivere:
«Leggi CLAUDE.md, SPEC.md e docs/STATO.md e continua dal prossimo passo.» Tutto il resto sta nel repository.

## Fatto (vedi CHANGELOG.md per il dettaglio, in italiano semplice)
- **Fase 0** fondamenta: moduli `core/*`, aggiornatore, Migliora/Ripara, CI e rilasci, modalità provvisoria.
- **Fase 1** Home: griglia/lista/ibrido, widget, ricerca, notifiche sulle icone, isola dinamica, personalizzazione.
- **Centro di controllo** (SPEC §6.8): tendina che segue il dito, sostituisce quella di Android (Home e sopra le altre app con il servizio di
  accessibilità), riquadri a prima vista scelti in Personalizza la Home, sezione «Altro», interruttori diretti con Shizuku,
  sblocco in un tocco delle impostazioni con restrizioni.
- **Fase 2** strumenti di sistema (Shizuku): Turbo, animazioni, densità, DNS anti-pubblicità, congelamento, stato, interruttori rapidi.
- **Schermata di blocco e StandBy (base):** sfondo di NOVA anche sul blocco; StandBy dentro la Home (ora, data, batteria, sveglia). Mancano widget, foto e Stato PC nello StandBy.
- **Fase 3**: motore delle modalità (`core/modes`), Gaming solo manuale, Studio con Pomodoro, blocco app, note e filtro caldo; Lavoro, Creativa e Presentazione **di base** (tendina → Altro). Doppio swipe, watchdog e Gaming automatica accesi di serie (scelta del proprietario, 9 novembre). Risoluzione per gioco (minimo 65%), misura da volume su, uscita con volume giù tenuto 3 s. Vedi `docs/modules/modalita.md`.
- **Fase 4, primo passo**: tastiera (`keyboard/`, fork di HeliBoard) costruita dalla CI e allegata alle release, con varianti Lavoro, Studio e Gaming (bassa e semitrasparente).
- **7 novembre (0.1.800 → 0.1.950):** pannello Gaming in sovrimpressione con registrazione, avviso di temperatura, Gaming automatica facoltativa; PIN della Presentazione; finestre flottanti e set di finestre nel Lavoro; Foglio rapido; evento del calendario nello StandBy; grafico della settimana; pannello privacy e blocco di Internet per app; Novità; app suggerite nella ricerca. Poi (fino alla 0.1.1030): permessi concessi, pagina Penna e tavoletta, barra Annulla/Ripeti nella Creativa, barra delle app preferite nel Lavoro, profili automatici con tastiera, penna e orario di studio, cartella privata. Sera del 7 novembre (fino alla 0.1.1100): meteo nello StandBy; testo dalle foto con PaddleOCR PP-OCRv6 tiny su ONNX Runtime (`feature/ocr`, processo separato `:ocr`, APK ~22 MB su 25 di budget, provato sul tablet: meno di 2 s); Foglio rapido fatto e poi tolto su richiesta del proprietario (si usano ibisPaint e le Note). Il touchscreen gslX680 manda la posizione solo ogni ~0,7 mm: limite del firmware, vedi SPEC §9.1.
- **9 novembre (0.1.1110 → 0.1.1250):**
  - Automazioni leggere «quando → allora» (Impostazioni → Automazioni);
  - fluidità della Home prima e dopo nelle Novità; musica e Pomodoro nello StandBy, testi lunghi con «…»; StandBy automatico puntuale a 30 s;
  - «Chiudi il gioco» solo per i giochi;
  - sfondo di NOVA anche come sfondo di sistema;
  - **App recenti di NOVA in stile iPad** sopra quelle di Android (SPEC §12.10): sfondo sfocato, anteprime vere via Shizuku, chiusura con un colpetto.
  - Provato tutto sul tablet.
  - Root studiato e scartato: il fastboot del U6 è in «secure mode» anche con lo Sblocco OEM acceso.
- **9 novembre, pomeriggio (fino alla 0.1.1310):** tastiera stile iPad e installabile da NOVA; StandBy senza tastiera sopra e con foto a rotazione facoltative; automazioni con «solo se»; ricerca senza la barra della Home in mezzo; doppio swipe, watchdog e Gaming automatica accesi di serie. Provato sul tablet (le foto vere e la Gaming in partita restano al proprietario).
- **12 novembre (fino alla 0.1.1380):**
  - barra della penna per ibisPaint (tasti letti dall'elenco di ibisPaint sul tablet);
  - «Gioca dal PC» in tendina: apre Moonlight. Il gaming dal PC è rimandato dal proprietario: Sunshine e Tailscale installati sul PC, ma senza collegamento diretto (reti dei telefoni) il ritardo è 1–2 s;
  - «Blocca pubblicità» che si sospende da solo se la rete non raggiunge AdGuard;
  - widget Calendario o Batteria nello StandBy;
  - misura delle prestazioni corretta (fotogrammi persi davvero + reattività): sul U6 consiglia Bilanciato;
  - Home: pagine accanto pronte prima e cambio pagina più morbido;
  - misura della tastiera salvata in `docs/modules/tastiera.md`.
  - Home ancora lenta nello scorrimento (mediana ~30 ms, UI thread): tracce Compose aggiunte (PR 148) per trovare il pezzo lento.
- **12 novembre, sera (fino alla 0.1.1480):**
  - scorciatoie delle app in cima al menu dell'icona (pressione lunga);
  - Il tuo tempo: grafico dei minuti per app, anche per una sola app;
  - Lavoro: le app aperte nella barra del desktop;
  - suggerimenti nella ricerca con il perché («adesso», «a quest'ora», «spesso»), anche secondo modalità, cuffie e tastiera;
  - Home essenziale facoltativa durante Studio e Presentazione, che all'uscita torna com'era;
  - risposta rapida alle notifiche dal menu dell'icona;
  - isola della musica sopra le altre app, facoltativa e spenta di serie.
  - Il modello della penna non si fa (detto dal proprietario).
  - Provati sul tablet con la 0.1.1480: suggerimenti con il perché, scorciatoie (Calendar → «Nuovo evento»), grafico per app, isola sopra YouTube Music (si apre con i comandi, sparisce sulla Home). Da provare a mano: risposta rapida (serve un messaggio vero), Home essenziale in Studio, app aperte nella barra del Lavoro.

- **13 novembre, notte (fino alla 0.1.1620): velocità** («vorrei velocizzare e rendere più fluido il tutto»):
  - NOVA e la tastiera si fanno compilare da ART dopo ogni aggiornamento (erano in «verify»), riavvio a schermo spento;
  - Compose 1.10, AGP 8.9, compileSdk 36; niente semantica per l'autocompletamento;
  - Home: mediana da 29 a 19 ms, 90° percentile da 42 a 29 ms; ricerca da 28 a 21 ms; Impostazioni da 23 a 21 ms (dettagli in `docs/modules/home.md`);
  - Creativa: 3 tasti di navigazione al posto dei gesti (i tratti dal bordo non diventano «Indietro»);
  - tratti persi in ibisPaint: lo schermo (gslX680, 50 letture al secondo) non sente alcuni contatti rapidissimi della penna; tutto ciò che sente arriva a ibisPaint. Non risolvibile senza root.
  - Copertine dei giochi nella libreria.

## Prossimo passo (in ordine)
Le parti del piano che Claude poteva fare da solo sono fatte (controllo del 12 novembre 2026). Restano:
1. **Prove a mano del proprietario:** risposta rapida con un messaggio vero, Home essenziale nella Studio, foto vere nello StandBy, Gaming e 65% in partita (vedi `docs/TEST-MANUALE.md`).
2. **Rimandate o non volute dal proprietario:** gaming e streaming dal PC (con lo Stato PC), Home portfolio della Presentazione, consigli per modello di penna, galleria dei lavori, file cifrati nella cartella privata («per ora no»).
3. **Penna:** profili della barra per altre app oltre a ibisPaint, solo se il proprietario ne indica una.
4. **Repository:** rimetterlo privato quando il limite delle Actions si è rinnovato (lo fa Claude, d'accordo con il proprietario).

## Regole che contano (CLAUDE.md)
Branch e PR con test, CHANGELOG in italiano semplice, budget di `perf-budget.json` mai superati, `core/safemode` indipendente da `feature/`, nessun permesso
Internet nella tastiera, mai cambiare chiave di firma né `applicationId`. La CI è il controllo che conta; sul PC del proprietario c'è anche un SDK in `C:\\Users\\Utente\\android-dev` (JDK 17 in `jdk17`).

## Misure su U6 (Brawl Stars, Footbrawl, una partita per caso)
100%: 46 FPS e un fotogramma lento su tre, con o senza Gaming e tendina. 70%: 61,5 FPS e 0,5% di lenti. Il 65% non è ancora misurato in partita. Nella terza prova la rotazione automatica risultava accesa nonostante la Gaming: causa non trovata.

## Sul tablet (4 novembre, 0.1.460)
Provate: Studio, Lavoro e l'aggiornamento automatico della tastiera (0.1.340 → 0.1.460 con «Controlla ora»). Per un'ora circa, durante un guasto di GitHub, il DNS di «Blocca pubblicità» non è stato confermato e il tablet non trovava nessun sito; poi si è ripreso da solo, causa non trovata. Il servizio di accessibilità di NOVA risulta spento, causa ignota: senza non si provano le uscite col volume. Mancano Creativa, Presentazione, Risoluzione e il 65% in partita.

## Sul tablet (7 novembre, 0.1.710)
Provate sul tablet: tendina con gesti veloci e lenti (la tendina di Android si chiude con Shizuku: l'azione di accessibilità `GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE` qui non fa niente), note rapide, pagine Studio, Gaming, Aggiornamenti e Prestazioni. Provati anche: pastiglia e uscita della Studio, avvio del Pomodoro, blocco e sblocco delle app, Lavoro con schermo diviso, ingresso e uscita dalla Creativa, minuti di «Il tuo tempo». Provati in Gaming: doppio swipe dal centro del bordo e le tre voci del menu di uscita, con ripristino completo. Il proprietario ha provato volume giù tenuto col tasto vero: funziona (i tasti inviati da adb non arrivano al servizio). Provato anche il tempo per ogni app, con il permesso concesso. Provata anche la Presentazione: la barra della luminosità segue il cambiamento (confermato dal proprietario). Con Shizuku la tendina di Android è bloccata (`AndroidShade`): dal gesto parte solo quella di NOVA, «Notifiche di Android» in Altro la apre e il gesto dopo la riblocca. Non ancora provati a mano: filtro caldo (per il proprietario poco importa), misura e 65% in partita, blocco della tendina sopra un'app esterna. Brawl Stars è al 65%, anche nel registro di NOVA. Il servizio di accessibilità si fermava perché il gestore Allwinner «Background Manage» bloccava NOVA (`awbms skipService`): il 6 novembre il proprietario ha autorizzato NOVA e Tastiera NOVA. Se la tendina veniva tirata mentre si chiudeva si apriva solo quella di Android: corretto; altri due casi simili, con più di un secondo di distanza, non si sono più ripetuti in oltre 30 prove.

Provati il 7 novembre: «Invia a Claude», elenco delle app da spegnere (nessuna spenta), tastiera con solo italiano e inglese, controllo gioco bloccato (su un browser fermo, dopo 18 secondi). Il repository è **pubblico solo per ora**: GitHub Actions si era fermato per il limite del repository privato; va rimesso privato quando il limite si rinnova.

## Tastiera (7 novembre, 0.1.780)
Sul tablet provati tema NOVA scuro e accento scelto in NOVA. Da provare a mano: cambio d'accento senza riavvii, voce «Tastiera fisica» (serve una tastiera), StandBy con lo sfondo (di giorno), righe di Lavoro e Studio, tastiera bassa in Gaming. L'interruttore «Tema chiaro/scuro automatico» della tastiera era spento sul tablet: acceso il 7 novembre.

## Sul tablet (7 novembre, 0.1.880)
Provati da me: Foglio rapido (disegno, salvataggio uscendo, Pulisci e Annulla), grafico della settimana in «Il tuo tempo», pagina Modalità Presentazione con la finestra del PIN (tastiera numerica di NOVA), voce «Foglio rapido» in Altro. Da provare a mano perché servono le modalità accese: pannello Gaming e registrazione, PIN all'uscita, finestre flottanti nel Lavoro, tastiera semitrasparente, avviso di temperatura, evento nello StandBy.

## Cose da sapere
- La CI di `ci.yml` controlla NOVA; `tastiera.yml` controlla `keyboard/` (progetto Gradle separato, strumenti più nuovi).
- Il tablet riceve gli aggiornamenti da GitHub Releases (WorkManager ogni 6 ore con rete, più «Controlla ora»); in standby profondo Android li rimanda.
- Ipotesi non ancora verificate sul tablet sono segnate nelle PR e in `docs/TEST-MANUALE.md`: la tendina sopra le altre app, gli interruttori con Shizuku.
