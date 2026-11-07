# Licenze di terze parti

| Componente | Licenza | Uso |
|---|---|---|
| AndroidX / Jetpack Compose | Apache-2.0 | UI e componenti Android |
| Kotlin, kotlinx.coroutines, kotlinx.serialization | Apache-2.0 | Linguaggio e librerie |
| detekt, ktlint-gradle | Apache-2.0 / MIT | Solo in fase di build |
| jsonschema (Python) | MIT | Solo in CI |
| AndroidX Graphics Core (`androidx.graphics:graphics-core`) | Apache-2.0 | Disegno «front-buffered» nel Foglio rapido |
| AndroidX Input Motion Prediction (`androidx.input:input-motionprediction`) | Apache-2.0 | Previsione del tratto nel Foglio rapido |
| ONNX Runtime (`com.microsoft.onnxruntime:onnxruntime-android`) | MIT | Esegue i modelli della lettura del testo (`feature/ocr`) |
| Modelli PaddleOCR PP-OCRv6 tiny (`det.onnx`, `rec.onnx`), di PaddlePaddle, convertiti in ONNX da RapidAI/RapidOCR | Apache-2.0 | Lettura del testo dalle foto, sul tablet (`feature/ocr/src/main/assets/ocr`) |

Coefficienti degli angoli continui (`SquircleShape`): costanti geometriche, nessun codice di terzi.
Nessun font, icona o asset proprietario è incluso.

## Tastiera di NOVA (`keyboard/`)

| Componente | Licenza | Uso |
|---|---|---|
| **HeliBoard** (commit `5790f31`, Helium314 e collaboratori), derivata dalla tastiera AOSP (Android Open Source Project) | **GPL-3.0** (`keyboard/LICENSE`); parti Apache-2.0 (`keyboard/LICENSE-Apache-2.0`) e CC-BY-SA-4.0 (`keyboard/LICENSE-CC-BY-SA-4.0`) | Base della tastiera: fork modificato, distribuito con il suo codice sorgente |
| Dizionari (`main_it.dict`, `main_en-US.dict`, `main_en-GB.dict`) | Vedi `keyboard/UPSTREAM-README.md` (dizionari della community AOSP/HeliBoard) | Correzione e suggerimenti |
| AndroidX, Compose Material 3, kotlinx.serialization, reorderable (sh.calvin), colorpicker-compose (skydoves) | Apache-2.0 | Dipendenze di HeliBoard |

Il codice della tastiera **resta GPL-3.0** (regola di CLAUDE.md): i file modificati mantengono gli avvisi di copyright originali. Cosa è stato cambiato rispetto a HeliBoard: vedi `keyboard/UPSTREAM.md`.
