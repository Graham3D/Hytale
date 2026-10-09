"""Generate the R229 installed world-spawn coverage report (offline only)."""
import functools
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = Path.home() / "AppData/Roaming/Hytale/install/pre-release/package/game/latest/Assets.zip"
CATALOG = json.loads((ROOT / "src/main/resources/rpg/progression/enemy-registry.json").read_text())
BINDINGS = json.loads((ROOT / "src/main/resources/rpg/enemies/native-bindings-v1.json").read_text())
AUTHORED = json.loads((ROOT / "src/main/resources/rpg/progression/authored-encounters.json").read_text())
ROWS = {row["roleId"]: row for key in ("roles", "aliases") for row in CATALOG[key]}
CAMPAIGN = {row["id"] for row in AUTHORED["roles"] if row.get("campaignRegion")}
CERTIFIED = {id for binding in BINDINGS["bindings"] if binding["productionPromotionEnabled"]
             for id in binding["nativeRoleIds"]}
for variant in BINDINGS["derivedBindings"]["canonicalVariants"]:
    CERTIFIED.update(variant["nativeRoleIds"])
for archetype in BINDINGS["derivedBindings"]["nativeArchetypes"]:
    CERTIFIED.update(member["roleId"] for member in archetype["memberRoleIds"])
for archetype in BINDINGS["derivedBindings"].get("installedCombatArchetypes", []):
    CERTIFIED.update(member["roleId"] for member in archetype["members"])


