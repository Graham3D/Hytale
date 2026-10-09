"""Rebuild the R228 single-melee native-role adapters from pinned installed assets.

Only the existing RPG_EnemyDamage leaf type is added. All native animation,
targeting, damage, knockback, status, movement, and loot data is copied intact.
"""
import hashlib
import json
from pathlib import Path
import sys
import zipfile


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = json.loads((ROOT / "src/main/resources/rpg/enemies/native-bindings-v1.json").read_text())
REGISTRY = json.loads((ROOT / "src/main/resources/rpg/progression/enemy-registry.json").read_text())
CATALOG = {row["roleId"]: row for row in REGISTRY["roles"]}
ASSETS = Path.home() / "AppData/Roaming/Hytale/install/pre-release/package/game/latest/Assets.zip"


def build(check=False):
    if hashlib.sha256(ASSETS.read_bytes()).hexdigest() != MANIFEST["assetsSha256"]:
        raise ValueError("Installed Assets.zip differs from the pinned native binding revision")
    with zipfile.ZipFile(ASSETS) as zip_file:
        native_roles = {name.rsplit("/", 1)[-1][:-5]: name for name in zip_file.namelist()
                        if name.startswith("Server/NPC/Roles/") and name.endswith(".json")}
        for archetype in MANIFEST["derivedBindings"]["nativeArchetypes"]:
            bite = archetype["id"] == "installed-predator-bite-physical-single-melee-v1"
            expected_root = "member-native-action" if bite else "Root_NPC_Attack_Melee"
            if archetype["parentRoleId"] != "Template_Predator" or archetype["nativeActionId"] != expected_root:
                raise ValueError("Unreviewed native archetype")
            custom_selector = archetype["id"] == "installed-predator-selector-physical-single-melee-v1"
            if not custom_selector and not bite and archetype["id"] != "installed-predator-physical-single-melee-v1":
                raise ValueError("Unreviewed native signature")
            for member in archetype["memberRoleIds"]:
                role = CATALOG[member["roleId"]]
                path = role["assetPath"]
                native_bytes = zip_file.read(path)
                if hashlib.sha256(native_bytes).hexdigest().upper() != role["assetSha256"]:
                    raise ValueError(f"Native role changed: {path}")
                data = json.loads(native_bytes)
                parent = data.get("Reference")
                if parent not in ({"Template_Predator", "Wolf_Black", "Snake_Marsh"} if bite else {"Template_Predator"}):
                    raise ValueError(f"Native parent changed: {path}")
                action = data.get("Modify", {}).get("Attack")
                if action is None and bite:
                    action = json.loads(zip_file.read(native_roles[parent])).get("Modify", {}).get("Attack")
                if action != (member["nativeActionId"] if bite else "Root_NPC_Attack_Melee"):
                    raise ValueError(f"Native parent/action changed: {path}")
                if bite:
                    species = action.removeprefix("Root_NPC_").removesuffix("_Attack")
                    roots = [name for name in zip_file.namelist()
                             if name.startswith("Server/Item/RootInteractions/NPCs/")
                             and name.endswith("/" + action + ".json")]
                    if len(roots) != 1 or '"Var": "Melee_Start"' not in zip_file.read(roots[0]).decode():
                        raise ValueError(f"Native bite root changed: {action}")
                    bites = [name for name in zip_file.namelist()
                             if name.startswith("Server/Item/Interactions/NPCs/")
                             and name.endswith("/" + species + "_Bite.json")]
                    parents = [name for name in zip_file.namelist()
                               if name.startswith("Server/Item/Interactions/NPCs/")
                               and name.endswith("/" + species + "_Bite_Damage.json")]
                    if len(bites) != 1 or len(parents) != 1:
                        raise ValueError(f"Native bite interaction unavailable: {action}")
                    bite_graph = json.loads(zip_file.read(bites[0]))
                    hit = bite_graph["Next"]["HitEntity"]["Interactions"]
                    native_parent = json.loads(zip_file.read(parents[0]))
                    if (len(hit) != 1 or hit[0].get("Var") != "Bite_Damage"
                            or native_parent.get("Parent") != "DamageEntityParent"
                            or set(native_parent.get("DamageCalculator", {}).get("BaseDamage", {})) != {"Physical"}
                            or "Knockback" not in native_parent.get("DamageEffects", {})):
                        raise ValueError(f"Native bite damage signature changed: {action}")
                variables = data["Modify"]["_InteractionVars"]
                expected_vars = ({"Melee_Start", "Bite_Damage"} if bite else
                                 {"Melee_Start", "Melee_Damage", "Melee_Selector"} if custom_selector else
                                 {"Melee_Start", "Melee_Damage"})
                if set(variables) != expected_vars:
                    raise ValueError(f"Native melee variables changed: {path}")
                leafs = variables["Bite_Damage" if bite else "Melee_Damage"]["Interactions"]
                if len(leafs) != 1:
                    raise ValueError(f"Native damage count changed: {path}")
                leaf = leafs[0]
                damage_parent=(member["nativeActionId"].removeprefix("Root_NPC_").removesuffix("_Attack")+"_Bite_Damage") if bite else "NPC_Attack_Melee_Damage"
                if (leaf.get("Parent") != damage_parent
                        or set(leaf.get("DamageCalculator", {}).get("BaseDamage", {})) != {"Physical"}
                        or any(key in leaf for key in ("Next", "Type"))
                        or (not custom_selector and not bite and any(key in leaf for key in ("DamageEffects", "Effects")))):
                    raise ValueError(f"Native direct Physical leaf changed: {path}")
                if custom_selector and (set(leaf.get("DamageEffects", {})) - {"Knockback", "WorldSoundEventId"}
                                        or set(leaf.get("Effects", {})) - {"WorldSoundEventId"}):
                    raise ValueError(f"Unreviewed native hit effect: {path}")
                if bite and (set(leaf.get("DamageEffects", {})) != {"Knockback"} or "Effects" in leaf):
                    raise ValueError(f"Unreviewed native bite effects: {path}")
                leaf["Type"] = "RPG_EnemyDamage"
                output = ROOT / "src/main/resources" / path
                expected = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
                if check:
                    if not output.is_file() or output.read_text(encoding="utf-8") != expected:
                        raise ValueError(f"Packaged native adapter differs: {path}")
                else:
                    output.parent.mkdir(parents=True, exist_ok=True)
                    output.write_text(expected, encoding="utf-8")
                print(f"{'verified' if check else 'generated'} {path}")


if __name__ == "__main__":
    build(check="--check" in sys.argv[1:])
