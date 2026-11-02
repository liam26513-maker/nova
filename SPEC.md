# NOVA — Specifica completa di prodotto e architettura

> **Nome in codice provvisorio:** NOVA (sostituibile in qualsiasi momento: vedi §2.3).
> **Proprietario:** Manlio Galano
> **Esecutore:** Claude Code (sessioni cloud su repository GitHub privato)
> **Versione specifica:** 1.0 — 30 ottobre 2026
> **Lingua dell'interfaccia:** italiano (inglese come seconda lingua)

Questo documento è la fonte di verità del progetto. Le regole operative per Claude Code sono in `CLAUDE.md`; la guida pratica per il proprietario è in `GUIDA-AVVIO.md`.

---

## Indice

1. Visione e principi
2. Dispositivo di riferimento e vincoli di piattaforma
3. Architettura generale
4. Il ciclo di auto-miglioramento (cuore del progetto)
5. Sistema di design
6. Home e navigazione
7. Strumenti di sistema (Shizuku)
8. Le modalità
9. Penna e disegno digitale
10. Tastiera
11. Collegamento con il PC e accensione remota
12. Intelligenza locale e produttività
13. Privacy e sicurezza
14. Affidabilità
15. Budget di prestazioni
16. Strategia di test
17. Fasi di sviluppo (roadmap)
18. Elementi esclusi e limiti dichiarati
19. Glossario

---

## 1. Visione e principi

### 1.1 Visione

NOVA trasforma un tablet Android economico in un'esperienza che **sembra** un top di gamma: bella come iOS, libera come Android, fluida su hardware modesto, pensata per **gaming, lavoro, studio e disegno digitale**. Si migliora da sola: il proprietario chiede una modifica dal tablet, Claude Code la realizza nel cloud, il tablet si aggiorna senza cavi.

NOVA **non** è un sistema operativo né una ROM. È una suite di app Android (launcher + servizi + tastiera) che si installa sopra Android 14 e usa Shizuku per i poteri di sistema, senza root.

### 1.2 Principi non negoziabili

1. **La fluidità batte la ricchezza.** Nessuna funzione entra se rompe i budget del §15. Ogni effetto ha una versione economica.
2. **Niente da zero quando esiste di meglio.** Si riusano API di piattaforma, librerie Jetpack e progetti open source maturi (tastiera, connessione col PC, streaming). Si costruisce solo ciò che distingue NOVA.
3. **Modulare e spegnibile.** Ogni funzione è un modulo; un modulo disattivato non consuma RAM, CPU, batteria né wake-lock.
4. **Mai intrappolare l'utente.** Ogni modalità ha uscite di emergenza; ogni aggiornamento ha una via di ritorno; il launcher di sistema resta sempre installato come paracadute.
5. **Offline per default.** Tutto funziona senza internet tranne aggiornamenti, streaming e invio a Claude.
6. **I dati restano sul tablet.** Nessuna telemetria remota. I report escono solo quando il proprietario preme "Ripara".
7. **Interfaccia ispirata, mai copiata.** Nessun marchio, icona, font o asset di Apple o di Niagara Launcher. Si riprendono concetti di interazione, non grafica.
8. **Tutto ciò che è opzionale è disattivato di default** quando ha un costo di prestazioni o cambia comportamenti abituali (es. tastiera divisa).

---

## 2. Dispositivo di riferimento e vincoli di piattaforma

### 2.1 Dispositivo di riferimento

| Voce | Valore |
|---|---|
| Modello | UJJ `U6_EEA` (piattaforma `saturn`, scheda `exdroid`, board `sun55iw3p1`) |
| Android | 14 (API 34), build `UP1A.231105.001.A1` del 19/02/2025, tipo `user`/`release-keys`, patch di sicurezza 2024-12-05, kernel 5.15, Google Play Services presenti |
| SoC | **Allwinner A523**, 8 core Cortex-A55 a 64 bit in 2 cluster (4×1,80 GHz + 4×1,42 GHz) |
| GPU | **ARM Mali-G57**, OpenGL ES 3.2, Vulkan 1.3 |
| RAM | **6 GB** (6144 MiB totali rilevati; circa 4,6 GB liberi a riposo) |
| Memoria | 128 GB (circa 96 GB liberi) |
| Schermo | 10,03" IPS **1280×800, 60 Hz** (unica modalità), densità 160 dpi (mdpi), HDR10 dichiarato |
| Touch | controller `gslX680` |
| Root | assente, non richiesto |

Conseguenze per il design:
- Tutti i core sono Cortex-A55 (nessun core "grande"): le prestazioni per singolo thread sono basse. **Il main thread va protetto più che mai**; il lavoro va distribuito in parallelo dove possibile.
- Mali-G57 regge Compose e i filtri `RenderEffect`, ma il blur in tempo reale a tutto schermo è costoso: **livello grafico iniziale atteso: Bilanciato** (finto blur), da confermare con il benchmark.
- Schermo solo 60 Hz: la voce "frequenza schermo massima" della modalità Gaming resta nascosta su questo dispositivo (rilevamento a runtime).
- 6 GB di RAM sono un buon margine: il budget RAM del launcher (§15) resta comunque invariato.
- Densità mdpi su 10": proporre nella procedura guidata la regolazione della densità (§7.3) per bilanciare dimensione elementi e spazio.

**Regola d'oro:** NOVA non deve dare per scontato nessun dato hardware. Al primo avvio il **Profilatore dispositivo** (§3.6) misura e salva le capacità reali, e tutte le decisioni (livello grafico, risoluzione di gioco, limiti di cache) leggono quel profilo.

### 2.2 Vincoli della piattaforma Android 14 senza root (da rispettare nel design)

Questi limiti sono reali; Claude Code deve progettare intorno ad essi e non promettere il contrario.

| Area | Vincolo | Soluzione adottata |
|---|---|---|
| Barra di stato e tendina notifiche | Un'app non può sostituire quelle di sistema | Centro di controllo e notifiche **propri** come pannello sovrapposto (AccessibilityService + overlay), richiamabile con gesto dal bordo; quello di sistema resta disponibile |
| Schermata di blocco | Non sostituibile in modo sicuro | Si usa quella di sistema. NOVA offre la modalità StandBy (§12.6) quando il tablet è sbloccato e in carica |
| Navigazione a gesti con launcher di terze parti | Le animazioni di "Recenti" del sistema sono ridotte con launcher non di sistema | Schermata Multitasking **propria** opzionale, richiamabile da gesto o tasto; gesti di sistema conservati |
| Overclock / governor CPU | Impossibile senza root | Non promesso. Si agisce su carico, priorità, risoluzione, rete |
| Ridimensionamento di gioco (GameManager) | Dipende dalla configurazione del produttore | Rilevamento a runtime; se non supportato si nasconde la voce e si suggeriscono le impostazioni grafiche interne al gioco |
| Installazione silenziosa degli aggiornamenti | Richiede privilegi | Tramite Shizuku (privilegi shell). Senza Shizuku: installazione con una conferma |
| Downgrade di un'app firmata in release | Il sistema lo rifiuta | Il "ritorno indietro" si fa ricompilando la versione precedente con un numero di versione più alto (§4.6) |
| Shizuku dopo il riavvio | In modalità debug wireless va riattivato | Flusso guidato di riattivazione in un tocco; verificare se la versione corrente di Shizuku supporta l'avvio automatico su Android 14 e, se sì, usarlo |
| Una sola VPN attiva per volta | Tailscale (§11) e firewall basati su VPN non coesistono | Blocco pubblicità via **DNS privato** (nessuna VPN); firewall per app via comandi di sistema tramite Shizuku (§13.2) |
| Utenti multipli | Dipende dal produttore | Modalità ospite: utente Android secondario se disponibile, altrimenti "home ospite" con lista app consentite e PIN |

### 2.3 Identità del progetto

- Nome in codice: `NOVA`. Package base: `it.manlio.nova` (launcher), `it.manlio.nova.keyboard` (tastiera).
- Rinominare il prodotto deve richiedere solo: cambiare `brand.json` nel livello di configurazione (nome visualizzato, colori del marchio, icona). I package restano stabili per non perdere gli aggiornamenti.

---

## 3. Architettura generale

### 3.1 Tre strati

```
┌────────────────────────────────────────────────────────────┐
│ STRATO 3 — Configurazione dinamica (si aggiorna in secondi)│
│ temi, layout home, widget, gesti, automazioni, profili,    │
│ modalità, testi, scorciatoie, flag dei moduli              │
│ JSON validato da schema · nessuna compilazione             │
├────────────────────────────────────────────────────────────┤
│ STRATO 2 — Moduli funzionali (Kotlin, compilati)           │
│ home, ricerca, isola, modalità, penna, desktop, OCR, ...   │
│ ognuno con interfaccia NovaModule, attivabile/spegnibile   │
├────────────────────────────────────────────────────────────┤
│ STRATO 1 — Nucleo (Kotlin, raramente modificato)           │
│ design system, motore config, registro moduli, ponte       │
│ Shizuku, aggiornatore, crash handler, modalità provvisoria,│
│ profilatore dispositivo, telemetria locale                 │
└────────────────────────────────────────────────────────────┘
```

