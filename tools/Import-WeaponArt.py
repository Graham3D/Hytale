"""Package authored weapon art byte-for-byte and audit its spatial suffixes.

The source directory is supplied explicitly because artists may keep it outside
the build worktree. No image is cropped, resampled, recolored, or re-encoded.
"""

import hashlib
import json
import re
import shutil
import sys
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "src/main/resources/Common/UI/Custom/Icons/RPG/WeaponArt"
MANIFEST = ROOT / "src/main/resources/rpg/inventory/weapon-art-v1.json"
FOOTPRINTS = ROOT / "src/main/resources/rpg/inventory/footprints-v1.json"

FAMILIES = {
    "axes": "Axe", "battleaxes": "Battleaxe", "books": "Spellbook",
    "bows": "Shortbow", "clubs": "Club", "crossbows": "Crossbow",
    "daggers": "Daggers", "guns": "Gun", "longswords": "Longsword",
    "maces": "Mace", "shields": "Shield", "spears": "Spear",
    "staves": "Staff", "swords": "Sword", "throwing": "",
    "wands": "Wand",
}
# These are alternate source renders or art with no matching installed item.
# They remain in the artist directory and never silently replace another item.
EXCLUDED = {
    "daggers_dual/Iron_Iron_Texture_icon2x2.png",
    "daggers1x2/Iron_Offhand_Iron_Texture_icon.png",
    "longswords/Iron_Iron_Statue_Texture_icon2x4.png",
    "longswords/Iron_Iron_Stone_Texture_icon2x4.png",
    "maces2x4/Iron_30_Iron_30_Texture_icon.png",
    "shields4x4/Wood_Wood_Plant_icon.png",
    "throwing/Shuriken2x2.png",
}
SPECIAL = {
    "crossbows2x2/crossbow_iron.png": "Weapon_Crossbow_Iron",
    "crossbows2x2/crossbow_rusty_iron.png": "Weapon_Crossbow_Ancient_Steel",
    "books/Book_Book_Fire_Texture_icon.png": "Weapon_Spellbook_Fire",
    "books/Frost_Frost_Texture_icon.png": "Weapon_Spellbook_Frost",
    "books/Grimoire_Grimoire_Brown_Texture_icon.png": "Weapon_Spellbook_Grimoire_Brown",
    "books/Grimoire_Grimoire_Purple_Texture_icon.png": "Weapon_Spellbook_Grimoire_Purple",
    "throwing/Kunai1x2.png": "Weapon_Kunai",
    "axes/Iron_Iron_Rusty_Texture_icon2x2.png": "Weapon_Axe_Iron_Rusty",
    "clubs/Steel_Flail_Steel_Flail_Rusty_Texture_icon2x3.png": "Weapon_Club_Steel_Flail_Rusty",
    "Bows2x4/Iron_Vamp_Texture_icon.png": "Weapon_Shortbow_Vampire",
    "staves/Bronze2x4n.png": "Weapon_Staff_Bronze",
    "daggers1x2/Bronze_Ancient_Bronze_Ancient_Texture_icon.png": "Weapon_Daggers_Bronze_Ancient",
    "shields4x4/Wood_Wood_Hide_icon.png": "Weapon_Shield_Wood",
    "spears1x4/Double_Incandescent_Double_Incandescent_Texture_icon.png": "Weapon_Spear_Double_Incandescent",
}


def split_dimension(name):
    match = re.search(r"([1-4])x([1-4])n?$", name, re.IGNORECASE)
    if not match:
        return name, None
    return name[:match.start()], (int(match[1]), int(match[2]))


def identity(relative):
    folder, filename = relative.split("/", 1)
    family_name, folder_size = split_dimension(folder)
    if relative in EXCLUDED:
        return None, folder_size
    family = FAMILIES.get(family_name.lower())
    if family is None:
        raise ValueError(f"Unknown art family: {folder}")
    stem, filename_size = split_dimension(Path(filename).stem)
    size = filename_size or folder_size
    if relative in SPECIAL:
        return SPECIAL[relative], size
    stem = re.sub(r"_(?:Texture|default)_icon$", "", stem, flags=re.IGNORECASE)
    stem = re.sub(r"_icon$", "", stem, flags=re.IGNORECASE)
    parts = stem.split("_")
    if len(parts) >= 2 and parts[0].lower() == parts[1].lower():
        parts.pop(0)
    suffix = "_".join(parts)
    if family_name.lower() == "bows":
        suffix = suffix.replace("Iron_Vamp", "Vampire")
    elif family_name.lower() == "shields" and suffix.startswith("Orbis_Knight_"):
        suffix = suffix.removeprefix("Orbis_Knight_")
    elif family_name.lower() == "staves" and suffix == "Crystal_Ice_Crystal":
        suffix = "Crystal_Ice"
    elif family_name.lower() == "daggers" and suffix.startswith("Iron_Offhand"):
        suffix = "Iron"
    if family_name.lower() == "throwing":
        return f"Weapon_{suffix}", size
    return f"Weapon_{family}_{suffix}", size


def main(source_root: Path) -> None:
    footprints = json.loads(FOOTPRINTS.read_text(encoding="utf-8"))["bindings"]
    sources = sorted(source_root.rglob("*.png"))
    if not sources:
        raise SystemExit(f"No PNGs under {source_root}")
    bindings = {}
    unrecognized = []
    for source in sources:
        relative = source.relative_to(source_root).as_posix()
        item_id, authored_size = identity(relative)
        if item_id is None:
            continue
        if item_id not in footprints:
            unrecognized.append((relative, item_id))
            continue
        if item_id in bindings:
            raise SystemExit(f"Duplicate art identity: {item_id}: {relative}")
        with Image.open(source) as image:
            if image.mode != "RGBA":
                raise SystemExit(f"Expected RGBA PNG: {source}")
            box = image.getchannel("A").getbbox()
            if box is None:
                raise SystemExit(f"Empty authored image: {source}")
            width, height = image.size
        target = DEST / f"{item_id}.png"
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        source_hash = hashlib.sha256(source.read_bytes()).hexdigest()
        if hashlib.sha256(target.read_bytes()).hexdigest() != source_hash:
            raise SystemExit(f"Authored image changed during packaging: {source}")
        bindings[item_id] = {
            "texture": f"Icons/RPG/WeaponArt/{item_id}.png",
            "source": relative,
            "sha256": source_hash,
            "canvasWidth": width,
            "canvasHeight": height,
            "alphaBounds": list(box),
            "authoredFootprint": None if authored_size is None else {
                "width": authored_size[0], "height": authored_size[1]},
        }
    if unrecognized:
        raise SystemExit(f"Art lacks an installed item identity: {unrecognized}")
    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST.write_text(json.dumps({"schemaVersion": 2, "bindings": dict(sorted(bindings.items()))},
                                   indent=2) + "\n", encoding="utf-8")
    print(f"Copied {len(bindings)} unchanged weapon PNGs; excluded {len(EXCLUDED)} alternate/unmapped renders")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: Import-WeaponArt.py PATH_TO_WEAPONS_DIRECTORY")
    main(Path(sys.argv[1]))
