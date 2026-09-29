"""Offline guard for the bounded spatial icon proof and packaged canvases."""

import json
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parent.parent
MANIFEST = ROOT / "art/spatial-icon-prototype/pose-batch-output/manifest.json"
CATALOG = ROOT / "src/main/resources/rpg/inventory/footprints-v1.json"
ICONS = ROOT / "src/main/resources/Common/UI/Custom/Icons/RPG/PoseBatch"


def main():
    rows = json.loads(MANIFEST.read_text(encoding="utf-8"))["items"]
    footprints = json.loads(CATALOG.read_text(encoding="utf-8"))["bindings"]
    assert len(rows) == 20 and len({row["itemId"] for row in rows}) == 20
    for row in rows:
        item_id = row["itemId"]
        assert not row.get("proxy"), f"Nonexistent family proxy: {item_id}"
        footprint = footprints[item_id]
        assert (row["width"], row["height"]) == (footprint["width"], footprint["height"])
        with Image.open(ICONS / row["file"]) as image:
            assert image.mode == "RGBA" and image.size == (64 * row["width"], 64 * row["height"]), item_id
            bbox = image.getchannel("A").getbbox()
            assert bbox and bbox[0] >= 2 and bbox[1] >= 2, item_id
            assert bbox[2] <= image.width - 2 and bbox[3] <= image.height - 2, item_id
        assert row["pose"]["Scale"] > 0 and row["pose"]["Translation"] == [0, 0], item_id
        assert len(row["pose"]["Rotation"]) == 3, item_id
        if item_id.startswith("Weapon_Shortbow_"):
            assert row["pose"] == {"Rotation": [0, 90, 0], "Scale": 0.4, "Translation": [0, 0]}, item_id
    print(f"PASS: {len(rows)} transparent, unclipped, catalog-sized pose assets; shortbows at 0/90/0 scale .4")


if __name__ == "__main__":
    main()
