"""Valida ogni file di config/ contro il suo schema (usato dalla CI).

Uso: python tools/validate_config.py [cartella-config]
Ogni file JSON deve avere uno schema associato: un file senza schema è un errore.
"""
import json
import sys
from pathlib import Path

from jsonschema import Draft202012Validator

SCHEMA_FOR = {
    "meta.json": "meta",
    "brand.json": "brand",
    "modules.json": "modules",
    "modes.json": "modes",
    "debloat.json": "debloat",
    "theme/*.json": "theme",
}


def schema_name(rel: Path):
    for pattern, name in SCHEMA_FOR.items():
        if rel.match(pattern) and len(rel.parts) == len(Path(pattern).parts):
            return name
    return None


def main(root: Path) -> int:
    errors = []
    files = sorted(p for p in root.rglob("*.json") if "schema" not in p.relative_to(root).parts)
    for f in files:
        rel = f.relative_to(root)
        name = schema_name(rel)
        if name is None:
            errors.append(f"{rel}: nessuno schema associato (aggiungilo in tools/validate_config.py)")
            continue
        schema = json.loads((root / "schema" / f"{name}.schema.json").read_text(encoding="utf-8"))
        Draft202012Validator.check_schema(schema)
        doc = json.loads(f.read_text(encoding="utf-8"))
        for e in Draft202012Validator(schema).iter_errors(doc):
            errors.append(f"{rel}: {'/'.join(map(str, e.path)) or '(radice)'}: {e.message}")
    for e in errors:
        print("ERRORE", e)
    print(f"{len(files)} file controllati, {len(errors)} errori")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(Path(sys.argv[1] if len(sys.argv) > 1 else "config")))
