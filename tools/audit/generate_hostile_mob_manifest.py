"""Generate a read-only hostile NPC audit from the installed release assets.

Usage: python tools/audit/generate_hostile_mob_manifest.py ASSETS_ZIP ELITE_SOURCE_ROOT OUTPUT_MD
"""
import collections
import hashlib
import json
import pathlib
import re
import subprocess
import sys
import zipfile


assets_path, source_path, output_path = map(pathlib.Path, sys.argv[1:4])
with assets_path.open("rb") as asset_file:
    asset_digest = hashlib.file_digest(asset_file, "sha256").hexdigest().upper()
if asset_digest != "B9164EB07D7C42E697D208BA3D35078A9B4A0125BEF6261847A2F91E293E53B4":
    raise ValueError("Expected the inspected Hytale 0.7.1 release asset package")
with zipfile.ZipFile(assets_path) as archive:
    roles = {
        name: json.loads(archive.read(name))
        for name in archive.namelist()
        if name.startswith("Server/NPC/Roles/") and name.endswith(".json")
    }
    stems = {pathlib.PurePosixPath(name).stem: name for name in roles}
    translations = {}
    for line in archive.read("Server/Languages/en-US/server.lang").decode("utf-8").splitlines():
        if " = " in line:
            key, value = line.split(" = ", 1)
            translations[key.strip()] = value.strip()

    spawns = collections.defaultdict(list)
    for name in archive.namelist():
        if not name.startswith("Server/NPC/Spawn/") or not name.endswith(".json"):
            continue
        # Development and retired selectors are not live natural locations.
        if any(part.strip(" _").lower() in {"tests", "test", "deprecated", "debug"}
               for part in pathlib.PurePosixPath(name).parts):
            continue
        config = json.loads(archive.read(name))
        for entry in config.get("NPCs", []):
            role = entry.get("Id", entry.get("Name"))
            if role in stems:
                spawns[role].append((name, tuple(config.get("Environments", []))))


def lineage(name):
    result = []
    while name and name not in result:
        result.append(name)
        name = stems.get(roles[name].get("Reference"))
    return result


def property_value(name, key):
    chain = lineage(name)
    for index, path in enumerate(chain):
        data = roles[path]
        value = data.get("Modify", {}).get(key, data.get(key))
        if value is None:
            continue
        if isinstance(value, dict) and "Compute" in value:
            parameter = value["Compute"]
            for candidate in chain[: index + 1]:
                setting = roles[candidate].get("Parameters", {}).get(parameter)
                if isinstance(setting, dict) and "Value" in setting:
                    return setting["Value"]
            continue
        return value
    return None


def attacks(name):
    for path in lineage(name):
        instructions = json.dumps(roles[path].get("Instructions", []), separators=(",", ":"))
        if ('"Combat"' in instructions or '"Attack"' in instructions
                or re.search(r'"Reference":"[^"]*Attack[^"]*"', instructions)):
            return True
    return False


def readable_location(paths):
    places = set()
    for path, environments in paths:
        segment = path.removeprefix("Server/NPC/Spawn/").removesuffix(".json")
        kind = segment.split("/", 1)[0]
        if environments:
            places.update(env.removeprefix("Env_").replace("_", " ") for env in environments)
        elif kind == "Markers":
            places.add("marker: " + segment.removeprefix("Markers/").rsplit("/", 1)[0].replace("_", " "))
        else:
            places.add(segment.replace("_", " "))
    if not places:
        return "No direct native spawn selector found"
    return "; ".join(sorted(places))


source = source_path / "src/main/resources/rpg"
catalog = json.loads((source / "progression/enemy-registry.json").read_text())
catalogued = {item["roleId"] for key in ("roles", "aliases") for item in catalog[key]}
population = json.loads((source / "spawning/native-population-roles-v1.json").read_text())
older_categories = population["categories"]
source_commit = subprocess.check_output(
    ["git", "-C", str(source_path), "rev-parse", "HEAD"], text=True).strip()
matrix = (source_path / "docs/enemies/ELITE_ROLE_MATRIX.md").read_text(encoding="utf-8")
elite_enabled = set()
for line in matrix.splitlines():
    if not line.startswith("| "):
        continue
    cells = [value.strip() for value in line.strip("|").split("|")]
    if len(cells) >= 6 and cells[3] == "yes" and cells[4].startswith("Y/") and cells[5].startswith("Y/"):
        elite_enabled.add(cells[0])


rows = []
scripted = []
for path, data in roles.items():
    if data.get("Type") not in ("Generic", "Variant") or "/_Core/" in path or "/_Deprecated/" in path:
        continue
    role = pathlib.PurePosixPath(path).stem
    combat = attacks(path)
    if not combat:
        continue
    attitude = property_value(path, "DefaultPlayerAttitude")
    goblin = "/Goblin/" in path and "Spectator" not in role and "Carrier" not in role
    special = role == "Dungeon_Feastmaster_Boss" or role.startswith("Goblin_Gobliterator_Phase")
    if older_categories.get(role) in {"WILDLIFE", "AMBIENT_AVIAN"}:
        continue
    if attitude != "Hostile" and not goblin and not special:
        continue
    translation = property_value(path, "NameTranslationKey")
    if isinstance(translation, str):
        translation = translation.removeprefix("server.")
    label = translations.get(translation, role.replace("_", " "))
    entry = (label, role, readable_location(spawns.get(role, [])),
             "Configured" if role in catalogued else "No catalog role",
             "Configured*" if role in elite_enabled else "No",
             attitude or "unspecified", path)
    (rows if spawns.get(role) else scripted).append(entry)

