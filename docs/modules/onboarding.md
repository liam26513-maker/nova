# Modulo `feature/onboarding`: procedura guidata (Fase 0, punto 12)

Al primo avvio NOVA si apre su una procedura in sette passi, nello stile calmo delle Impostazioni:
- un passo per pagina, che scivola con una molla;
- in basso Indietro, i puntini del progresso e Avanti;
- ogni passo si può saltare.

Si rifà quando vuoi da **Impostazioni → Strumenti di sistema → Configurazione guidata**.

| Passo | Cosa fa | Quando è "Fatto" |
|---|---|---|
| Benvenuto | Spiega cosa si sta per fare | — |
| NOVA come Home | Apre la scelta dell'app Home di Android | NOVA è la Home predefinita |
| Strumenti di sistema | Apre Shizuku o chiede il permesso; se Shizuku è spento spiega come accenderlo | Shizuku è pronto |
| Aggiornamenti automatici | Campo per il token, con il link alla pagina di GitHub per crearlo | Il token è salvato |
| Prestazioni | Avvia la misura; al termine compare il resoconto e poi si torna qui | C'è una misura |
| Aspetto | Sceglie uno dei temi pronti della Home | Un tema è scelto |
| Tutto pronto | Elenca i passi rimasti da fare; "Vai alla Home" chiude e apre la Home | — |

- **Modello:** `OnboardingStep`, `OnboardingStatus` (con `isDone` e `pending`, provati dai test) e `OnboardingActions`. Il modulo non dipende dal nucleo di sistema: l'app traduce lo stato reale in `app/OnboardingRoute`.
- **Stato del passo:** lo tiene `NovaRoot`, così sopravvive alla misura delle prestazioni, che apre un'altra schermata.
- **Completamento:** il segno di procedura completata è salvato in `files/onboarding.properties`.
