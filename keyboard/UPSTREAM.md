# Origine e modifiche (GPL-3.0)

Questa cartella è un **fork di [HeliBoard](https://github.com/Helium314/HeliBoard)** (a sua volta derivata dalla tastiera AOSP), commit
`5790f31aba7a470384c5403ee6f9158c468357fa`. La licenza è la **GPL-3.0** (file `LICENSE`); gli avvisi di copyright dei file originali sono
rimasti dove sono. Il README originale è in `UPSTREAM-README.md`.

È un progetto Gradle **separato** da NOVA (strumenti più nuovi: Android Gradle Plugin 9, Gradle 9, Kotlin 2.4, compilazione del codice
nativo con NDK): si compila da qui (`./gradlew :app:assembleRelease`) e lo controlla il workflow `.github/workflows/tastiera.yml`.

## Cosa è stato cambiato rispetto a HeliBoard

| Cosa | Perché |
|---|---|
| `applicationId` = `it.manlio.nova.keyboard`; nome «Tastiera NOVA» | Identità del progetto (SPEC §2.3). Mai da cambiare dopo il primo rilascio. |
| Numero e nome di versione dalle variabili `NOVA_VERSION_CODE` / `NOVA_VERSION_NAME` | Stessa numerazione di NOVA. |
| Firma con la chiave di NOVA, solo in CI (`NOVA_KEYSTORE_*`) | Gli aggiornamenti devono essere firmati come l'app. |
| Solo `armeabi-v7a` e `arm64-v8a` (tolti `x86`, `x86_64`) | Peso e codice nativo da caricare: sono tablet ARM. |
| Dizionari: tenuti **italiano**, **inglese USA** e **inglese GB**; tolti gli altri 15 | Peso (budget 15 MB, SPEC §15). Si possono riaggiungere (vedi sotto). |
| Rimossi `tools/` (generatore di emoji), `art/`, `fastlane/`, `.github/`, `.idea/`, `AI_USAGE.md`, `CONTRIBUTING.md` | Non servono per costruire la tastiera. |
| Nessun permesso Internet | SPEC §10.1: lo verifica `tools/check_keyboard.py` sul manifest unito. |

## Come aggiungere un dizionario

Copia il file `main_<lingua>.dict` da `app/src/main/assets/dicts/` del progetto HeliBoard (allo stesso commit) in questa cartella e ricompila;
ogni dizionario pesa da 0,3 a 10 MB, quindi tieni d'occhio il budget.

## Aggiornare da HeliBoard

Non si fa con un merge automatico (le cartelle sono state alleggerite). Si confrontano le novità di HeliBoard, si riportano a mano le correzioni
che servono e si aggiorna il commit in questo file e in `docs/LICENZE.md`.
