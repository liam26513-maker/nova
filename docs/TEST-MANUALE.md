# Verifiche manuali sul tablet

Controlli che i test automatici non possono fare. Ogni PR indica quali punti ripetere.

## Aspetto grafico e livelli (Fase 0, punti 1–4)
- [ ] L'app NOVA si apre su "Impostazioni": sfondo neutro e calmo, due gruppi traslucidi (Livello grafico, Stato del sistema).
- [ ] Nel selettore, toccando **Massimo**, **Bilanciato** e **Prestazioni** il cuscinetto scivola sull'opzione, i gruppi cambiano leggermente e sotto compare "Fissato da te: …".
- [ ] Toccando **Automatico** il cuscinetto va su "Automatico" e sotto compare "Scelto automaticamente: Bilanciato".
- [ ] Ogni cambio di scelta dà una leggera vibrazione e un piccolo "rimbalzo".
- [ ] Ruotando il tablet la schermata si adatta senza riavviarsi.
- [ ] Con il tema scuro di sistema attivo, all'apertura NOVA usa il tema scuro.

## Livelli e misura (correzioni)
- [ ] Toccando **Massimo** il vetro dell'anteprima sfoca in tempo reale le forme che passano; con **Bilanciato** la sfocatura resta ferma; con **Prestazioni** il pannello è pieno.
- [ ] Dopo "Misura le prestazioni" compare il **Resoconto** con tre barre di fluidità e un livello "consigliato"; "Fatto" riporta alle Impostazioni.
- [ ] Anche se il consigliato è più basso, toccando **Massimo** o **Bilanciato** la scelta viene applicata ("Fissato da te: …").

