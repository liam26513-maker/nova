"""Controlli sull'APK della tastiera (SPEC §10.1, §15): niente Internet, pacchetto giusto, peso entro il budget.

Uso: python tools/check_keyboard.py <cartella con keyboard/app/build> [apk]
  - legge il manifest UNITO della build release (quello che finisce davvero nell'APK, con i permessi delle librerie);
  - fallisce se manca (un controllo che non trova il file non deve passare in silenzio);
  - se è dato un APK, ne controlla anche la dimensione contro keyboardApkMb di perf-budget.json.
"""
import glob
import json
import os
import re
import sys

PACKAGE = "it.manlio.nova.keyboard"
FORBIDDEN = ("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", "android.permission.ACCESS_WIFI_STATE")


def merged_manifest(build_dir):
    found = glob.glob(os.path.join(build_dir, "intermediates", "merged_manifests", "release", "**", "AndroidManifest.xml"), recursive=True)
    return found[0] if found else None


def check_manifest(text):
    """Restituisce l'elenco dei problemi trovati nel manifest unito (vuoto = tutto a posto)."""
    problems = []
    if f'package="{PACKAGE}"' not in text and f'"{PACKAGE}"' not in text:
        problems.append(f"il pacchetto non è {PACKAGE}")
    for name in re.findall(r'<uses-permission[^>]*android:name="([^"]+)"', text):
        if name in FORBIDDEN:
            problems.append(f"permesso vietato nella tastiera: {name}")
    return problems


def main(argv):
    build_dir = argv[1]
    manifest = merged_manifest(build_dir)
    if manifest is None:
        print(f"ERRORE: manifest unito non trovato in {build_dir}")
        return 1
    problems = check_manifest(open(manifest, encoding="utf-8").read())
    if len(argv) > 2:
        size_mb = os.path.getsize(argv[2]) / (1024 * 1024)
        limit = json.load(open("perf-budget.json", encoding="utf-8"))["keyboardApkMb"]
        print(f"APK della tastiera {size_mb:.2f} MB (budget {limit} MB)")
        if size_mb > limit:
            problems.append("APK oltre il budget (SPEC §15): serve un ADR approvato per alzarlo")
    for p in problems:
        print("ERRORE:", p)
    if not problems:
        print("Tastiera: nessun permesso Internet, pacchetto e peso a posto.")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
