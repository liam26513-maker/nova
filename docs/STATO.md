# Stato del piano e come ripartire

Aggiornato a: release 0.1.3xx. Per riprendere il lavoro in una chat nuova basta scrivere:
«Leggi CLAUDE.md, SPEC.md e docs/STATO.md e continua dal prossimo passo.» Tutto il resto sta nel repository.

## Fatto (vedi CHANGELOG.md per il dettaglio, in italiano semplice)
- **Fase 0** fondamenta: moduli `core/*`, aggiornatore, Migliora/Ripara, CI e rilasci, modalità provvisoria.
- **Fase 1** Home: griglia/lista/ibrido, widget, ricerca, notifiche sulle icone, isola dinamica, personalizzazione.
- **Centro di controllo** (SPEC §6.8): tendina che segue il dito, sostituisce quella di Android (Home e sopra le altre app con il servizio di
  accessibilità), riquadri a prima vista scelti in Personalizza la Home, sezione «Altro», interruttori diretti con Shizuku, tasto di
  accessibilità di scorta, sblocco in un tocco delle impostazioni con restrizioni.
- **Fase 2** strumenti di sistema (Shizuku): Turbo, animazioni, densità, DNS anti-pubblicità, congelamento, stato, interruttori rapidi.
- **Schermata di blocco e StandBy (base):** sfondo di NOVA anche sul blocco; StandBy dentro la Home (ora, data, batteria, sveglia). Mancano foto/calendario/widget/Stato PC nello StandBy.
- **Fase 3**: motore delle modalità (`core/modes`), Gaming solo manuale, Studio, Lavoro, Creativa e Presentazione **di base** (tendina → Altro). Risoluzione per gioco (minimo 65%), misura da volume su, uscita con volume giù tenuto 3 s. Vedi `docs/modules/modalita.md`.
- **Fase 4, primo passo**: tastiera (`keyboard/`, fork di HeliBoard) costruita dalla CI e allegata alle release.

## Prossimo passo (in ordine)
1. **Tastiera** (SPEC §10; dettagli in `docs/modules/tastiera.md`): NOVA la aggiorna già con Shizuku; manca la prima installazione dall'app, poi tema di
   NOVA tramite `ContentProvider`, aspetto iOS, funzioni per tablet (divisa, flottante, riga numeri: disattivate di default), trackpad sulla barra spaziatrice,
   espansioni di testo, layout italiano per la tastiera fisica. Misurare la velocità sul tablet prima e dopo ogni ottimizzazione.
2. **Fase 3, seguito:** doppio swipe dal bordo e watchdog **prima** di qualunque attivazione automatica. Attenzione: il doppio swipe richiede finestre sui bordi sopra il gioco e il watchdog una lettura periodica dei fotogrammi, che nelle prove disturbava il gioco: vanno decisi con il proprietario. Poi pannello in sovrimpressione, contenuti veri di Studio, Lavoro, Creativa e Presentazione, profili automatici.
3. **Fase 4** penna, **Fase 5** PC e streaming, **Fase 6** intelligenza e rifiniture.

## Regole che contano (CLAUDE.md)
Branch e PR con test, CHANGELOG in italiano semplice, budget di `perf-budget.json` mai superati, `core/safemode` indipendente da `feature/`, nessun permesso
Internet nella tastiera, mai cambiare chiave di firma né `applicationId`. La CI è il controllo che conta; sul PC del proprietario c'è anche un SDK in `C:\\Users\\Utente\\android-dev` (JDK 17 in `jdk17`).

## Misure su U6 (Brawl Stars, Footbrawl, una partita per caso)
100%: 46 FPS e un fotogramma lento su tre, con o senza Gaming e tendina. 70%: 61,5 FPS e 0,5% di lenti. Il 65% non è ancora misurato in partita. Nella terza prova la rotazione automatica risultava accesa nonostante la Gaming: causa non trovata.

## Sul tablet (4 novembre, 0.1.460)
Provate: Studio, Lavoro e l'aggiornamento automatico della tastiera (0.1.340 → 0.1.460 con «Controlla ora»). Per un'ora circa, durante un guasto di GitHub, il DNS di «Blocca pubblicità» non è stato confermato e il tablet non trovava nessun sito; poi si è ripreso da solo, causa non trovata. Il servizio di accessibilità di NOVA risulta spento, causa ignota: senza non si provano le uscite col volume. Mancano Creativa, Presentazione, Risoluzione e il 65% in partita.

## Cose da sapere
- La CI di `ci.yml` controlla NOVA; `tastiera.yml` controlla `keyboard/` (progetto Gradle separato, strumenti più nuovi).
- Il tablet riceve gli aggiornamenti da GitHub Releases (WorkManager ogni 6 ore con rete, più «Controlla ora»); in standby profondo Android li rimanda.
- Ipotesi non ancora verificate sul tablet sono segnate nelle PR e in `docs/TEST-MANUALE.md`: la tendina sopra le altre app, gli interruttori con Shizuku.
