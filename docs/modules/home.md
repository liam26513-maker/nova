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

## Personalizzazione ("studio" sul modello di Niagara)
Analisi di partenza: le pagine di aiuto di Niagara (funzioni Pro, temi) e gli articoli sull'aggiornamento "Studio". NOVA riprende le **funzioni**, non la grafica, i nomi o le icone.

**Impostazioni → Home → Personalizza la Home.** Ogni scelta si applica subito:

| Area | Opzioni | Come in Niagara |
|---|---|---|
| **Temi** | 5 temi pronti (Notte, Aurora, Carta, Tramonto, Essenziale); salva il tema in un file `.novatheme` e aprilo su un altro tablet. Il tema porta solo l'aspetto, non le app nascoste o i nomi | temi pronti, condivisione dei temi |
| **Sfondo** | Il mio (sfondo di sistema), NOVA (8 sfumature originali), Foto (dalla galleria), Colore (8 tinte); sfocatura No, Leggera, Forte o Massima; sfumatura Leggera, Media o Forte | raccolta di sfondi, sfondo nel tema |
| **Colori** | Tema scuro, chiaro o automatico; colore d'accento preso dallo sfondo o scelto (8) | colore del tema |
| **Testo e orologio** | Misura, carattere (Moderno, Elegante, Tecnico: solo caratteri di sistema), spessore; orologio Sottile, Marcato o Su due righe; data sì o no | caratteri, stili dell'orologio |
| **Icone** | Sì o no; misura; forma (Originale, Cerchio, Morbido = superellisse, Quadrato); a colori o **monocromatiche** (icona tematica di Android 13+, altrimenti in grigio); **pacchetti di icone** di terzi (`appfilter.xml`) | pacchetti di icone, stili delle icone |
| **Disposizione e gesti** | Alfabeto a destra o a sinistra; barra di stato sì o no; **scorri giù per le notifiche**; **doppio tocco sull'alfabeto per spegnere lo schermo** (tramite Shizuku) | nascondi la barra di stato, doppio tocco per spegnere |
| **App** | App nascoste (con un tocco tornano) e ripristino dei nomi | nascondi o rinomina app |

**Dalla Home, tenendo premuta un'app:** un menu che compare con una molla.
- Aggiungi ai preferiti o togli dai preferiti.
- Rinomina, oppure torna al nome originale.
- Nascondi.
- Informazioni sull'app.
- Disinstalla, con la conferma di Android (`REQUEST_DELETE_PACKAGES`).

**Riordino dei preferiti:** tieni premuto un preferito e trascinalo. La logica degli scambi è in `ReorderState`.

**Non incluso:** widget, pop-up e cartelle, notifiche sotto le app, meteo e agenda, timer d'uso, icone generate automaticamente per le app senza icona nel pacchetto. Sono nella Fase 1 o successive della SPEC; alcune richiedono servizi esterni o permessi che non vanno chiesti adesso.

## Sfondo e app recenti
- **Problema risolto:** nelle app recenti a volte traspariva l'app aperta prima. La finestra della home era traslucida; ora **non lo è più**.
- **Sfondo disegnato da NOVA:** quando NOVA disegna lo sfondo da sé (foto, NOVA, colore, oppure il tuo sfondo se le consenti di leggerlo), la finestra è opaca.
- **Preparazione dell'immagine:** `WallpaperLoader` rimpicciolisce l'immagine e la sfoca una volta sola, fuori dal thread principale. Più sfocatura significa un'immagine più piccola, quindi anche più leggera. Il colore principale si ricava con `WallpaperColors.fromBitmap`.
- **Leggere il tuo sfondo:** da Android 13 un'app può leggerlo solo con l'accesso a tutti i file (`MANAGE_EXTERNAL_STORAGE`). È **facoltativo**: lo concedi tu, da una voce chiara, e serve solo a quello. Senza il permesso resta lo sfondo di Android dietro la finestra, sfocato da Android se il tablet lo supporta (`setBackgroundBlurRadius`, spento a Prestazioni). Con «Foto» la sfocatura funziona sempre senza permessi.

## Come è fatto
| File | Ruolo |
|---|---|
| `AppCatalog` | elenco delle app da `LauncherApps` (tutti i profili utente), aggiornato dai callback di sistema su installazione, rimozione e modifica: niente polling. Icone caricate fuori dal thread principale, una sola misura (56 dp), cache LRU di 300. Attivo solo tra `start()` e `stop()`. |
| `AppIndex` | lettera e ordinamento italiano (testato). |
| `Favorites` | preferiti in `files/home-favorites.txt`, una chiave per riga, scrittura atomica fuori dal thread principale (testato). |
| `HomeScreen`, `HomeLists`, `HomeRows`, `AppMenu`, `ReorderState`, `HomePrefs` (+ `PrefsCodec`), `HomeLook`, `ThemePresets`, `NovaWallpapers`, `WallpaperLoader`, `IconStyler`, `IconPacks`, `Alphabet`, `Clock` | interfaccia Compose; l'ora si aggiorna col tic di sistema di ogni minuto, solo mentre la home è visibile. |
| `app/HomeActivity` | attività `HOME` (`singleTask`, tema traslucido con lo sfondo di sistema); collega catalogo, preferiti, colori dello sfondo, sfocatura e livello grafico; passa i tempi dei fotogrammi al livello automatico. |
| `app/HomeSection`, `app/HomeCustomizeScreen` (+ `HomeCustomizeParts`, `HomeCustomizeRoute`), `app/HomeWindow`, `app/HomeSystem` | in Impostazioni: quale app fa da Home, il pulsante per cambiarla (`Settings.ACTION_HOME_SETTINGS`) e la personalizzazione. |

**Chiave di un'app:** `pacchetto/attività#seriale-utente`, stabile tra un riavvio e l'altro.

## Consumi
Lista, icone, colori dello sfondo e sfocatura esistono solo mentre la home è visibile: in `onStop` si annullano il callback di `LauncherApps`, gli ascoltatori dei colori e della sfocatura, il ricevitore dell'ora e le coroutine. La sfocatura reale costa GPU: si spegne con il livello Prestazioni. Le liste usano chiavi stabili e non allocano durante lo scorrimento.

## Note sulle regole
La home è il guscio del launcher (`app/` assembla i moduli, SPEC §3.3), quindi non è un `NovaModule` spegnibile: senza home non c'è launcher. Rispetta comunque la regola "da spento non consuma nulla", perché non consuma niente quando non è visibile. Il launcher di sistema resta installato e si può tornare a usarlo dalle Impostazioni o dalla modalità provvisoria.
