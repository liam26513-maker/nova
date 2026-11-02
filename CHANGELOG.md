# Novità di NOVA

Ogni voce racconta in parole semplici cosa è cambiato.

## Non rilasciato

### Tastiera di NOVA: base inserita (primo passo, ancora da pubblicare)
- Richiesta: "continua con i restanti passaggi del piano ... sottolineo l'ottimizzazione della tastiera per renderla più veloce e funzionale".
- **Cosa c'è ora:** nella cartella `keyboard/` è entrato il codice di **HeliBoard** (tastiera open source, licenza GPL-3.0, derivata da quella di Android), come prevede il piano (SPEC §10.1), con nome **«Tastiera NOVA»** e pacchetto `it.manlio.nova.keyboard`. La licenza e le modifiche sono documentate in `docs/LICENZE.md` e `keyboard/UPSTREAM.md`.
- **Più leggera e veloce da installare:** solo i processori dei tablet (ARM a 32 e 64 bit) e solo i dizionari di **italiano e inglese**; meno peso, meno da caricare.
- **Privacy:** nessun permesso Internet, controllato in automatico sul manifest finale a ogni modifica.
- **Non è ancora installabile da NOVA:** per ora la CI controlla che la tastiera si compili e rispetti peso e permessi. Pubblicarla nelle release e installarla/aggiornarla dall'app è il passo successivo. Mancano ancora le funzioni di §10 (aspetto iOS e tema di NOVA, tastiera divisa e flottante, trackpad sulla barra spaziatrice, espansioni di testo, scrittura a mano, ecc.).
- **Velocità:** non l'ho ancora misurata sul tablet, quindi non prometto numeri: le ottimizzazioni vere si faranno confrontando prima e dopo.

### Tendina sopra le altre app: due cause per cui spariva subito, e un tasto di scorta
- Richiesta: "Il centro di controllo non funziona in altre app".
- **Causa 1:** quando Android apre la sua tendina insieme alla nostra, il sistema segnala un «cambio di finestra» da SystemUI. NOVA lo scambiava per «è arrivata un'altra app davanti» e richiudeva la propria tendina un attimo dopo averla aperta (e, avendo anche richiuso quella di Android, non si vedeva niente). **Ora** quel segnale viene ignorato.
- **Causa 2:** quando Android riconosce a sua volta il gesto dalla barra di stato, di solito **annulla** il tocco alle altre finestre. NOVA lo trattava come un rilascio e richiudeva. **Ora**, se il tocco viene annullato mentre il dito stava scendendo, la tendina si **apre comunque**.
- **Tasto di scorta, senza gesto:** NOVA chiede ad Android il **pulsante di accessibilità**. In Impostazioni di Android → Accessibilità → «Centro di controllo di NOVA», scegli come scorciatoia il **Pulsante di accessibilità** (compare un tasto tondo, o nella barra di navigazione, che puoi spostare): un tocco apre e chiude la tendina in qualunque app, anche nei giochi.
- **Per capire cosa succede:** il servizio ora scrive nel registro interno cosa fa (striscia creata o no, gesto riconosciuto, tocco annullato dal sistema, pulsante usato). Esce soltanto con «Ripara», filtrato, e solo se lo invii tu.

### Sblocco in un tocco del centro di controllo sopra le altre app (con Shizuku)
- Richiesta: "Non me la fa attivare e mi dice adesso con limitazioni" / "Fai il pulsante con Shizuku non farmi rifare questo passaggio ogni volta".
- **Il problema:** Android blocca come «impostazione con restrizioni» l'accensione del servizio di accessibilità di un'app installata a mano.
- **Ora, con Shizuku attivo:** Impostazioni di NOVA → Home → **«Centro di controllo sopra le altre app»** (un tocco): NOVA toglie da sola il blocco (`appops set … ACCESS_RESTRICTED_SETTINGS allow`, solo per sé stessa), te lo conferma con un avviso e ti porta alle impostazioni di accessibilità, dove ti resta solo da accendere «Centro di controllo di NOVA». Niente più giro dai tre puntini, e puoi ripeterlo quando vuoi (dopo un aggiornamento, se serve).
- **Se non riesce** (o senza Shizuku): lo dice e indica la strada a mano (App → NOVA → ⋮ → «Consenti impostazioni con restrizioni»).
- **Sicurezza:** comando fisso e tipizzato in `core/system`; il pacchetto deve essere **esattamente quello di NOVA** (qualunque altra app è rifiutata); la modifica è registrata e si annulla con «Annulla tutte le modifiche» (che rimette il blocco: basta toccare di nuovo la riga).

### Corretto: l'app si chiudeva dopo aver tirato giù la tendina sopra le altre app
- Richiesta: "Mi dice che si continua ad interrompere l'app nova".
- **Causa:** nella versione 0.1.280 la tendina sopra le altre app, appena lasciavi il dito, partiva con un'animazione che ha bisogno di un «orologio dei fotogrammi». Il servizio non ne aveva uno (la Home sì): Android lo considerava un errore e chiudeva NOVA a ogni gesto.
- **Ora:** il servizio ha il suo orologio agganciato allo schermo, e in più la tendina, se mai si trovasse senza orologio, arriva in fondo di colpo invece di far chiudere l'app.

