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

