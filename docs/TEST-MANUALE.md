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
