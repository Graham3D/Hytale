import hashlib, json, zipfile
from pathlib import Path

old = Path.home() / 'AppData/Roaming/Hytale/data/pre-release/Saves/RPG/mods/Hywind.jar'
addition = 'Server/NPC/Roles/Undead/Skeleton/Skeleton/Skeleton_Soldier.json'
change = 'rpg/enemies/native-bindings-v1.json'
with zipfile.ZipFile(old) as jar:
    entries = {name: hashlib.sha256(jar.read(name)).hexdigest() for name in jar.namelist()
               if name.endswith('.json') and name != 'manifest.json'}
assert addition not in entries and change in entries
def root(values):
    lines = '\n'.join(f'{name} {values[name]}' for name in sorted(values)) + '\n'
    return hashlib.sha256(lines.encode()).hexdigest()
baseline = {
    'baselineRevision': 'R247-U7P5A',
    'baselineJarSha256': hashlib.sha256(old.read_bytes()).hexdigest(),
    'jsonAssetCount': len(entries),
    'allowedAdded': [addition],
    'allowedChanged': [change],
    'unchangedJsonSha256': root({name: digest for name, digest in entries.items() if name != change}),
    'baselineChangedAssetSha256': {change: entries[change]},
}
Path('tools/u7p5-r247-package-baseline.json').write_text(json.dumps(baseline, indent=2) + '\n', encoding='utf-8')
