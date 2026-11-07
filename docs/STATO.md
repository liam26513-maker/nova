# Stato del piano e come ripartire

Aggiornato a: release 0.1.780. Per riprendere il lavoro in una chat nuova basta scrivere:
«Leggi CLAUDE.md, SPEC.md e docs/STATO.md e continua dal prossimo passo.» Tutto il resto sta nel repository.

## Fatto (vedi CHANGELOG.md per il dettaglio, in italiano semplice)
- **Fase 0** fondamenta: moduli `core/*`, aggiornatore, Migliora/Ripara, CI e rilasci, modalità provvisoria.
- **Fase 1** Home: griglia/lista/ibrido, widget, ricerca, notifiche sulle icone, isola dinamica, personalizzazione.
- **Centro di controllo** (SPEC §6.8): tendina che segue il dito, sostituisce quella di Android (Home e sopra le altre app con il servizio di
  accessibilità), riquadri a prima vista scelti in Personalizza la Home, sezione «Altro», interruttori diretti con Shizuku,
  sblocco in un tocco delle impostazioni con restrizioni.
- **Fase 2** strumenti di sistema (Shizuku): Turbo, animazioni, densità, DNS anti-pubblicità, congelamento, stato, interruttori rapidi.
- **Schermata di blocco e StandBy (base):** sfondo di NOVA anche sul blocco; StandBy dentro la Home (ora, data, batteria, sveglia). Mancano calendario/widget/Stato PC nello StandBy.
- **Fase 3**: motore delle modalità (`core/modes`), Gaming solo manuale, Studio con Pomodoro, blocco app, note e filtro caldo; Lavoro, Creativa e Presentazione **di base** (tendina → Altro). Doppio swipe e watchdog ci sono, spenti di serie. Risoluzione per gioco (minimo 65%), misura da volume su, uscita con volume giù tenuto 3 s. Vedi `docs/modules/modalita.md`.
- **Fase 4, primo passo**: tastiera (`keyboard/`, fork di HeliBoard) costruita dalla CI e allegata alle release.

## Prossimo passo (in ordine)
1. **Prove sul tablet** di `docs/TEST-MANUALE.md`: tutto ciò che è arrivato dopo la 0.1.460 è provato solo con test e CI.
2. **Tastiera** (SPEC §10; dettagli in `docs/modules/tastiera.md`): NOVA la aggiorna già con Shizuku; manca la prima installazione dall'app, poi aspetto iOS. Misurare la velocità sul tablet prima e dopo ogni ottimizzazione.
3. **Fase 3, seguito:** decidere con il proprietario se doppio swipe e watchdog restano spenti di serie e se serve l'attivazione automatica della Gaming. Poi desktop e finestre flottanti per Lavoro, PIN per Presentazione (la Home portfolio è rimandata: il proprietario ha detto di non farla per ora), profili automatici.
4. **Fase 4** penna, **Fase 5** PC e streaming, **Fase 6** intelligenza e rifiniture.

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

## Cose da sapere
- La CI di `ci.yml` controlla NOVA; `tastiera.yml` controlla `keyboard/` (progetto Gradle separato, strumenti più nuovi).
- Il tablet riceve gli aggiornamenti da GitHub Releases (WorkManager ogni 6 ore con rete, più «Controlla ora»); in standby profondo Android li rimanda.
- Ipotesi non ancora verificate sul tablet sono segnate nelle PR e in `docs/TEST-MANUALE.md`: la tendina sopra le altre app, gli interruttori con Shizuku.
