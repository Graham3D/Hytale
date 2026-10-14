"""Offline scope evidence, not a production eligibility list or execution certificate.

Resolve simple native parameter references before collecting combat roots. Preserve
unresolved Compute expressions. World selectors, markers, beacons, flock membership
and arbitrary references are distinct evidence. No naming convention grants scope.
"""
import argparse
from collections import Counter, defaultdict
import copy
import csv
import hashlib
import io
import json
from pathlib import Path
import zipfile
import me12_content as m
from me12_native_inventory import ASSET_PIN, strings


def walk(value, path=""):
    yield path, value
    if isinstance(value, dict):
        for key, child in value.items():
            if key not in ("Description", "$Comment", "Comment"):
                yield from walk(child, path + "/" + key)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from walk(child, path + "/" + str(index))


def expand(value, parameters, active=()):
    if isinstance(value, dict):
        if set(value) == {"Compute"}:
            expression = value["Compute"]
            if expression in parameters and expression not in active:
                return expand(parameters[expression], parameters, (*active, expression))
            return value  # Expressions and cycles are unknown, never guessed.
        return {key: expand(child, parameters, active) for key, child in value.items()
                if key not in ("Parameters", "$Comment", "Description")}
    if isinstance(value, list):
        return [expand(child, parameters, active) for child in value]
    return value


def active_walk(value, path=""):
    """Prune only literal disabled nodes; do not invent evaluation of native expressions."""
    if isinstance(value, dict) and value.get("Enabled") is False:
        return
    yield path, value
    if isinstance(value, dict):
        for key, child in value.items():
            if key not in ("Description", "$Comment", "Comment", "Parameters", "Modify", "InteractionVars"):
                yield from active_walk(child, path + "/" + key)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from active_walk(child, path + "/" + str(index))


