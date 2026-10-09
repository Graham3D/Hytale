"""Versioned native Tile + Custom biome coverage, using existing authored zone bands."""
import hashlib, json, re, sys, zipfile
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
ASSETS = Path.home()/"AppData/Roaming/Hytale/install/pre-release/package/game/latest/Assets.zip"
OUT = ROOT/"src/main/resources/rpg/progression/campaign-biomes-v1.json"
registry=json.loads((ROOT/"src/main/resources/rpg/progression/enemy-registry.json").read_text())
zones={}
for row in registry["biomes"]:
    region=zones.setdefault(row["zoneId"],row["rpgBand"])
    assert region==row["rpgBand"], "Ambiguous authored region"
rows={}; excluded=[]
with zipfile.ZipFile(ASSETS) as z:
    for path in sorted(z.namelist()):
        m=re.fullmatch(r"Server/World/Default/Zones/([^/]+)/(Tile|Custom)\.([^/]+)\.json",path)
        if not m: continue
        zone,kind,biome=m.groups()
        if zone not in zones:
            excluded.append(dict(asset=path,reason="REGION_UNMAPPED_NO_AUTHORED_BAND"));continue
        key=f"Default/{zone}/{biome}"
        entry=rows.setdefault(key,dict(key=key,region=zones[zone],sources=[]))
        entry["sources"].append(dict(asset=path,sha256=hashlib.sha256(z.read(path)).hexdigest()))
payload=dict(revision="r244-campaign-biomes-u7p5",biomes=list(rows.values()),excluded=excluded)
text=json.dumps(payload,indent=2)+"\n"
if '--verify' in sys.argv:
    assert OUT.read_text()==text, 'Campaign biome coverage is stale'
else: OUT.write_text(text)
print(f'Campaign biome coverage: {len(rows)} supported keys; {len(excluded)} assets without an authored region')
