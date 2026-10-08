"""Audit installed native world-spawn roles; never runs a Hytale server."""
import argparse
import collections
import json
import pathlib
import zipfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("assets", type=pathlib.Path)
    parser.add_argument("--output", type=pathlib.Path)
    parser.add_argument("--classification-output", type=pathlib.Path)
    parser.add_argument("--assets-sha256", required=False)
    args = parser.parse_args()
    with zipfile.ZipFile(args.assets) as archive:
        names = archive.namelist()
        roles = collections.defaultdict(list)
        for name in names:
            if name.startswith("Server/NPC/Roles/") and name.endswith(".json"):
                roles[pathlib.PurePosixPath(name).stem].append(name)
        world_entries = collections.defaultdict(list)
        for name in names:
            if name.startswith("Server/NPC/Spawn/World/") and name.endswith(".json"):
                asset = json.loads(archive.read(name))
                for row in asset.get("NPCs", []):
                    if row.get("Id"):
                        world_entries[row["Id"]].append((name, row))

        def inherit(role_id, field, visited=None):
            visited = set() if visited is None else visited
            if role_id in visited or len(roles[role_id]) != 1:
                return None
            visited.add(role_id)
            asset = json.loads(archive.read(roles[role_id][0]))
            for key in ("Modify", "Parameters", "Set", ""):
                part = asset if not key else asset.get(key, {})
                if field in part:
                    value = part[field]
                    if isinstance(value, dict) and "Value" in value:
                        return value["Value"]
                    if isinstance(value, dict) and "Compute" in value:
                        parameter = inherit(role_id, value["Compute"], set())
                        if parameter is not None:
                            return parameter
                    if not isinstance(value, dict) or "Compute" not in value:
                        return value
            parent = asset.get("Reference") or asset.get("Parent")
            return inherit(parent, field, visited) if isinstance(parent, str) else None

        output = []
        for role_id, entries in sorted(world_entries.items()):
            blocks = sorted({e.get("SpawnBlockSet", "") for _, e in entries if e.get("SpawnBlockSet")})
            fluids = sorted({e.get("SpawnFluidTag", "") for _, e in entries if e.get("SpawnFluidTag")})
            movement = sorted({str(mode) for _, e in entries for mode in e.get("MovementModeWeights", {})})
            controllers = inherit(role_id, "MotionControllerList")
            if isinstance(controllers, list):
                movement = sorted(set(movement) | {c.get("Type", "UNKNOWN") for c in controllers if isinstance(c, dict)})
            attitude = inherit(role_id, "DefaultPlayerAttitude")
            group = inherit(role_id, "AttitudeGroup")
            role_asset = roles[role_id][0] if len(roles[role_id]) == 1 else None
            if fluids:
                category = "OTHER"
            elif attitude == "Hostile" or group == "Goblin":
                category = "HOSTILE"
            elif "Fly" in movement and role_asset and "/Avian/" in role_asset:
                category = "AMBIENT_AVIAN"
            elif "Walk" in movement and (attitude in ("Neutral", "Friendly") or group in ("Prey", "PreyBig")):
                category = "WILDLIFE"
            else:
                category = "OTHER"
            output.append({
                "id": role_id,
                "category": category,
                "roleAsset": role_asset,
                "nativePlayerAttitude": attitude,
                "nativeAttitudeGroup": group,
                "spawnBlockSets": blocks,
                "spawnFluidTags": fluids,
                "spawnMovementModes": movement,
                "spawnDefinitions": len(entries),
                "sources": sorted({name for name, _ in entries}),
            })
    data = {"schemaVersion": 1, "nativeWorldSpawnRoles": output}
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    if args.classification_output:
        if not args.assets_sha256:
            parser.error("--classification-output requires --assets-sha256")
        compact = {"schemaVersion": 1, "assetsSha256": args.assets_sha256.lower(),
                   "categories": {row["id"]: row["category"] for row in output}}
        args.classification_output.parent.mkdir(parents=True, exist_ok=True)
        args.classification_output.write_text(json.dumps(compact, indent=2) + "\n", encoding="utf-8")
    for row in output:
        print(row["id"], row["nativePlayerAttitude"], row["nativeAttitudeGroup"],
              "fluid=" + ",".join(row["spawnFluidTags"]),
              "movement=" + ",".join(row["spawnMovementModes"]))


if __name__ == "__main__":
    main()
