# Novità di NOVA

Ogni voce racconta in parole semplici cosa è cambiato.

## Non rilasciato

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

