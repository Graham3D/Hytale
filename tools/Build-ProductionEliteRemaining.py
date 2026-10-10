"""Pin reviewed native combat archetypes and add only role-local damage adapters.

This is an offline authoring/validation tool. It never starts a Hytale server.
"""
import copy
from collections import Counter
import hashlib
import json
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MANIFEST_PATH = ROOT / "src/main/resources/rpg/enemies/native-bindings-v1.json"
CATALOG_PATH = ROOT / "src/main/resources/rpg/progression/enemy-registry.json"
ASSETS = Path.home() / "AppData/Roaming/Hytale/install/pre-release/package/game/latest/Assets.zip"
GROUPS = {
    "SINGLE_MELEE": ("Piranha", "Piranha_Black", "Slug_Magma"),
    "CHAIN_SHARED_MELEE": (
        "Crawler_Void", "Scarak_Fighter", "Skeleton_Burnt_Lancer", "Skeleton_Fighter",
        "Skeleton_Frost_Fighter", "Skeleton_Frost_Knight", "Skeleton_Frost_Soldier", "Skeleton_Soldier",
        "Skeleton_Incandescent_Footman", "Skeleton_Sand_Assassin", "Skeleton_Sand_Guard",
        "Wraith", "Zombie_Burnt"),
    "CHAIN_VARIABLE_MELEE": ("Bear_Grizzly", "Bear_Polar", "Scorpion", "Skeleton_Burnt_Soldier",
                             "Toad_Rhino", "Toad_Rhino_Magma"),
    "CHAIN_REPEATED_MELEE": ("Goblin_Scrapper",),
    "SINGLE_PROJECTILE": ("Eye_Void", "Skeleton_Archer", "Skeleton_Burnt_Archer", "Skeleton_Frost_Archer",
                          "Skeleton_Burnt_Gunner", "Skeleton_Frost_Mage",
                          "Skeleton_Incandescent_Mage", "Skeleton_Mage", "Skeleton_Sand_Mage"),
    "MIXED_NATIVE_ACTIONS": ("Outlander_Berserker", "Outlander_Hunter"),
    "DUAL_NATIVE_ACTIONS": ("Void_Spectre",),
    "CONDITIONAL_MELEE": ("Snapdragon", "Spider"),
    "CAE_NATIVE_ACTIONS": ("Skeleton_Burnt_Praetorian",),
}
MELEE_AFFIXES = ["ME-002", "ME-003", "ME-004", "ME-005", "ME-006", "ME-007", "ME-008",
                 "ME-009", "ME-010", "ME-011", "ME-012", "ME-013", "ME-014", "ME-016",
                 "ME-017", "ME-018", "ME-020", "ME-021", "ME-022", "ME-024", "ME-025", "ME-026"]
PROJECTILE_AFFIXES = ["ME-002", "ME-003", "ME-004", "ME-017"]
CONDITIONAL_AFFIXES = ["ME-002", "ME-003", "ME-004", "ME-017", "ME-018", "ME-026"]
# Connected R247 captured these concrete native flock children even though the
# world-spawn table names only their parents. Their installed Variant data is
# checked below before sharing a parent's combat certificate.
REPORTED_FLOCK_VARIANTS = {"Skeleton_Archer_Wander", "Skeleton_Burnt_Archer_Wander",
                           "Skeleton_Soldier_Wander"}


