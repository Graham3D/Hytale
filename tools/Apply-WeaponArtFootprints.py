"""Apply the artist's spatial suffixes to installed native and managed carriers.

Only bindings with an explicit folder or filename suffix change. Previous
dimensions are retained in the catalog for saved-bag migration. PNGs are never
opened for writing.
"""

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "src/main/resources/rpg/inventory/footprints-v1.json"
ART = ROOT / "src/main/resources/rpg/inventory/weapon-art-v1.json"
GEAR = ROOT / "src/main/resources/rpg/gear/native-bindings-v1.json"


def main():
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    art = json.loads(ART.read_text(encoding="utf-8"))["bindings"]
    gear = json.loads(GEAR.read_text(encoding="utf-8"))["bindings"]
    if catalog["catalogRevision"] not in (2, 3):
        raise ValueError("Expected installed catalog revision 2 or migrated revision 3")
    bindings = catalog["bindings"]
    changed_native = {}
    for item_id, image in art.items():
        size = image["authoredFootprint"]
        if size is None:
            continue
        if item_id not in bindings:
            raise ValueError(f"Art lacks footprint binding: {item_id}")
        row = bindings[item_id]
        if (row["width"], row["height"]) == (size["width"], size["height"]):
            continue
        if catalog["catalogRevision"] != 2:
            raise ValueError(f"Unexpected second footprint change: {item_id}")
        changed_native[item_id] = (row["width"], row["height"], size["width"], size["height"])

    carrier_to_native = {}
    for row in gear:
        item_id = row.get("nativeItemId")
        carrier = row.get("managedItemId")
        if item_id in changed_native and carrier:
            carrier_to_native[carrier] = item_id
    changed_carriers = 0
    for item_id, row in bindings.items():
        native = next((base for carrier, base in carrier_to_native.items()
                       if item_id == carrier or item_id.startswith(carrier + "_")), None)
        if native is None:
            continue
        old_w, old_h, new_w, new_h = changed_native[native]
        if (row["width"], row["height"]) != (old_w, old_h):
            raise ValueError(f"Carrier footprint differs from base: {item_id}")
        row.update(width=new_w, height=new_h, previousWidth=old_w, previousHeight=old_h)
        changed_carriers += 1
    for item_id, (old_w, old_h, new_w, new_h) in changed_native.items():
        bindings[item_id].update(width=new_w, height=new_h,
                                 previousWidth=old_w, previousHeight=old_h)
    catalog["catalogRevision"] = 3
    CATALOG.write_text(json.dumps(catalog, indent=2) + "\n", encoding="utf-8")
    print(f"Updated {len(changed_native)} native footprints and {changed_carriers} managed carrier bindings")


if __name__ == "__main__":
    main()
