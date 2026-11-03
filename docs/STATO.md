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
- **Fase 3, primo passo**: motore delle modalità (`core/modes`) e Gaming di base solo manuale (tendina → Altro). Vedi `docs/modules/modalita.md`.
- **Fase 4, primo passo**: tastiera (`keyboard/`, fork di HeliBoard) costruita dalla CI e allegata alle release.

## Prossimo passo (in ordine)
1. **Tastiera** (SPEC §10; dettagli in `docs/modules/tastiera.md`): installarla e aggiornarla da NOVA (con Shizuku `pm install`, come l'app), poi tema di
   NOVA tramite `ContentProvider`, aspetto iOS, funzioni per tablet (divisa, flottante, riga numeri: disattivate di default), trackpad sulla barra spaziatrice,
   espansioni di testo, layout italiano per la tastiera fisica. Misurare la velocità sul tablet prima e dopo ogni ottimizzazione.
2. **Fase 3, seguito:** le uscite di emergenza della Gaming (volume giù 3 s, doppio swipe dal bordo, watchdog, «Chiudi gioco») **prima** di qualunque attivazione automatica; poi pannello in sovrimpressione, Studio, Lavoro, Creativa, Presentazione, profili automatici.
3. **Fase 4** penna, **Fase 5** PC e streaming, **Fase 6** intelligenza e rifiniture.

## Regole che contano (CLAUDE.md)
Branch e PR con test, CHANGELOG in italiano semplice, budget di `perf-budget.json` mai superati, `core/safemode` indipendente da `feature/`, nessun permesso
Internet nella tastiera, mai cambiare chiave di firma né `applicationId`. In ogni sessione: la CI è l'unico modo di compilare (nessun Android SDK in locale).

## Cose da sapere
- La CI di `ci.yml` controlla NOVA; `tastiera.yml` controlla `keyboard/` (progetto Gradle separato, strumenti più nuovi).
- Il tablet riceve gli aggiornamenti da GitHub Releases (WorkManager ogni 6 ore con rete, più «Controlla ora»); in standby profondo Android li rimanda.
- Ipotesi non ancora verificate sul tablet sono segnate nelle PR e in `docs/TEST-MANUALE.md`: la tendina sopra le altre app, gli interruttori con Shizuku.
