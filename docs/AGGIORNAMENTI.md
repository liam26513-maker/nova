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
