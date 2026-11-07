# Tastiera di NOVA (`keyboard/`)

Stato: **base inserita e allegata alle release (Fase 4, primi passi)**. Fork di HeliBoard (GPL-3.0); vedi `keyboard/UPSTREAM.md` per l'origine e le modifiche e
`docs/LICENZE.md` per le licenze. Mancano ancora quasi tutte le funzioni di SPEC §10 (elenco in fondo).

## Com'è fatta

- **Progetto Gradle separato** (`keyboard/`, con il suo `gradlew`): HeliBoard richiede Android Gradle Plugin 9.1, Gradle 9.7, Kotlin 2.4 e
  compileSdk 37, NOVA usa versioni più vecchie e non conviene alzarle tutte per una tastiera. `settings.gradle` di NOVA non la include.
- **Pacchetto** `it.manlio.nova.keyboard` (i pacchetti Kotlin/Java interni restano `helium314.keyboard…`: rinominarli renderebbe quasi impossibile
  riportare le correzioni di HeliBoard).
- **CI:** `.github/workflows/tastiera.yml`, solo quando cambia `keyboard/`: prove di `tools/check_keyboard.py`, `assembleRelease`, controllo del
  **manifest unito** (nessun `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`; pacchetto giusto) e del peso contro `keyboardApkMb` (15 MB).
- **Firma e rilascio:** come l'app, con le variabili `NOVA_KEYSTORE_*` (solo in CI). `release.yml` ha un job `tastiera` (facoltativo, in parallelo) che allega alla release `nova-tastiera-<code>.apk` + `.sha256` dopo aver verificato firma, permessi e peso. La **prima installazione** è a mano dalla pagina Releases; dopo la aggiorna NOVA (`Updater.updateKeyboard`), solo con Shizuku pronto e quando NOVA stessa non ha aggiornamenti da installare.

## Profilo NOVA (sensazione)

Modificati in `Defaults.kt` (valgono per le installazioni nuove) e scritti una volta sola da `AppUpgrade.kt` per chi aveva la prima versione:
tasti arrotondati con bordo (`STYLE_ROUNDED`, `PREF_THEME_KEY_BORDERS`), **tocco leggero**: vibrazione accesa e brevissima (`PREF_VIBRATION_DURATION_SETTINGS` = 10 ms), clic sommesso (`PREF_KEYPRESS_SOUND_VOLUME` = 0,3), niente fumetto del tasto (`PREF_POPUP_ON` = false), **clic sonoro** acceso se il tablet non ha il
motorino (`Defaults.hasVibrator`), niente `PREF_SHOW_HINTS`, niente suggerimenti di emoji (`PREF_SUGGEST_EMOJIS`, `PREF_INLINE_EMOJI_SEARCH`) né degli
appunti (`PREF_SUGGEST_CLIPBOARD_CONTENT`). Il numero di versione è `NOVA_VERSION_BASE` (100000) + quello di NOVA: `upgradeKind` garantisce che le
migrazioni di HeliBoard non si rieseguano (`NovaUpgradeTest`, girano in `tastiera.yml`).

**Limite noto:** la scrittura a scorrimento (gesture typing) non c'è: in HeliBoard richiede la libreria proprietaria di Google (`libjni_latinimegoogle.so`),
non ridistribuibile. Servirebbe una libreria aperta. **Non misurato:** la latenza; da misurare sul tablet prima di promettere numeri.

## Ottimizzazioni già fatte (a livello di costruzione)

- Solo ARM a 32 e 64 bit (via x86/x86_64): meno codice nativo nell'APK e meno da caricare.
- Solo i dizionari di italiano e inglese (via 15 lingue): APK molto più leggero e installazione più rapida.
- Codice ridotto e ottimizzato da R8 (`isMinifyEnabled`, già nella versione release di HeliBoard).

Non ho ancora misurato velocità di scrittura o avvio sul tablet: ogni miglioramento di velocità andrà confrontato con una misura (latenza dal
tocco al tasto, apertura della tastiera, memoria) prima e dopo.

## Già disponibili (da HeliBoard, in Impostazioni della tastiera)

Tastiera divisa, flottante e riga numeri, tutte spente di serie (§10.3). Cursore con lo swipe sulla barra spaziatrice, cronologia appunti, italiano e inglese insieme.

Anche le espansioni di testo ci sono: Impostazioni della tastiera → Correzione testo → Dizionario personale, campo «Scorciatoia».

## Invia a Claude (§10.4)

Tasto della barra strumenti `SEND_TO_CLAUDE`, spento di serie: si attiva in Impostazioni della tastiera → Barra degli strumenti. Copia il testo selezionato (o tutto il campo) e lo condivide con `com.anthropic.claude`. Nessuna rete, nessuna chiave.

## Tema NOVA (§10.1)

Tema «NOVA» chiaro e scuro, predefinito, con i colori di `config/theme`. L'accento scelto in Personalizza la Home arriva da `AccentProvider` di NOVA (sola lettura, solo quel colore) e la tastiera si ricolora subito; con l'accento automatico resta quello del tema. Chiaro o scuro seguono Android, non la scelta di tema della Home.

## Variante Lavoro (§10.5)

Con la modalità Lavoro accesa, accanto ai suggerimenti ci sono copia, incolla, taglia, annulla, ripeti e seleziona tutto, al posto dei tasti fissati. Uscendo tornano quelli di prima; nessuna preferenza viene toccata. 

## Variante Studio (§10.5)

Con la modalità Studio accesa, in cima alla tastiera c'è una riga di dieci simboli (√ π ² ≠ ≤ ≥ ± ∑ ° α); gli altri si trovano tenendo premuto il tasto (³ ∫ ∞ → β Δ). Uscendo torna la tastiera di prima. La variante Gaming non c'è ancora.

## Da fare (SPEC §10)

Aspetto iOS, selezione con due dita sulla barra spaziatrice, appunti → PC, scrittura a mano → testo, varianti per modalità, scorciatoie della
tastiera fisica. Prima installazione dall'app.
