"""Author and audit proc metadata on baseline managed native damage leaves.

Uses the pinned installed stock graph plus packaged overrides. No runtime assets are
generated and no action templates are changed. --verify is read only.
"""
import argparse
import hashlib
import json
import math
import sys
import zipfile
from collections import Counter, defaultdict
from pathlib import Path

PIN = "eddf425546559bfbc3f2656babf30daa4008543c38ee5aa4499c605ed1732236"
FAMILIES = {
    "plate": 360, "tool": 135, "cloth": 120, "leather": 120, "robes": 120,
    "staff": 120, "battleaxe": 105, "book": 105, "daggers": 105,
    "mace": 105, "shield": 105, "shortbow": 105, "sword": 105,
    "crossbow": 75, "longsword": 75, "wand": 75, "garb": 60, "bomb": 45,
}
HERE = Path(__file__).resolve().parent.parent
SERVER = HERE / "src/main/resources/Server"
SECTIONS = {
    "root": "Item/RootInteractions",
    "interaction": "Item/Interactions",
    "projectile": "ProjectileConfigs",
}
TYPES = {"RPG_GearDamage", "RPG_CarrierDamage"}
PROJECTILES = {"RPG_GearProjectile", "RPG_CarrierProjectile"}
REFERENCE_FIELDS = {"Interactions", "Next", "Failed", "HitEntity", "HitBlock", "ProjectileHit", "ProjectileMiss", "ProjectileSpawn"}


class AuditError(RuntimeError):
    pass


def leaves(value):
    if isinstance(value, dict):
        if value.get("Type") in TYPES:
            yield value
        for child in value.values():
            yield from leaves(child)
    elif isinstance(value, list):
        for child in value:
            yield from leaves(child)


def projectile_count(value):
    if isinstance(value, dict):
        return (value.get("Type") in PROJECTILES) + sum(projectile_count(v) for v in value.values())
    if isinstance(value, list):
        return sum(projectile_count(v) for v in value)
    return 0


class Graph:
    def __init__(self, archive, server):
        self.archive = archive
        self.server = server
        self.sources = {kind: {} for kind in SECTIONS}
        self.cache = {}
        self.visited = set()
        for name in archive.namelist():
            for kind, prefix in SECTIONS.items():
                if name.startswith("Server/" + prefix + "/") and name.endswith(".json"):
                    ident = Path(name).stem
                    self.sources[kind][ident] = name
        for kind, prefix in SECTIONS.items():
            for path in (server / prefix).rglob("*.json"):
                self.sources[kind][path.stem] = path

    def get(self, kind, ident):
        key = kind, ident
        if key not in self.cache:
            source = self.sources[kind].get(ident)
            if source is None:
                raise AuditError(f"missing {kind} {ident}")
            content = source.read_text(encoding="utf-8") if isinstance(source, Path) else self.archive.read(source)
            self.cache[key] = json.loads(content)
        return self.cache[key]

    def source(self, kind, ident):
        return self.sources[kind][ident]


def walk(graph, item, item_path, result):
    variables = item.get("InteractionVars", {})
    active = set()

    def visit_asset(kind, ident, context):
        key = kind, ident
        graph.visited.add(key)
        if key in active:
            return
        active.add(key)
        try:
            node = graph.get(kind, ident)
            descend(node, graph.source(kind, ident), ident, context)
        finally:
            active.remove(key)

    def descend(node, source, selector, context, field=""):
        if isinstance(node, list):
            for child in node:
                descend(child, source, selector, context, field)
            return
        if isinstance(node, str):
            if field in REFERENCE_FIELDS:
                if node in graph.sources["interaction"]:
                    visit_asset("interaction", node, context)
                elif node in graph.sources["root"]:
                    visit_asset("root", node, context)
                elif node.startswith(("RPG_GearRoute_", "RPG_Carrier_")):
                    raise AuditError(f"{item_path.name}: unresolved managed reference {node}")
            return
        if not isinstance(node, dict):
            return
        typ = node.get("Type")
        if typ == "Replace":
            var = node.get("Var")
            if var in variables:
                selected = variables[var]
                # Item variables may be root IDs or inline interaction arrays.
                if isinstance(selected, str) and selected in graph.sources["root"]:
                    visit_asset("root", selected, context)
                else:
                    descend(selected, item_path, var, context, "Interactions")
            elif "DefaultValue" in node:
                descend(node["DefaultValue"], source, var or selector, context)
            elif not node.get("DefaultOk", False):
                raise AuditError(f"{item_path.name}: unresolved Replace {var}")
            return
        if typ in TYPES:
            if not isinstance(source, Path):
                raise AuditError(f"{item_path.name}: managed damage in stock {source}")
            if "ActionProfiles" in source.parts:
                raise AuditError(f"{item_path.name}: action-profile damage belongs to Actions3: {source}")
            result[(source, id(node))] = (node, selector, context)
        if typ in PROJECTILES:
            config = node.get("Config")
            if not isinstance(config, str):
                raise AuditError(f"{item_path.name}: projectile without config")
            visit_asset("projectile", config, context)
        if "Parent" in node and isinstance(node["Parent"], str):
            parent = node["Parent"]
            if parent in graph.sources["interaction"]:
                visit_asset("interaction", parent, context)
        for name, child in node.items():
            if name in {"Parent", "Config", "Type", "DefaultValue"}:
                continue
            if isinstance(child, (dict, list)) or name in REFERENCE_FIELDS:
                descend(child, source, selector, context, name if name in REFERENCE_FIELDS else field)

    for action, value in item.get("Interactions", {}).items():
        descend(value, item_path, action, action, "Interactions")


