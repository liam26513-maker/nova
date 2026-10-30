"""Calcola il prossimo versionCode dai tag delle release esistenti (vX.Y.<code>).

Uso: python tools/next_version.py <tag1> <tag2> ...   (i tag arrivano da `gh release list`)
Stampa: <versionCode> <versionName>. Il versionCode cresce sempre, anche con i ripristini.
"""
import re
import sys

FIRST = 101  # le anteprime di sviluppo usano 1: le release partono sopra


def next_version(tags):
    codes = [int(m.group(1)) for t in tags if (m := re.fullmatch(r"v\d+\.\d+\.(\d+)", t.strip()))]
    code = max([FIRST - 1, *codes]) + 1
    return code, f"0.1.{code}"


if __name__ == "__main__":
    assert next_version([]) == (101, "0.1.101")
    assert next_version(["v0.1.101", "config-v0.2.0", "v0.1.105", "rubbish"]) == (106, "0.1.106")
    code, name = next_version(sys.argv[1:])
    print(code, name)
