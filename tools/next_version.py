"""Calcola i numeri di versione dai tag delle release esistenti (vX.Y.<code>).

Uso:
  python tools/next_version.py <tag>...              -> "<code> <name> <standby>"  (release normale)
  python tools/next_version.py --rollback <tag>...   -> "<code> <name>"            (rollback.yml)

Schema (docs/AGGIORNAMENTI.md), sempre crescente e senza collisioni:
- release normale: prossima decina sopra il tag più alto (110, 120, ...);
- APK di ripristino allegato alla release: release + 1 (sempre dispari, mai un tag);
- ricompilazione da rollback.yml: tag più alto + 2 (sempre pari, mai uguale a un ripristino).
"""
import re
import sys

FIRST = 100  # le anteprime di sviluppo usano 1: le release partono sopra


def highest(tags):
    codes = [int(m.group(1)) for t in tags if (m := re.fullmatch(r"v\d+\.\d+\.(\d+)", t.strip()))]
    return max([FIRST, *codes])


def next_release(tags):
    code = (highest(tags) // 10 + 1) * 10
    return code, f"0.1.{code}", code + 1


def next_rollback(tags):
    code = highest(tags) + 2
    return code, f"0.1.{code}"


if __name__ == "__main__":
    assert next_release([]) == (110, "0.1.110", 111)
    assert next_release(["v0.1.101", "config-v0.2.0", "v0.1.102", "rubbish"]) == (110, "0.1.110", 111)
    assert next_release(["v0.1.110", "v0.1.112"]) == (120, "0.1.120", 121)
    assert next_rollback(["v0.1.110"]) == (112, "0.1.112")
    assert next_rollback(["v0.1.110", "v0.1.118"]) == (120, "0.1.120")
    assert next_release(["v0.1.120"]) == (130, "0.1.130", 131)  # dopo un ripristino pari a una decina
    args = sys.argv[1:]
    if args[:1] == ["--rollback"]:
        print(*next_rollback(args[1:]))
    else:
        print(*next_release(args))
