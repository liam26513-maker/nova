"""Crea il pacchetto di configurazione da pubblicare (contratto: docs/AGGIORNAMENTI.md).

Contiene meta, brand, modules e modes. I temi chiaro/scuro restano quelli dentro l'app: il motore
sceglie il tema in base alla modalità di sistema, e un pacchetto con un solo tema la ignorerebbe.
Uso: python tools/config_bundle.py config > nova-config.json
"""
import json
import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else "config")
bundle = {name: json.loads((root / f"{name}.json").read_text(encoding="utf-8")) for name in ("meta", "brand", "modules", "modes")}
print(json.dumps(bundle, ensure_ascii=False, indent=2))