class NativeIndex:
    def __init__(self, archive):
        self.archive = archive
        self.paths = set(archive.namelist())
        self.roles = {Path(p).stem: p for p in sorted(self.paths)
                      if p.startswith("Server/NPC/Roles/") and p.endswith(".json")}
        self.items = {Path(p).stem: p for p in sorted(self.paths)
                      if p.startswith("Server/Item/Items/") and p.endswith(".json")}
        self.graphs = defaultdict(list)
        for path in sorted(self.paths):
            if path.endswith(".json") and any(path.startswith("Server/" + prefix) for prefix in (
                    "Item/Interactions/", "Item/RootInteractions/", "ProjectileConfigs/", "Projectiles/", "NPC/Combat/")):
                self.graphs[Path(path).stem].append(path)
        self.cache = {}
        self.evidence = {}

    def read(self, path):
        if path not in self.cache:
            raw = self.archive.read(path)
            self.cache[path] = json.loads(raw)
            self.evidence[path] = hashlib.sha256(raw).hexdigest()
        return self.cache[path]

    def role(self, role_id, overrides=None):
        chain = []
        current = role_id
        while current in self.roles:
            if current in chain:
                raise ValueError("ROLE_REFERENCE_CYCLE:" + role_id)
            chain.append(current)
            current = self.read(self.roles[current]).get("Reference")
        parameters, body = {}, {}
        for item in reversed(chain):
            value = self.read(self.roles[item])
            parameters.update({k: v["Value"] for k, v in value.get("Parameters", {}).items() if "Value" in v})
            parameters.update(value.get("Modify", {}))
            if value.get("Type") != "Variant":
                body = {k: v for k, v in value.items() if k != "Parameters"}
        parameters.update(overrides or {})
        return chain, expand(body, parameters), parameters

    def combat(self, role_id):
        chain, body, parameters = self.role(role_id)
        roots, components, unresolved, operations, movement = set(), set(), set(), set(), set()
        item_actions, item_paths = set(), set()
        pending = [(role_id, body, parameters)]
        while pending:
            owner, value, params = pending.pop()
            for pointer, child in active_walk(value):
                if isinstance(child, dict):
                    if "Compute" in child:
                        unresolved.add(str(child["Compute"]))
                    if child.get("Type") in ("Attack", "Combat", "Spawn", "SpawnNPC", "ChangeRole"):
                        operations.add(child["Type"])
                    if child.get("Type") == "Attack" and not child.get("Attack"):
                        item_actions.add(child.get("AttackType", "Primary"))
                    reference = child.get("Reference")
                    if isinstance(reference, str) and reference in self.roles and reference.startswith("Component_"):
                        # Component-local Modify values specialize parent parameters.
                        child_params = dict(params)
                        child_params.update(child.get("Modify", {}))
                        key = (reference, json.dumps(child_params, sort_keys=True))
                        if key not in components:
                            components.add(key)
                            if len(components) > 256:
                                raise ValueError("COMPONENT_AUDIT_BOUND:" + role_id)
                            _, resolved, resolved_params = self.role(reference, child_params)
                            pending.append((reference, resolved, resolved_params))
                if (isinstance(child, str) and child in self.graphs and
                        pointer.rsplit("/", 1)[-1] in ("Attack", "Interaction", "CombatConfig",
                            "NPCCollisionInteraction", "PlayerCollisionInteraction", "BlockCollisionInteraction")):
                    roots.add(child)
            for controller in value.get("MotionControllerList", []) if isinstance(value, dict) else []:
                movement.add(str(controller.get("Type", "UNKNOWN")))
        # A native Primary attack may come from the held item rather than an NPC root.
        # Inventory possible slots only; actual equipped-slot/state selection needs execution.
        if item_actions:
            for item in strings(body.get("HotbarItems", [])):
                seen_items, item_value, current = set(), {}, item
                while current in self.items:
                    if current in seen_items:
                        raise ValueError("ITEM_PARENT_CYCLE:" + item)
                    seen_items.add(current)
                    path = self.items[current]
                    item_paths.add(path)
                    value = self.read(path)
                    for key, val in value.items():
                        item_value.setdefault(key, val)
                    current = value.get("Parent")
                for action in item_actions:
                    interaction = item_value.get("Interactions", {}).get(action)
                    roots.update(v for v in strings(interaction) if v in self.graphs)
        graph_paths, graph_types, channels, effects = set(), set(), set(), set()
        graph_pending = list(roots)
        seen = set()
        while graph_pending:
            key = graph_pending.pop()
            if key in seen:
                continue
            seen.add(key)
            if len(seen) > 2048:
                raise ValueError("GRAPH_AUDIT_BOUND:" + role_id)
            for path in self.graphs.get(key, []):
                graph_paths.add(path)
                value = self.read(path)
                for pointer, node in walk(value):
                    if isinstance(node, dict):
                        if "Type" in node:
                            graph_types.add(str(node["Type"]))
                        channels.update(node.get("BaseDamage", {}).keys())
                        if "EffectId" in node:
                            effects.add(str(node["EffectId"]))
                        # Native Replace defaults/role variables are still conservative: collect
                        # the effective variable only when the graph actually references its key.
                        variable = node.get("Var")
                        if node.get("Type") == "Replace" and variable in body.get("InteractionVars", {}):
                            graph_pending.extend(v for v in strings(body["InteractionVars"][variable]) if v in self.graphs)
                graph_pending.extend(v for v in strings(value) if v in self.graphs and v not in seen)
        return dict(lineage=chain, roots=sorted(roots), graphPaths=sorted(graph_paths),
                    graphTypes=sorted(graph_types), damageChannels=sorted(channels), effects=sorted(effects),
                    components=sorted({key[0] for key in components}), unresolvedCompute=sorted(unresolved),
                    operations=sorted(operations), motion=sorted(movement),
                    attitude=body.get("DefaultPlayerAttitude", "UNSPECIFIED"),
                    nativeEffects=parameters.get("_CombatConfig", body.get("CombatConfig")),
                    flock=parameters.get("FlockArray", body.get("FlockSpawnTypes", [])),
                    itemActionTypes=sorted(item_actions), itemPaths=sorted(item_paths),
                    capabilityEvidence="STATIC_REACHABILITY_NOT_NATIVE_EXECUTION")