### Tendina: solo la nostra, che segue il dito, con funzioni che si attivano subito
- Richiesta: "1. Quando tiro giu la tendina di nova si tira giu anche quella di android, non va bene, quella di android non ci deve essere. 2. La sensazione che da non va bene e' troppo a molla, fa fatica a scendere. 3. Non ci sono tutte le funzionalità che ci sono in quella di android. 4. Non C'è fluidità e se clicco qualche funzione non si attiva subito dalla tendina di nova e vorrei che fosse cosi".
- **1. Quella di Android non deve esserci.** Quando NOVA apre la sua tendina, chiede ad Android di richiudere la sua (ripetutamente mentre il dito scende e subito dopo il rilascio), quindi non resta visibile. Sulla Home, **scorrere giù da qualsiasi punto apre la tendina di NOVA** e non più le notifiche di Android (la voce in Personalizza la Home ora si chiama «Scorri giù per la tendina»). Le notifiche di Android restano raggiungibili solo da «Altro» → «Notifiche di Android».
- **2. Meno molla, più dito.** Sopra le altre app la tendina ora **segue il dito** durante il tiro (prima si apriva da sola con una molla dopo il riconoscimento del gesto). Ho tolto la zona morta all'inizio (il pannello compariva solo dopo circa 75 dp di tiro) e reso la molla di assestamento più secca e senza rimbalzo.
- **3 e 4. Funzioni vere, subito.** Con **Shizuku attivo**:
  - **Wi-Fi, Bluetooth e Non disturbare** (i riquadri) si accendono e si spengono **con un tocco, senza uscire dalla tendina** e si colorano subito; se Android non obbedisce, tornano allo stato vero.
  - Sotto «Altro» ora cambiano subito, con «Sì/No» a lato: **Rotazione automatica, Modalità aereo, Posizione, Risparmio energetico, Risparmio dati, Tema scuro, Luce notturna, Inversione dei colori.**
  - Le altre (Hotspot, NFC, Trasmetti schermo, Suoni, Schermo, Correzione colori, Accessibilità, Sveglie) aprono la schermata giusta di Android. «Impostazioni rapide di Android» resta come rete di sicurezza per tutto il resto.
  - **Senza Shizuku** tutto resta com'era: i riquadri e le voci aprono la schermata di Android (un'app normale non può cambiarle da sola).