def run(verify, server, archive_path):
    with archive_path.open('rb') as source:
        installed_sha256 = hashlib.file_digest(source, 'sha256').hexdigest()
    if installed_sha256 != PIN:
        raise AuditError("installed Assets.zip SHA-256 differs from pinned stock source")
    with zipfile.ZipFile(archive_path) as archive:
        graph = Graph(archive, server)
        items = sorted((server / "Item/Items/RPG/Gear").glob("*.json"))
        if not items:
            raise AuditError("no packaged managed gear")
        found = {}
        item_trees = {}
        families = Counter()
        for path in items:
            item = json.loads(path.read_text(encoding="utf-8"))
            item_trees[path] = item
            family = path.stem.removeprefix("RPG_Gear_").split("_")[0]
            families[family] += 1
            walk(graph, item, path, found)
            for section in ("InteractionVars", "Interactions"):
                for selector, value in item.get(section, {}).items():
                    for leaf in leaves(value):
                        found.setdefault((path, id(leaf)), (leaf, selector, "literal-item"))
        if families != Counter(FAMILIES):
            raise AuditError(f"managed carrier enumeration changed: {families}")

        def multi_parallel(node, label):
            if isinstance(node, dict):
                if node.get("Type") == "Parallel" and projectile_count(node) > 1 and label not in stages:
                    raise AuditError(f"unsupported multi-projectile strike {label}")
                for value in node.values():
                    multi_parallel(value, label)
            elif isinstance(node, list):
                for value in node:
                    multi_parallel(value, label)

        # The only concurrent multi-projectile strike in the installed managed
        # graphs is Shortbow Signature Volley. Prove all three strengths and
        # reject a new multi-projectile family until its budget is authored.
        stages = [f"RPG_GearRoute_I_Weapon_Shortbow_Signature_Volley_Strength_{i}" for i in range(3)]
        for stage in stages:
            actual = projectile_count(graph.get("interaction", stage))
            if actual != 3:
                raise AuditError(f"{stage}: expected three projectile launches, got {actual}")
        for (kind, ident) in graph.visited:
            multi_parallel(graph.get(kind, ident), ident)
            if kind == "interaction" and ident.startswith(("RPG_GearRoute_", "RPG_Carrier_")):
                launch_count = projectile_count(graph.get(kind, ident))
                if launch_count > 1 and ident not in stages:
                    raise AuditError(f"unsupported managed multi-projectile interaction {ident}: {launch_count}")
        for path, tree in item_trees.items():
            multi_parallel(tree, path.name)

        # Defaults referenced by Replace remain packaged damage entry points,
        # even where every current carrier supplies an inline override.
        literal = set()
        for folder in ("Gear", "Carriers"):
            for path in (server / "Item/Interactions/RPG" / folder).rglob("*.json"):
                for leaf in leaves(graph.get("interaction", path.stem)):
                    literal.add(path)
                    found.setdefault((path, id(leaf)), (leaf, path.stem, "literal"))

        edits = defaultdict(int)
        selectors = Counter()
        for (path, _), (leaf, selector, context) in found.items():
            # A shared leaf must have one stable selector regardless of its
            # caller. Named interaction IDs give that stable identity.
            if path != server / "Item/Items/RPG/Gear" / path.name:
                selector = path.stem
            coefficient = 1 / 3 if "Signature_Volley_Damage" in selector else 1.0
            if not selector or not 0 < coefficient <= 1 or not math.isfinite(coefficient):
                raise AuditError(f"invalid proc share for {path}: {selector}")
            expected = {"RpgProcSelector": selector, "RpgProcCoefficient": coefficient}
            if verify:
                for key, value in expected.items():
                    if leaf.get(key) != value:
                        raise AuditError(f"reachable missing/wrong {key}: {path} {selector}")
            else:
                leaf.update(expected)
            edits[path] += 1
            selectors[selector] += 1

        # Literal managed damage assets must be covered even if a currently
        # selected item branch changes in a future generation.
        if not literal.issubset(edits.keys()):
            raise AuditError("unreachable literal managed damage assets: " + ", ".join(str(p) for p in sorted(literal - edits.keys())))
        if not found:
            raise AuditError("no reachable managed damage leaves")
        if len(found) != 4181 or len(edits) != 686:
            raise AuditError(f"managed damage enumeration changed: {len(found)} nodes in {len(edits)} files")
        if not verify:
            for path in sorted(edits):
                # Cached node objects are the same objects visited above.
                tree = item_trees[path] if path in item_trees else graph.get("interaction", path.stem)
                path.write_text(json.dumps(tree, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"{'Verified' if verify else 'Authored'} {len(items)} carriers, {len(families)} families, "
              f"{len(found)} audited damage nodes, {len(edits)} files, {len(selectors)} selectors; "
              "shortbow volley=3 pellets x 1/3")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify", action="store_true")
    parser.add_argument("--server-root", type=Path, default=SERVER)
    parser.add_argument("--assets-zip", type=Path, default=Path.home() / "AppData/Roaming/Hytale/install/pre-release/package/game/latest/Assets.zip")
    args = parser.parse_args()
    try:
        run(args.verify, args.server_root.resolve(), args.assets_zip)
    except (AuditError, OSError, ValueError, KeyError) as exc:
        print(f"native proc metadata: {exc}", file=sys.stderr)
        sys.exit(1)
