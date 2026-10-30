# Architettura (stato attuale)

Riferimento completo: `SPEC.md` §3. Qui solo ciò che esiste già nel codice.

| Modulo Gradle | Tipo | Contenuto |
|---|---|---|
| `:core:modules` | Kotlin/JVM | `NovaModule`, `ModuleRegistry` (dipendenze, permessi, sospensione dopo crash) |
| `:core:config` | Kotlin/JVM | Modelli, `ConfigEngine` (tre strati + ricarica a caldo), `ConfigValidator` |
| `:core:design` | Android/Compose | Token, `SquircleShape`, molle, aptica, `GraphicsLevel` + controller, `BoxBlur`, `MaterialSurface` |
| `:app` | Android app | Assemblaggio del nucleo e schermata dimostrativa |

`core:modules` e `core:config` sono puro Kotlin: i loro test girano in pochi secondi senza Android.

## Flusso della configurazione

```
assets (= cartella config/ del repo)  ──►  default
pacchetto scaricato (Fase 0, punto 8) ──►  applyBundle()
override locali (filesDir/config-overrides.json) ──► setOverrides()
                     │ fusione profonda → decodifica rigida → validazione
                     ▼
            ConfigEngine.current (StateFlow) ──► tema, registro moduli
```

Un livello non valido viene rifiutato con un motivo leggibile e la configurazione precedente resta attiva.

## Livelli grafici

`MainActivity` inoltra i tempi di frame (`FrameMetrics`) a `GraphicsLevelController`:
5 secondi consecutivi con p90 oltre 16,6 ms → livello più leggero; 10 minuti stabili → un livello più ricco, fino al tetto.
Il tetto sarà calcolato dal profilatore dispositivo (Fase 0, punto 6).
