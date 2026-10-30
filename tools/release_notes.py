"""Estrae dal CHANGELOG la sezione "## Non rilasciato" come note di rilascio.

shortcut: la sezione non viene svuotata a ogni release, quindi le note sono cumulative; dividerla per versione quando le release diventano frequenti.

Uso: python tools/release_notes.py CHANGELOG.md > note.md
"""
import sys

text = open(sys.argv[1], encoding="utf-8").read()
start = text.find("## Non rilasciato")
if start < 0:
    print("Aggiornamento di NOVA.")
    sys.exit(0)
body = text[start + len("## Non rilasciato"):]
end = body.find("\n## ")
print((body if end < 0 else body[:end]).strip() or "Aggiornamento di NOVA.")
