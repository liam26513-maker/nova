# Tastiera di NOVA (`keyboard/`)

Stato: **base inserita (Fase 4, primo passo)**. Fork di HeliBoard (GPL-3.0); vedi `keyboard/UPSTREAM.md` per l'origine e le modifiche e
`docs/LICENZE.md` per le licenze. Mancano ancora quasi tutte le funzioni di SPEC §10 (elenco in fondo).

## Com'è fatta

- **Progetto Gradle separato** (`keyboard/`, con il suo `gradlew`): HeliBoard richiede Android Gradle Plugin 9.1, Gradle 9.7, Kotlin 2.4 e
  compileSdk 37, NOVA usa versioni più vecchie e non conviene alzarle tutte per una tastiera. `settings.gradle` di NOVA non la include.
- **Pacchetto** `it.manlio.nova.keyboard` (i pacchetti Kotlin/Java interni restano `helium314.keyboard…`: rinominarli renderebbe quasi impossibile
  riportare le correzioni di HeliBoard).
- **CI:** `.github/workflows/tastiera.yml`, solo quando cambia `keyboard/`: prove di `tools/check_keyboard.py`, `assembleRelease`, controllo del
  **manifest unito** (nessun `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`; pacchetto giusto) e del peso contro `keyboardApkMb` (15 MB).
- **Firma:** come l'app, con le variabili `NOVA_KEYSTORE_*` (solo in CI).

## Ottimizzazioni già fatte (a livello di costruzione)

- Solo ARM a 32 e 64 bit (via x86/x86_64): meno codice nativo nell'APK e meno da caricare.
- Solo i dizionari di italiano e inglese (via 15 lingue): APK molto più leggero e installazione più rapida.
- Codice ridotto e ottimizzato da R8 (`isMinifyEnabled`, già nella versione release di HeliBoard).

Non ho ancora misurato velocità di scrittura o avvio sul tablet: ogni miglioramento di velocità andrà confrontato con una misura (latenza dal
tocco al tasto, apertura della tastiera, memoria) prima e dopo.

## Da fare (SPEC §10)

Aspetto iOS e tema di NOVA (`ContentProvider` dei token), tastiera divisa/flottante/riga numeri (disattivate di default), trackpad sulla barra
spaziatrice, espansioni di testo, appunti → PC, dettatura, scrittura a mano → testo, «Invia a Claude», varianti per modalità, layout italiano della
tastiera fisica e scorciatoie. Pubblicazione dell'APK nelle release e installazione/aggiornamento dall'app: passo successivo.
