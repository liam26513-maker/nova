# Modulo `feature/home` — Home minima (Fase 0, punto 11)

La prima schermata del tablet quando NOVA è l'app Home predefinita. Versione minima ma curata; la home completa (layout commutabili, notifiche in linea, widget, dock, cartelle, alfabeto a onda completo) arriva nella Fase 1 (SPEC §6).

## Cosa fa
- **Stile** ispirato a Niagara, senza copiarne la grafica:
  - si vede lo **sfondo di sistema**, coperto da una sfumatura scura e opaca tinta col suo colore principale (`WallpaperColors`, letto senza permessi);
  - dove il dispositivo lo supporta (`isCrossWindowBlurEnabled`) e il livello grafico è Massimo o Bilanciato, Android **sfoca** anche lo sfondo (`setBackgroundBlurRadius`: 40 dp oppure 22 dp), e la sfumatura diventa più leggera;
  - con il livello Prestazioni, o senza supporto, resta solo la sfumatura.
- **Preferiti:** ora grande (88 sp, sottile) e data; sotto, i preferiti come lista di nomi grandi con l'icona; "Tutte le app ›" in fondo.
- **Tutte le app:** elenco con le intestazioni di lettera; "‹ Preferiti" in cima, oppure Indietro, per tornare.
- **Alfabeto a onda** sul bordo destro:
  - sotto il dito le lettere si gonfiano e sporgono con una curva continua (gaussiana, larga 52 dp);
  - accanto al dito una bolla mostra la lettera;
  - a ogni cambio di lettera c'è un tic aptico e l'elenco salta lì.

  Le animazioni leggono la posizione del dito solo in fase di disegno, quindi trascinare non ricompone l'interfaccia.
- **Movimento:**
  - i preferiti entrano a cascata;
  - le righe si rimpiccioliscono sotto il dito e tornano con una molla;
  - preferiti e tutte le app si scambiano con una dissolvenza e uno scivolamento a molla;
  - le aggiunte e le rimozioni nelle liste sono animate (`animateItem`);
  - le app si aprono "crescendo" dalla riga toccata (`makeClipRevealAnimation`).
- **Gesti:**
  - tocco → apre l'app;
  - pressione lunga → aggiunge o toglie dai preferiti (nell'elenco completo i preferiti hanno un puntino);
  - tasto Home → torna ai preferiti e in cima;
  - Indietro → torna ai preferiti, oppure non fa nulla se sei già lì.
- **Ordine** italiano (`Collator`): maiuscole e accenti ignorati, "É" sotto "E", numeri e simboli in fondo sotto "#".

## Personalizzazione (le opzioni migliori di Niagara)
**Impostazioni → Home → Personalizza la Home.** Ogni scelta si applica subito, anche alla Home già aperta:
- **Icone:** mostra o nascondi (modalità "solo testo").
- **Testo:** misura (piccolo 22 sp, medio 28 sp, grande 34 sp) e carattere (Moderno = sans-serif, Elegante = serif, Tecnico = monospace). Si usano solo i caratteri di sistema, nessun font di terzi.
- **Ora e data:** mostrate o nascoste.
- **Sfondo:** sfumatura leggera, media o forte; sfocatura sì o no.
- **Alfabeto:** a destra o a sinistra. L'onda e la bolla si girano verso il contenuto.
- **App nascoste:** l'elenco, con un tocco per farle tornare; e un pulsante per ripristinare tutti i nomi originali.

**Dalla Home, tenendo premuta un'app:** un menu che compare con una molla.
- Aggiungi ai preferiti o togli dai preferiti.
- Rinomina, oppure torna al nome originale. L'app rinominata si riordina sotto la nuova lettera.
- Nascondi.
- Informazioni sull'app (scheda di Android).
- Disinstalla: Android chiede conferma; serve il permesso `REQUEST_DELETE_PACKAGES`.

**Riordino dei preferiti:** tieni premuto un preferito e trascinalo. La riga si solleva e segue il dito, e le altre si spostano con una molla, con un tic a ogni scambio. Se tieni premuto senza muoverti si apre il menu. Il dito si misura in coordinate dello schermo e il rilascio viene consumato, quindi una pressione lunga non apre anche l'app. La logica degli scambi è in `ReorderState` ed è provata dai test.

Le scelte sono salvate in `files/home-prefs.properties` (`HomePrefsStore`, scrittura atomica fuori dal thread principale). Restano sul tablet: sono preferenze personali, non configurazione del progetto.

## Come è fatto
| File | Ruolo |
|---|---|
| `AppCatalog` | elenco delle app da `LauncherApps` (tutti i profili utente), aggiornato dai callback di sistema su installazione, rimozione e modifica: niente polling. Icone caricate fuori dal thread principale, una sola misura (56 dp), cache LRU di 300. Attivo solo tra `start()` e `stop()`. |
| `AppIndex` | lettera e ordinamento italiano (testato). |
| `Favorites` | preferiti in `files/home-favorites.txt`, una chiave per riga, scrittura atomica fuori dal thread principale (testato). |
| `HomeScreen`, `HomeLists`, `HomeRows`, `AppMenu`, `ReorderState`, `HomePrefs`, `HomeLook`, `Alphabet`, `Clock` | interfaccia Compose; l'ora si aggiorna col tic di sistema di ogni minuto, solo mentre la home è visibile. |
| `app/HomeActivity` | attività `HOME` (`singleTask`, tema traslucido con lo sfondo di sistema); collega catalogo, preferiti, colori dello sfondo, sfocatura e livello grafico; passa i tempi dei fotogrammi al livello automatico. |
| `app/HomeSection`, `app/HomeCustomizeScreen` | in Impostazioni: quale app fa da Home, il pulsante per cambiarla (`Settings.ACTION_HOME_SETTINGS`) e la personalizzazione. |

**Chiave di un'app:** `pacchetto/attività#seriale-utente`, stabile tra un riavvio e l'altro.

## Consumi
Lista, icone, colori dello sfondo e sfocatura esistono solo mentre la home è visibile: in `onStop` si annullano il callback di `LauncherApps`, gli ascoltatori dei colori e della sfocatura, il ricevitore dell'ora e le coroutine. La sfocatura reale costa GPU: si spegne con il livello Prestazioni. Le liste usano chiavi stabili e non allocano durante lo scorrimento.

## Note sulle regole
La home è il guscio del launcher (`app/` assembla i moduli, SPEC §3.3), quindi non è un `NovaModule` spegnibile: senza home non c'è launcher. Rispetta comunque la regola "da spento non consuma nulla", perché non consuma niente quando non è visibile. Il launcher di sistema resta installato e si può tornare a usarlo dalle Impostazioni o dalla modalità provvisoria.
