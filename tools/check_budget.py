"""Controlla i budget statici di perf-budget.json (oggi: dimensione dell'APK del launcher).

Uso: python tools/check_budget.py <apk>
"""
import json
import os
import sys

budget = json.load(open("perf-budget.json", encoding="utf-8"))
apk = sys.argv[1]
size_mb = os.path.getsize(apk) / (1024 * 1024)
limit = budget["launcherApkMb"]
print(f"APK {size_mb:.2f} MB (budget {limit} MB)")
if size_mb > limit:
    print("ERRORE: APK oltre il budget (SPEC §15). Serve un ADR approvato per alzarlo.")
    sys.exit(1)