def scope(row, native, spawn, category):
    """Evidence labels only. Never feed these labels to runtime promotion."""
    if not row["productionRejection"]:
        return "CONFIGURED_EXISTING", "Existing production binding; actual actor admission still required"
    if row["productionRejection"] == "AUTHORED_CAMPAIGN_ENCOUNTER":
        return "EXISTING_AUTHORED_EXCLUSION", "EnemyNativeBindings.productionEligibilityRejection"
    if not native:
        return "RETIRED_ID_RECONCILIATION", "No native role asset; preserve saved catalog identity"
    if row["canonicalRoleId"] in ("Goblin_Turret", "Scarak_Seeker"):
        return "AD1_PRIORITY_FAMILY", "ME12-STAGES-4P-01 1C names these roles and aliases; spawn context remains separate"
    if row["canonicalRoleId"] is None:
        return "NEW_NATIVE_SCOPE_DECISION", "No approved exact catalog mapping; hostile inventory is not procedural authorization"
    if category in ("WILDLIFE", "AMBIENT_AVIAN"):
        return "EXISTING_NONHOSTILE_CATEGORY_REVIEW", "Prior population category; not a newly invented promotion exclusion"
    if native["attitude"] != "Hostile" and category != "HOSTILE":
        return "BEHAVIOR_OR_ENCOUNTER_REVIEW", "Combat capability does not establish hostile natural provenance"
    if any(s["kind"] == "World" for s in spawn):
        return "EXISTING_HOSTILE_WORLD_CANDIDATE", "Catalog plus native world selector and hostile evidence; certificate still missing"
    if any(s["kind"] == "Markers" for s in spawn):
        return "EXISTING_HOSTILE_MARKER_CANDIDATE", "Marker-defined placement; current world-job promotion path is not proof of marker support"
    return "CONDITIONAL_OR_FLOCK_CANDIDATE", "No direct world/marker selector; inspect referenced creator or flock provenance"


def groups(row, combat, spawn):
    if row["productionRejection"] == "AUTHORED_CAMPAIGN_ENCOUNTER":
        return ["EXISTING_POLICY"]
    if combat is None:
        return ["CATALOG_RETIRED_ID"]
    result = []
    if row["canonicalRoleId"] is None:
        result.append("CATALOG_MAPPING_AND_SCOPE")
    if row["productionRejection"]:
        result.append("EXACT_ROLE_CERTIFICATE")
        if not any(s["kind"] == "World" for s in spawn):
            result.append("SPAWN_PROVENANCE")
        types = set(combat["graphTypes"])
        if "Projectile" in types:
            result.append("MODERN_PROJECTILE_RECEIPT")
        if "LaunchProjectile" in types:
            result.append("LEGACY_PROJECTILE_ROLE_CERTIFICATE")
        if "DamageEntity" in types:
            result.append("NATIVE_MELEE_OR_CONFIG_DAMAGE")
        if "EffectCondition" in types or "ApplyEffect" in types:
            result.append("CONDITIONAL_STATUS_EFFECT")
        if not combat["roots"]:
            result.append("UNRESOLVED_OR_NO_COMBAT_ROOT")
        if combat["itemActionTypes"]:
            result.append("EQUIPPED_ITEM_ACTION_CERTIFICATE")
    if row["roleId"] == "Golem_Firesteel":
        result.append("ACTIONLESS_PRODUCTION_ATTACHMENT")
    if not row["productionRejection"] and "Projectile" in combat["graphTypes"]:
        result.append("AUXILIARY_CONFIG_PROJECTILE_NOT_PLAYER_ATTACK_CERTIFICATE")
    return result or ["EXISTING_OFFLINE_CONTRACT"]


