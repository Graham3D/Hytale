"""Render the bounded leather/weapon QA set and package a read-only in-game gallery."""

import argparse
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import zipfile

from PIL import Image, ImageDraw, ImageFont


TOOLS = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("spatial_icons", TOOLS / "Generate-SpatialIcons.py")
icons = importlib.util.module_from_spec(spec)
spec.loader.exec_module(icons)
ROOT = TOOLS.parent
TABLE = ROOT / "art/spatial-icon-prototype/pose-batch-v1.json"
FOOTPRINTS = ROOT / "src/main/resources/rpg/inventory/footprints-v1.json"
ASSET_DIR = ROOT / "src/main/resources/Common/UI/Custom/Icons/RPG/PoseBatch"
GALLERY = ROOT / "src/main/resources/Common/UI/Custom/RpgIconPoseBatch.ui"
PROOF = ROOT / "art/spatial-icon-prototype/pose-batch-output"


def crop_and_fit(raw, width, height, scale_ratio, translation, image_roll=0):
    image = Image.open(raw).convert("RGBA")
    if image_roll:
        image = image.rotate(image_roll, resample=Image.Resampling.BICUBIC, expand=True)
    bounds = image.getchannel("A").point(lambda alpha: 255 if alpha >= 8 else 0).getbbox()
    if bounds is None:
        raise ValueError(f"No visible pixels in {raw}")
    image = image.crop(bounds)
    # Reserve ten percent of the fit range so changing Scale from the native
    # seed has a visible effect instead of being erased by a new alpha crop.
    max_fit = min((width - 12) / image.width, (height - 12) / image.height)
    factor = min(max_fit, max_fit * 0.9 * scale_ratio)
    size = (max(1, round(image.width * factor)), max(1, round(image.height * factor)))
    image = image.resize(size, Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (width, height))
    x = (width - size[0]) // 2 + round(translation[0])
    y = (height - size[1]) // 2 + round(translation[1])
    if x < 0 or y < 0 or x + size[0] > width or y + size[1] > height:
        raise ValueError("Pose translation/scale would clip the spatial icon")
    canvas.alpha_composite(image, (x, y))
    return canvas, {"sourceBounds": list(bounds), "scaledSize": list(size),
                    "baselineFitFraction": 0.9, "scaleRatioToNative": scale_ratio,
                    "positionPixels": [x, y], "clampedToFootprint": factor == max_fit}


def pose_for(row, native):
    if not all(k in native for k in ("Rotation", "Scale", "Translation")):
        raise ValueError(f"Incomplete native IconProperties: {row['itemId']}")
    if row["family"] == "Chest":
        return {"Rotation": [10, -30, -5], "Scale": 0.45, "Translation": [0, 0]}, "CHEST_TEMPLATE"
    if row["itemId"].startswith("Armor_"):
        return {"Rotation": native["Rotation"], "Scale": native["Scale"],
                "Translation": [0, 0]}, "NATIVE_ARMOR_ZERO_OFFSETS"
    if row["itemId"].startswith("Weapon_Shortbow_"):
        return {"Rotation": [0, 90, 0], "Scale": 0.4,
                "Translation": [0, 0]}, "SHORTBOW_UPRIGHT_0_90_0"
    return {"Rotation": native["Rotation"], "Scale": native["Scale"],
            "Translation": [0, 0]}, "NATIVE_WEAPON_CENTERED"


def font(size):
    path = Path("C:/Windows/Fonts/segoeui.ttf")
    return ImageFont.truetype(str(path), size) if path.exists() else ImageFont.load_default()


