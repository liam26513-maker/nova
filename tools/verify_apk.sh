#!/usr/bin/env bash
# Verifica che un APK sia firmato con la chiave di NOVA (impronta pubblica in NOVA_CERT_SHA256).
# Una firma diversa farebbe rifiutare l'aggiornamento al tablet. Uso: tools/verify_apk.sh <apk>
set -euo pipefail
apksigner=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)/apksigner
"$apksigner" verify --print-certs "$1" | tee "$1.firma.txt"
grep -q "SHA-256 digest: $NOVA_CERT_SHA256" "$1.firma.txt" || { echo "Firma inattesa in $1"; exit 1; }
rm "$1.firma.txt"
