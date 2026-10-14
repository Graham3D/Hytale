"""Render Stage 1 structural coverage and honest 0.7.1 certificate gaps.

Reads installed assets only. All output is inside the selected source worktree.
Native reference inventories are evidence for review, not execution certificates.
"""
import argparse
import hashlib
import json
import re
import zipfile
from pathlib import Path
import me12_content as m

ASSET_PIN = "b9164eb07d7c42e697d208ba3d35078a9b4a0125beF6261847a2f91e293e53b4".lower()


def strings(value):
    if isinstance(value, str):
        yield value
    elif isinstance(value, dict):
        for key, child in value.items():
            if key not in ("Type", "$Comment", "Description", "Name", "Texture", "Model", "TranslationKey"):
                yield from strings(child)
    elif isinstance(value, list):
        for child in value:
            yield from strings(child)


def deltas(a, b, path=""):
    if type(a) != type(b):
        return [dict(path=path, packaged=a, native=b)]
    if isinstance(a, dict):
        result = []
        for key in sorted(a.keys() | b.keys()):
            result += deltas(a.get(key), b.get(key), path + "/" + key)
        return result
    if isinstance(a, list):
        if len(a) != len(b):
            return [dict(path=path, packaged=a, native=b)]
        return [d for i, (x, y) in enumerate(zip(a, b)) for d in deltas(x, y, path + "/" + str(i))]
    return [] if a == b else [dict(path=path, packaged=a, native=b)]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--assets", type=Path, default=Path.home() / "AppData/Roaming/Hytale/install/release/package/game/latest/Assets.zip")
    args = parser.parse_args()
    with args.assets.open("rb") as stream:
        assert hashlib.file_digest(stream, "sha256").hexdigest() == ASSET_PIN
    report = m.read(m.ROOT / "build/reports/me12/structural-coverage.json")
    inputs = m.read(m.ROOT / "build/reports/me12/coverage-inputs.json")
    exported = {r["roleId"]: r for r in inputs["rows"]}
    rows = {r["roleId"]: r for r in report["rows"]}
    manifest = (m.ROOT / "docs/enemies/HOSTILE_MOB_MANIFEST_0_7_1.md").read_text(encoding="utf-8")
    section = "DIRECT_SPAWN_ATTACKER"
    native_scope = {}
    for line in manifest.splitlines():
        if line.startswith("## ") and ("conditional" in line.lower() or "scripted" in line.lower()):
            section = "CONDITIONAL_ATTACKER"
        match = re.match(r"\| .*?\(`([^`]+)`\) \|", line)
        if match:
            native_scope[match.group(1)] = section
    assert len(native_scope) == 267, len(native_scope)
    for role_id in native_scope.keys() - rows.keys():
        rows[role_id] = dict(roleId=role_id, canonicalRoleId=None, productionRejection="NO_CATALOG_ROLE",
                            eras={era: dict(status="BLOCKED_NO_CATALOG_OR_CERTIFICATE") for era in m.ERAS})
    resources = m.ROOT / "src/main/resources"
    with zipfile.ZipFile(args.assets) as archive:
        paths = [p for p in archive.namelist() if p.startswith("Server/") and p.endswith(".json")]
        native_roles = {Path(p).stem: p for p in paths if p.startswith("Server/NPC/Roles/")}
        indexed = {}
        for path in paths:
            if any(path.startswith("Server/" + prefix) for prefix in (
                    "Item/RootInteractions/", "Item/Interactions/", "ProjectileConfigs/", "Projectiles/", "NPC/Combat/")):
                indexed.setdefault(Path(path).stem, []).append(path)
        cache = {}
        def asset(path):
            if path not in cache:
                cache[path] = json.loads(archive.read(path))
            return cache[path]

        for role_id, row in sorted(rows.items()):
            row["scope"] = native_scope.get(role_id, "LEGACY_CATALOG_REVIEW_REQUIRED")
            row["nativeConnectedPassed"] = False
            row["ownerAccepted"] = False
            row["nativeRolePath"] = native_roles.get(role_id)
            row["lineage"] = []
            path = row["nativeRolePath"]
            seen = set()
            refs = set()
            while path and path not in seen:
                seen.add(path)
                native = asset(path)
                override = resources / path
                effective = m.read(override) if override.exists() else native
                evidence = dict(path=path, nativeSha256=hashlib.sha256(archive.read(path)).hexdigest(),
                                packagedOverride=override.exists())
                if override.exists():
                    evidence["packagedSha256"] = hashlib.sha256(override.read_bytes()).hexdigest()
                    evidence["overrideDelta"] = deltas(effective, native)
                evidence["motionControllers"] = effective.get("MotionControllerList", [])
                evidence["motionSettings"] = {k: v for k, v in effective.get("Modify", {}).items()
                                             if k in ("MaxSpeed", "KnockbackScale", "SlowImmune", "StunImmune",
                                                      "ChaseRelativeSpeed", "CombatMovingRelativeSpeed")}
                row["lineage"].append(evidence)
                refs.update(v for v in strings(effective) if v in indexed)
                path = native_roles.get(effective.get("Reference"))
            source = exported.get(role_id, {})
            role = source.get("role")
            if role:
                row["binding"] = role
                row["individualEligibility"] = source["individualEligibility"]
                row["sourcePinMatches071"] = (role["sourceAssetPath"] in archive.namelist()
                    and hashlib.sha256(archive.read(role["sourceAssetPath"])).hexdigest() == role["sourceAssetSha256"].lower())
                refs.update(a["nativeActionId"] for a in role["actions"])
            row["nativeReferenceInventory"] = []
            visited = set()
            pending = sorted(refs)
            while pending:
                ref = pending.pop()
                if ref in visited:
                    continue
                visited.add(ref)
                assert len(visited) <= 2048, "native reference inventory bound exhausted"
                for target in indexed.get(ref, []):
                    value = asset(target)
                    row["nativeReferenceInventory"].append(dict(id=ref, path=target,
                        sha256=hashlib.sha256(archive.read(target)).hexdigest()))
                    pending.extend(v for v in strings(value) if v in indexed and v not in visited)
            row["nativeReferenceInventory"].sort(key=lambda d: d["path"])
            row["certificateStatus"] = "BLOCKED_071_CONNECTED_AND_ROUTE_CERTIFICATION"
            if not role:
                row["certificateStatus"] = "BLOCKED_NO_ROLE_CERTIFICATE"
    # Intern repeated native evidence instead of checking in a large duplicated QA archive.
    lineage_assets = {}
    references = {}
    for row in rows.values():
        for entry in row["lineage"]:
            previous = lineage_assets.setdefault(entry["path"], entry)
            assert previous == entry
        row["lineage"] = [entry["path"] for entry in row["lineage"]]
        for entry in row["nativeReferenceInventory"]:
            previous = references.setdefault(entry["path"], entry)
            assert previous == entry
        row["nativeReferenceInventory"] = [entry["path"] for entry in row["nativeReferenceInventory"]]
    report["lineageAssets"] = dict(sorted(lineage_assets.items()))
    report["referencedAssets"] = dict(sorted(references.items()))
    report["rows"] = [rows[k] for k in sorted(rows)]
    report["assetSha256"] = ASSET_PIN
    report["nativeManifestRoles"] = len(native_scope)
    report["legacyCatalogRoles"] = len(exported)
    report["unionRoles"] = len(rows)
    report["note"] = "Explicit superset inventory; catalog-only entries are not assumed hostile. No new eligibility or operator allowlist."
    destination = m.ROOT / "build/reports/me12/native-coverage.json"
    destination.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    keys = {key: f"C{i:02}" for i, key in enumerate(sorted(report["contexts"]), 1)}
    lines = ["# ME12 Stage 1 role and name coverage", "",
             "Generated by tools/audit/me12_native_inventory.py from current Java eligibility and pinned 0.7.1 assets.",
             "**BLOCKED. These are structural upper bounds, not native certificates or rollout eligibility.**",
             "Regenerable build/reports/me12/native-coverage.json includes per-role source hashes, native reference paths, override deltas, motion declarations,",
             "individual rejection reasons, affix capabilities, immunity channels and exact feasible name pairs/witnesses.",
             "Live roster reachability, inherited-effect legality, complete native execution and final immunity admission remain required.",
             "All nativeConnectedPassed and ownerAccepted axes are false. Catalog-only entries remain explicit scope-review entries.",
             "", f"Inventory: {len(exported)} legacy canonical/alias IDs, {len(native_scope)} native attackers, {len(rows)} union IDs.", "",
             "## Structural contexts", "",
             "Roster assumption: three reachable minions; Horde adds two (six initial members including leader), under the existing cap eight.",
             "No operator overrides. A single witness proves structural existence only. No weighted sampler is implemented.", "",
             "| Context | Era | Individually eligible | Name pairs | Tier masses (numerator/Z) | Maximum own count |",
             "| --- | --- | --- | ---: | --- | ---: |"]
    for key, ident in keys.items():
        c = report["contexts"][key]
        era, pool = key.split(":", 1)
        masses = ", ".join(f"{pair}:{mass}" for pair, mass in c["tierMassNumerators"].items()) or "none"
        lines.append(f"| {ident} | {era} | {pool} | {len(c['feasibleNamePairs'])} | {masses}; Z={c['normalization']} | {c['maximumOwnCount']} |")
    lines += ["", "## Every role and alias", "",
              "X = explicit native-immunity policy exclusion; NB = no binding; NC = no catalog/certificate.",
              "Context links denote only structural plans. Every row remains uncertified on connected 0.7.1.",
              "", "| Role | Canonical | Scope | Normal | Nightmare | Hell | Current admission |",
              "| --- | --- | --- | --- | --- | --- | --- |"]
    for row in report["rows"]:
        values = []
        for era in m.ERAS:
            state = row["eras"][era]
            if "structuralContext" in state:
                context = report["contexts"][state["structuralContext"]]
                values.append(keys[state["structuralContext"]] + ("" if context["minimumSupported"] else " BLOCKED"))
            else:
                values.append({"POLICY_EXCLUDED_NATIVE_IMMUNITY_CAP": "X", "BLOCKED_NO_BINDING": "NB",
                               "BLOCKED_NO_CATALOG_OR_CERTIFICATE": "NC"}[state["status"]])
        lines.append("| " + " | ".join([row["roleId"], row["canonicalRoleId"] or "—", row["scope"], *values,
                                        row["productionRejection"] or "v1 configured; v2 blocked"]) + " |")
    lines += ["", "## Per-operator structural reachability", "",
              "| Context | Reachable operators in some name pair |",
              "| --- | --- |"]
    for key, ident in keys.items():
        lines.append(f"| {ident} | {', '.join(report['contexts'][key]['reachableOperators']) or 'none'} |")
    (m.ROOT / "docs/enemies/ME12_STAGE1_COVERAGE.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"ME12 native inventory: {len(rows)} union roles; all connected certificates remain BLOCKED")


if __name__ == "__main__":
    main()
