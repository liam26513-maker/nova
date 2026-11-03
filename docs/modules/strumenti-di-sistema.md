# Strumenti di sistema (Fase 2, SPEC §7)

Impostazioni → **Prestazioni**. Tutto passa da `core/system` (`SystemTools`): solo comandi della lista consentita (`SystemCommand`), argomenti validati, ogni modifica nel registro `SystemChangeLog` e annullabile (anche da `core/safemode`). Serve Shizuku **pronto**; senza, la schermata lo dice e porta a Shizuku.

## ⚠️ Cosa cambia in `core/system` (nota richiesta da CLAUDE.md)
- Nuovi comandi consentiti: `GetDensity` (`wm density`) e `SetDensity` (`wm density <dpi|reset>`), **limitati a 120–640 dpi** dal comando stesso.
- Nuova voce di registro `SystemChange.Density` (valore precedente, `confirmed`) e metodi `SystemChangeLog.setDensity/confirmDensity/revertUnconfirmedDensity/enablePackage/revertSettings`. `revertAll` ora annulla anche la densità.
- Nuova classe `SystemTools` (animazioni, DNS, Turbo, densità, congelamento) e `TurboPlan`. Nessun comando fuori dalla lista; nessun testo libero verso la shell.
- Test aggiuntivi in `SystemToolsTest` (shell finta): animazioni, DNS (compreso «spegni = com'era»), Turbo (protetti saltati, fallimenti contati), densità (limiti, ripristino automatico, sopravvive al riavvio, `revertAll`), congelamento (protetti rifiutati), errori della shell mai registrati come fatti.

## Strumenti
| Strumento | Come | Reversibile |
|---|---|---|
| **Turbo** | `am force-stop` sulle app avviabili, tranne protette, preferiti (anche dock), musica in corso, tastiera in uso, Home predefinita; poi livello grafico Prestazioni per 10 min | le app si riavviano da sole; il livello grafico torna come prima (se NOVA si chiude prima resta su Prestazioni: `shortcut:` nel codice) |
| **Animazioni** | `settings put global window_animation_scale / transition_animation_scale / animator_duration_scale` ∈ {1, 0.5, 0.25, 0} | sì, valore originale nel registro |
| **Dimensione elementi** | `wm density` ×{0,85, 1, 1,15, 1,3} della densità di fabbrica, arrotondata a 5 | sì, **prova di 15 s**: senza conferma torna da sola; non confermata dopo un riavvio dell'app → torna appena Shizuku risponde |
| **Blocco pubblicità** | `private_dns_mode=hostname`, `private_dns_specifier=dns.adguard.com` | sì, «spegni» rimette i valori originali (o li cancella se non c'erano) |
| **Congelamento** | `pm disable-user --user 0` / `pm enable`; mai pacchetti protetti | sì, elenco delle app congelate, «Tocca per riattivare» |

## Stato del sistema (§7.4)
`SystemStatusReader`: memoria (`ActivityManager.MemoryInfo`), spazio (`StatFs`), batteria (`ACTION_BATTERY_CHANGED`: percentuale, salute, temperatura, cicli su Android 14). Solo lettura, nessun permesso. La salute in % non si ricava: Android non la dà alle app normali.

## Limiti dichiarati
Risoluzione dello schermo (solo densità), risparmio energetico intelligente automatico (§14.5), elenco di app consigliate per il debloat (non c'è un elenco affidabile: si sceglie a mano), Wi-Fi/Bluetooth con un tocco (per ora aprono il pannello di Android).

## Interruttori del pannello rapido (centro di controllo)

`SystemCommand.SetToggle(QuickToggle, on)`: Wi-Fi, Bluetooth, modalità aereo, non disturbare, tema scuro, posizione, risparmio energetico, risparmio dati, luce notturna, inversione dei colori. Insieme **chiuso**: ognuno ha il suo comando fisso (nessuna stringa dall'utente alla shell). `SystemTools.setToggle(toggle, on, wasOn)` registra lo stato originale in `SystemChange.Toggle` e si annulla con «Annulla tutte le modifiche». Lo stato si legge dalle API pubbliche dell'app (`app/QuickToggles`), non dalla shell.

## Sblocco delle impostazioni con restrizioni di NOVA

`SystemCommand.SetRestrictedSettings(ownPackage, allow)` → `appops set <pacchetto> ACCESS_RESTRICTED_SETTINGS allow|default`. `ownPackage` **deve essere** `SystemCommand.OWN_PACKAGE` (`it.manlio.nova`): qualunque altro pacchetto è rifiutato già nel costruttore. `SystemChangeLog.allowRestrictedSettings` registra `SystemChange.RestrictedSettings` (una sola voce) e `revertAll` riporta il valore predefinito. In app: `HomeSystem.unlockAndOpenAccessibility` (riga «Centro di controllo sopra le altre app»): con Shizuku pronto sblocca, avvisa con un Toast e apre l'accessibilità; senza, apre soltanto l'accessibilità.

## Misura dei fotogrammi (sola lettura)
`SystemCommand.ListSurfaceLayers` (`dumpsys SurfaceFlinger --list`) e `SystemCommand.SurfaceLatency(layer)` (`dumpsys SurfaceFlinger --latency <layer>`, nome validato da regex) leggono lo stato del compositore senza modificare nulla. `FrameCollector` unisce i campioni (scarta duplicati e valori non validi, ignora le pause ≥ 750 ms come caricamenti) e produce `FrameReport`; `LayerPicker` sceglie il livello `SurfaceView` del gioco. Test: `FrameStatsTest`.

`SystemCommand.TimeStats(step)` (`dumpsys SurfaceFlinger --timestats -enable|-clear|-dump|-disable`): passi fissi, nessun parametro libero. Accendere le statistiche è volatile (si spegne con `-disable` a fine misura e al riavvio). `TimeStatsParser` ricava `FrameReport` da `averageFPS` e dall'istogramma `presentToPresent`; test in `TimeStatsParserTest`. Il formato è stato scritto a memoria del codice di Android e va confermato sul tablet: se non coincide, la misura salva la risposta grezza.

`SystemCommand.FrameTimeline` (`dumpsys SurfaceFlinger --frametimeline`): sola lettura, usato solo per la diagnosi quando la misura non legge nulla. Sul tablet UJJ U6 (Android 14) `--latency` restituisce solo zeri e il dump `--timestats` ha statistiche globali dello schermo; `LayerPicker` ora salta le righe `ActivityRecord…`.