- **Sicurezza e annullamento:** queste modifiche passano da `core/system` come comandi fissi e tipizzati (nessun testo arriva alla shell), vengono **registrate con lo stato di prima** e si annullano da Impostazioni → Strumenti di sistema → «Annulla tutte le modifiche» (anche dalla modalità provvisoria). Se cambi lo stesso interruttore più volte, resta una sola voce con lo stato originale.
- Nuovo permesso normale `ACCESS_WIFI_STATE` (solo per sapere se il Wi-Fi è acceso; non dà accesso a Internet e non c'è nessuna richiesta).

### Tendina sopra le altre app: la striscia ora copre davvero la barra di stato
- Richiesta: "Ho aggiornato ma rimane sempre quella di android".
- **Causa:** da Android 11 una finestra sopra le app, se non lo dichiara, si ferma **sotto** la barra di stato. La striscia di NOVA stava un po' più in basso: tirando giù dalla barra il tocco andava ad Android, che apriva la sua tendina.
- **Ora:** la striscia (e il pannello) dichiarano di poter coprire la barra di stato e il bordo dello schermo, quindi il tocco sulla barra arriva a NOVA.
- Se NOVA non riesce a creare la striscia, ora lo dice con un avviso («Spegni e riaccendi…») invece di non fare niente.

### Tendina sopra le altre app: ora risponde sempre, anche nei giochi
- Richiesta: "Il servizio è attivo ma non succede niente. Il centro di controllo mi serve accessibile anche nei giochi ovviamente."
- **Causa probabile:** la striscia che prende il gesto si spegneva da sola quando credeva che la barra di stato fosse nascosta; sul tuo tablet lo credeva sempre, quindi il servizio era «Attivo» ma il gesto non arrivava mai.
- **Ora:** la striscia è **sempre attiva**, anche a schermo intero. Nei giochi basta tirare giù dal bordo in alto. Il prezzo: nella fascia in alto (circa 24 dp) il tocco va a NOVA invece che al gioco; se un gioco ha lì un tasto importante, usa quello più in basso o spegni il servizio.

### Scegli i riquadri a prima vista della tendina
- Richiesta: "Fai scegliere i riquadri a prima vista da Personalizza la Home".
- **Dove:** Impostazioni di NOVA → Home → Personalizza → **«Centro di controllo»**: un elenco con tutti i riquadri (Wi-Fi e rete, Bluetooth, Non disturbare, Torcia, Livello grafico, Turbo, Modifica la Home, Impostazioni di NOVA). Spunta quelli che vuoi **subito sotto il dito**; tutti gli altri finiscono sotto **«Altro»**. Vale sia sulla Home sia sopra le altre app e si applica subito.
- Predefiniti come prima: Wi-Fi, Bluetooth, Non disturbare, Torcia. Puoi anche toglierli tutti: a prima vista restano solo volume e luminosità.
- L'ordine è quello della lista (non si riordina ancora). Un riquadro non disponibile (torcia senza flash, Turbo senza Shizuku) non compare né a prima vista né sotto «Altro».
- Il tema condiviso/importato non cambia questa scelta, come per la disposizione.

### Tendina che sostituisce quella di Android, minimale, con «Altro»
- Richiesta: "Fai la due, ma la tendina deve contenere tutte le impostazioni che ha quella attuale di android, ma nascoste perché deve avere un aspetto minimalista e elegante ma sempre corrente con il tema, anche con uno personalizzabile, a prima vista poi le restanti impostazioni si scoprono con una sezione "altro" sempre nella tendina oltre ad avere quelle nuove".
- **Sostituisce quella di Android:** tirando giù da **qualsiasi punto della barra di stato** (non più solo dall'angolo) si apre la tendina di NOVA, in tutte le app. La striscia è sempre attiva, anche a barra di stato nascosta (giochi, video a tutto schermo).
- **A prima vista, solo l'essenziale:** Wi-Fi, Bluetooth, Non disturbare, Torcia, poi volume e luminosità, e una scritta **«Altro ⌄»**.
- **Sotto «Altro»:** prima i comandi nuovi di NOVA (livello grafico, Turbo, modifica Home, impostazioni di NOVA), poi le impostazioni di Android: **Notifiche di Android, Impostazioni rapide di Android, Rotazione automatica** (si cambia lì, con il permesso della luminosità), Modalità aereo, Hotspot, Posizione, Risparmio energetico, Risparmio dati, Schermo e tema scuro, Luce notturna, NFC, Trasmetti schermo, Suoni, Inversione e Correzione dei colori, Accessibilità, Sveglie. La lista scorre.
- **Perché le "Notifiche di Android" e le "Impostazioni rapide di Android" sono lì:** Android non lascia a un'app cambiare da sola quasi nessun interruttore (Wi-Fi, aereo, hotspot, posizione…): NOVA apre la schermata giusta. Per i riquadri che NOVA non ha (registra schermo, scanner QR, portafoglio, dispositivi domestici…) la riga «Impostazioni rapide di Android» apre il pannello originale: **nessuna impostazione va persa**, e le notifiche restano sempre raggiungibili anche se la barra di stato ora apre NOVA.
- **Segue il tema, anche personalizzato:** la scheda prende un velo dell'accento del tema (e quindi dello sfondo o del colore che scegli per la Home), con testo e simboli del tema chiaro/scuro; al livello grafico «Prestazioni» resta piatta e leggera.
- Una voce che porta fuori da NOVA (impostazioni di Android, notifiche) chiude la tendina; un interruttore come la Rotazione resta aperta e aggiorna «Sì/No».
- **Non fatto:** scegliere quali riquadri stanno a prima vista (oggi sono fissi); spegnere Wi-Fi, aereo ecc. con un tocco (Android non lo permette a un'app normale).

### Centro di controllo anche sopra le altre app
- Richiesta: "Fai comparire il centro di controllo anche sopra le altre app".
- **Come si accende (una volta, a mano):** Impostazioni di NOVA → Home → **«Centro di controllo sopra le altre app»** apre l'accessibilità di Android; scegli **«Centro di controllo di NOVA»** e accendilo. Se Android lo nega ("impostazione con restrizioni"): Impostazioni di Android → App → NOVA → ⋮ → «Consenti impostazioni con restrizioni», poi riprova.
- **Come si usa:** in qualunque app **tira giù dall'angolo in alto a destra**, sopra la barra di stato: compare lo stesso pannello della Home (Wi-Fi, Bluetooth, torcia, Turbo, volume, luminosità…). Si chiude tirando su, toccando fuori o con Indietro; si chiude da solo se cambi app o torni alla Home. (Aggiornato nella voce sopra: ora vale per tutta la barra di stato.)
- **Cosa fa e cosa no:** il servizio **non legge lo schermo** e non tocca niente al posto tuo; disegna solo due finestre di NOVA (una striscia invisibile nell'angolo e il pannello). Da spento non esiste e non consuma nulla. A **schermo bloccato non si apre**. Sulla Home di NOVA la striscia si spegne, perché lì c'è già il gesto dentro la Home.
- **Differenza dalla Home:** sopra le altre app il pannello si apre con una molla appena riconosci il gesto, ma **non segue il dito durante il tiro** (una finestra non può passare il tocco a un'altra). Il riquadro «Modifica la Home» non c'è.
- **Attenzione, giochi e app a tutto schermo:** la striscia è alta quanto la barra di stato (almeno 24 dp); a barra nascosta si disattiva da sola (vedi la voce sopra).

### Centro di controllo a tendina (come sull'iPad) e cursori che seguono il dito
- Richiesta: "il centro di controllo deve sostituire quello del sistema originale sempre a tendina non a pulsante come e' adesso inoltre lo scorrimento del volume e della luminosità deve seguire la linea orizzontale come in ogni centro di controllo. fallo piu fluido e veloce, osserva sempre quello di ios."
- **A tendina, non più a pulsante:** il pulsante con i quattro riquadri è sparito. Sulla Home **tira giù dall'angolo in alto a destra**: il pannello scende **seguendo il dito** (come sull'iPad) e, se lasci a metà, si completa da solo; uno scatto veloce basta anche a poca distanza. Si chiude **tirando su**, toccando fuori o con Indietro. Tirando giù da qualsiasi altro punto della Home continuano ad aprirsi le notifiche, come prima. «Cerca» → «controllo» o «torcia» lo apre ancora.
- **Cursori di volume e luminosità:** ora il riempimento **segue il dito lungo la linea orizzontale**, subito dal primo tocco (prima doveva superare una piccola soglia e a volte saltava). Muovere il dito in su o in giù mentre scorri **non conta e non chiude il pannello**; il dito può anche uscire dalla barra e il cursore lo segue. Mentre scorri la barra si ingrossa un poco. Il volume si aggancia ai suoi gradini; la luminosità è continua.
- **Più fluido e veloce:** il pannello non pesa più sulla Home (da chiuso non esiste nemmeno); durante il gesto non si ricompone nulla, si ridisegna solo; i cursori scrivono su Android al massimo ~25 volte al secondo (e sempre il valore finale quando stacchi il dito), così il dito non aspetta mai il sistema. Wi-Fi, Bluetooth e Non disturbare, che portano alle impostazioni di Android, ora chiudono la tendina, così al ritorno trovi la Home pulita.
- **Accessibilità:** per TalkBack e comandi vocali resta una voce invisibile «Centro di controllo» che apre la tendina senza il gesto.
- **Una cosa che Android non permette (e cosa abbiamo fatto):** un'app normale **non può sostituire** la tendina di sistema (quella che scende dalla barra di stato) né comparire sopra le altre app senza un servizio speciale di accessibilità. Per questo oggi la sostituzione vale **sulla Home di NOVA**: l'angolo in alto a destra apre il nostro centro di controllo, e quello di Android resta raggiungibile dalla barra di stato. Farlo comparire anche dentro le altre app richiede il servizio di accessibilità e il permesso «Mostra sopra altre app» (SPEC §2.2): è il passo successivo, da decidere insieme perché sono permessi delicati.

### Aggiornamento dell'app: risolta (alla radice) l'installazione che falliva con Shizuku
- Richiesta: "Non è possibile che ogni volta che provo a fare un aggiornamento da sempre lo stesso problema, risolvi" (errore: «Failed to parse APK file … nova-210.apk»).
- **Causa vera:** NOVA scarica e controlla l'aggiornamento, ma poi chiedeva a Shizuku di installarlo da un percorso. Shizuku lavora con l'utente «shell» di Android, che **non può leggere la cartella privata di NOVA**: per questo non riusciva nemmeno ad aprire il file. La correzione precedente (sul percorso permesso) non poteva bastare.
- **Ora:** NOVA apre il file lei stessa e lo **passa a Shizuku in streaming** (come fanno gli installatori di app per Shizuku), quindi non serve più che la shell legga niente di privato. Il file resta controllato (impronta, firma, versione) prima.
- **E se qualcosa va storto,** NOVA non si blocca più: ripiega da sola sull'installazione normale di Android, con la conferma a schermo.
- **Per questa volta** serve installare a mano la nuova versione (la tua app attuale ha ancora il vecchio metodo): da GitHub, sezione release, `nova-<numero>.apk`. Dalla successiva in poi gli aggiornamenti dentro NOVA funzionano da soli.

### Strumenti di sistema con Shizuku: Turbo, animazioni, dimensione dello schermo, pubblicità, congelamento, stato (Fase 2)
- Richiesta: dal piano concordato, blocco C («strumenti di sistema»).
- **Dove:** Impostazioni → **Prestazioni** (serve Shizuku attivo; se non lo è, la voce ti porta a Shizuku). Tutte le modifiche sono **registrate e reversibili**: Impostazioni → Strumenti di sistema → «Annulla tutte le modifiche» le rimette com'erano, anche dalla modalità provvisoria.
- **Turbo** (anche dal centro di controllo, nel riquadro «Turbo»): chiude le app in secondo piano e porta la grafica su Prestazioni per 10 minuti. Restano aperte i preferiti, la musica in corso, la tastiera e la Home; le app chiuse si riaprono quando servono.
- **Velocità delle animazioni** di Android: Normali, Veloci, Molto veloci, Spente.
- **Dimensione degli elementi dello schermo** (Piccoli, Normali, Grandi, Molto grandi): la nuova misura si **prova per 15 secondi** e, se non la confermi, **torna da sola com'era**; torna da sola anche se NOVA si chiude a metà o riparti il tablet.
- **Blocco pubblicità e tracciatori:** DNS privato di Android con AdGuard (gratuito). Spento, rimette quello che c'era prima.
- **Congelamento:** scegli un'app da congelare (non gira e sparisce finché non la riattivi); le app di sistema essenziali non si possono congelare mai.
- **Stato del tablet** (Impostazioni → Stato del sistema): memoria, spazio, batteria (percentuale, salute, temperatura, cicli di carica). Non serve nessun permesso.
- Non ci sono ancora: risparmio energetico intelligente automatico, debloat con elenco di app consigliate, risoluzione (solo densità).

### Notifiche sulle icone, isola dinamica e widget Musica (Fase 1, blocco B)
- Richiesta: "Continua con il blocco B non appena la nuova versione è pronta" (dal piano: notifiche in linea, isola dinamica, widget).
- **Prima di tutto, un passo tuo:** Impostazioni → Home → **«Notifiche e musica sulla Home»** → consenti a NOVA l'accesso alle notifiche. È un permesso speciale di Android, non lo si può dare da dentro l'app. Se Android lo nega ("impostazione con restrizioni"), vai su Impostazioni di Android → App → NOVA → ⋮ → «Consenti impostazioni con restrizioni», poi riprova.
- **Notifiche sulle icone:** un numero rosso sull'icona (griglia e dock) con quante notifiche ci sono. Nello stile Elenco, sotto il nome dell'app compare l'ultima («Anna: Ci sei stasera?»). Tenendo premuta l'app, «Cancella la notifica».
- **Isola dinamica:** una pillola in alto al centro della Home che compare **solo mentre suona della musica**: titolo e copertina; toccandola si allarga con precedente, pausa e successiva. È una parte della Home, non resta sopra le altre app.
- **Widget Musica** («+» → Musica): copertina, titolo, artista e comandi; funziona anche in pausa, per riprendere.
- **Privacy:** le notifiche restano **solo in memoria**, non vengono salvate, registrate né inviate. Quelle di servizio (in corso, riassunti, comandi della musica) non vengono contate.
- Non ci sono ancora: rispondere a un messaggio dall'icona e il «segna come letto».

### Meteo, Pomodoro, centro di controllo e calcolatrice (Fase 1, primo blocco del resto del piano)
- Richiesta: "Inizia con il resto del piano. E le cose con i limiti trova un modo per togliere questi limiti e hai i miei consensi".
- **Meteo** (widget «+» → Meteo): scrivi il nome della città, vedi temperatura, minima e massima, un simbolo del tempo. Il limite che c'era (servivano Internet e la posizione) è tolto così: **niente posizione**, solo il nome della città; i dati vengono da Open-Meteo, gratuito e senza account. Si aggiorna al massimo ogni 30 minuti; senza rete mostra l'ultimo tempo conosciuto.
- **Pomodoro** (widget «+» → Pomodoro): 25 minuti di concentrazione e 5 di pausa, con anello, avvio/pausa e azzeramento. Il timer sopravvive al riavvio. Il conto ogni secondo c'è solo mentre il timer va e la Home è visibile.
- **Centro di controllo** (pulsante con i quattro riquadri, accanto alle impostazioni; oppure scrivi «torcia» o «controllo» in Cerca):
  - **Torcia, volume e luminosità** agiscono subito. Per la luminosità Android chiede un permesso (una voce chiara nel pannello: «Concedi il permesso per regolare la luminosità»).
  - **Wi-Fi, Bluetooth e non disturbare** aprono il pannello giusto di Android: un'app normale non può cambiarli da sola.
  - **Livello grafico** di NOVA a rotazione: Automatico, Massimo, Bilanciato, Prestazioni.
  - Scorciatoie a «Modifica la Home» e alle Impostazioni di NOVA.
- **Cerca** ora fa anche i conti e le conversioni: «12*(3+4)», «15% di 80», «10 km in mi», «100 c in f». Tocchi il risultato per copiarlo. Scrivendo «widget» o «modifica» trovi «Modifica la Home».
- Le schede (centro di controllo, cartelle, widget) ora le legge meglio TalkBack: ogni voce è un elemento a parte.

### Aggiornamento dell'app: risolto "percorso apk non consentito"
- Richiesta: "aggiornamento non riuscito: percorso apk non consentito: …/cache/updates/nova-190.apk".
- **Risolto:** con Shizuku attivo NOVA scaricava l'aggiornamento nella sua cartella `updates`, ma la regola di sicurezza che controlla dove può stare l'APK non la riconosceva e rifiutava l'installazione. Ora accetta quella cartella (e solo quella).
- La regola resta severa: solo la cartella privata di NOVA, nessuna altra sottocartella, nessun altro pacchetto.
- Per installare la 0.1.190 serve comunque una volta l'installazione a mano (vedi sotto); dalla versione successiva l'aggiornamento dentro NOVA funziona.

### Aggiornamenti pubblicati più in fretta
- Richiesta: "Sì, riduci il tempo di rilascio" (dopo aver chiesto perché la nuova versione ci metteva così tanto).
- Dopo ogni modifica unita al progetto, la nuova versione di NOVA arriva sul tablet in circa **4 minuti invece di 9**.
- Come: i test non si rifanno al momento del rilascio (sono già stati fatti prima di unire la modifica), e la copia della versione precedente per il ritorno immediato si prepara **insieme** alla nuova, non dopo.
- Non cambia niente nell'app e nel ritorno alla versione precedente. Le verifiche di firma e di peso restano.

### Home ancora più simile all'iPad: icone libere, cartelle, widget e ricerca
- Richiesta: "Falla piu simile a quella dell'ultimo modello di iPad in commercio, falla esattamente uguale a quella e togli tutti i limiti riscontati adesso".
- **Vetro.** Dock, pulsanti, widget, cartelle e schede hanno ora l'aspetto del vetro: più chiaro in alto, con un filo di luce sul bordo. Non pesa sul tablet (niente sfocatura in tempo reale); con il livello grafico Prestazioni diventa un riempimento pieno.
- **Modifica la Home.** Tieni premuto lo sfondo (o un'icona → «Modifica la Home»): le icone ondeggiano.
  - **Trascina** un'icona dove vuoi, anche su un'altra pagina (portala al bordo e aspetta un attimo).
  - Mettila **sopra un'altra icona** e nasce una **cartella** (anteprima 3×3); sopra una cartella ci entra.
  - **«−»** toglie un'icona dalla Home: resta in «Tutte le app» e la rimetti con «Aggiungi alla Home».
  - **«+»** aggiunge un widget, **Fine** esce (anche con Indietro).
  - Un'app trascinata sul dock diventa un preferito; nel dock le icone si riordinano trascinandole di lato.
- **Cartelle.** Si aprono con un tocco, hanno un nome che puoi cambiare; tenendo premuta un'app dentro c'è «Togli dalla cartella». Una cartella con una sola app si scioglie da sola.
- **Widget di NOVA:** Orologio, Calendario, Batteria e Nota rapida. Sono leggeri e si aggiornano solo mentre la Home è visibile.
- **Widget delle altre app** (quelli di Android): «+» → «Widget delle app». Android chiede un permesso la prima volta per ciascuno.
- **Cerca** (la capsula sopra il dock): scrivi e trovi le app (senza badare a maiuscole e accenti), le Impostazioni di NOVA e «Cerca sul web» (si apre il browser: NOVA non ha Internet). Senza testo c'è «Tutte le app».
- **Stile «Ibrido»** (Personalizza → Disposizione e gesti → Stile della Home): in orizzontale i preferiti a elenco a sinistra e la Home a icone a destra; in verticale resta l'elenco.
- Resta fuori, e te lo dico chiaro: **«esattamente uguale»**. Le regole del progetto vietano di copiare marchi, icone, caratteri e grafica di Apple (e quelle di Niagara). NOVA riprende il comportamento e l'aspetto generale, con grafica sua. Vedi la nota nella richiesta di modifica.
- Non c'è il Meteo (servirebbero Internet e la posizione, che NOVA non chiede).

### Interfaccia più simile a un iPad
- Richiesta: "rendi tutta l'interfaccia piu simile ad un iPad".
- **Home a icone, come sull'iPad.** Ora la Home mostra pagine di icone con i nomi sotto, da scorrere di lato, con i puntini della pagina. In basso c'è il **dock**: un pannello trasparente con i tuoi primi 6 preferiti. Sopra al dock trovi «Cerca app», che apre l'elenco di tutte le app con l'alfabeto a onda. Tenendo premuta un'icona (nella griglia o nel dock) compare il solito menu: preferiti, rinomina, nascondi.
- **Icone tutte morbide.** Le icone hanno la stessa forma arrotondata («Morbido») e sono più grandi. Se preferivi la forma originale la rimetti da Personalizza → Icone.
- **Impostazioni con la barra laterale.** A sinistra le categorie con una pastiglia colorata (Migliora, Livello grafico, Il tuo tablet, Home, Aggiornamenti, Strumenti, Stato), a destra il dettaglio, come nelle Impostazioni dell'iPad. Titoli grandi in grassetto. Con lo schermo stretto (telefono o finestra piccola) diventa un elenco che si apre sul dettaglio, con «‹» e Indietro per tornare.
- **Personalizza la Home** usa la stessa barra laterale (Temi, Sfondo, Colori, Testo e orologio, Icone, Disposizione e gesti, App nascoste) e ha una nuova scelta, **Stile della Home**: «Icone e dock» (nuovo) oppure «Elenco» (la Home di prima, in stile Niagara). I temi non cambiano lo stile che hai scelto.
- Non cambia nulla per i consumi: il dock è un semplice pannello traslucido (con il livello grafico Prestazioni diventa più pieno e senza bordo luminoso), senza sfocatura in tempo reale.
- Per ora le icone sono in ordine alfabetico: spostarle liberamente, le cartelle e i widget arrivano nella Fase 1.

### Migliora e Ripara (Fase 0, punto 9: Fase 0 completata)
- Richiesta: "Per adesso continua con i passaggi successivi".
- **Impostazioni → Migliora:** scrivi cosa vuoi con parole tue. NOVA apre Claude Code con la richiesta già scritta, completa dei dettagli tecnici: tu tocchi solo Invia.
- **Impostazioni → Ripara:** NOVA prepara un report del problema (crash, eventi, versioni). Prima di uscire dal tablet lo rende **anonimo** (niente email, numeri, token) e te lo fa vedere. Poi lo salva nel tuo repository e chiede a Claude Code di correggere il problema e aggiungere un test.

### Configurazione guidata al primo avvio (Fase 0, punto 12)
- Richiesta: "continua con i restanti passaggi".
- Alla prima apertura NOVA ti accompagna in pochi passi:
  - sceglierla come Home;
  - attivare Shizuku;
  - inserire il token per gli aggiornamenti;
  - misurare le prestazioni;
  - scegliere un tema.
- Ogni passo si può saltare. Alla fine vedi cosa resta da fare.
- Si rifà quando vuoi da Impostazioni → Strumenti di sistema → Configurazione guidata.

### App recenti senza "fantasmi" e studio di personalizzazione completo
- Richiesta: "Quando si va nella schermata delle app aperte … ogni tanto si vede sullo sfondo l'app aperta all'ultimo, non va bene sostituisci e risolvi con lo sfondo ma più sfocato dietro. Poi aggiungi la customizzazione dello sfondo, del tema, delle icone, di tutto. Come niagara launcher".
- **Risolto:** nelle app recenti dietro alla Home non si vede più l'app aperta prima. Ora è NOVA a disegnare lo sfondo, sfocato quanto vuoi, fino a "Massima".
- **Temi pronti**, da applicare con un tocco e poi ritoccare: Notte, Aurora, Carta, Tramonto, Essenziale. Puoi anche salvare il tuo tema in un file e riaprirlo.
- **Sfondo:** il tuo (sfocato se consenti a NOVA di leggerlo), una foto della galleria, una delle sfumature di NOVA o un colore; sfocatura regolabile.
- **Colori:** tema scuro, chiaro o automatico; colore d'accento preso dallo sfondo o scelto da te.
- **Testo e orologio:** carattere, misura e spessore; orologio sottile, marcato o su due righe; data sì o no.
- **Icone:** misura, forma, icone monocromatiche e **pacchetti di icone** scaricati dal Play Store.
- **Gesti:** scorri giù per le notifiche, doppio tocco sull'alfabeto per spegnere lo schermo (con Shizuku), barra di stato nascondibile.

### Personalizza la Home come in Niagara
- Richiesta: "Va bene aggiungi la personalizzazione migliore di niagara launcher".
- **Impostazioni → Home → Personalizza la Home:**
  - icone sì o no;
  - misura del testo;
  - carattere (Moderno, Elegante, Tecnico);
  - ora e data;
  - sfumatura leggera, media o forte;
  - sfocatura sì o no;
  - alfabeto a destra o a sinistra;
  - elenco delle app nascoste.
- **Tenendo premuta un'app** si apre un menu: preferiti, rinomina, nascondi, informazioni, disinstalla.
- **Riordino dei preferiti trascinando**, con le righe che si spostano morbide.

### Home rifatta: più fluida, in stile Niagara
- Richiesta: "E' brutta, fallo più dinamico e fluido con lo sfondo sfumato opaco piu simile a niagara launcher".
- **Sfondo:** si vede il tuo sfondo, coperto da una sfumatura scura e opaca che prende il suo colore. Se il tablet lo supporta, lo sfondo è anche sfocato.
- **Niente più riquadri:** ora grande e i preferiti come lista di nomi grandi, direttamente sullo sfondo.
- **Alfabeto a onda:** trascinando il dito le lettere si gonfiano e accanto al dito compare una bolla con la lettera. Si apre "Tutte le app" esattamente lì.
- **Animazioni a molla ovunque:** ingresso a cascata, rimbalzo leggero al tocco, passaggio morbido tra Preferiti e Tutte le app, app che si aprono dal punto toccato.

### NOVA diventa la Home del tablet (Fase 0, punto 11)
- Richiesta: "Va bene continua con i passaggi successivi".
- **Nuova Home:** a sinistra tutte le app in ordine alfabetico, con l'alfabeto sul bordo per saltare a una lettera trascinando il dito; a destra ora, data e i tuoi **Preferiti**.
- **Preferiti:** tieni premuta un'app per aggiungerla o toglierla.
- **Impostazioni → Home:** mostra quale app fa da Home e permette di scegliere NOVA, o di tornare al launcher di sistema quando vuoi.
- La Home consuma risorse solo quando è sullo schermo.

### Ritorno alla versione precedente in pochi secondi
- Richiesta: "10 minuti è troppo, fallo con un massimo di attesa di 1 minuto".
- **Versione appena precedente:** ogni nuova versione porta con sé, già pronta, quella di prima. Tornarci è immediato: si scarica e si installa in pochi secondi. Nella lista è segnata come "immediato".
- **Versioni più vecchie:** GitHub deve prepararle e ci mette 3–5 minuti. NOVA controlla ogni 30 secondi e la installa appena è pronta, senza i 10 minuti fissi di prima.
- Il ritorno immediato funziona dalla prossima versione in poi: la 0.1.102 non ha ancora con sé la versione precedente.

### Tornare a una versione precedente dalle Impostazioni
- Richiesta: "lo vorrei direttamente nelle impostazioni che si usera solo nei casi dove una versione succesiva non riesca a risolvere i possibili problemi della attuale".
- In **Impostazioni → Aggiornamenti** c'è ora l'ultima voce **"Torna a una versione precedente"**, pensata solo per le emergenze. Si sceglie la versione, si conferma, e dopo circa 10 minuti arriva sul tablet come un aggiornamento normale. Le impostazioni restano; quando arriva una correzione NOVA si aggiorna di nuovo da sola.
- Serve che il token GitHub abbia il permesso "Actions: Read and write" (è già nella guida).

### Versione ufficiale e aggiornamenti automatici (Fase 0, punto 10)
- **Versione ufficiale firmata:** ogni miglioramento unito al progetto diventa da solo una nuova versione di NOVA, firmata con la chiave definitiva, che il tablet scarica e installa.
- **Configurazione a caldo:** le modifiche ai soli colori, testi e moduli si pubblicano come aggiornamento leggero, applicato senza reinstallare.
- **Tornare indietro:** si può ripubblicare una versione precedente con un pulsante su GitHub; il tablet la installa sopra come un aggiornamento normale.
- **Controllo del peso:** se l'app supera il limite di dimensione stabilito, la versione non viene pubblicata.

### Fondamenta (Fase 0, punti 1–4)
- **Struttura del progetto:** l'app è divisa in parti indipendenti (aspetto grafico, configurazione, moduli) così ogni miglioramento tocca solo ciò che serve.
- **Aspetto grafico:** colori, angoli arrotondati "continui" stile iOS, animazioni a molla che si possono interrompere col dito e vibrazioni leggere.
- **Tre livelli grafici:** Massimo (sfocatura in tempo reale), Bilanciato (sfocatura calcolata una volta sola, quasi gratis) e Prestazioni. Il tablet scende da solo di livello se fatica e risale quando torna fluido; puoi anche fissarlo a mano.
- **Configurazione modificabile senza reinstallare:** temi, nome e moduli stanno in file in `config/`. Una configurazione sbagliata viene rifiutata e il tablet resta su quella precedente.
- **Moduli spegnibili:** ogni funzione futura sarà un modulo che si accende o si spegne; se un modulo va in crash 3 volte in 10 minuti viene sospeso da solo.
- **Impostazioni con uno stile calmo e minimale** — *"vorrei che lo sfondo delle impostazioni dell'inizio e di qualsiasi altra impostazione del sistema abbia un tema elegante e calmo e moderno e minimal"*: sfondo neutro con luci appena accennate, titoli leggeri, gruppi traslucidi, selettore segmentato. Tutte le schermate di sistema future (procedura guidata, stato, impostazioni) usano lo stesso stile.
- **Corretto il pulsante "Automatico"** — *"il tasto automatico per le prestazioni non va"*: ora si evidenzia quando è attivo, riporta al livello di partenza e la scritta sotto dice se il livello è "scelto automaticamente" o "fissato da te".

### Affidabilità e profilo del tablet (Fase 0, punti 5–6)
- **Modalità provvisoria:** se NOVA si chiude 3 volte di fila all'avvio, o 2 volte nel primo minuto dopo un aggiornamento, si apre una versione ridotta e sicura con lo stesso stile calmo. Da lì puoi riavviare NOVA, usare la home di sistema, vedere l'ultimo errore o aprire le tue app. Puoi anche entrarci a mano tenendo premuto **volume giù** nei primi secondi dopo l'apertura di NOVA.
- **Registro degli errori sul tablet:** ogni crash viene salvato con le ultime operazioni, per aiutare "Ripara". Non esce mai dal tablet da solo.
- **"Il tuo tablet" nelle Impostazioni:** processore, memoria e schermo letti dall'hardware reale. Con **Misura le prestazioni** (circa 12 secondi) NOVA prova i tre livelli grafici e sceglie da sola il livello massimo che resta fluido: Automatico non lo supera mai.
- **Corretti i livelli grafici e la misura** — *"quando si fa la misurazione delle prestazioni non ti da il resoconto e quando si cambiano le impostazioni non si può passare al massimo o al bilanciato poi non cambia niente"*:
  - ora puoi **sempre** scegliere a mano Massimo, Bilanciato o Prestazioni; il livello consigliato dalla misura vale solo per Automatico;
  - la misura usa lo stesso criterio di Google (un fotogramma è lento solo se arriva con più di un fotogramma di ritardo), mentre prima era così severa che ogni livello risultava "lento". Le misure fatte con la versione precedente vengono scartate: rifalla una volta;
  - dopo la misura compare il **resoconto**, con la fluidità di ogni livello e il livello consigliato. Si può riaprire dalle Impostazioni;
  - sotto il selettore c'è un'**anteprima dal vivo**: forme colorate che passano dietro un pannello di vetro, per vedere davvero la differenza fra i livelli, con una frase che la spiega.

### Strumenti di sistema e aggiornamenti (Fase 0, punti 7–8)
- **Shizuku:** NOVA può usare le funzioni di sistema senza root. Solo comandi di un elenco fisso e controllato, mai testo libero; app di sistema essenziali protette; **ogni modifica viene registrata e si annulla con un tocco**, anche dalla modalità provvisoria.
- **Aggiornamenti automatici dal tuo GitHub:** colleghi una volta il token (salvato cifrato) e NOVA controlla all'avvio e ogni 6 ore.
  - La **configurazione** si aggiorna da sola, senza reinstallare, e si può tornare indietro all'istante.
  - L'**app** si aggiorna da sola con Shizuku attivo, altrimenti con un tocco su "Installa".
  - Ogni file viene controllato: impronta e **stessa firma** dell'app installata. Prima di ogni cambio viene salvato un punto di ripristino.