def contact_sheet(rows):
    card_w, card_h, cols = 240, 355, 4
    sheet = Image.new("RGB", (card_w * cols + 20, card_h * ((len(rows) + cols - 1) // cols) + 20), "#0a1727")
    draw = ImageDraw.Draw(sheet)
    for index, row in enumerate(rows):
        x = 10 + index % cols * card_w
        y = 10 + index // cols * card_h
        draw.rounded_rectangle((x, y, x + card_w - 8, y + card_h - 8), radius=6,
                               fill="#192d41", outline="#57748e")
        draw.text((x + 10, y + 9), row["family"], font=font(18), fill="#e7bb69")
        draw.text((x + 10, y + 34), row["itemId"].replace("Weapon_", "").replace("Armor_", ""),
                  font=font(12), fill="#d4e2ee")
        image = Image.open(ASSET_DIR / row["file"]).convert("RGBA")
        px = x + (card_w - 8 - image.width) // 2
        py = y + 58 + (256 - image.height) // 2
        draw.rectangle((px, py, px + image.width - 1, py + image.height - 1), fill="#aebbc8")
        sheet.paste(image, (px, py), image)
        p = row["pose"]
        draw.text((x + 10, y + 318), f"{row['width']}x{row['height']}  rot {p['Rotation']}  scale {p['Scale']}",
                  font=font(11), fill="#b6c9da")
        if row.get("proxy"):
            draw.text((x + 10, y + 334), "VISUAL PROXY - no Long Bow item", font=font(10), fill="#f3bd70")
    sheet.save(PROOF / "contact-sheet.png")


def gallery_ui(rows):
    # Individual texture cards retain their authored 64-pixel-per-cell dimensions.
    cards = []
    for offset in range(0, len(rows), 4):
        entries = []
        for row in rows[offset:offset + 4]:
            w, h = row["width"] * 64, row["height"] * 64
            title = row["family"] + (" (proxy)" if row.get("proxy") else "")
            entries.append(f'''Group {{ Anchor: (Width: 240, Height: 350); LayoutMode: Top; Background: #192d41; Padding: (Full: 6);
              Label {{ Text: "{title}"; Anchor: (Height: 24); Style: (FontSize: 16, TextColor: #e7bb69); }}
              Label {{ Text: "{row['itemId']}"; Anchor: (Height: 25); Style: (FontSize: 11, TextColor: #d4e2ee); }}
              Group {{ Anchor: (Height: 258); LayoutMode: Center;
                Group {{ Anchor: (Width: {w}, Height: {h}); Background: #9fadb9;
                  Group {{ Anchor: (Full: 0); Background: (TexturePath: "Icons/RPG/PoseBatch/{row['file']}"); }}
                }}
              }}
              Label {{ Text: "{row['width']}x{row['height']}  scale {row['pose']['Scale']}"; Anchor: (Height: 22); Style: (FontSize: 12, TextColor: #b6c9da); }}
            }}''')
        cards.append('Group { Anchor: (Height: 354); LayoutMode: Left; ' + '\n'.join(entries) + ' }')
    GALLERY.write_text('''$C = "Common.ui";
Group { LayoutMode: Center;
  $C.@Panel { Anchor: (Full: 18, MaxWidth: 1040); Padding: (Full: 16); LayoutMode: Top;
    Label { Text: "SPATIAL ICON POSE TEST  |  Escape to close"; Anchor: (Height: 36); Style: (FontSize: 20, TextColor: #e7bb69); }
    Label { Text: "Read-only art preview. It does not create gear or change the inventory."; Anchor: (Height: 30); Style: (FontSize: 14, TextColor: #b6c9da); }
    Group { FlexWeight: 1; LayoutMode: TopScrolling; ScrollbarStyle: $C.@DefaultScrollbarStyle; KeepScrollPosition: true;
''' + '\n'.join(cards) + '''
    }
  }
}
''', encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assets", type=Path, required=True)
    parser.add_argument("--blender", type=Path, required=True)
    args = parser.parse_args()
    table = json.loads(TABLE.read_text(encoding="utf-8"))
    footprints = json.loads(FOOTPRINTS.read_text(encoding="utf-8"))["bindings"]
    if table["schemaVersion"] != 1 or table["slotPixels"] != 64 or len(table["entries"]) != 20:
        raise ValueError("Pose batch must contain exactly the bounded 20-item proof")
    ASSET_DIR.mkdir(parents=True, exist_ok=True)
    PROOF.mkdir(parents=True, exist_ok=True)
    manifest = []
    with zipfile.ZipFile(args.assets) as archive, tempfile.TemporaryDirectory(prefix="hywind-pose-") as directory:
        index = icons.item_assets(archive)
        for row in table["entries"]:
            item_id = row["itemId"]
            item, sources = icons.resolve_item(archive, index, item_id)
            footprint = footprints.get(item_id)
            if footprint is None:
                raise ValueError(f"Missing live footprint: {item_id}")
            width, height = footprint["width"], footprint["height"]
            native = item.get("IconProperties") or {}
            pose, policy = pose_for(row, native)
            paths = {key: icons.archive_path(item[key]) for key in ("Model", "Texture", "Icon")}
            if any(path not in archive.namelist() for path in paths.values()):
                raise ValueError(f"Missing model, texture, or icon for {item_id}")
            work = Path(directory)
            model, texture, properties, raw = (work / name for name in
                                               ("item.blockymodel", "texture.png", "pose.json", "render.png"))
            model.write_bytes(archive.read(paths["Model"]))
            texture.write_bytes(archive.read(paths["Texture"]))
            properties.write_text(json.dumps(pose), encoding="utf-8")
            run = subprocess.run([str(args.blender), "--factory-startup", "-b", "--python", str(TOOLS / "Render-InventoryIcon.py"), "--",
                                  str(model), str(texture), str(raw), str(properties)],
                                 capture_output=True, text=True, timeout=180)
            if run.returncode or not raw.is_file():
                raise RuntimeError(f"Render failed for {item_id}: {run.stdout[-1000:]} {run.stderr[-1000:]}")
            image_roll = -45 if row["family"] in (
                "Sword", "Daggers", "Battleaxe", "Mace", "Longsword", "Spear", "Staff", "Wand") else 0
            fitted, fit = crop_and_fit(raw, width * 64, height * 64,
                                       pose["Scale"] / native["Scale"], pose["Translation"], image_roll)
            file = item_id + ".png"
            fitted.save(ASSET_DIR / file)
            manifest.append({"family": row["family"], "itemId": item_id, "proxy": row.get("proxy", False),
                             "width": width, "height": height, "file": file,
                             "nativePose": native, "nativePoseSource": sources.get("IconProperties"),
                             "pose": pose, "posePolicy": policy, "imageRollDegrees": image_roll, "fit": fit,
                             "model": paths["Model"], "texture": paths["Texture"], "stockIcon": paths["Icon"]})
            print(f"RENDERED {row['family']}: {item_id} {width}x{height} {pose}", flush=True)
    contact_sheet(manifest)
    gallery_ui(manifest)
    (PROOF / "manifest.json").write_text(json.dumps({"schemaVersion": 1, "items": manifest}, indent=2), encoding="utf-8")
    print(f"GALLERY {GALLERY}", flush=True)


if __name__ == "__main__":
    main()
