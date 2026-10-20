import json, hashlib, zipfile
from pathlib import Path

old = Path.home() / 'AppData/Roaming/Hytale/data/pre-release/Saves/RPG/mods/Hywind.jar'
new = Path('build/libs/HyARPG.jar')
def collect(path):
    with zipfile.ZipFile(path) as jar:
        return {name: hashlib.sha256(jar.read(name)).hexdigest() for name in jar.namelist()
                if name.endswith('.json') and name != 'manifest.json'}
a, b = collect(old), collect(new)
print('old', len(a), 'new', len(b))
print('added', sorted(b.keys() - a.keys()))
print('removed', sorted(a.keys() - b.keys()))
print('changed', sorted(name for name in a.keys() & b.keys() if a[name] != b[name]))
