# Aggiornamenti: contratto delle release

Come `core/update` trova e verifica gli aggiornamenti (SPEC §4.5–4.6). Il workflow di rilascio (Fase 0, punto 10) **deve** produrre esattamente questi nomi.

## App
- Release GitHub con tag `vX.Y.Z` (non bozza, non pre-release).
- Asset `nova-<versionCode>.apk`: APK release firmato con la chiave di NOVA.
- Asset `nova-<versionCode>.apk.sha256`: impronta SHA-256 nel formato di `sha256sum`.
- Il tablet installa solo se, tutte insieme:
  - il `versionCode` è maggiore di quello installato;
  - l'impronta corrisponde;
  - il pacchetto è `it.manlio.nova`;
  - la versione nel file è quella attesa;
  - **la firma è identica** a quella dell'app installata.

## Configurazione (aggiornamento a caldo)
- Release con tag `config-vX.Y.Z`.
- Asset `nova-config.json`: documento completo `{ "meta": …, "brand": …, "theme": …, "modules": … }`, con `meta.version` uguale a `X.Y.Z`.
- Asset `nova-config.json.sha256`.
- Si applica se la versione è maggiore di quella attiva. Il motore la valida: un pacchetto non valido viene rifiutato e resta quello precedente.
- Il tablet conserva gli ultimi 10 pacchetti: "Torna alla configurazione precedente" è istantaneo e non usa la rete.

## Sicurezza
- Token GitHub fine-grained limitato al repository `nova`, salvato cifrato con una chiave dell'Android Keystore e inviato solo ad `api.github.com`. Nel rinvio verso lo storage dei file non viene inviato.
- L'APK viene scaricato in `Android/data/it.manlio.nova/cache/updates/`, l'unica cartella da cui il comando consentito `InstallApk` accetta file.

## Quando si controlla
- All'avvio di NOVA, ogni 6 ore (WorkManager, solo con rete) e con "Controlla ora".
- La configurazione si applica sempre da sola.
- L'app si installa da sola solo nei controlli automatici e solo con Shizuku attivo. Altrimenti compare "Installa" e Android chiede conferma.
- Prima di ogni cambio viene salvato un punto di ripristino (ultimi 10).

## Come nascono le release (GitHub Actions)

| Workflow | Quando parte | Cosa pubblica |
|---|---|---|
| `release.yml` | merge su `main` che tocca il codice (non solo `config/` o documenti) | `vX.Y.<code>` con `nova-<code>.apk` firmato + `.sha256`; note dal `CHANGELOG.md` |
| `config-release.yml` | merge su `main` che tocca `config/` | `config-v<meta.version>` con `nova-config.json` (meta, brand, modules) + `.sha256`; se la versione non è stata aumentata avvisa e non pubblica |
| `rollback.yml` | dal tablet: Impostazioni → Aggiornamenti → "Torna a una versione precedente" (oppure a mano da Actions) | ricompila il codice di un tag passato con un `versionCode` più alto, così si installa sopra |

- **versionCode:** il più alto tra le release esistenti + 1, partendo da 101 (`tools/next_version.py`). Le anteprime di sviluppo hanno 1, quindi qualsiasi release le sostituisce.
- **Firma:** la chiave sta solo nei segreti del repository (`NOVA_KEYSTORE_BASE64`, `NOVA_KEYSTORE_PASSWORD`, `NOVA_KEY_ALIAS`, `NOVA_KEY_PASSWORD`) e in una copia offline del proprietario. Il file viene creato nel runner solo per la build e poi cancellato. **Non cambiarla mai:** con un'altra firma il tablet rifiuta gli aggiornamenti.
- **Budget:** CI e rilascio falliscono se l'APK supera `launcherApkMb` di `perf-budget.json` (`tools/check_budget.py`). I budget dinamici (avvio, fotogrammi) verranno misurati con Macrobenchmark quando ci sarà un dispositivo in CI; per ora si verificano a mano (docs/TEST-MANUALE.md).

## Ritorno d'emergenza dal tablet

Da usare solo quando un aggiornamento ha creato un problema che le versioni successive non risolvono.

1. Impostazioni → Aggiornamenti → **Torna a una versione precedente**: NOVA mostra le versioni più vecchie di quella installata (al massimo 10, dalla più recente).
2. Si sceglie la versione e si conferma (senza conferma non parte nulla).
3. NOVA salva un punto di ripristino e avvia `rollback.yml` con l'API di GitHub (`POST /actions/workflows/rollback.yml/dispatches`, input `tag`). Serve il permesso del token **Actions: Read and write**; se manca, NOVA lo dice.
4. Dopo 10 minuti NOVA controlla da sola gli aggiornamenti: la nuova release (codice vecchio, `versionCode` più alto) si installa come qualsiasi aggiornamento, in silenzio se Shizuku è attivo.
5. La prima correzione pubblicata dopo avrà un `versionCode` ancora più alto e sostituirà il ripristino in automatico.

Android non permette di installare una versione più vecchia sopra una più nuova senza perdere i dati: per questo il ritorno passa sempre da una nuova build.