def build(archive, coverage, population):
    index = NativeIndex(archive)
    spawns, references = defaultdict(list), defaultdict(list)
    for path in sorted(index.paths):
        if not path.endswith(".json") or not path.startswith("Server/NPC/Spawn/"):
            continue
        if any(part.strip(" _").lower() in {"test", "tests", "deprecated", "debug"} for part in Path(path).parts):
            continue
        value = index.read(path)
        for pointer, node in walk(value):
            if pointer.startswith("/NPCs/") and isinstance(node, dict):
                role_id = node.get("Id", node.get("Name"))
                if role_id in index.roles:
                    spawns[role_id].append(dict(path=path, pointer=pointer, kind=path.split("/")[3],
                                               environments=value.get("Environments", []), weight=node.get("Weight")))
    universe = {r["roleId"] for r in coverage["rows"]}
    # References are deliberately not asserted as spawning. Include JSON pointers and owner.
    for role_id, path in sorted(index.roles.items()):
        value = index.read(path)
        for pointer, child in walk(value):
            if isinstance(child, str) and child in universe and child != role_id:
                references[child].append(dict(owner=role_id, path=path, pointer=pointer,
                                             kind="INHERITANCE" if pointer == "/Reference" else "REFERENCE_ONLY"))
    rows = []
    for original in coverage["rows"]:
        row = copy.deepcopy(original)
        role_id = row["roleId"]
        path = index.roles.get(role_id)
        combat = index.combat(role_id) if path else None
        category = population.get(role_id, "UNCLASSIFIED")
        decision, reason = scope(row, combat, spawns[role_id], category)
        role_type = index.read(path).get("Type") if path else "MISSING"
        # Encounter indicators need review, not an automatically applied policy exclusion.
        indicators = []
        if path and "/Boss/" in path:
            indicators.append("NATIVE_BOSS_ASSET_DIRECTORY")
        if role_type in ("Abstract", "Component"):
            indicators.append("ABSTRACT_OR_COMPONENT")
        row.update(nativeType=role_type, proceduralScope=decision, scopeEvidence=reason,
                   category=category, encounterIndicators=indicators, spawnDefinitions=spawns[role_id],
                   references=references[role_id], combatEvidence=combat,
                   nativeFamily=(combat["lineage"][1:] if combat else []),
                   catalogAliases=sorted(r["roleId"] for r in coverage["rows"]
                       if row["canonicalRoleId"] is not None and r["canonicalRoleId"] == row["canonicalRoleId"] and r["roleId"] != role_id),
                   blockerGroups=groups(row, combat, spawns[role_id]),
                   offlineNativeExecution="EXISTING_TESTED_SEAMS_ONLY" if not row["productionRejection"] else "NOT_CERTIFIED",
                   connectedExecution="DEFERRED_TO_STAGE4", productScopeChanged=False)
        row["auditBucket"] = ("EXISTING_AUTHORED_EXCLUSION" if row["productionRejection"] == "AUTHORED_CAMPAIGN_ENCOUNTER"
            else "CONFIGURED_EXISTING" if not row["productionRejection"]
            else "CATALOG_ONLY_41" if row["scope"] == "LEGACY_CATALOG_REVIEW_REQUIRED" else "NATIVE_UNCERTIFIED_179")
        rows.append(row)
    return dict(schemaVersion=1, assetsSha256=ASSET_PIN, runtimeLoaded=False, eligibilityChanged=False,
                policyScope="EVIDENCE_ONLY_NO_NEW_EXCLUSIONS", rows=rows,
                scopeCounts=dict(sorted(Counter(r["proceduralScope"] for r in rows).items())),
                blockerCounts=dict(sorted(Counter(g for r in rows for g in r["blockerGroups"]).items())),
                sourceHashes=dict(sorted(index.evidence.items())))


def plan_columns(report, row):
    result = []
    for era in m.ERAS:
        state = row["eras"][era]
        key = state.get("structuralContext")
        result.append(str(len(report["contexts"][key]["feasibleNamePairs"])) if key else
                      "X" if state["status"] == "POLICY_EXCLUDED_NATIVE_IMMUNITY_CAP" else "?")
    return result


def csv_evidence(report):
    output = io.StringIO(newline="")
    writer = csv.writer(output, lineterminator="\n")
    writer.writerow(["role", "bucket", "catalogCanonical", "catalogAliases", "nativeType", "nativePath", "nativeSha256",
        "nativeReferenceAncestry", "encounterIndicators", "spawnDefinitions", "referenceOwnersAndPointers",
        "effectiveRootCandidates", "graphAssetPaths", "nativeDamageChannels", "nativeEffects", "movement", "flock",
        "unresolvedExpressions", "scopeEvidenceClass", "scopeEvidence", "existingRejection", "Normal", "Nightmare", "Hell",
        "missingRequirements", "nativeExecution", "connectedAcceptance"])
    for row in report["rows"]:
        c = row["combatEvidence"] or {}
        path = row.get("nativeRolePath")
        writer.writerow([row["roleId"], row["auditBucket"], row["canonicalRoleId"], ";".join(row["catalogAliases"]), row["nativeType"], path,
            report["sourceHashes"].get(path, "MISSING"), ";".join(row["nativeFamily"]), ";".join(row["encounterIndicators"]),
            ";".join(s["path"] + "#" + s["pointer"] for s in row["spawnDefinitions"]),
            ";".join(s["owner"] + "#" + s["pointer"] + ":" + s["kind"] for s in row["references"]),
            ";".join(c.get("roots", [])), ";".join(c.get("graphPaths", [])), ";".join(c.get("damageChannels", [])),
            json.dumps(c.get("nativeEffects")), ";".join(c.get("motion", [])), json.dumps(c.get("flock")),
            ";".join(c.get("unresolvedCompute", [])), row["proceduralScope"], row["scopeEvidence"], row["productionRejection"],
            *plan_columns(report, row), ";".join(row["blockerGroups"]), row["offlineNativeExecution"], row["connectedExecution"]])
    return output.getvalue()


