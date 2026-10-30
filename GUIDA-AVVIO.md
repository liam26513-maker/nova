# Guida di avvio NOVA — per Manlio

Questa guida ti accompagna dall'idea al primo aggiornamento chiesto dal tablet. Non serve scrivere codice. I tre file del progetto sono:

- **SPEC.md** — cosa costruire (la specifica completa).
- **CLAUDE.md** — come Claude Code deve lavorare.
- **GUIDA-AVVIO.md** — questa guida.

---

## Passo 1 — Dati del tablet ✅ fatto

I dati reali (Allwinner A523, Mali-G57, 6 GB RAM, 128 GB, schermo 1280×800 a 60 Hz) sono già in `SPEC.md` §2.1.

## Passo 2 — GitHub (10 minuti)

1. Crea un account gratuito su github.com, se non ce l'hai.
2. Crea un repository **privato** chiamato `nova`.
3. Carica nel repository i tre file: `SPEC.md`, `CLAUDE.md`, `GUIDA-AVVIO.md` (pulsante "Add file → Upload files").

## Passo 3 — Collega Claude Code al repository

1. Apri **claude.ai/code** (dal PC o dal tablet) con il tuo account Pro.
2. Collega GitHub quando te lo chiede e autorizza l'accesso al repository `nova`.

## Passo 4 — Avvia la Fase 0

Il tuo piano Pro ha limiti d'uso ogni 5 ore: la Fase 0 è grande, quindi dividila in più sessioni. Questa è la prima richiesta da incollare:

```
Leggi CLAUDE.md e SPEC.md per intero. Stiamo iniziando la Fase 0 (§17).
In questa sessione realizza i punti 1–4 della Fase 0: struttura multi-modulo,
design system con i tre livelli grafici, motore di configurazione a strati con
schemi, registro moduli. Includi test, documentazione in docs/ e CHANGELOG.
Apri una PR. Alla fine dimmi in italiano semplice cosa hai fatto e qual è il
prossimo blocco della Fase 0 da chiedere.
```

Nelle sessioni successive chiedi: "Continua la Fase 0 dal punto X secondo SPEC.md", finché la Fase 0 non è completa.

**Unisci le PR:** finché la CI non è attiva, controlla che Claude dica che i test passano e premi "Merge" su GitHub. Dopo il punto 10 della Fase 0 la CI e l'unione automatica faranno da sole.

## Passo 5 — La chiave di firma (IMPORTANTISSIMO)

Al punto 10 della Fase 0 Claude Code ti guiderà a creare la **chiave di firma** delle app e a salvarla nei "Secrets" di GitHub.

**Fai almeno due copie di backup della chiave e delle password** (per esempio una su chiavetta USB e una nel tuo Google Drive personale, in una cartella privata). Se la perdi, non potrai più aggiornare NOVA senza disinstallarla e perdere le impostazioni.

## Passo 6 — Prima installazione sul tablet

1. Sul tablet apri la pagina **Releases** del repository su GitHub e scarica l'APK più recente.
2. Consenti l'installazione da questa fonte quando Android lo chiede.
3. Apri NOVA: parte la **configurazione guidata**.

### Shizuku (la guida in NOVA te lo spiega passo passo)

1. Impostazioni → Info tablet → tocca 7 volte "Numero build" per attivare le **Opzioni sviluppatore**.
2. Installa **Shizuku** dal Play Store.
3. In Opzioni sviluppatore attiva **Debug wireless** e associa Shizuku seguendo la sua notifica.
4. Dopo ogni riavvio del tablet potrebbe servire riattivarlo: NOVA te lo segnala con un tocco.

### Token GitHub

La configurazione guidata ti chiederà un token per leggere gli aggiornamenti:

1. Su GitHub: Settings → Developer settings → **Fine-grained tokens** → Generate new token.
2. Repository: **solo `nova`**.
3. Permessi: *Contents: Read and write*, *Actions: Read and write*, *Metadata: Read*.
4. Copia il token e incollalo in NOVA. Viene salvato cifrato sul tablet.

### Imposta NOVA come home

Quando Android lo chiede, scegli NOVA come app Home predefinita. Il launcher di sistema resta installato come paracadute.

## Passo 7 — Il primo "Migliora"

Prova il ciclo completo:

1. In NOVA premi **Migliora** e scrivi: "cambia il colore d'accento in viola".
2. NOVA copia la richiesta completa e apre Claude Code: incolla e invia.
3. Claude modifica, la CI controlla, GitHub pubblica.
4. Dopo qualche minuto il tablet applica il cambiamento da solo, senza reinstallare.

Se funziona, il sistema è vivo: da qui in poi puoi chiedere tutto il resto (Fasi 1–6) direttamente dal tablet.

## Passo 8 (Fase 5) — Accensione del PC da remoto

Da fare quando arrivi alla Fase 5; NOVA ti guiderà anche dall'interno.

1. Collega il PC al router **con il cavo Ethernet**.
2. Nel BIOS/UEFI attiva **Wake on LAN** (a volte si chiama "Power On by PCI-E").
3. In Windows: Gestione dispositivi → scheda di rete → Proprietà → Risparmio energia: spunta "Consenti a questo dispositivo di riattivare il computer"; in Avanzate attiva "Wake on Magic Packet".
4. Pannello di controllo → Opzioni risparmio energia → disattiva **Avvio rapido**.
5. Installa **Tailscale** su PC, tablet e sul "ponte" (Raspberry Pi Zero 2 W, circa 20-30 €, oppure un vecchio telefono Android sempre in carica).
6. Installa sul PC **Sunshine** (per lo streaming) e **KDE Connect**; sul tablet **Moonlight** e **KDE Connect** (la configurazione guidata di NOVA lo fa per te).

## Se qualcosa va storto

- **NOVA non si apre o crasha:** dopo pochi tentativi parte da sola la **modalità provvisoria**, con il pulsante "Torna alla versione precedente".
- **Vuoi forzarla:** tieni premuto volume giù mentre si apre la home.
- **Nel peggiore dei casi:** Android ti propone il launcher di sistema; da lì apri NOVA → Ripara.
- **Un gioco si blocca in modalità Gaming:** tieni premuto volume giù per 3 secondi, oppure fai doppio swipe dal bordo.

## Promemoria

- Le richieste vanno scritte in modo semplice, una cosa per volta: funzionano meglio.
- Se Claude dice che qualcosa non è possibile su Android senza root, ti propone l'alternativa più vicina.
- Tutto resta tuo: codice su GitHub privato, dati sul tablet, nessuna chiave API e nessun costo oltre al piano Pro.