rows.sort(key=lambda row: (row[1].split("_")[0], row[1]))
scripted.sort(key=lambda row: row[1])
chapter_goblins = [row for row in rows + scripted if "Goblin" in row[1] or "Feastmaster" in row[1]]
uncatalogued = [row for row in rows if row[3] == "No catalog role"]
prior_hostile_missing = {role for role, category in older_categories.items()
                         if category == "HOSTILE" and role not in {row[1] for row in rows + scripted}}
if prior_hostile_missing != {"Goblin_Thief"}:
    raise ValueError(f"Unexpected gap against previous hostile population audit: {prior_hostile_missing}")


def table(items):
    result = ["| In-game name / native role | Native location selector(s) | Hywind combat/loot | Champion & Unique |",
              "| --- | --- | --- | --- |"]
    for label, role, location, catalog_status, elite_status, _attitude, _path in items:
        result.append(f"| {label} (`{role}`) | {location} | {catalog_status} | {elite_status} |")
    return "\n".join(result)


document = f"""# Hostile mob manifest — installed Hytale 0.7.1

This is a **source audit**, not a gameplay change. It inventories {len(rows)} directly spawnable native roles whose role behavior has a Combat/Attack branch and either an explicit hostile player attitude or a Chapter I Goblin combat role. Another {len(scripted)} combat roles have no direct production `Server/NPC/Spawn` selector and are listed separately as scripted/conditional encounters. {len(chapter_goblins)} listed roles belong to the Goblin/Feastmaster families. The inspected package is the installed release `Assets.zip` ({assets_path.stat().st_size:,} bytes; SHA-256 `{asset_digest}`). The mod comparison is QA-pending `codex/elite-natural-hookup` source commit `{source_commit}`. This is an asset-defined roster, not an in-game observation of every possible spawn.

**How to read the status.** “Configured” means the native role appears in Hywind's `enemy-registry.json`, so ordinary encounter progression and additional equipment-loot handling have an authored route when admission and player contribution succeed. Native Hytale drops remain native. “No catalog role” means this mod has no authored encounter/reward route for that exact role. “Configured*” means the 0.7.0 production Elite matrix enables both Champion and Unique for that exact role; it is **not a claim of working promotion on 0.7.1**. The latest client log reports all four natural-Elite native hooks missing after the update, so **no listed role is currently verified to promote naturally on 0.7.1**. The old role/asset bindings also require a separate 0.7.1 compatibility review. This table makes the configured coverage gap visible without silently calling new Goblins eligible.

Locations are native production spawn-selector environments or marker families; they indicate where Hytale *can* place a role, not a guaranteed sighting. Test, debug, and deprecated selectors are excluded. Names come from installed `en-US/server.lang` when a role has a translation key. The audit reads `Server/NPC/Roles`, `Server/NPC/Spawn`, and `Server/Languages/en-US/server.lang` in `Assets.zip`; mod status comes from `rpg/progression/enemy-registry.json`, `rpg/spawning/native-population-roles-v1.json`, and `docs/enemies/ELITE_ROLE_MATRIX.md`. A hostile attitude alone is insufficient: passive critters such as frogs are excluded because their behavior flees rather than attacks. Neutral animals that retaliate only after provocation are excluded. Fox and Cactee inherit combat instructions but the earlier Hywind population audit classified them as wildlife; they remain **behavior-review candidates**, not confirmed attackers. Patrol, wander, dungeon, and Coffer variants are separate rows because Hywind binds exact native role IDs.

## Directly spawnable attackers ({len(rows)})

{table(rows)}

## Scripted or conditional combat roles without a direct native spawn selector ({len(scripted)})

These roles may be created by encounters, transitions, or code. Their role assets contain combat behavior, but this audit did not find a direct `Server/NPC/Spawn` entry; their location is therefore intentionally left unasserted.

{table(scripted)}

## Audit findings

- {len(uncatalogued)} directly spawnable attackers have no exact Hywind encounter-catalog role. New Chapter I Goblin variants are prominent in this gap. A native drop list in the game is not evidence of Hywind equipment loot.
- The Champion/Unique column is authored **0.7.0** eligibility only. Current 0.7.1 connected logs show `RPG_NATURAL_ELITE_HOOKS_MISSING`; promotion needs renewed native-hook and binding verification before QA can establish actual eligibility.
- The generated rows are exact native roles, so names sharing a player-facing translation may appear several times. This is intentional: their spawn context and mod coverage can differ.
- Fox and Cactee are omitted from the confirmed list because inherited combat instructions alone do not establish attack-on-sight behavior; the earlier population audit classifies them as wildlife. Additional neutral or unspecified-attitude combat roles may be reactive or scripted. Connected QA would be needed to resolve ambiguous behavior. This manifest is exhaustive for the stated asset classification rule, not proof that every installed NPC has been witnessed in-game.
- Cross-checking the prior Hywind HOSTILE population list found one omitted role: `Goblin_Thief`. Its installed Chapter I behavior detects and flees rather than executing an attack action, so it does not meet this manifest's attack-on-sight criterion.
"""
output_path.parent.mkdir(parents=True, exist_ok=True)
output_path.write_text(document, encoding="utf-8")
print(f"direct={len(rows)} scripted={len(scripted)} goblin={len(chapter_goblins)} uncatalogued={len(uncatalogued)} output={output_path}")
