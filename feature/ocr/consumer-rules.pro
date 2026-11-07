# ONNX Runtime chiama queste classi dal codice nativo (JNI) per nome: se R8 le rinomina o le toglie, la lettura del testo
# chiude l'app (provato sul tablet il 7 novembre 2026: «JNI DETECTED ERROR ... java_class == null»).
-keep class ai.onnxruntime.** { *; }