### 3.2 Stack tecnologico

| Ambito | Scelta | Motivo |
|---|---|---|
| Linguaggio | Kotlin (ultima stabile) | Standard Android |
| UI | Jetpack Compose + Material 3 come base tecnica (stile completamente personalizzato) | Produttività, animazioni a molla native |
| Avvio rapido | **Baseline Profiles** + R8 in modalità completa | Riduzione forte dei tempi di avvio e di jank su hardware lento |
| Concorrenza | Coroutines + Flow | Standard |
| DI | Hilt (o Koin se Hilt pesa troppo sui tempi di build) | Modularità |
| Persistenza | DataStore (preferenze), Room (statistiche, cronologia, indice ricerca) | Leggeri |
| Configurazione | JSON + JSON Schema, validazione con kotlinx.serialization | Strato 3 |
| Poteri di sistema | **Shizuku** + API | Niente root |
| Grafica a bassa latenza | `androidx.graphics:graphics-core` (rendering front-buffered) + `androidx.input` (`MotionEventPredictor`); valutare `androidx.ink` | Penna |
| Riconoscimento testo / scrittura a mano | ML Kit on-device (Text Recognition v2, Digital Ink Recognition, lingua italiana) | Offline, gratuito |
| Widget | `AppWidgetHost` | Widget di tutte le app |
| App installate | `LauncherApps`, `PackageManager` | API launcher |
| Notifiche | `NotificationListenerService` | Notifiche nella lista stile Niagara |
| Overlay e gesti globali | `AccessibilityService` (solo per gesti dal bordo, tasti volume, pannelli) | Funzioni oltre il launcher |
| Build e rilascio | Gradle (Kotlin DSL, version catalog) + GitHub Actions | Compilazione nel cloud gratuita |
| Qualità | ktlint, detekt, Android Lint, JUnit, Robolectric, test UI Compose, Roborazzi (screenshot), Macrobenchmark | §16 |

### 3.3 Struttura del repository

```
nova/
├── CLAUDE.md                 regole operative per Claude Code
├── SPEC.md                   questa specifica
├── GUIDA-AVVIO.md            guida per il proprietario
├── CHANGELOG.md              aggiornato a ogni rilascio, in italiano
├── perf-budget.json          budget di prestazioni (§15), letto dalla CI
├── config/                   STRATO 3 — configurazione dinamica
│   ├── schema/               JSON Schema di ogni file
│   ├── brand.json
│   ├── theme/*.json
│   ├── home.json
│   ├── gestures.json
│   ├── modes/*.json          gaming, lavoro, studio, creativa, presentazione
│   ├── automations/*.json
│   ├── keyboard.json
│   ├── debloat.json          classificazione app da disattivare (§7.2)
│   ├── pen-apps.json         scorciatoie per app di disegno (§9.4)
│   └── modules.json          flag on/off dei moduli
├── reports/                  report "Ripara" caricati dal tablet (§4.5)
├── docs/                     documentazione tecnica per modulo + ADR
├── app/                      launcher (shell che assembla i moduli)
├── core/
│   ├── design/               token, componenti, molle, aptica
│   ├── config/               caricamento, validazione, strati, hot reload
│   ├── modules/              interfaccia NovaModule + registro
│   ├── system/               ponte Shizuku, comandi shell sicuri
│   ├── update/               aggiornatore APK e configurazione
│   ├── telemetry/            metriche locali, log, crash handler
│   ├── safemode/             home minimale di emergenza
│   └── device/               profilatore dispositivo + benchmark
├── feature/
│   ├── home/  search/  island/  controlcenter/  multitasking/
│   ├── modes/ gaming/  work-desktop/  study/  creative/  presentation/
│   ├── pen/   quicksketch/
│   ├── system-tools/ privacy/ battery/ files/
│   ├── pc-link/  standby/  ocr/  automations/  suggestions/
│   ├── theme-editor/  whatsnew/  guest/  adam/  wellbeing/
│   └── onboarding/
├── keyboard/                 fork di HeliBoard (app separata, §10)
├── benchmark/                Macrobenchmark
├── baselineprofile/          generatore Baseline Profile
├── tools/wol-bridge/         servizio di accensione PC per il ponte (§11.1)
└── .github/workflows/        ci.yml, release.yml, config-release.yml, rollback.yml
```

### 3.4 Interfaccia dei moduli

Ogni funzione implementa:

```kotlin
interface NovaModule {
    val id: String                    // es. "island", "gaming"
    val displayName: String
    val defaultEnabled: Boolean
    val requires: Set<Capability>     // es. SHIZUKU, ACCESSIBILITY, NOTIFICATION_ACCESS, OVERLAY
    val dependsOn: Set<String>        // id di altri moduli
    suspend fun start(context: ModuleContext)
    suspend fun stop()                // deve rilasciare TUTTO: listener, servizi, wake-lock, cache
    fun healthCheck(): ModuleHealth   // usato da "Stato del sistema" e da "Ripara"
}
```

Regole:
- Il registro avvia solo i moduli abilitati in `config/modules.json` + override locali.
- Un modulo spento non registra receiver, servizi, job WorkManager né observer.
- Un modulo che va in crash 3 volte in 10 minuti viene disattivato automaticamente e segnalato nell'isola: "Modulo X sospeso — tocca per Ripara".
- Dipendenze mancanti (es. Shizuku non attivo) → il modulo resta visibile ma "in attesa", con spiegazione e pulsante per risolvere.

### 3.5 Livelli di configurazione