def main(check=False):
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    catalog = json.loads(CATALOG_PATH.read_text(encoding="utf-8"))
    if hashlib.sha256(ASSETS.read_bytes()).hexdigest() != manifest["assetsSha256"]:
        raise ValueError("Installed assets differ from the pinned native version")
    canonical = {r["roleId"]: r for r in catalog["roles"]}
    aliases = {r["roleId"]: r for r in catalog["aliases"]}
    with zipfile.ZipFile(ASSETS) as z:
        world_roles = set()
        for path in z.namelist():
            if path.startswith("Server/NPC/Spawn/World/") and path.endswith(".json"):
                world_roles.update(row["Id"] for row in json.loads(z.read(path)).get("NPCs", [])
                                   if row.get("Id"))
        role_paths = {n.rsplit("/", 1)[-1][:-5]: n for n in z.namelist()
                      if n.startswith("Server/NPC/Roles/") and n.endswith(".json")}
        model_paths = {n.rsplit("/", 1)[-1][:-5]: n for n in z.namelist()
                       if n.startswith("Server/Models/") and n.endswith(".json")}
        root_paths = {n.rsplit("/", 1)[-1][:-5]: n for n in z.namelist()
                      if n.startswith("Server/Item/RootInteractions/") and n.endswith(".json")}
        interaction_paths = {n.rsplit("/", 1)[-1][:-5]: n for n in z.namelist()
                             if n.startswith("Server/Item/Interactions/") and n.endswith(".json")}

        def native(path):
            return json.loads(z.read(path))

        def resolved(role_id):
            obj = native(role_paths[role_id])
            parent_id = obj.get("Reference")
            parent, parameters = resolved(parent_id) if parent_id in role_paths else ({}, {})
            fields = dict(parent)
            params = dict(parameters)
            params.update({k: v.get("Value", v) if isinstance(v, dict) else v
                           for k, v in obj.get("Parameters", {}).items()})
            fields.update(obj.get("Modify", {}))
            fields.update({k: v for k, v in obj.items()
                           if k not in ("Type", "Reference", "Parameters", "Modify")})
            return fields, params

        def field(role_id, key):
            fields, params = resolved(role_id)
            value = fields.get(key, params.get(key))
            if isinstance(value, dict) and "Compute" in value:
                value = params.get(value["Compute"])
            return value

        def textures(model_id):
            seen, result = set(), set()
            while model_id and model_id not in seen:
                seen.add(model_id)
                if model_id not in model_paths:
                    raise ValueError(f"Unknown model: {model_id}")
                obj = native(model_paths[model_id])
                if isinstance(obj.get("Texture"), str):
                    result.add(obj["Texture"])
                for attachment in obj.get("DefaultAttachments", []):
                    if isinstance(attachment.get("Texture"), str):
                        result.add(attachment["Texture"])
                model_id = obj.get("Parent")
            if not result or any("Common/" + texture not in z.namelist() for texture in result):
                raise ValueError("Model texture not present in pinned assets")
            return sorted(result)

        def refs(value, seen=frozenset()):
            if isinstance(value, str):
                if value in interaction_paths and value not in seen:
                    yield from refs(native(interaction_paths[value]), seen | {value})
            elif isinstance(value, list):
                for item in value:
                    yield from refs(item, seen)
            elif isinstance(value, dict):
                yield value
                for key, item in value.items():
                    if key in ("Interactions", "Next", "HitEntity", "HitBlock", "DefaultValue",
                               "Failed", "Passed", "Missed", "Hit", "Else"):
                        yield from refs(item, seen)

        def inline_nodes(value):
            if isinstance(value, list):
                for item in value:
                    yield from inline_nodes(item)
            elif isinstance(value, dict):
                yield value
                for item in value.values():
                    yield from inline_nodes(item)

        def attack_roots(role_id, kind):
            if kind == "SINGLE_PROJECTILE":
                root = field(role_id, "RangedAttack") or field(role_id, "Attack")
                return [(root, "Projectile")]
            if kind == "DUAL_NATIVE_ACTIONS":
                return [(field(role_id, "CombatAttack"), "Physical"),
                        (field(role_id, "CombatChargeHitEntityInteraction"), "Physical")]
            if kind == "CAE_NATIVE_ACTIONS":
                asset_id = field(role_id, "_CombatConfig")
                if asset_id != "CAE_Skeleton_Praetorian":
                    raise ValueError(f"Unreviewed native combat evaluator: {role_id}/{asset_id}")
                asset = native("Server/NPC/Balancing/Undead/CAE_Skeleton_Praetorian.json")
                sets = asset["CombatActionEvaluator"]["ActionSets"]
                attacks = {tuple(value["BasicAttacks"]["Attacks"])
                           for value in sets.values() if "BasicAttacks" in value}
                if len(attacks) != 1 or len(next(iter(attacks))) != 1:
                    raise ValueError("Native combat evaluator basic root changed")
                return [(next(iter(attacks))[0], "Physical"),
                        (field(role_id, "CombatChargeHitEntityInteraction"), "Physical")]
            if kind != "MIXED_NATIVE_ACTIONS":
                return [(field(role_id, "Attack"), "Physical")]
            result = []
            for key, channel in (("AttackSequence", "Physical"), ("RangedAttackSequence", "Projectile")):
                sequence = field(role_id, key)
                if not sequence:
                    continue
                if sequence not in interaction_paths and sequence not in role_paths:
                    # NPC component files are distinct from native Item interactions.
                    path = next((n for n in z.namelist() if n.endswith("/" + sequence + ".json")
                                 and n.startswith("Server/NPC/Roles/")), None)
                else:
                    path = role_paths.get(sequence)
                if not path:
                    raise ValueError(f"Missing native attack sequence: {role_id}/{sequence}")
                component = native(path)
                for item in component["Content"]["Actions"]:
                    if item.get("Type") == "Attack":
                        result.append((item["Attack"], channel))
            return result

        def damage_vars(role_id, kind, root):
            if kind == "SINGLE_PROJECTILE":
                return []
            if kind == "CONDITIONAL_MELEE":
                return ["native_cooldown", "native_fallback"]
            if kind == "CAE_NATIVE_ACTIONS":
                if root == field(role_id, "CombatChargeHitEntityInteraction"):
                    return ["charge"]
                asset = native("Server/NPC/Balancing/Undead/CAE_Skeleton_Praetorian.json")
                sets = asset["CombatActionEvaluator"]["ActionSets"]
                variable_sets = [set(value["BasicAttacks"]["InteractionVars"])
                                 for value in sets.values() if "BasicAttacks" in value]
                if len(variable_sets) != 2 or variable_sets[0] != variable_sets[1]:
                    raise ValueError("Native combat evaluator variable roster changed")
                return sorted(variable_sets[0])
            if kind in ("CHAIN_VARIABLE_MELEE", "CHAIN_REPEATED_MELEE"):
                root_obj = native(root_paths[root])
                chains = [node for node in inline_nodes(root_obj) if node.get("Type") == "Chaining"]
                if len(chains) != 1:
                    raise ValueError(f"Expected one native chain: {root}")
                variables = []
                for branch in chains[0]["Next"]:
                    replaces = [node["Var"] for node in refs(branch)
                                if node.get("Type") == "Replace" and node.get("Var", "").endswith("_Damage")]
                    if not replaces and not [node for node in refs(branch)
                                             if node.get("Type") in ("DamageEntity", "LaunchProjectile")]:
                        continue  # Native shield/block branch has no damage owner.
                    if (not replaces or (kind == "CHAIN_VARIABLE_MELEE" and len(replaces) != 1)
                            or len(set(replaces)) != 1):
                        raise ValueError(f"Expected one damage variable per branch: {root}/{branch}: {replaces}")
                    variables.append(replaces[0])
                if kind == "CHAIN_VARIABLE_MELEE" and len(set(variables)) != len(variables):
                    raise ValueError(f"Distinct native variables required: {role_id}")
                return list(dict.fromkeys(variables))
            if kind == "DUAL_NATIVE_ACTIONS":
                variables = {node.get("Var") for node in refs(native(root_paths[root]))
                             if node.get("Type") == "Replace" and node.get("Var", "").endswith("_Damage")}
                if len(variables) != 1:
                    raise ValueError(f"Dual native route needs one authored damage variable: {role_id}/{root}")
                return list(variables)
            return ["Melee_Damage"]

        result = []
        extended = {role for members in GROUPS.values() for role in members}
        variants = [row for row in manifest["derivedBindings"]["canonicalVariants"]
                    if row["canonicalRoleId"] not in extended]
        for kind, members in GROUPS.items():
            group = {"id": "installed-" + kind.lower().replace("_", "-") + "-v1",
                     "routeKind": kind,
                     "supportedAffixIds": CONDITIONAL_AFFIXES if kind == "CONDITIONAL_MELEE" else
                     PROJECTILE_AFFIXES if kind in ("SINGLE_PROJECTILE", "MIXED_NATIVE_ACTIONS") else
                     [id for id in MELEE_AFFIXES if id not in ("ME-020", "ME-021", "ME-022", "ME-024")]
                     if kind == "CAE_NATIVE_ACTIONS" else MELEE_AFFIXES,
                     "members": []}
            for role_id in members:
                source = canonical[role_id]
                native_bytes = z.read(source["assetPath"])
                if hashlib.sha256(native_bytes).hexdigest().upper() != source["assetSha256"]:
                    raise ValueError(f"Catalog role hash changed: {role_id}")
                obj = json.loads(native_bytes)
                parent = obj.get("Reference")
                # SupportConfigBuilder.readConfig defaults an omitted player attitude to HOSTILE.
                if field(role_id, "DefaultPlayerAttitude") not in (None, "Hostile"):
                    raise ValueError(f"Not statically hostile: {role_id}")
                action_records, variables = [], set()
                for root, channel in attack_roots(role_id, kind):
                    if not isinstance(root, str) or root not in root_paths:
                        raise ValueError(f"Missing native root: {role_id}/{root}")
                    graph = native(root_paths[root])
                    chain = [node for node in inline_nodes(graph) if node.get("Type") == "Chaining"]
                    if kind.startswith("CHAIN_") and len(chain) != 1:
                        raise ValueError(f"Native chain signature changed: {role_id}")
                    if kind not in ("CHAIN_SHARED_MELEE", "CHAIN_VARIABLE_MELEE", "CHAIN_REPEATED_MELEE",
                                    "CAE_NATIVE_ACTIONS") and chain:
                        raise ValueError(f"Unexpected native chain: {role_id}")
                    launches = [node for node in refs(graph) if node.get("Type") == "LaunchProjectile"]
                    if channel == "Projectile":
                        if len(launches) != 1:
                            raise ValueError(f"Not one existing native launch: {role_id}/{root}")
                        projectile = launches[0]["ProjectileId"]
                        proj_path = next((n for n in z.namelist() if n.startswith("Server/Projectiles/")
                                          and n.endswith("/" + projectile + ".json")), None)
                        if not proj_path or field(role_id, "Attack") == "<invented>":
                            raise ValueError(f"Native projectile missing: {role_id}/{projectile}")
                    elif launches:
                        raise ValueError(f"Unexpected native projectile: {role_id}/{root}")
                    keys = damage_vars(role_id, kind, root) if channel == "Physical" else ["projectile"]
                    if kind == "CONDITIONAL_MELEE":
                        variables.add("Bite_Damage" if role_id == "Spider" else "Melee_Damage")
                    elif kind == "CAE_NATIVE_ACTIONS":
                        pass  # Native combat-evaluator asset owns the two variable maps.
                    else:
                        variables.update(keys if channel == "Physical" else [])
                    action = {"rootId": root, "strikeKeys": keys, "channel": channel}
                    if kind == "CHAIN_REPEATED_MELEE":
                        visits = Counter(node["Var"] for branch in chain[0]["Next"]
                                         for node in refs(branch)
                                         if node.get("Type") == "Replace"
                                         and node.get("Var") in keys)
                        if role_id != "Goblin_Scrapper" or visits != {
                                "Club_Swing_Left_Right_Damage": 6,
                                "Club_Swing_Left_Right_Down_Damage": 3}:
                            raise ValueError(f"Native repeated contact pattern changed: {role_id}/{visits}")
                        action["contactOccurrences"] = dict(visits)
                    if kind == "CAE_NATIVE_ACTIONS" and len(keys) > 1:
                        action["variantOwnerId"] = "CAE_Skeleton_Praetorian"
                    action_records.append(action)
                if not action_records or len({x["rootId"] for x in action_records}) != len(action_records):
                    raise ValueError(f"Native actions invalid: {role_id}")
                overlay = copy.deepcopy(obj)
                for variable in sorted(variables):
                    slot = overlay.setdefault("Modify", {}).setdefault("_InteractionVars", {}).get(variable)
                    if slot is None:
                        fields, _ = resolved(role_id)
                        inherited = fields.get("InteractionVars", {}).get(variable)
                        if inherited is None:
                            # Some native chains use an authored Replace DefaultValue rather
                            # than a role variable. Route only that same default asset.
                            defaults = []
                            for root, channel in attack_roots(role_id, kind):
                                if channel == "Physical":
                                    for node in refs(native(root_paths[root])):
                                        if node.get("Type") == "Replace" and node.get("Var") == variable:
                                            defaults.append(node.get("DefaultValue", {}).get("Interactions"))
                            if not defaults or any(item != ["NPC_Attack_Melee_Damage"] for item in defaults):
                                raise ValueError(f"No authored native damage variable: {role_id}/{variable}")
                            inherited = {"Interactions": [{"Parent": "NPC_Attack_Melee_Damage"}]}
                        slot = copy.deepcopy(inherited)
                        overlay["Modify"]["_InteractionVars"][variable] = slot
                    leaves = slot.get("Interactions", [])
                    if kind == "CONDITIONAL_MELEE":
                        if len(leaves) != 1 or leaves[0].get("Type") != "CooldownCondition":
                            raise ValueError(f"Native poison cooldown changed: {role_id}")
                        damage_leaves = [node for node in inline_nodes(leaves[0])
                                         if node.get("Parent") in ("NPC_Attack_Melee_Damage", "Spider_Bite_Damage")
                                         and "DamageCalculator" in node]
                        if len(damage_leaves) != 2 or any(
                                set(leaf["DamageCalculator"].get("BaseDamage", {})) != {"Physical"}
                                or "Type" in leaf for leaf in damage_leaves):
                            raise ValueError(f"Native conditional damage changed: {role_id}")
                        for leaf in damage_leaves:
                            leaf["Type"] = "RPG_EnemyDamage"
                        continue
                    if len(leaves) != 1 or leaves[0].get("Type") or not leaves[0].get("Parent"):
                        raise ValueError(f"Unreviewed native leaf shape: {role_id}/{variable}")
                    damage = leaves[0].get("DamageCalculator") or native(
                        interaction_paths[leaves[0]["Parent"]]).get("DamageCalculator", {})
                    if set(damage.get("BaseDamage", {})) != {"Physical"}:
                        raise ValueError(f"Nonphysical native leaf: {role_id}/{variable}")
                    leaves[0]["Type"] = "RPG_EnemyDamage"
                if variables:
                    output = ROOT / "src/main/resources" / source["assetPath"]
                    encoded = json.dumps(overlay, ensure_ascii=False, indent=2) + "\n"
                    if check:
                        if not output.is_file() or output.read_text(encoding="utf-8") != encoded:
                            raise ValueError(f"Packaged native role differs: {role_id}")
                    else:
                        output.parent.mkdir(parents=True, exist_ok=True)
                        output.write_text(encoded, encoding="utf-8")
                model_id = field(role_id, "Appearance")
                effects = field(role_id, "_CombatConfig")
                if kind == "CAE_NATIVE_ACTIONS":
                    effects = native("Server/NPC/Balancing/Undead/CAE_Skeleton_Praetorian.json")
                effects = effects.get("EntityEffects", []) if isinstance(effects, dict) else []
                immunities = ["FIRE"] if "Immunity_Fire" in effects else []
                group["members"].append({"roleId": role_id, "parentRoleId": parent,
                                         "modelAssetId": model_id,
                                         "defaultTextureAssetIds": textures(model_id),
                                         "nativeLootSourceId": field(role_id, "DropList"),
                                         "nativeImmunityChannels": immunities,
                                         "nativeStunStaggerImmune": False, "nativeSlowImmune": False,
                                         "actions": action_records})
                alias_ids = sorted(id for id, alias in aliases.items()
                                   if alias["canonicalRoleId"] == role_id
                                   and (id in world_roles or id in REPORTED_FLOCK_VARIANTS))
                if alias_ids:
                    for alias_id in alias_ids:
                        alias = native(aliases[alias_id]["assetPath"])
                        if alias.get("Reference") != role_id or set(alias.get("Modify", {})) - {
                                "WanderRadius", "Patrol", "FlockArray", "ApplySeparation", "FollowPatrolPath",
                                "NameTranslationKey"}:
                            raise ValueError(f"Alias changes native combat: {alias_id}")
                    variants.append({"canonicalRoleId": role_id, "nativeRoleIds": alias_ids})
                print(f"{'verified' if check else 'generated'} {role_id}: {kind}, {len(action_records)} roots, "
                      f"{len(variables)} damage vars, {len(alias_ids)} aliases")
            result.append(group)
        cae_path = "Server/NPC/Balancing/Undead/CAE_Skeleton_Praetorian.json"
        native_cae = native(cae_path)
        routed_cae = copy.deepcopy(native_cae)
        for name in ("Default", "Strafe"):
            slot = routed_cae["CombatActionEvaluator"]["ActionSets"][name]["BasicAttacks"]["InteractionVars"]
            if set(slot) != {"Sword_Swing_Left_Damage", "Sword_Swing_Right_Damage", "Sword_Swing_Down_Damage"}:
                raise ValueError("Native Praetorian evaluator strike roster changed")
            for variable in slot.values():
                leaves = variable["Interactions"]
                if len(leaves) != 1 or leaves[0].get("Type") or set(
                        leaves[0]["DamageCalculator"].get("BaseDamage", {})) != {"Physical"}:
                    raise ValueError("Native Praetorian evaluator damage changed")
                leaves[0]["Type"] = "RPG_EnemyDamage"
        charge_path = "Server/Item/Interactions/NPCs/Undead/Skeleton_Burnt_Praetorian/Skeleton_Burnt_Praetorian_Charge_Damage.json"
        charge = native(charge_path)
        if charge.get("Type") or charge.get("Parent") != "DamageEntityParent" or set(
                charge["DamageCalculator"].get("BaseDamage", {})) != {"Physical"}:
            raise ValueError("Native Praetorian charge changed")
        charge["Type"] = "RPG_EnemyDamage"
        for path, data in ((cae_path, routed_cae), (charge_path, charge)):
            output = ROOT / "src/main/resources" / path
            encoded = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
            if check:
                if not output.is_file() or output.read_text(encoding="utf-8") != encoded:
                    raise ValueError(f"Packaged native combat evaluator differs: {path}")
            else:
                output.parent.mkdir(parents=True, exist_ok=True)
                output.write_text(encoded, encoding="utf-8")
        manifest["derivedBindings"]["installedCombatArchetypes"] = result
        manifest["derivedBindings"]["canonicalVariants"] = variants
        encoded = json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"
        if check:
            if MANIFEST_PATH.read_text(encoding="utf-8") != encoded:
                raise ValueError("Pinned manifest differs from installed combat archetype proof")
        else:
            MANIFEST_PATH.write_text(encoded, encoding="utf-8")


if __name__ == "__main__":
    main(check="--check" in sys.argv[1:])