## Shizuku e aggiornamenti (Fase 0, punti 7–8)
- [ ] Senza Shizuku: "Strumenti di sistema" mostra "Non attivo" e "Apri Shizuku per attivarlo" apre Shizuku o il Play Store.
- [ ] Con Shizuku attivo: "Concedi il permesso a NOVA" mostra la richiesta di Shizuku; dopo il sì lo stato diventa "Attivo".
- [ ] "Collega GitHub": incolla il token, "Salva il token". Lo stato diventa "Tutto aggiornato · controllato alle …" (o mostra l'errore in italiano se il token è sbagliato).
- [ ] Dopo il primo rilascio automatico (punto 10): compare "Nuova versione disponibile" e "Installa" aggiorna l'app, senza conferme se Shizuku è attivo.


## Home (Fase 0, punto 11)
Questi punti valgono per lo stile **Elenco** (Personalizza → Disposizione e gesti → Stile della Home). Lo stile predefinito, a icone, ha la sua lista più sotto.
- [ ] Impostazioni → **Home** → "Usa NOVA come Home": nell'elenco di Android scegli NOVA e conferma. Tornando nelle Impostazioni, "App Home" dice "NOVA".
- [ ] Premendo Home si vede **il tuo sfondo** sotto una sfumatura scura, con ora grande, data, preferiti e "Tutte le app".
- [ ] Con il livello **Massimo** o **Bilanciato**, se il tablet lo supporta, lo sfondo appare anche **sfocato**. Con **Prestazioni** resta solo la sfumatura.
- [ ] Cambiando sfondo di sistema, la sfumatura prende il nuovo colore.
- [ ] I preferiti entrano a cascata. Toccando un'app, la riga si rimpicciolisce e l'app si apre "crescendo" dal punto toccato.
- [ ] Trascina il dito sull'alfabeto a destra:
  - [ ] le lettere si gonfiano a onda e accanto al dito compare la bolla con la lettera;
  - [ ] senti un tic a ogni lettera;
  - [ ] si apre "Tutte le app" alla lettera giusta.
- [ ] Tieni premuta un'app: entra nei preferiti o ne esce, e la lista si riordina con un'animazione.
- [ ] In "Tutte le app", Indietro o "‹ Preferiti" riportano ai preferiti. Nei preferiti, Indietro non fa nulla.
- [ ] Il tasto Home premuto nella home riporta ai preferiti, in cima.
- [ ] Installa o disinstalla un'app: gli elenchi si aggiornano da soli.
- [ ] Ruota il tablet in verticale: la disposizione resta ordinata.
- [ ] Il pulsante tondo accanto all'alfabeto apre le Impostazioni di NOVA.
- [ ] Per tornare al launcher di sistema: Impostazioni → Home → "Cambia app Home".

## Interfaccia stile iPad
- [ ] **Home a icone:** premi Home. Vedi ora e data in alto a sinistra, pagine di icone con i nomi sotto, la capsula «Cerca app» e in basso il dock con i tuoi preferiti. Le icone sono tutte arrotondate allo stesso modo e ben leggibili.
- [ ] Scorri la griglia di lato: cambia pagina e i puntini seguono. Il tasto Home premuto sulla seconda pagina riporta alla prima.
- [ ] Tocca un'icona e un'icona del dock: l'app si apre «crescendo» dall'icona. Sotto il dito l'icona si rimpicciolisce subito.
- [ ] Tieni premuta un'icona (griglia e dock): compare il menu. «Aggiungi ai preferiti» la porta nel dock (e la toglie dalla griglia); «Togli dai preferiti» la riporta nella griglia.
- [ ] Senza preferiti il dock spiega come riempirlo.
- [ ] «Cerca app» apre l'elenco di tutte le app con l'alfabeto a onda; Indietro o «‹ Home» tornano alle icone.
- [ ] Scorri giù sulla Home: si aprono le notifiche.
- [ ] Ruota il tablet in verticale: le colonne diminuiscono, il dock resta centrato e nulla va fuori schermo.
- [ ] Con il livello grafico **Prestazioni** il dock diventa più pieno e senza bordo luminoso; con gli altri è trasparente. La Home resta fluida (nessun scatto scorrendo le pagine).
- [ ] Personalizza → Disposizione e gesti → **Stile della Home** → «Elenco»: torna la Home di prima, con ora grande e alfabeto. Rimettendo «Icone e dock» tutto torna. Cambiare tema non cambia lo stile.
- [ ] Personalizza → Icone → «Originale» rimette le icone com'erano; la scelta resta dopo il riavvio.
- [ ] **Modifica la Home:** tieni premuto lo sfondo vuoto: le icone ondeggiano, compaiono «+», «Fine» e i «−». Indietro esce.
  - [ ] Trascina un'icona in un altro posto; trascinala al bordo e aspetta: cambia pagina e la lascia lì.
  - [ ] Mettila sopra un'altra icona: nasce una cartella con le due app; sopra una cartella ci entra.
  - [ ] «−» su un'icona la toglie dalla Home; in Cerca → Tutte le app → pressione lunga → «Aggiungi alla Home» torna.
  - [ ] Trascina un'icona sul dock: diventa un preferito. Nel dock trascina un'icona di lato: si riordina.
  - [ ] Riavvia il tablet: la disposizione, le cartelle e i widget sono come li hai lasciati.
- [ ] **Cartelle:** tocca una cartella: si apre; cambia il nome e chiudi (resta); pressione lunga su un'app → «Togli dalla cartella». Con una sola app rimasta la cartella sparisce.
- [ ] **Widget di NOVA** («+»): Orologio (l'ora è giusta), Calendario, Batteria (percentuale e «In carica» attaccando il cavo), Nota rapida (scrivi, esci e rientra: il testo resta). Il «−» li toglie.
- [ ] **Widget di altre app** («+» → Widget delle app): scegline uno. Android chiede il permesso; se ha una configurazione si apre; il widget compare e si aggiorna. Toglilo con «−». *(Funzione non provata su un tablet vero: se qualcosa non va, segnala il nome del widget.)*
- [ ] **Cerca:** scrivi poche lettere (anche senza accenti o con le maiuscole): compaiono le app giuste; toccane una e si apre. «sfondo» o «nova» mostra «Impostazioni di NOVA». «Cerca sul web» apre il browser. Senza testo c'è «Tutte le app».
- [ ] **Meteo:** «+» → Meteo → scrivi la tua città → «Salva»: compaiono temperatura, minima/massima e il simbolo giusto. Tocca il nome della città per cambiarla. Con il Wi-Fi spento dopo la prima volta, mostra l'ultimo tempo; mai spento e mai caricato, dice «Meteo non disponibile» e riprova toccando.
- [ ] **Pomodoro:** «+» → Pomodoro → Avvia: il tempo scende; Pausa lo ferma; Azzera torna a 25:00. Lascia finire (o prova con un tempo breve nel test): senti un tic e passa a «Pausa». Riavvia il tablet con il timer in corso: continua da dove era.
- [ ] **Centro di controllo** (a tendina, nessun pulsante):
  - [ ] Tira giù dall'angolo in alto a destra: il pannello scende **seguendo il dito**; lascia a metà e finisce da solo; uno scatto veloce lo apre anche dopo poco. Tira giù da un altro punto: si aprono le notifiche, non il pannello.
  - [ ] Si chiude tirando su (anche con uno scatto), toccando fuori e con Indietro. Se lo apri e lo chiudi dieci volte di fila non rallenta e la Home non scatta.
  - [ ] Volume e luminosità: il riempimento sta **sotto il dito** lungo la barra, dal primo tocco, senza salti; muovi il dito in su e in giù mentre scorri: il cursore continua e il pannello **non si chiude**. Il volume cambia a scatti (i suoi gradini), la luminosità in modo continuo e senza far scattare lo schermo.
  - [ ] Luminosità: la prima volta «Concedi il permesso…» apre la schermata di Android; concedi, torna: il cursore regola lo schermo.
  - [ ] Torcia: si accende e si spegne (e segue se la cambi da un'altra parte).
  - [ ] Wi-Fi e rete, Bluetooth, Non disturbare aprono il pannello giusto di Android **e chiudono la tendina**: tornando trovi la Home pulita.
  - [ ] Livello grafico: ogni tocco passa Automatico → Massimo → Bilanciato → Prestazioni → Automatico.
  - [ ] «Modifica la Home» entra in modifica. «Cerca» → «controllo» apre la tendina.
  - [ ] Con TalkBack acceso: «Centro di controllo» si trova e apre il pannello senza il gesto.
- [ ] **Centro di controllo sopra le altre app:** Impostazioni di NOVA → Home → «Centro di controllo sopra le altre app» → accendi «Centro di controllo di NOVA» nell'accessibilità (se Android lo nega: App → NOVA → ⋮ → «Consenti impostazioni con restrizioni»). Torna alle Impostazioni di NOVA: dice «Attivo».
  - [ ] Apri un'app qualsiasi (browser, YouTube) e tira giù dall'angolo in alto a destra, **sopra la barra di stato**: compare il pannello. Tira giù dalla parte sinistra della barra: scende quella di Android.
  - [ ] Torcia, volume e luminosità funzionano come sulla Home. Wi-Fi/Bluetooth/Non disturbare aprono Android e chiudono il pannello. «Impostazioni di NOVA» apre NOVA. Non c'è «Modifica la Home».
  - [ ] Si chiude tirando su, toccando fuori, con Indietro, e da solo se premi Home o apri un'altra app. Aprilo e chiudilo dieci volte: nessun rallentamento.
  - [ ] A schermo bloccato non si apre. Sulla Home di NOVA il gesto dall'angolo è quello della Home (segue il dito).
  - [ ] In un gioco a tutto schermo: l'angolo in alto a destra non tocca il gioco? (se dà fastidio, spegni il servizio e segnalalo.)
  - [ ] Spegni il servizio dall'accessibilità: l'angolo torna normale e la riga nelle Impostazioni dice «Non attivo».
- [ ] **Cerca come calcolatrice:** «12*(3+4)» → «= 84»; «10 km in mi» → «= 6.213712 mi»; toccare il risultato lo copia (incollalo da qualche parte). «torcia» e «widget» trovano i comandi.
- [ ] **Notifiche e musica sulla Home:** Impostazioni → Home → «Notifiche e musica sulla Home» → consenti l'accesso. (Se Android lo nega: Impostazioni di Android → App → NOVA → ⋮ → «Consenti impostazioni con restrizioni».) Torna alle Impostazioni: dice «Attivo».
  - [ ] Fatti scrivere un messaggio: sull'icona dell'app compare un numero rosso; nello stile Elenco, sotto il nome c'è l'anteprima.
  - [ ] Tieni premuta quell'app → «Cancella la notifica»: il numero sparisce (e la notifica anche da Android).
  - [ ] Avvia della musica: in alto compare la pillola con titolo e copertina; toccala: precedente/pausa/successiva funzionano. In pausa la pillola sparisce.
  - [ ] «+» → Musica: il widget mostra la canzone, anche in pausa, e i comandi.
  - [ ] Togli l'accesso da Android: numeri e musica spariscono, l'app non si blocca.
- [ ] **Stile Ibrido** (Personalizza → Stile della Home): in orizzontale preferiti a sinistra e icone a destra; in verticale solo l'elenco.
- [ ] **Vetro:** il dock, i widget e le cartelle sembrano vetro. Con il livello Prestazioni diventano più pieni, e la Home non scatta.
- [ ] **Impostazioni con barra laterale:** apri Impostazioni di NOVA. A sinistra le categorie con la pastiglia colorata, a destra il dettaglio. Toccare una categoria cambia il dettaglio e la voce scelta si evidenzia.
- [ ] Personalizza la Home usa la stessa barra laterale; «‹ Fatto» in alto a sinistra esce. Ogni scelta si vede ancora subito.
- [ ] In una finestra stretta (o a schermo diviso) le Impostazioni diventano un elenco; toccando una voce si apre il dettaglio e Indietro torna all'elenco.

## Strumenti di sistema (Fase 2)
Serve Shizuku attivo. Dopo ogni prova puoi usare Impostazioni → Strumenti di sistema → «Annulla tutte le modifiche».
- [ ] Impostazioni → **Prestazioni**: senza Shizuku dice «Serve Shizuku attivo» e apre Shizuku.
- [ ] **Turbo** (anche dal riquadro nel centro di controllo): dice quante app ha chiuso; preferiti, musica in corso e tastiera restano; la grafica passa a Prestazioni.
- [ ] **Animazioni** «Spente» / «Veloci»: aprendo app e menu di Android si nota la differenza; «Normali» le rimette.
- [ ] **Dimensione degli elementi** «Grandi»: lo schermo cambia e compare «Mantieni questa dimensione?» con il conto alla rovescia.
  - [ ] Non toccare niente: dopo 15 secondi torna com'era.
  - [ ] Riprova e tocca «Mantieni»: resta.
  - [ ] Riprova, chiudi NOVA dalle app recenti prima dei 15 secondi, e riaprila: la dimensione è tornata normale.
- [ ] **Blocco pubblicità** «Sì»: Impostazioni di Android → Rete → DNS privato mostra dns.adguard.com; «No» rimette quello di prima. Se un sito non si apre, spegni.
- [ ] **Congelamento:** scegli un'app poco importante: sparisce dalla Home; in Prestazioni compare tra le congelate; «Tocca per riattivare» la rimette. Prova a congelare le Impostazioni di Android: non è nell'elenco.
- [ ] **Stato del sistema** (Impostazioni → Stato del sistema): memoria, spazio, batteria con salute, temperatura e cicli.

## Personalizzazione della Home
- [ ] Impostazioni → Home → **Personalizza la Home**. Prova le opzioni e torna alla Home: devono essersi applicate.
  - [ ] Icone nascoste: restano solo i nomi.
  - [ ] Testo grande e carattere Elegante.
  - [ ] Ora e data nascoste.
  - [ ] Alfabeto a sinistra.
  - [ ] Sfumatura Forte, poi Leggera.
  - [ ] Sfocatura No.
- [ ] Nella Home, tieni premuta un'app in "Tutte le app": compare il menu.
  - [ ] "Rinomina" → nuovo nome → "Salva il nome": l'app compare col nuovo nome, sotto la nuova lettera.
  - [ ] "Nascondi": l'app sparisce. In Personalizza → App nascoste toccala: torna.
  - [ ] "Informazioni sull'app" apre la scheda di Android.
  - [ ] "Disinstalla" chiede conferma ad Android.
- [ ] Tieni premuto un preferito e trascinalo su o giù:
  - [ ] la riga si solleva, le altre si spostano e senti un tic a ogni scambio;
  - [ ] lasciandola l'ordine resta, anche dopo aver riavviato il tablet;
  - [ ] tenendo premuto senza muoverti si apre il menu, e non si apre l'app.

## Studio della Home (temi, sfondo, icone, gesti)
- [ ] **App recenti:** apri un'app, torna alla Home, premi il tasto quadrato più volte. Dietro alla Home non deve mai comparire l'app aperta prima.
- [ ] **Temi:** applica Aurora, Carta, Tramonto, Essenziale e Notte. Ognuno cambia insieme sfondo, colori, testo e icone.
- [ ] **Salva il tema in un file**, poi cambia qualcosa e **Apri un tema da un file**: torna com'era. Le app nascoste restano nascoste.
- [ ] **Sfondo:**
  - [ ] "Il mio" con "Sfoca il tuo sfondo": concedi il permesso e torna alla Home; lo sfondo è molto sfocato.
  - [ ] "Foto": scegli un'immagine; appare sfocata.
  - [ ] Cambia la sfocatura da No a Massima.
  - [ ] "NOVA" e "Colore" funzionano.
- [ ] **Colori:** tema Chiaro (testo scuro, barra di stato scura) e Automatico (segue il tema di Android). L'accento colora la bolla dell'alfabeto e i puntini dei preferiti.
- [ ] **Testo e orologio:** carattere, spessore e i tre stili dell'orologio; la data si può nascondere.
- [ ] **Icone:**
  - [ ] le forme e le icone monocromatiche;
  - [ ] con un pacchetto di icone installato dal Play Store, sceglilo e controlla che le icone cambino;
  - [ ] "Nessun pacchetto" torna alle icone originali.
- [ ] **Gesti:**
  - [ ] scorri giù sulla Home e si aprono le notifiche;
  - [ ] con Shizuku attivo e "Doppio tocco" acceso, due tocchi rapidi sull'alfabeto spengono lo schermo;
  - [ ] barra di stato nascosta: riappare scorrendo dall'alto.

## Procedura guidata (Fase 0, punto 12)
- [ ] Al primo avvio dopo l'aggiornamento NOVA apre "Benvenuto in NOVA". Tocca "Iniziamo".
- [ ] **NOVA come Home:** "Scegli NOVA come Home" apre la scelta di Android; tornando, il passo mostra "Fatto".
- [ ] **Strumenti di sistema:** il pulsante apre Shizuku o chiede il permesso; con Shizuku pronto compare "Fatto".
- [ ] **Aggiornamenti:** incolla il token e "Salva il token". Con un token sbagliato compare l'errore; con quello giusto compare "Fatto". "Come si crea il token" apre GitHub nel browser.
- [ ] **Prestazioni:** "Misura le prestazioni" fa la misura e mostra il resoconto; con "Fatto" si torna alla procedura.
- [ ] **Aspetto:** scegli un tema; la Home lo usa subito.
- [ ] **Tutto pronto:** elenca i passi rimasti; "Vai alla Home" chiude e apre la Home. Riaprendo NOVA, la procedura non ricompare.
- [ ] Impostazioni → Strumenti di sistema → **Configurazione guidata** la riapre.

## Migliora e Ripara (Fase 0, punto 9)
- [ ] Impostazioni → **Migliora**: scrivi "cambia il colore d'accento in viola" e "Invia a Claude Code". Si apre Claude Code (app o sito) con la richiesta già scritta e il repository nova scelto. Tocca Invia.
- [ ] Dopo qualche minuto arriva l'aggiornamento: la configurazione in silenzio, oppure una nuova versione.
- [ ] Impostazioni → **Ripara**:
  - [ ] "Guarda il report": non ci devono essere email, numeri di telefono o token;
  - [ ] invia: con il token compare "Report caricato in reports/" e Claude Code si apre con "Leggi reports/…";
  - [ ] senza token compare il messaggio sugli appunti.
