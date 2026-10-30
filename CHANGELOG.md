# Novità di NOVA

Ogni voce racconta in parole semplici cosa è cambiato.

## Non rilasciato

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