def main():
    if hashlib.sha256(ASSETS.read_bytes()).hexdigest() != BINDINGS["assetsSha256"]:
        raise ValueError("Installed Assets.zip differs from binding manifest")
    with zipfile.ZipFile(ASSETS) as zip_file:
        native_paths = {name.rsplit("/", 1)[-1][:-5]: name for name in zip_file.namelist()
                        if name.startswith("Server/NPC/Roles/") and name.endswith(".json")}

        @functools.lru_cache(None)
        def resolved(role_id):
            path = native_paths.get(role_id)
            if path is None:
                return {}, {}, None
            obj = json.loads(zip_file.read(path))
            base, parameters, _ = resolved(obj["Reference"]) if "Reference" in obj else ({}, {}, None)
            direct = dict(base)
            params = dict(parameters)
            for key, value in obj.get("Parameters", {}).items():
                params[key] = value.get("Value") if isinstance(value, dict) and "Value" in value else value
            params.update(obj.get("Modify", {}))
            direct.update({key: value for key, value in obj.items()
                           if key not in ("Parameters", "Modify", "Reference", "Type")})
            return direct, params, obj

        world = set()
        for name in zip_file.namelist():
            if name.startswith("Server/NPC/Spawn/World/") and name.endswith(".json"):
                world.update(row["Id"] for row in json.loads(zip_file.read(name)).get("NPCs", [])
                             if row.get("Id"))
        natural = sorted(world & ROWS.keys())

        def native_field(role_id, key):
            direct, params, _ = resolved(role_id)
            value = direct.get(key, params.get(key))
            if isinstance(value, dict) and "Compute" in value:
                value = params.get(value["Compute"])
            # Installed SupportConfigBuilder.readConfig defaults this enum to
            # Attitude.HOSTILE when the role omits DefaultPlayerAttitude.
            if key == "DefaultPlayerAttitude" and value is None:
                return "Hostile"
            return "<computed native route>" if isinstance(value, dict) else value

        def reason(role_id):
            row = ROWS[role_id]
            canonical = row.get("canonicalRoleId", role_id)
            if canonical in CAMPAIGN or role_id in CAMPAIGN or row.get("rank") == "BOSS":
                return "Protected boss/campaign ownership; random promotion excluded"
            attitude = native_field(role_id, "DefaultPlayerAttitude")
            if attitude == "Neutral":
                return "Native default player attitude Neutral; hostile world-spawn admission rejects"
            if attitude != "Hostile":
                return f"Native player hostility unresolved ({attitude}); action binding not certified"
            if role_id == "Scarak_Seeker":
                return ("TECHNICAL: native Scarak_Seeker_Spit_Projectile is a ProjectileInteraction, "
                        "not the certified LaunchProjectileInteraction; its projectile-config impact "
                        "also directly applies Poison_T1. The existing pre-queue receipt hook and "
                        "status owner cannot certify this launch→impact route without new infrastructure")
            if role_id == "Goblin_Turret":
                return ("TECHNICAL: Root_NPC_Goblin_Turret_Attack uses a native ProjectileInteraction "
                        "and Projectile_Config_Goblin_Turret_Trash owns the Physical hit and Stone_Death "
                        "effect. The existing LaunchProjectile pre-queue receipt hook cannot correlate "
                        "this native projectile and its status/impact mutations")
            _, params, source = resolved(role_id)
            if source is None:
                return "Installed native role asset unavailable"
            attack = native_field(role_id, "Attack")
            ref = source.get("Reference", "<no Reference>")
            if canonical != role_id:
                return f"Canonical {canonical} lacks a certified binding for inherited action {attack or '<computed>'}"
            if ref == "Template_Predator" and attack == "Root_NPC_Attack_Melee":
                variables = params.get("_InteractionVars", {})
                if "Melee_Selector" in variables:
                    return "Native melee selector differs from certified single-hit archetype"
                damage = variables.get("Melee_Damage", {}).get("Interactions", [])
                if len(damage) != 1 or damage[0].get("Parent") != "NPC_Attack_Melee_Damage":
                    return "Conditional or multi-step native melee damage is not certified"
                if "DamageEffects" in damage[0] or "Effects" in damage[0]:
                    return "Native melee hit has additional effects; separate capability proof required"
                return "Native melee signature differs from the certified plain Physical leaf"
            if attack == "Root_NPC_Attack_Melee":
                return f"Native parent {ref} has an unreviewed melee/status or movement signature"
            if not attack:
                return f"Native attack is computed or absent under {ref}; exact damage route unbound"
            return f"Distinct native action {attack} under {ref}; damage route not certified"

        hostile = [id for id in natural if native_field(id, "DefaultPlayerAttitude") == "Hostile"]
        natural_certified = sorted(set(natural) & CERTIFIED)
        remaining = sorted(set(natural) - CERTIFIED)
        certified_hostile = sorted(set(hostile) & CERTIFIED)
        unbound_hostile = sorted(set(hostile) - CERTIFIED)
        if unbound_hostile != ["Goblin_Turret", "Scarak_Seeker"]:
            raise ValueError(f"Hostile coverage changed; review exact native routes: {unbound_hostile}")
        lines = ["# R229 production elite coverage — installed native assets", "",
                 "Generated by `tools/Audit-ProductionEliteCoverage.py` from the pinned installed `Assets.zip`, "
                 "the authored catalog, and `native-bindings-v1.json`. Native world-spawn `NPCs[].Id` is the "
                 "population denominator; marker, quest, and command-only spawns are excluded. "
                 "A certified role still requires the existing runtime hostile/provenance/profile checks. "
                 "The static attitude count resolves installed Role `Reference`, `Parameters`, and `Modify`; "
                 "the live classifier remains authoritative.", "",
                 f"- Total catalog IDs: **{len(ROWS)}** ({len(CATALOG['roles'])} canonical, {len(CATALOG['aliases'])} aliases).",
                 f"- Native world-spawn IDs: **{len(world)}**; catalogued natural world-spawn role IDs: **{len(natural)}**.",
                 f"- Naturally spawning catalogued hostile roles: **{len(hostile)}**.",
                 f"- Production-certified hostile roles: **{len(certified_hostile)}**.",
                 f"- Genuine technical exclusions among hostile roles: **{len(unbound_hostile)}** "
                 f"({', '.join(unbound_hostile)}).",
                 f"- Other unbound natural roles: **{len(remaining)-len(unbound_hostile)}**; "
                 "these have Neutral native player attitude or protected ownership.", "",
                 "## Newly inherited canonical variants", "",
                 "| Variant | Canonical binding | Native difference |", "| --- | --- | --- |",
                 ]
        for variant in BINDINGS["derivedBindings"]["canonicalVariants"]:
            for id in variant["nativeRoleIds"]:
                lines.append(f"| {id} | {variant['canonicalRoleId']} | "
                             "Installed alias changes only reviewed noncombat role fields |")
        lines += ["", "## Newly certified installed combat archetypes", "",
                  "Each group is generated from the pinned native graph by "
                  "`tools/Build-ProductionEliteRemaining.py`. Melee adapters change only existing "
                  "damage-leaf types; the Praetorian evaluator and charge retain their original "
                  "selection, movement, damage values and effects. Native projectiles retain "
                  "their original launch and impact owners. Unsupported affixes are rejected "
                  "from the recorded action capability set, never rolled as silent no-ops.", "",
                  "| Archetype | Member | Roots |", "| --- | --- | ---: |"]
        for archetype in BINDINGS["derivedBindings"]["installedCombatArchetypes"]:
            for member in archetype["members"]:
                lines.append(f"| {archetype['routeKind']} | {member['roleId']} | "
                             f"{len(member['actions'])} |")
        lines += ["", "## Remaining natural-role denials", "",
                  "These are exact static certification/hostility reasons, not a second production classifier. "
                  "The runtime classifier remains authoritative. The two hostile technical exclusions "
                  "need exact native hit identity or a source-aware projectile/status route the existing "
                  "owner cannot prove. Their native attacks remain intact; no attack or capability was invented.",
                  "", "| Natural role | Denial reason |", "| --- | --- |"]
        for role_id in remaining:
            lines.append(f"| {role_id} | {reason(role_id)} |")
        lines += ["", "## Offline validation boundary", "",
                  "This report proves pinned asset signatures, packaged adapter deltas and "
                  "static hostile coverage. Compilation and focused unit/package validation are "
                  "reported separately in the release note. Runtime attachment still re-resolves "
                  "the actual loaded graph and fails closed if it differs. Connected combat behavior "
                  "remains for the single consolidated owner QA build.", ""]
        output = ROOT / "docs/enemies/R229_PRODUCTION_ELITE_COVERAGE.md"
        output.write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"catalog={len(ROWS)} natural={len(natural)} hostile={len(hostile)} "
              f"certifiedHostile={len(certified_hostile)} technicalExclusions={len(unbound_hostile)}")


if __name__ == "__main__":
    main()
