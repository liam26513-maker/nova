# Modulo `feature/home` — Home minima (Fase 0, punto 11)

La prima schermata del tablet quando NOVA è l'app Home predefinita. Versione minima ma curata; la home completa (layout commutabili, notifiche in linea, widget, dock, cartelle, alfabeto a onda completo) arriva nella Fase 1 (SPEC §6).

## Cosa fa
- **Orizzontale (1280×800):** a sinistra, circa 1/3 della larghezza, la lista di tutte le app stile Niagara con l'alfabeto sul bordo; a destra ora, data, pulsante delle impostazioni di NOVA e griglia dei **preferiti** (6 colonne).
- **Verticale:** ora e data, preferiti (4 colonne), poi la lista.
- **Tocco** su un'app: la apre. **Pressione lunga**: la aggiunge ai preferiti, o la toglie. I preferiti hanno un puntino d'accento nella lista.
- **Alfabeto:** toccando o trascinando il dito si salta alla prima app di quella lettera. Le lettere vicine al dito si ingrandiscono e c'è un tic aptico a ogni cambio. Ordine italiano (`Collator`): maiuscole e accenti ignorati, "É" va sotto "E", numeri e simboli in fondo sotto "#".
- **Tasto Home** premuto mentre si è già nella home: la lista torna in cima. **Indietro** non chiude la home.
- Sfondo: lo sfondo calmo di NOVA, con i pannelli traslucidi che seguono il livello grafico. Lo sfondo di sistema non si usa: da Android 13 un'app normale non può leggerlo senza permessi speciali. Si potrà valutare nella Fase 1.

## Come è fatto
| File | Ruolo |
|---|---|
| `AppCatalog` | elenco delle app da `LauncherApps` (tutti i profili utente), aggiornato dai callback di sistema su installazione, rimozione e modifica: niente polling. Icone caricate fuori dal thread principale, una sola misura (56 dp), cache LRU di 300. Attivo solo tra `start()` e `stop()`. |
| `AppIndex` | lettera e ordinamento italiano (testato). |
| `Favorites` | preferiti in `files/home-favorites.txt`, una chiave per riga, scrittura atomica fuori dal thread principale (testato). |
| `HomeScreen`, `LetterStrip`, `Clock` | interfaccia Compose; l'ora si aggiorna col tic di sistema di ogni minuto, solo mentre la home è visibile. |
| `app/HomeActivity` | attività `HOME` (`singleTask`); collega catalogo, preferiti, tema e livello grafico; passa i tempi dei fotogrammi al livello automatico. |
| `app/HomeSection` | in Impostazioni: quale app fa da Home, e il pulsante per cambiarla (`Settings.ACTION_HOME_SETTINGS`). |

**Chiave di un'app:** `pacchetto/attività#seriale-utente`, stabile tra un riavvio e l'altro.

## Consumi
Lista e icone esistono solo mentre la home è visibile: in `onStop` si annullano il callback di `LauncherApps`, il ricevitore dell'ora e le coroutine. Le liste usano chiavi stabili e non allocano durante lo scorrimento.

## Note sulle regole
La home è il guscio del launcher (`app/` assembla i moduli, SPEC §3.3), quindi non è un `NovaModule` spegnibile: senza home non c'è launcher. Rispetta comunque la regola "da spento non consuma nulla", perché non consuma niente quando non è visibile. Il launcher di sistema resta installato e si può tornare a usarlo dalle Impostazioni o dalla modalità provvisoria.