def render(report):
    lines = ["# ME12 Stage 1 native coverage reconciliation", "",
             "Generated from pinned installed 0.7.1 role inheritance and spawn definitions. No production eligibility or policy exclusions changed.",
             "The 309-ID union is an audit inventory, not 309 approved procedural Unique roles. Counts below are evidence classifications, not product decisions.",
             "World = native population selector; Markers = placed spawn definition; Beacons = beacon definition. A reference is not a spawn observation.",
             "Variant is a native concrete specialization, not proof of an abstract role. Catalog canonical identity and native Reference ancestry are separate.",
             "Combat roots are a conservative static graph inventory with simple Compute substitution; unresolved expressions/branches remain explicit in JSON. This is not a Hytale role-builder or execution certificate.",
             "Exact source paths, JSON pointers, hashes, aliases, effects, controllers and unresolved expressions: build/reports/me12/reconciliation.json.",
             "Version-controlled per-role evidence: ME12_STAGE1_RECONCILIATION.csv. All 304 present IDs are concrete native Variant specializations; five catalog IDs have no native role asset. Encounter involvement is a separate axis.",
             "All connected execution/owner acceptance remains false and DEFERRED_TO_STAGE4. Static plan coverage is inherited from the current Java-certified binding audit, never fabricated for missing bindings.", "",
             "## Scope evidence", "", "| Classification | IDs |", "| --- | ---: |"]
    lines += [f"| {key} | {count} |" for key, count in report["scopeCounts"].items()]
    lines += ["", "## Every role", "",
              "N/Nm/H: number of proven feasible name pairs, X = existing Normal immunity policy exclusion, ? = no certified binding/plan (not zero feasibility).",
              "Exact rows for native boss/component indicators are review evidence; only EXISTING_AUTHORED_EXCLUSION is an already enforced policy exclusion.", "",
              "| Role -> catalog canonical | Native kind / parent | Spawn definition kinds | Combat roots / channels | Scope | N/Nm/H | Missing work |",
              "| --- | --- | --- | --- | --- | --- | --- |"]
    for row in report["rows"]:
        c = row["combatEvidence"]
        plan = plan_columns(report, row)
        kinds = ",".join(sorted({x["kind"] for x in row["spawnDefinitions"]})) or "none; references=" + str(len(row["references"]))
        parent = row["nativeFamily"][0] if row["nativeFamily"] else "none"
        roots = ", ".join(c["roots"]) if c else "missing native ID"
        channels = ",".join(c["damageChannels"]) if c else ""
        lines.append("| " + " | ".join([row["roleId"] + " -> " + (row["canonicalRoleId"] or "unmapped"),
            row["nativeType"] + " / " + parent + ("; encounter review" if row["encounterIndicators"] else ""), kinds, roots + " / " + channels,
            row["proceduralScope"], "/".join(plan), ", ".join(row["blockerGroups"])]) + " |")
    lines += ["", "## Grouped work (counts overlap)", "", "| Group | IDs |", "| --- | ---: |"]
    lines += [f"| {key} | {count} |" for key, count in report["blockerCounts"].items()]
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    assets = Path.home() / "AppData/Roaming/Hytale/install/release/package/game/latest/Assets.zip"
    with assets.open("rb") as stream:
        assert hashlib.file_digest(stream, "sha256").hexdigest() == ASSET_PIN
    coverage = m.read(m.ROOT / "build/reports/me12/native-coverage.json")
    population = m.read(m.ROOT / "src/main/resources/rpg/spawning/native-population-roles-v1.json")["categories"]
    with zipfile.ZipFile(assets) as archive:
        report = build(archive, coverage, population)
    report["contexts"] = coverage["contexts"]
    assert len(report["rows"]) == 309
    assert Counter(r["auditBucket"] for r in report["rows"]) == {
        "NATIVE_UNCERTIFIED_179": 179, "CATALOG_ONLY_41": 41, "CONFIGURED_EXISTING": 84, "EXISTING_AUTHORED_EXCLUSION": 5}
    output = m.ROOT / "build/reports/me12/reconciliation.json"
    output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    for suffix, rendered in (("md", render(report)), ("csv", csv_evidence(report))):
        target = m.ROOT / ("docs/enemies/ME12_STAGE1_RECONCILIATION." + suffix)
        if args.check:
            assert target.read_text(encoding="utf-8") == rendered, "reconciliation matrix differs from source"
        else:
            target.write_text(rendered, encoding="utf-8")
    print(json.dumps(dict(roles=len(report["rows"]), scope=report["scopeCounts"], groups=report["blockerCounts"]), indent=2))


if __name__ == "__main__":
    main()