Ordine di priorità (l'ultimo vince):
1. **Default compilati** nell'APK (garantiscono che NOVA funzioni anche senza config scaricata).
2. **Pacchetto config dal repository** (`config/`), pubblicato come asset di rilascio firmato con hash.
3. **Override locali** creati sul tablet (editor temi, impostazioni, "Ritocco visivo").

Ogni pacchetto config dichiara `schemaVersion` e `minAppVersion`. Se non compatibile o non valido contro lo schema: rifiutato, si resta sul precedente, e il motivo compare in "Novità/Stato".

Gli override locali possono essere **esportati nel repository** (commit tramite API GitHub, con token) così Claude Code li vede e il backup è completo (§14.4).

### 3.6 Profilatore dispositivo

Al primo avvio, e su richiesta dopo ogni aggiornamento importante:
- Legge: SoC (`Build.SOC_MODEL`, `/proc/cpuinfo`), core e frequenze massime, RAM reale (`ActivityManager.MemoryInfo.totalMem`), classe di prestazioni (`Build.VERSION.MEDIA_PERFORMANCE_CLASS`), risoluzione, densità, frequenze di aggiornamento supportate, versione GPU/driver, supporto blur (`RenderEffect`), supporto GameManager, supporto freeform, supporto multiutente, presenza digitalizzatore penna, spazio libero.
- Esegue un micro-benchmark di 15 secondi: scorrimento lista con blur on/off, animazione icone, decodifica immagini. Misura tempi di frame (`JankStats` / `FrameMetrics`).
- Produce `DeviceProfile` (salvato) e sceglie il **livello grafico iniziale** (§5.3).
- Mostra all'utente una scheda leggibile: "Il tuo tablet: 4 GB RAM reali, processore X, schermo 60 Hz. Livello grafico consigliato: Bilanciato."

---

## 4. Il ciclo di auto-miglioramento

Questo è il cuore del progetto e va costruito **per primo** (Fase 0), perché da quel momento ogni altra funzione si può chiedere direttamente dal tablet.

### 4.1 Panoramica

```
Tablet                       GitHub / Claude Code (cloud)                Tablet
──────                       ────────────────────────────                ──────
[Migliora] o [Ripara]
  │ prepara richiesta
  │ + contesto + report
  ├─► carica report in reports/ (API GitHub)
  └─► apre Claude Code ──► sessione sul repo ──► branch + PR
                                                  │
                                       CI: build, test, lint,
                                       budget prestazioni, screenshot
                                                  │ verde
                                       merge su main (auto-merge)
                                                  │
                           ┌──────────────────────┴────────────────┐
                    solo config/ cambiata                  codice cambiato
                    config-release.yml                     release.yml
                    (pacchetto config firmato)             (APK firmato + changelog)
                           └──────────────────────┬────────────────┘
                                                  ▼
                                    Release GitHub (privata)
                                                  │
                                    aggiornatore NOVA la rileva ─► scarica ─► verifica
                                                  │                 firma/hash
                                    config: applica a caldo
                                    APK: installa via Shizuku ─► riavvio launcher
                                                  │
                                    schermata "Novità" + metriche post-aggiornamento
```

### 4.2 Pulsante "Migliora"

Raggiungibile da: impostazioni, pressione lunga su qualsiasi elemento (via "Ritocco visivo", §5.6), comando vocale/testuale nella ricerca ("migliora: …").

Flusso:
1. Campo di testo grande + tasto dettatura. Esempio: "rendi il dock più trasparente e aggiungi un widget meteo".
2. NOVA classifica la richiesta in **leggera** (solo `config/`) o **profonda** (codice) con regole locali semplici; la classificazione è solo un suggerimento: decide Claude Code.
3. NOVA genera un **prompt completo** contenente:
   - la richiesta testuale;
   - versione app e config installate, `DeviceProfile` sintetico;
   - elemento selezionato (se avviato da "Ritocco visivo": id componente e file di config corrispondente);
   - istruzione di rispettare `CLAUDE.md` e `SPEC.md`.
4. Copia il prompt negli appunti e apre Claude Code (app Claude o `https://claude.ai/code`). **Verificare** se esiste un parametro URL per precompilare il prompt di una sessione Claude Code; se esiste, usarlo; altrimenti restano appunti + istruzione a schermo "incolla e invia".
5. NOVA mostra nell'isola lo stato "In attesa di Claude Code" e poi segue la CI (§4.4).

**Nessuna chiave API Anthropic** è usata o richiesta. Tutto passa dall'abbonamento del proprietario.

### 4.3 Pulsante "Ripara"

Come "Migliora", ma in più:
- raccoglie automaticamente: ultimi crash (stack trace dal crash handler), log dell'app (buffer circolare interno), log di sistema dell'app tramite Shizuku (`logcat --pid` / filtro package) negli ultimi minuti, `healthCheck()` di tutti i moduli, metriche di frame recenti, stato Shizuku/permessi;
- **rimuove dati personali** dai log (indirizzi email, numeri di telefono, token, contenuti delle notifiche) con filtri regex prima del caricamento;
- carica il report come `reports/AAAA-MM-GG_HHMM_<slug>.md` nel repository tramite API GitHub;
- il prompt per Claude Code dice: "Leggi `reports/<file>.md`, individua la causa, correggi, aggiungi un test che riproduca il problema".

### 4.4 CI — `ci.yml` (su ogni PR)

Passi obbligatori, tutti bloccanti:
1. Validazione JSON di `config/` contro gli schemi.
2. ktlint + detekt + Android Lint (nessun nuovo errore).
3. Test unitari + Robolectric.
4. Test UI Compose dei flussi critici (home, ricerca, uscita di emergenza, modalità provvisoria, aggiornatore).
5. Test screenshot (Roborazzi) con confronto: le differenze devono essere intenzionali (il PR le elenca).
6. Build release (R8) e controllo **budget statici**: dimensione APK, numero di dipendenze, presenza Baseline Profile.
7. **Budget dinamici** su emulatore (API 34, profilo hardware basso, KVM): Macrobenchmark di avvio home, scorrimento lista app, apertura ricerca, apertura centro di controllo. Su emulatore i valori assoluti non sono affidabili: si confrontano con la **baseline di `main`** e si blocca se il peggioramento supera la tolleranza di `perf-budget.json` (default 10%).
8. Commento automatico sul PR con tabella prima/dopo.

Auto-merge abilitato: se tutto è verde, la PR viene unita. Il proprietario può sempre bloccare o annullare.

### 4.5 Rilascio — `release.yml` e `config-release.yml`

- Su merge in `main`:
  - se cambiano solo file in `config/` → `config-release.yml` produce `config-bundle-<versione>.zip` + `sha256` + firma, come Release GitHub con tag `config-vX.Y.Z`;
  - altrimenti → `release.yml` incrementa `versionCode`, compila APK firmati (launcher e, se cambiata, tastiera), genera note in italiano da `CHANGELOG.md`, pubblica Release con tag `vX.Y.Z`. Allega anche il pacchetto config corrente.
- Chiave di firma: in GitHub Secrets (`NOVA_KEYSTORE_BASE64`, password). **Se si perde la chiave non si potrà più aggiornare senza disinstallare**: la guida impone un backup offline.
- Le Release restano private (repository privato). Il tablet le legge con un token GitHub a permessi minimi (§13.4).

### 4.6 Aggiornatore sul tablet

- Controlla nuove Release: all'apertura del launcher, ogni 6 ore (WorkManager, solo con rete non a consumo se impostato), e su comando "Controlla ora".
- Verifica: firma APK coincidente con quella installata, hash SHA-256, `minAppVersion` della config.
- Installazione APK: via Shizuku (`PackageInstaller` con privilegi shell) senza conferme; senza Shizuku, con una conferma di sistema.
- Prima di installare: salva la versione corrente in **"punti di ripristino"** (metadati, config attiva, override locali).
- Dopo l'installazione: avvio di controllo. Se NOVA va in crash entro 60 secondi per 2 volte → **modalità provvisoria** (§14.2) e proposta di ritorno indietro.
- **Ritorno indietro del codice:** Android rifiuta i downgrade, quindi la voce "Torna a una versione precedente" (ultima di Impostazioni → Aggiornamenti, solo per emergenze, con scelta della versione e conferma) avvia il workflow `rollback.yml` (API GitHub, `workflow_dispatch`, token con permesso Actions) che ricompila quel tag con `versionCode` superiore e lo pubblica. La versione immediatamente precedente è già allegata a ogni release come APK di ripristino e si installa subito; per le più vecchie NOVA ricontrolla ogni 30 secondi e le installa appena GitHub le pubblica. Dettagli in `docs/AGGIORNAMENTI.md`.
- **Ritorno indietro della config:** istantaneo e locale (si conservano gli ultimi 10 pacchetti).

### 4.7 Fallback senza cloud (opzionale, Fase 5)

Se Claude Code nel cloud non è raggiungibile (limiti d'uso esauriti, servizio non disponibile), la richiesta può essere girata ad **ADAM sul PC**, svegliando il PC (§11.2). ADAM esegue Claude Code localmente sullo stesso repository e segue lo stesso identico flusso PR → CI → Release. Nessuna differenza per il tablet.

### 4.8 Registro modifiche e "Novità"

- Ogni aggiornamento mostra una schermata "Novità": cosa è cambiato in italiano semplice, **la richiesta originale** del proprietario (se la PR la cita), confronto prestazioni prima/dopo.
- Archivio consultabile di tutti gli aggiornamenti con pulsante "Torna a questa versione".

---

## 5. Sistema di design

### 5.1 Linguaggio visivo

Ispirazione iOS/iPadOS (pulizia, gerarchia, materiali traslucidi, angoli continui), mai copia.

- **Font:** Inter (licenza OFL) come predefinito; alternative selezionabili: Manrope, Plus Jakarta Sans. **Vietato** usare SF Pro (la licenza ne vieta l'uso su piattaforme non Apple).
- **Icone di sistema:** set open source (es. Lucide o Material Symbols con stile arrotondato), mai SF Symbols.
- **Angoli:** "squircle" (superellisse) per icone, card, pannelli. Implementare una `Shape` superellittica riutilizzabile.
- **Token** (in `core/design` + sovrascrivibili da `config/theme/*.json`): colori (chiaro/scuro), raggi, spaziature (scala 4 pt), tipografia, opacità dei materiali, durate e costanti delle molle, intensità aptiche.

### 5.2 Movimento: animazioni a molla interrompibili

- Tutte le transizioni usano molle fisiche (`spring()` di Compose) con parametri da token. Niente animazioni a durata fissa per gesti.
- **Ogni animazione è interrompibile** dal dito in qualunque istante e prosegue con la velocità del gesto (gestire il passaggio di velocità tra gesto e animazione).
- Feedback visivo entro **1 frame** dal tocco (scala 0,96 / evidenziazione), indipendentemente dal lavoro successivo.
- Rispetto di "Rimuovi animazioni" di accessibilità: molle sostituite da dissolvenze brevi.

### 5.3 Livelli grafici adattivi

| Livello | Materiali | Uso |
|---|---|---|
| **Massimo** | `RenderEffect` blur in tempo reale | SoC capaci e batteria > 30% |
| **Bilanciato** (default atteso sul tablet U6) | "Finto blur": sfondo sfocato **pre-calcolato una volta** a ogni cambio di sfondo/tema, disegnato sotto i pannelli con traslucenza | Aspetto quasi identico, costo quasi nullo |
| **Prestazioni** | Superfici semi-opache piatte, ombre ridotte | Hardware in difficoltà, risparmio energetico, Gaming |

- **Regolazione automatica:** un monitor dei frame (JankStats) scende di livello se il 90° percentile del tempo di frame supera il budget per 5 secondi consecutivi, e prova a risalire dopo 10 minuti stabili. Ogni cambio è registrato.
- L'utente può fissare un livello manualmente.

### 5.4 Aptica

- Vocabolario aptico definito nei token: `tap`, `selection`, `toggle`, `success`, `warning`, `boundary` (fine scorrimento), `long-press`.
- Usare `HapticFeedbackConstants` / `VibrationEffect.Composition` dove disponibile; fallback a impulsi brevi tarati. Il motore di vibrazione dei tablet economici è spesso mediocre: tarare durate su `DeviceProfile` e permettere intensità regolabile o spenta.

### 5.5 Icone uniformate

- Tutte le icone app vengono rese con la stessa maschera squircle. Icone adattive: si usano foreground/background. Icone legacy: centratura su sfondo derivato dal colore dominante (Palette), con padding uniforme.
- Supporto icon pack di terze parti (formato standard) come opzione.
- Cache su disco delle icone renderizzate per dimensione/tema: la home non ricalcola mai al volo.

### 5.6 "Ritocco visivo"

Pressione lunga su un elemento in modalità modifica → pannello con controlli diretti (dimensione, raggio, trasparenza, colore, posizione) che scrivono override locali, più il tasto "Chiedi a Claude…" che apre "Migliora" con l'elemento già indicato.

### 5.6.1 Impostazioni a barra laterale (fatto)
Le schermate lunghe di impostazioni (Impostazioni, Personalizza la Home) usano `SplitSettings` di `core/design`, come l'app Impostazioni dell'iPad: a sinistra la barra laterale con le categorie (pastiglie colorate con simboli disegnati da NOVA, `SidebarGlyph`), a destra il dettaglio. Da 840 dp di larghezza sono due colonne; più stretto, un elenco che apre il dettaglio (Indietro torna all'elenco). Titoli grandi in grassetto. Le schermate corte e guidate (procedura iniziale, misura, Migliora) restano a colonna singola (`SettingsScaffold`).

### 5.7 Editor temi

Editor visivo con anteprima dal vivo: palette, materiali, raggi, font, densità, sfondi. Salva temi come JSON (override locale), esportabili nel repo. Temi preimpostati: Chiaro, Scuro, Notte OLED, Studio (chiaro caldo), Gaming (scuro ad alto contrasto), Creativo (neutro, colori fedeli).

---

## 6. Home e navigazione

### 6.1 Layout ibrido (iOS + concetto Niagara)

Tre stili commutabili in un tocco, anche per profilo/modalità:

1. **Ibrido** (default):
   - *Orizzontale:* colonna sinistra stile Niagara (lista preferiti + alfabeto a onda) larga circa 1/3; area destra con griglia di widget e dock.
   - *Verticale:* lista Niagara a tutto schermo; widget raggiungibili con swipe orizzontale; dock in basso.
2. **Lista** (Niagara puro): solo lista verticale + alfabeto a onda.
3. **Griglia** (stile iPadOS): pagine di icone, cartelle, widget, dock.

### 6.1.1 Studio di personalizzazione (fatto in Fase 0)
Temi pronti e condivisibili, sfondo (sistema, raccolta di NOVA, foto, colore) con sfocatura propria, tema scuro/chiaro/automatico, colore d'accento, carattere, misura e spessore del testo, stili dell'orologio, icone (forma, misura, monocromatiche, pacchetti `appfilter.xml`), barra di stato, gesti (scorri giù per le notifiche, doppio tocco per spegnere tramite Shizuku), app nascoste e rinominate. Finestra della home opaca: nessuna trasparenza nelle app recenti. Dettagli in `docs/modules/home.md`.

### 6.1.2 Stile iPad (fatto, richiesta "rendi tutta l'interfaccia più simile ad un iPad" e seguito)
Implementate tutte e tre le disposizioni di §6.1: **Griglia** (stile iPadOS, **predefinita**), **Ibrido** (solo in orizzontale; in verticale è la Lista) e **Lista**. La Griglia ha pagine di icone squircle posizionabili a piacere, cartelle con anteprima 3×3, widget (Orologio, Calendario, Batteria, Nota rapida di NOVA e widget di altre app tramite `AppWidgetHost`), modalità modifica con trascinamento tra le pagine, dock di vetro con i primi 6 preferiti e ricerca universale (app, impostazioni di NOVA, web). Materiali "vetro" disegnati senza sfocatura in tempo reale (§5.3). **Non è una copia dell'iPad**: nessun asset, marchio o carattere di Apple (regola 7 e CLAUDE.md). Fatti nel blocco successivo: Meteo (per nome di città, senza posizione), Pomodoro, centro di controllo base (§6.8) e calcolatrice/comandi nella ricerca (§6.6). Fatti nel blocco B: notifiche sulle icone (§6.2, in forma essenziale: numero e ultima anteprima, «cancella»), widget Musica e isola dinamica dentro la Home (§6.5, §6.7). Mancano ancora: Stato PC, Galleria lavori (§6.5), risposta rapida dalle notifiche, swipe su un'app per scorciatoie (§6.2), isola come finestra sopra le altre app. Dettagli in `docs/modules/home.md`.

### 6.2 Lista dei preferiti

- Elementi grandi, raggiungibili col pollice; riordinabili col trascinamento.
- **Notifiche in linea**: sotto ogni app, anteprima dell'ultima notifica con azioni rapide (rispondi, archivia, segna come letto) tramite `NotificationListenerService`.
- **Swipe su un'app**: mostra il suo widget o le scorciatoie (`LauncherApps.getShortcuts`).
- Riga "suggerite" opzionale in cima (§12.5).

### 6.3 Alfabeto a onda

- Barra verticale delle lettere sul bordo (lato configurabile). Trascinando il dito, le lettere vicine si ingrandiscono con un'onda e le app di quella lettera compaiono accanto al pollice: si apre qualsiasi app con un solo gesto continuo.
- Aptica `selection` a ogni cambio lettera.
- Gestione di numeri e caratteri accentati (raggruppamento sotto la lettera base, ordinamento italiano con `Collator`).

### 6.4 Dock, cartelle, libreria app

- Dock con 4–8 app (configurabile), traslucido secondo il livello grafico.
- Cartelle con anteprima 3×3, apertura animata a molla.
- **Libreria app**: categorie automatiche (`ApplicationInfo.category` + regole locali + correzioni manuali): Social, Giochi, Studio, Lavoro, Creatività, Utility, Intrattenimento, Altro.

### 6.5 Widget

- Host per widget Android standard (`AppWidgetHost`), ridimensionabili.
- Widget nativi NOVA leggeri (Compose, aggiornati solo quando visibili): Orologio, Meteo (fonte gratuita senza chiave, es. Open-Meteo), Calendario, Batteria e dispositivi, Stato PC, Musica, Pomodoro, Galleria lavori, Note rapide.

### 6.6 Ricerca universale

- Richiamo: swipe verso il basso sulla home, `Ctrl+Spazio` su tastiera fisica, tocco sull'isola.
- Risultati istantanei (indice locale Room FTS) da: app, scorciatoie delle app, contatti (con permesso), impostazioni di sistema (lista di `Settings` intent), impostazioni NOVA, file recenti, comandi NOVA ("modalità studio", "turbo", "accendi PC", "migliora: …"), calcolatrice ed unità di misura, ricerca web (browser predefinito).
- Obiettivo: primo risultato entro 50 ms dalla digitazione.

### 6.7 Isola dinamica

- Pillola in alto (overlay) che mostra attività in corso: musica (MediaSession), timer/Pomodoro, download, chiamata, modalità attiva, stato aggiornamento ("Compilazione 60%"), registrazione schermo, PC in accensione, avvisi dei moduli.
- Tocco → espansione con controlli; pressione lunga → app sorgente.
- Si nasconde a schermo intero (giochi, video) salvo avvisi critici.

### 6.8 Centro di controllo

- Pannello proprio a riquadri, **a tendina**: si tira giù dall'angolo in alto a destra e segue il dito (soglia a metà o scatto veloce), si chiude tirando su, toccando fuori o con Indietro; nessun pulsante fisso sulla Home (resta una voce invisibile per TalkBack). Cursori di volume e luminosità a barra orizzontale che seguono il dito dal primo tocco, scrivono al sistema a frequenza limitata e si agganciano ai gradini quando il sistema ne ha. Sulla Home è fatto; sopra le altre app è fatto con il servizio di accessibilità `NovaControlService` (§2.2): acceso a mano dall'utente, una striscia nell'angolo e il pannello in finestre `TYPE_ACCESSIBILITY_OVERLAY`, senza lettura dello schermo, non attivo a schermo bloccato. Sopra le altre app la striscia che apre la tendina copre **tutta la barra di stato** (sostituisce quella di Android)  ed è sempre attiva (anche nei giochi a schermo intero). **A prima vista solo l'essenziale** (Wi-Fi, Bluetooth, Non disturbare, torcia, volume, luminosità); sotto **«Altro»** i comandi di NOVA e le impostazioni di Android (le notifiche e le impostazioni rapide di Android sono sempre raggiungibili da lì). La scheda segue il tema e l'accento scelti dall'utente. Contenuto (gesto dal bordo superiore destro, configurabile): Wi-Fi, Bluetooth, luminosità, volume, rotazione, non disturbare, torcia se presente, registrazione schermo, cambio modalità, livello grafico, Turbo, accendi PC, gioca dal PC, KDE Connect, DNS anti-pubblicità on/off.
- Le azioni che richiedono privilegi usano Shizuku; altrimenti aprono il pannello di sistema corrispondente (`Settings.Panel`).
- Riquadri riordinabili e configurabili in `config/`.

### 6.9 Multitasking proprio (opzionale)

- Schermata app recenti con anteprime (tramite `ActivityManager`/`UsageStatsManager` + Shizuku dove serve), chiusura con swipe, split-screen rapido (trascina un'app su metà schermo), "chiudi tutto".
- Disattivata di default: i gesti di sistema restano la base.

---

## 7. Strumenti di sistema (Shizuku)

### 7.1 Ponte Shizuku

- `core/system` espone solo **comandi in lista consentita**, tipizzati (niente shell libera da input utente): `disablePackage`, `enablePackage`, `forceStop`, `setStandbyBucket`, `setAppOps`, `putSetting(namespace, key, value)`, `wmDensity`, `wmSize`, `installApk`, `gameMode`, `logcatForPackage`, `setPackageNetworking`, ecc.
- Ogni modifica di sistema viene **registrata con il valore precedente** e reversibile da "Ripristina tutto" (impostazioni + modalità provvisoria).
- Impostazioni critiche protette: mai disattivare package nella lista nera (launcher di sistema, impostazioni, installer, SystemUI, servizi telefonici, tastiera di sistema finché la nuova non è verificata, Shizuku, servizi di rete).

### 7.2 Debloat guidato

- Elenco delle app e dei servizi del produttore e di Google classificati: **Sicuro da disattivare** / **Valuta** / **Non toccare**, con spiegazione in italiano. Database delle classificazioni in `config/debloat.json` (basato sulle liste pubbliche note, es. Universal Android Debloater), estendibile.
- Usa `pm disable-user` (reversibile), mai disinstallazione per l'utente 0 senza conferma esplicita e spiegazione.

### 7.3 Prestazioni

- **Congelamento app inattive** (`am set-standby-bucket restricted` / sospensione) dopo N giorni di non uso, con eccezioni.
- **Turbo** (tasto unico): chiude le app in background non essenziali, libera cache, forza il livello grafico Prestazioni per 10 minuti.
- **Scala animazioni di sistema** (0,5× consigliato) e **densità/risoluzione** (`wm density`, `wm size`) con anteprima e ripristino automatico dopo 15 secondi se non confermato (evita schermate inutilizzabili).
- **Limite processi in background** (impostazione sviluppatore via `settings`), facoltativo e spiegato.

### 7.4 Stato del sistema

Scheda e widget: RAM usata/libera, temperatura (batteria e, se leggibile, CPU), carico CPU, spazio, batteria e salute (§13.6), stato Shizuku, stato moduli, stato PC (acceso/spento/raggiungibile), stato dell'ultimo aggiornamento. Ogni problema ha accanto un pulsante "Sistema".

---

## 8. Le modalità

### 8.1 Motore delle modalità

- Una modalità è un JSON in `config/modes/` che descrive: azioni all'ingresso, azioni all'uscita (o ripristino automatico dello stato salvato), stile home, tema, livello grafico, regole notifiche, tastiera, moduli da attivare/sospendere.
- **Attivazione:** manuale (centro di controllo, ricerca, widget), automatica (app aperta, accessorio collegato, orario, rete Wi-Fi), o da ADAM.
- **Uscita:** ripristina esattamente lo stato precedente (snapshot prima dell'ingresso).
- Una sola modalità "principale" attiva per volta; la modalità ospite è separata.

### 8.2 Modalità Gaming

**Attivazione:** apertura di un'app in categoria Giochi (o lista manuale) / tasto / libreria giochi.

**Libera risorse:**
- sospende sincronizzazioni e download in background; congela le app non essenziali; libera RAM prima del lancio (con barra di avanzamento visibile al massimo 1 secondo);
- livello grafico NOVA → Prestazioni; moduli non necessari in pausa.

**Spinge il gioco:**
- **Ridimensionamento per gioco** tramite `GameManager` / `cmd game` (es. 0,7×) se il dispositivo lo supporta (rilevato dal profilatore); profilo per gioco salvato;
- **frequenza schermo massima** se lo schermo supporta più di 60 Hz;
- **Wi-Fi a bassa latenza** (`WifiManager.WIFI_MODE_FULL_LOW_LATENCY` lock) per tutta la sessione;
- **precaricamento predittivo** (ispirazione Hyper Boost): se l'utente apre la libreria giochi o è il suo orario abituale di gioco, si liberano risorse e si "scalda" il gioco più probabile (avvio anticipato del processo dove consentito).

**Nessuna distrazione:** non disturbare con eccezioni configurabili, chiamate bloccate (eccetto preferiti), notifiche nascoste, schermo sempre acceso, luminosità e rotazione bloccate.

**Gesti protetti (non disattivati):** il primo swipe di sistema dal bordo mostra una maniglia; il secondo entro 2 secondi esegue l'azione. Implementazione tramite overlay di cattura sui bordi (AccessibilityService).

**Uscite di emergenza (tutte sempre attive in Gaming):**
1. gesti protetti (doppio swipe);
2. **volume giù tenuto premuto 3 secondi** → menu "Esci / Chiudi forzatamente / Torna alla home" (intercettazione tasti via AccessibilityService `FLAG_REQUEST_FILTER_KEY_EVENTS`);
3. **watchdog blocchi:** se il gioco non produce frame o non risponde per 8 secondi, NOVA propone la chiusura;
4. pulsante "Chiudi gioco" nel pannello Gaming (`am force-stop` via Shizuku);
5. tasto di accensione tenuto premuto ~10 s (riavvio hardware, sempre disponibile; documentato nel tutorial).

**Pannello in sovrimpressione** (swipe dal bordo, a scomparsa): FPS stimati, temperatura, batteria, ping, RAM; pulsanti screenshot, registrazione, Turbo, cambio risoluzione, esci. Disegno leggero, aggiornato al massimo 2 volte al secondo.

**Libreria giochi unica:** giochi locali + giochi PC in streaming (Moonlight, §11.4) + cloud gaming (scorciatoie a Xbox Cloud Gaming, GeForce NOW). Copertine dai metadati o icone.

**Profili intelligenti:** per ogni gioco si salvano impostazioni e statistiche (FPS medi, minimi, temperatura). Dopo ogni sessione, suggerimenti: "Ha fatto 24 FPS medi: provo la risoluzione al 75%?" (un tocco per applicare).

**Controller:** riconoscimento gamepad Bluetooth/USB, stato batteria del controller, mappatura tasti per i giochi che lo prevedono.

**Uscita:** ripristino completo dello stato precedente; riepilogo sessione facoltativo.

### 8.3 Modalità Lavoro

- **Desktop:** barra applicazioni propria in basso (overlay) con app aperte e preferite; **finestre flottanti ridimensionabili** tramite modalità freeform di Android (abilitazione `enable_freeform_support` via Shizuku, avvio con `ActivityOptions.setLaunchBounds`); split-screen rapido. Riferimento architetturale: progetto open source "Taskbar" (farmerbb), da studiare per le tecniche, rispettando la licenza.
- **Set di finestre stile Stage Manager:** gruppi di app salvati con posizione e dimensione ("Ricerca: browser + note + Claude"), riapribili con un tocco; set recenti a lato.
- Tastiera fisica e mouse: §10.6.
- Appunti con cronologia, trascinamento testo/file tra finestre (drag & drop di piattaforma).
- Scorciatoie: desktop remoto del PC, "tablet come secondo schermo" (app consigliate, configurazione guidata).

### 8.4 Modalità Studio

- Timer Pomodoro nella home e nell'isola (durate configurabili), con statistiche settimanali.
- Blocco delle app che distraggono durante le sessioni (overlay che intercetta l'apertura, con "sblocca per 5 minuti" che richiede conferma).
- Note rapide sempre a un gesto; modalità lettura (filtro caldo, font ottimizzati per i PDF dove possibile); tastiera con riga scientifica (§10.5).
- Flusso "Appunti → Claude" (§12.3) e testo dalle immagini (§12.1) in primo piano.

### 8.5 Modalità Creativa

- Si attiva al collegamento di una penna/tavoletta o all'apertura di un'app di disegno.
- Priorità all'app di disegno (congelamento delle altre), effetti NOVA spenti, notifiche bloccate, schermo sempre acceso, rotazione bloccata.
- **Filtro luce blu disattivato e modalità colore fedele** (ripristino all'uscita).
- Barra scorciatoie flottante e tasti penna: §9.4.

### 8.6 Modalità Presentazione

- Home "portfolio": cartella di lavori/siti da mostrare ai clienti, a schermo intero.
- Notifiche bloccate, schermo sempre acceso, luminosità alta, trasmissione schermo (Cast di sistema) con un tocco.
- Uscita protetta da PIN opzionale.

### 8.7 Profili automatici

Regole del tipo "quando X allora modalità Y" definite con l'editor automazioni (§12.4): gioco aperto → Gaming; tastiera fisica collegata → Lavoro; orario di studio → Studio; penna/tavoletta collegata → Creativa.

---

## 9. Penna e disegno digitale

### 9.1 Supporto universale

- Rilevamento di ogni input penna tramite `MotionEvent.getToolType()` (`TOOL_TYPE_STYLUS`, `TOOL_TYPE_ERASER`): penne integrate (EMR/USI) e tavolette esterne via USB OTG o Bluetooth (Wacom, XP-Pen, Huion e simili).
- Lettura di pressione, inclinazione, orientamento, hover, pulsanti (`getButtonState`), estremità gomma.
- **Controllo di compatibilità** al collegamento (`InputManager.InputDeviceListener`): cosa è stato rilevato, se arrivano pressione e inclinazione (test interattivo "disegna una linea premendo di più"), supporto OTG, suggerimenti (es. "Per le Wacom One pen tablet su Android 8–13 serve l'app Wacom Center"; "Aggiorna il firmware della tavoletta da PC").
- Nota per il proprietario: una penna di tavoletta grafica **non funziona sullo schermo** del tablet senza digitalizzatore; funziona tramite la tavoletta collegata.

### 9.2 Bassa latenza

- Nelle superfici di disegno NOVA (Foglio rapido, annotazioni, scrittura a mano): rendering **front-buffered** (`androidx.graphics.lowlatency.CanvasFrontBufferedRenderer` o `GLFrontBufferedRenderer`) + **predizione del tratto** (`MotionEventPredictor`). Valutare `androidx.ink` per pennelli e geometria.
- Input non raggruppato (`View.requestUnbufferedDispatch`) per gli eventi penna.
- Obiettivo: latenza percepita del tratto paragonabile ai tablet di fascia alta (misurata con test interni di consegna eventi→frame).

### 9.3 Rifiuto del palmo

- Nelle superfici NOVA: quando la penna è vicina (hover) o attiva, si ignorano i tocchi `TOOL_TYPE_FINGER` con area di contatto ampia, e i tocchi annullati dal sistema (`FLAG_CANCELED`).
- Nelle altre app dipende dall'app stessa: non promesso.

### 9.4 Strumenti

- **Barra scorciatoie flottante** sopra qualsiasi app (overlay): annulla, ripeti, contagocce, dimensione pennello, gomma, salva. Invia all'app di disegno le **scorciatoie da tastiera** corrispondenti (iniezione eventi tasto via Shizuku `input keyevent`), con profili per app (Ibis Paint, Infinite Painter, Krita, Medibang…) in `config/pen-apps.json`.
- **Tasti penna personalizzabili** dove Android li espone; nelle superfici NOVA sempre.
- **Foglio rapido:** gesto dalla home o dalla lista apre subito una tela (penna o dito), salvataggio automatico, esportazione PNG/PDF, condivisione.
- **Galleria lavori:** widget con ultimi disegni (cartelle configurate) e riapertura nell'app di origine.
- **Disegno via PC:** dalla modalità Creativa, scorciatoia "Disegna sul PC" che avvia lo streaming (§11.4) per usare Krita/Photoshop sul PC; la tavoletta resta collegata al tablet.

---

## 10. Tastiera

### 10.1 Base

- App separata `keyboard/`: **fork di HeliBoard** (open source, GPL-3.0, derivata dalla tastiera AOSP, dizionari e correzione maturi). Valutare FlorisBoard solo se HeliBoard risultasse inadatto. Rispettare la licenza: il codice della tastiera resta GPL-3.0; documentarlo in `docs/LICENZE.md`.
- **Nessun permesso internet** nel manifest della tastiera (verificato in CI con un test che legge il manifest unito).
- Tema sincronizzato con NOVA: la tastiera legge i token del tema tramite un `ContentProvider` di NOVA in sola lettura.

### 10.2 Estetica

Aspetto ispirato iOS (tasti arrotondati, spaziature generose, anteprima tasto), aptica e suoni dal vocabolario di §5.4, regolabili o spenti.

### 10.3 Funzioni per tablet — **disattivate di default**, attivabili una per una

- Tastiera **divisa** in orizzontale.
- Tastiera **flottante** ridimensionabile e spostabile.
- **Riga numeri** sempre visibile.

### 10.4 Scrittura

- Barra spaziatrice come trackpad (pressione lunga + trascinamento muove il cursore; con due dita seleziona).
- Scrittura a scorrimento dove la base lo supporta (verificare disponibilità della libreria nella build).
- Italiano + inglese contemporaneamente (multilingua senza cambio manuale).
- Espansioni di testo configurabili (`config/keyboard.json` + editor locale): es. `@@` → email, `firma` → firma completa.
- Cronologia appunti nella tastiera, sincronizzabile con il PC tramite KDE Connect (§11.3).
- Tasto dettatura (riconoscimento vocale di sistema).
- **Scrittura a mano → testo** con penna (ML Kit Digital Ink, italiano, offline) in un pannello dedicato della tastiera.
- **Tasto "Invia a Claude"** (visibile solo se attivato): prende il testo selezionato o il campo corrente, lo copia e apre l'app Claude; **mai** chiamate API. Funziona solo quando premuto.

### 10.5 Varianti per modalità

- Gaming: compatta e semitrasparente.
- Studio: riga extra di simboli (√ π ² ³ ≠ ≤ ≥ ± ∑ ∫ ∞ ° → α β Δ).
- Lavoro: riga con copia, incolla, taglia, annulla, ripeti, seleziona tutto.

### 10.6 Tastiera fisica e mouse

- **Layout italiano corretto** configurato dalla procedura guidata (scelta del layout in `InputManager`/impostazioni di sistema, con verifica interattiva "premi il tasto à").
- Scorciatoie di sistema NOVA: `Ctrl+Spazio` ricerca, `Alt+Tab` cambio app, tasto Meta/Windows → home, `Meta+freccia` split-screen, `Ctrl+Alt+T` Turbo.
- **Elenco scorciatoie**: tenendo premuto `Ctrl` compare l'elenco delle scorciatoie dell'app in primo piano (`Activity.onProvideKeyboardShortcuts` / `requestShowKeyboardShortcuts`) più quelle NOVA.
- Tasti rimappabili (AccessibilityService key filtering, entro i limiti di piattaforma).
- Gesti touchpad/mouse: tasto centrale → multitasking, scorrimento orizzontale → cambio desktop/set.

---

## 11. Collegamento con il PC e accensione remota

### 11.1 Architettura di rete

- **Tailscale** (gratuito) su tablet, PC e "ponte" crea una rete privata sicura: nessuna porta aperta sul router.
- **Ponte sempre acceso** in casa (uno dei due): Raspberry Pi Zero 2 W, oppure un vecchio telefono Android in carica con Tailscale. In alternativa, router con Wake-on-LAN remoto (es. alcuni FRITZ!Box) se disponibile.
- Il ponte esegue un micro-servizio (`tools/wol-bridge/`, script Python o Kotlin per Android, incluso nel repo) che riceve una richiesta autenticata (token condiviso) e invia il magic packet in LAN.

### 11.2 Accensione del PC

- Requisiti sul PC (guidati in `GUIDA-AVVIO.md`): cavo Ethernet, Wake-on-LAN attivo in BIOS/UEFI e nella scheda di rete, "Avvio rapido" di Windows disattivato.
- In NOVA: tasto "Accendi PC" (centro di controllo, ricerca, widget Stato PC, ADAM). Stato in tempo reale nell'isola: "Accensione… → PC raggiungibile".
- Verifica di raggiungibilità via Tailscale (ping/porta nota) ogni pochi secondi per massimo 3 minuti.
- Spegnimento/sospensione remota: tramite KDE Connect (comando) o servizio di ADAM.

### 11.3 KDE Connect (integrazione, non riscrittura)

- NOVA guida l'installazione e l'abbinamento di KDE Connect (Android + Windows) e lo integra: appunti condivisi, invio file nei due sensi, notifiche del tablet sul PC con risposta, comandi remoti, "continua sull'altro dispositivo" (condivisione URL/file all'app KDE Connect).
- NOVA usa gli intent pubblici e la condivisione di sistema; nessuna modifica a KDE Connect.

### 11.4 Streaming di giochi e desktop

- **Moonlight** (tablet) + **Sunshine** (PC): configurazione guidata. Il tasto "Gioca dal PC" fa: accendi PC → attendi raggiungibilità → avvia Moonlight sull'host (deep link/intent supportato, da verificare) → modalità Gaming in versione streaming (Wi-Fi a bassa latenza, notifiche bloccate, nessun congelamento necessario).
- Stesso meccanismo per "Disegna sul PC" e "Desktop remoto".

### 11.5 Gestore file unico

- Vista unificata: memoria del tablet (Storage Access Framework / accesso completo se concesso), Google Drive (tramite SAF/DocumentsProvider dell'app Drive), cartelle del PC (tramite KDE Connect / condivisione SMB opzionale su Tailscale).
- Funzioni: ricerca, anteprime, condivisione, spostamento tra destinazioni.

---

## 12. Intelligenza locale e produttività

### 12.1 Testo dalle immagini

- Selezione e copia del testo da foto, screenshot, pagine fotografate: ML Kit Text Recognition v2 (latino), offline.
- Ingressi: condivisione di un'immagine a NOVA, galleria lavori, dopo uno screenshot (anteprima con "Seleziona testo").

### 12.2 Cerca da schermo

- Gesto/tasto: cattura schermo (MediaProjection, consenso di sistema richiesto ogni sessione) → selezione area → azioni: "Copia testo", "Cerca sul web", "Invia a Claude con una domanda" (immagine + testo negli appunti/condivisione verso app Claude).

### 12.3 Appunti → Claude

- Fotocamera → foto degli appunti (uno o più fogli) → OCR locale → condivisione all'app Claude di immagini + testo con prompt preimpostati: "Riassumi", "Fai uno schema", "Preparami un quiz", "Spiegamelo semplice". Solo condivisione verso l'app Claude, nessuna API.

### 12.4 Automazioni visive

- Editor a blocchi trascinabili (stile Comandi Rapidi): **Quando** (app aperta/chiusa, accessorio collegato, orario, Wi-Fi, batteria, carica, modalità) → **Se** (condizioni) → **Allora** (cambia modalità, apri app, imposta impostazione, mostra avviso, avvia timer, accendi PC, invia a KDE Connect…).
- Salvate come JSON (`config/automations/`), validate da schema, eseguite da un motore deterministico (nessuno scripting arbitrario).

### 12.5 Suggerimenti locali

- Riga di 4–6 app previste in base a ora, giorno, accessori collegati, modalità, ultime app. Modello semplice e trasparente (frequenze condizionate con decadimento temporale) calcolato sul tablet; dati in Room, mai esportati. Pulsante "Perché questo suggerimento?".

### 12.6 StandBy

- Quando il tablet è in carica, sbloccato e in orizzontale (o su richiesta): schermo a tutto campo con orologio grande, calendario, foto, Stato PC, widget a scelta; luminosità ridotta, notte in rosso scuro per non disturbare.

### 12.7 Benessere digitale (solo statistiche, nessun limite)

- Tempo per app e per modalità (ore di studio, gaming, lavoro, disegno) da `UsageStatsManager`. Grafici settimanali. **Nessun blocco o limite** (eccetto il blocco distrazioni opzionale e volontario della modalità Studio).

### 12.8 Comfort occhi

- Filtro caldo e luminosità programmati per orario; disattivazione automatica in modalità Creativa.

### 12.9 ADAM

- Scheda "ADAM" per parlare con ADAM (il sistema AI del proprietario sul PC) tramite il canale che ADAM espone (da definire con il progetto ADAM: endpoint HTTP su Tailscale).
- ADAM può inviare comandi a NOVA tramite un'API locale autenticata (token, solo rete Tailscale): cambiare modalità, avviare timer, accendere/spegnere funzioni, aprire app. Ogni comando ricevuto è registrato e revocabile.

---

## 13. Privacy e sicurezza

### 13.1 Blocco pubblicità di sistema

- Tramite **DNS privato** di Android (`private_dns_mode=hostname`, `private_dns_specifier=<provider>`) impostato via Shizuku; provider selezionabili (es. AdGuard DNS, NextDNS con profilo personale). Nessuna VPN: compatibile con Tailscale.
- Interruttore rapido nel centro di controllo (alcuni siti/app possono rompersi). Elenco eccezioni non disponibile a livello DNS: documentarlo.

### 13.2 Firewall per app

- Tramite Shizuku con i comandi di connettività di sistema (su Android 14 esiste la possibilità di limitare la rete per package; **verificare a runtime** la disponibilità esatta del comando, es. `cmd connectivity set-package-networking-enabled`). Se non disponibile: modalità alternativa basata su VPN locale, attivabile solo quando Tailscale non serve, con avviso chiaro.

### 13.3 Pannello privacy

- Cronologia accessi a fotocamera, microfono, posizione (AppOps via Shizuku: `appops get`), permessi pericolosi per app, revoca rapida.

### 13.4 Credenziali

- Token GitHub **fine-grained**, limitato al solo repository NOVA, permessi minimi: Contents (lettura per Release; scrittura solo per `reports/` e override se abilitati), Actions (scrittura solo per `rollback.yml`). Conservato cifrato (Android Keystore + EncryptedSharedPreferences / Tink).
- Token del ponte WoL e dell'API ADAM: generati casualmente, conservati allo stesso modo.
- Mai credenziali nel repository, nei log o nei report (test che lo verificano sui report generati).

### 13.5 Cartella privata

- App e file protetti da PIN/biometria (`BiometricPrompt`): nascondimento dalla home e dalla ricerca; file in archivio cifrato nell'area privata dell'app.

### 13.6 Salute della batteria

- Cicli (se esposti, `BatteryManager.EXTRA_CYCLE_COUNT` su Android 14), temperatura, stato di salute, consigli di ricarica; avviso temperatura alta in Gaming.

### 13.7 Modalità ospite

- Se il dispositivo supporta più utenti: crea e gestisce un utente ospite (via Shizuku/`pm` dove consentito).
- Altrimenti: "home ospite" con elenco app consentite, nessun accesso a impostazioni, ricerca, cartella privata e NOVA; uscita con PIN.

---

## 14. Affidabilità

### 14.1 Gestione errori

- Crash handler globale: salva stack trace, versione, modulo, ultimi eventi; mostra al riavvio "Si è verificato un errore — Ripara?".
- Nessun crash di un modulo deve far crollare la home: i moduli girano in boundary isolati (try/catch ai confini, supervisione delle coroutine).

### 14.2 Modalità provvisoria

- Componente minimale nello stesso APK, **senza dipendenze dai moduli** e con UI essenziale: griglia app, ricerca semplice, pulsanti "Torna alla versione precedente", "Ripristina impostazioni di sistema modificate", "Ripara", "Apri launcher di sistema".
- Si attiva: 3 crash di avvio consecutivi, 2 crash entro 60 s dopo un aggiornamento, o manualmente (tenere premuto volume giù durante l'avvio della home).
- Il launcher di sistema non viene mai disattivato: se NOVA fosse completamente inutilizzabile, Android lo propone.

### 14.3 Punti di ripristino

- Prima di ogni aggiornamento e di ogni modifica di sistema: snapshot di versione, config attiva, override locali, valori di sistema modificati.

### 14.4 Backup completo

- Config, override, temi, automazioni, profili di gioco, layout home: esportati nel repository GitHub (cartella `config/overrides/<dispositivo>/`) su richiesta o automaticamente ogni giorno. Su un nuovo tablet: accesso a GitHub → ripristino totale in meno di un minuto.

### 14.5 Risparmio energetico intelligente

- Batteria < 20% (o risparmio energetico di sistema attivo): livello grafico Prestazioni, isola semplificata, suggerimenti e indicizzazione sospesi. Ripristino in carica.

### 14.6 Accessibilità

- Supporto TalkBack (descrizioni, ordine di focus), testo grande (scala di sistema rispettata fino a 200%), alto contrasto, modalità a una mano (contenuto abbassato), riduzione movimento.

---

## 15. Budget di prestazioni

Valori iniziali in `perf-budget.json`; la CI e il tablet li applicano. Misure di riferimento sul dispositivo reale (raccolte dal tablet e inviate solo nei report "Ripara" o nella scheda Stato).

| Metrica | Budget |
|---|---|
| Feedback visivo al tocco | ≤ 1 frame |
| Tempo di frame p90 in home (livello Bilanciato, 60 Hz) | ≤ 16,6 ms; frame scartati < 3% |
| Ritorno alla home (warm) | ≤ 250 ms |
| Avvio a freddo del launcher | ≤ 900 ms sul dispositivo di riferimento |
| Apertura ricerca + primo risultato | ≤ 150 ms + ≤ 50 ms per digitazione |
| Apertura centro di controllo | ≤ 120 ms |
| RAM launcher (PSS) inattivo | ≤ 150 MB con moduli default; ogni modulo dichiara il proprio costo |
| CPU a riposo con home visibile | ~0% (nessun lavoro periodico se nulla cambia) |
| Wake-up in background | nessun allarme esatto; lavori WorkManager raggruppati, max 1 ogni 6 h per l'aggiornatore |
| Dimensione APK launcher | ≤ 25 MB |
| Dimensione APK tastiera | ≤ 15 MB senza dizionari extra |
| Peggioramento ammesso per PR (emulatore, vs `main`) | ≤ 10% su ogni metrica |

Regole:
- Ogni nuovo modulo documenta il proprio costo (RAM, avvio, background) in `docs/modules/<id>.md`.
- Superare un budget richiede una decisione esplicita del proprietario, registrata in un ADR (`docs/adr/`).
- **Misure sul campo:** dopo ogni aggiornamento il tablet confronta le metriche reali con quelle della versione precedente; se peggiorano oltre il 15%, l'isola propone "Questa versione è più lenta: tornare indietro o chiedere un'ottimizzazione?".

---

## 16. Strategia di test

| Livello | Strumenti | Copertura richiesta |
|---|---|---|
| Unità | JUnit5/JUnit4, MockK, Turbine | motore config, registro moduli, motore modalità, automazioni, aggiornatore (verifica firma/hash, scelta release), classificazione richieste, filtri anonimizzazione, parser comandi ricerca: **≥ 80% righe** su `core/` |
| Integrazione JVM | Robolectric | servizi, ContentProvider tema, gestione permessi |
| UI | Compose UI Test | home, alfabeto a onda, ricerca, centro di controllo, uscite di emergenza Gaming, modalità provvisoria, procedura guidata |
| Screenshot | Roborazzi | componenti del design system in tema chiaro/scuro, 3 livelli grafici, orizzontale/verticale |
| Prestazioni | Macrobenchmark + JankStats | §15 |
| Contratti | test sui manifest uniti | tastiera senza `INTERNET`; permessi dichiarati = permessi documentati |
| Sicurezza | test dedicati | nessun segreto nei report/log; comandi Shizuku solo da lista consentita |
| Config | validazione JSON Schema + test di caricamento di ogni file in `config/` | 100% file |
| Manuale guidata | checklist in `docs/TEST-MANUALE.md` | cose non automatizzabili (penna reale, WoL reale, Moonlight) — il tablet mostra la checklist nella schermata "Novità" quando un rilascio tocca quelle aree |

---

## 17. Fasi di sviluppo (roadmap)

Ogni fase si chiude solo quando: tutti i test sono verdi, i budget rispettati, la documentazione aggiornata (`docs/`, `CHANGELOG.md`), e il rilascio è installabile e funzionante. Le fasi successive alla 0 possono essere richieste direttamente dal tablet con "Migliora".

### Fase 0 — Fondamenta e ciclo di auto-miglioramento (PRIMA DI TUTTO)

1. Repository multi-modulo, version catalog, convenzioni, `CLAUDE.md`, ktlint/detekt/lint.
2. `core/design`: token, squircle, molle, aptica, componenti base, tre livelli grafici con finto blur.
3. `core/config`: schemi, caricamento a strati, hot reload, validazione, `config/` iniziale.
4. `core/modules`: interfaccia, registro, salute, disattivazione automatica su crash.
5. `core/telemetry` + crash handler + `core/safemode`.
6. `core/device`: profilatore + benchmark + scelta livello.
7. `core/system`: ponte Shizuku con lista consentita e registro reversibile.
8. `core/update`: aggiornatore APK + config, verifica firma/hash, installazione via Shizuku, punti di ripristino.
9. Pulsanti **Migliora** e **Ripara** (prompt, report anonimizzati caricati in `reports/`).
10. Workflow `ci.yml`, `release.yml`, `config-release.yml`, `rollback.yml`, budget in CI, Baseline Profile.
11. Home **minima** ma già curata (lista + griglia semplice) per poter usare NOVA come launcher.
12. Procedura guidata primo avvio: permessi, Shizuku, token GitHub, profilatore.

**Accettazione Fase 0:** il proprietario chiede dal tablet "cambia il colore d'accento in viola" → arriva come aggiornamento config senza reinstallare; chiede "aggiungi un widget orologio" → arriva come APK installato in silenzio; un crash forzato attiva la modalità provvisoria e "Torna alla versione precedente" funziona.

### Fase 1 — Home completa e design
Layout ibrido/lista/griglia, alfabeto a onda, notifiche in linea, swipe azioni, dock, cartelle, libreria app, widget host + widget NOVA, icone uniformate, ricerca universale, isola dinamica, centro di controllo, temi preimpostati, "Ritocco visivo", tutorial interattivo.

### Fase 2 — Strumenti di sistema (fatto in gran parte: vedi `docs/modules/strumenti-di-sistema.md`)
Debloat guidato, congelamento, Turbo, densità/risoluzione con ripristino automatico, scala animazioni, stato del sistema, salute batteria, DNS anti-pubblicità, risparmio energetico intelligente.

### Fase 3 — Modalità
Motore modalità + Gaming completa (con uscite di emergenza, watchdog, pannello, libreria, profili, ridimensionamento, Wi-Fi bassa latenza, precaricamento), Studio, Lavoro (desktop, finestre flottanti, set stile Stage Manager), Creativa, Presentazione, profili automatici.

### Fase 4 — Penna e tastiera
Supporto universale penna, controllo compatibilità, superfici a bassa latenza, foglio rapido, barra scorciatoie, galleria lavori; fork HeliBoard con tutte le funzioni di §10, tastiera fisica e scorciatoie.

### Fase 5 — PC e streaming
Ponte WoL + Tailscale, accensione PC e stato, KDE Connect integrato, Moonlight/Sunshine ("Gioca dal PC", "Disegna sul PC"), gestore file unico, fallback aggiornamenti via ADAM.

### Fase 6 — Intelligenza e rifiniture
Testo dalle immagini, cerca da schermo, appunti → Claude, automazioni visive, suggerimenti locali, StandBy, benessere (solo statistiche), comfort occhi, editor temi, pannello privacy, firewall, cartella privata, modalità ospite, ADAM sul tablet, backup completo, accessibilità completa, schermata "Novità" ricca.

---

## 18. Elementi esclusi e limiti dichiarati

- **Nessuno sblocco di app o giochi a pagamento**, nessun aggiramento di licenze o protezioni anti-copia. Libertà = root opzionale futuro, installazione APK da qualsiasi fonte (con installer chiaro), personalizzazione totale.
- Nessuna promessa di prestazioni "10 volte superiori": l'obiettivo è la massima fluidità percepita e reale ottenibile sull'hardware dato, misurata (§15).
- Nessun overclock, nessuna modifica del kernel, nessuna ROM.
- Nessun asset, marchio o font proprietario di Apple, Niagara Launcher o altri.
- Nessuna chiave API Anthropic e nessun costo a consumo: tutto passa dall'abbonamento del proprietario e da servizi gratuiti (GitHub Actions nei limiti gratuiti per repo privati, Tailscale personale, ML Kit on-device, DNS gratuiti).
- I giochi molto pesanti non gireranno bene in locale: la risposta è lo streaming dal PC o il cloud gaming.

---

## 19. Glossario

- **Shizuku:** app che concede ad altre app privilegi di "shell ADB" senza root, attivata tramite debug wireless.
- **Launcher:** l'app della schermata home.
- **Freeform:** modalità finestre libere di Android.
- **Front-buffered rendering:** disegno diretto sul buffer visibile per ridurre la latenza della penna.
- **Baseline Profile:** elenco di codice precompilato che accelera avvio e animazioni.
- **WoL (Wake-on-LAN):** accensione di un PC tramite un pacchetto di rete.
- **Tailscale:** rete privata virtuale gratuita tra i propri dispositivi.
- **ADR:** Architecture Decision Record, breve documento che registra una decisione tecnica e il suo perché.
