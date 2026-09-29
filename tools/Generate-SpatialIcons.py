"""Offline Hytale blockymodel -> transparent spatial PNG proof (five samples by default)."""

import argparse
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile

from PIL import Image, ImageDraw, ImageFilter, ImageFont


def item_assets(archive):
    return {Path(name).stem: name for name in archive.namelist()
            if name.startswith("Server/Item/Items/") and name.endswith(".json")}


def resolve_item(archive, index, item_id):
    seen = set()
    merged = {}
    sources = {}
    path = index.get(item_id)
    if path is None:
        raise ValueError(f"Item asset unavailable: {item_id}")
    while path:
        if path in seen:
            raise ValueError(f"Item parent cycle: {item_id}")
        seen.add(path)
        data = json.loads(archive.read(path))
        for key, value in data.items():
            if key not in merged:
                merged[key] = value
                sources[key] = path
        parent = data.get("Parent")
        if not parent:
            break
        same_folder = str(Path(path).parent / f"{parent}.json").replace("\\", "/")
        path = same_folder if same_folder in archive.namelist() else index.get(parent)
        if path is None:
            raise ValueError(f"Missing parent {parent} for {item_id}")
    for key in ("Model", "Texture", "Icon"):
        if not merged.get(key):
            raise ValueError(f"{item_id} has no {key}")
    return merged, sources


def archive_path(path):
    clean = Path(path).as_posix().lstrip("/")
    if ".." in Path(clean).parts:
        raise ValueError("Asset path escapes archive")
    return "Common/" + clean


def normalized_alpha(image):
    alpha = image.getchannel("A")
    bounds = alpha.point(lambda value: 255 if value >= 8 else 0).getbbox()
    if bounds is None:
        raise ValueError("Image has no visible pixels")
    trimmed = alpha.crop(bounds)
    trimmed.thumbnail((64, 64), Image.Resampling.LANCZOS)
    canvas = Image.new("L", (72, 72), 0)
    canvas.paste(trimmed, ((72 - trimmed.width) // 2, (72 - trimmed.height) // 2))
    return canvas.filter(ImageFilter.GaussianBlur(1.5))


def stock_pose_alignment(rendered, stock_bytes):
    reference = normalized_alpha(Image.open(io.BytesIO(stock_bytes)).convert("RGBA"))
    stock_values = reference.tobytes()
    candidates = ((0, rendered), (180, rendered.transpose(Image.Transpose.ROTATE_180)))
    scores = []
    for degrees, candidate in candidates:
        values = normalized_alpha(candidate).tobytes()
        overlap = sum(min(a, b) for a, b in zip(values, stock_values))
        union = sum(max(a, b) for a, b in zip(values, stock_values))
        scores.append((overlap / union if union else 0, degrees, candidate))
    best = max(scores, key=lambda result: result[0])
    margin = abs(scores[0][0] - scores[1][0])
    return best[2], {"rollDegrees": best[1], "alphaIoU": round(best[0], 4),
                     "decisionMargin": round(margin, 4),
                     "reviewRequired": best[0] < 0.5 or margin < 0.08,
                     "candidates": {str(degrees): round(score, 4) for score, degrees, _ in scores}}


def fit_visible_image(raw, width, height, stock_bytes):
    image = Image.open(raw).convert("RGBA")
    alpha = image.getchannel("A")
    bounds = alpha.point(lambda value: 255 if value >= 8 else 0).getbbox()
    if bounds is None:
        raise ValueError(f"Renderer produced no visible pixels: {raw}")
    trimmed = image.crop(bounds)
    trimmed, pose = stock_pose_alignment(trimmed, stock_bytes)
    padding = 6
    factor = min((width - 2 * padding) / trimmed.width,
                 (height - 2 * padding) / trimmed.height)
    size = (max(1, round(trimmed.width * factor)), max(1, round(trimmed.height * factor)))
    scaled = trimmed.resize(size, Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    canvas.alpha_composite(scaled, ((width - size[0]) // 2, (height - size[1]) // 2))
    return canvas, {"visibleBoundsInRender": list(bounds), "scaledVisibleSize": list(size),
                    "paddingPixels": padding}, pose


def contact_sheet(rows, output):
    card_w, card_h = 218, 390
    sheet = Image.new("RGB", (card_w * len(rows) + 20, card_h + 20), "#0a1625")
    draw = ImageDraw.Draw(sheet)
    font_path = Path(os.environ.get("WINDIR", "C:/Windows")) / "Fonts" / "segoeui.ttf"
    font = ImageFont.truetype(str(font_path), 15) if font_path.exists() else ImageFont.load_default()
    small = ImageFont.truetype(str(font_path), 12) if font_path.exists() else ImageFont.load_default()
    for index, row in enumerate(rows):
        left = 10 + index * card_w
        draw.rounded_rectangle((left, 10, left + card_w - 10, card_h + 10), radius=6,
                               fill="#172b42", outline="#52718c", width=1)
        draw.text((left + 10, 20), row["displayName"], font=font, fill="#f3e9d0")
        draw.text((left + 10, 42), f"{row['invWidth']} x {row['invHeight']} | {row['itemFamily']}",
                  font=small, fill="#adbdce")
        icon = Image.open(output / row["outputPng"]).convert("RGBA")
        grid_left = left + (card_w - 10 - icon.width) // 2
        grid_top = 76
        checker = Image.new("RGB", icon.size, "#c5ced8")
        checker_draw = ImageDraw.Draw(checker)
        for y in range(0, icon.height, 16):
            for x in range(0, icon.width, 16):
                if (x // 16 + y // 16) % 2:
                    checker_draw.rectangle((x, y, x + 15, y + 15), fill="#e0e5eb")
        sheet.paste(checker, (grid_left, grid_top))
        sheet.paste(icon, (grid_left, grid_top), icon)
        for x in range(0, icon.width + 1, 64):
            draw.line((grid_left + x, grid_top, grid_left + x, grid_top + icon.height), fill="#59809d", width=1)
        for y in range(0, icon.height + 1, 64):
            draw.line((grid_left, grid_top + y, grid_left + icon.width, grid_top + y), fill="#59809d", width=1)
        draw.text((left + 10, 345), "Stock 64px:", font=small, fill="#adbdce")
        pose = row["stockPoseAlignment"]
        draw.text((left + 10, 365),
                  f"Pose {pose['alphaIoU']:.2f}" + (" REVIEW" if pose["reviewRequired"] else ""),
                  font=small, fill="#eeb45c" if pose["reviewRequired"] else "#adbdce")
        stock = Image.open(io.BytesIO(row["stockIconBytes"])).convert("RGBA")
        stock.thumbnail((64, 64))
        sheet.paste(stock, (left + 140, 334), stock)
    sheet.save(output / "contact-sheet.png")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assets", type=Path, required=True, help="Installed pre-release Assets.zip")
    parser.add_argument("--table", type=Path, default=Path("art/spatial-icon-prototype/footprints-v1.json"))
    parser.add_argument("--output", type=Path, default=Path("art/spatial-icon-prototype/output"))
    parser.add_argument("--blender", type=Path, required=True)
    parser.add_argument("--ids", nargs="*", help="Optional bounded list of item IDs; default is the five samples")
    options = parser.parse_args()
    table = json.loads(options.table.read_text(encoding="utf-8"))
    if table["schemaVersion"] != 1 or table["slotPixels"] != 64:
        raise ValueError("Unsupported footprint table")
    entries = table["entries"]
    selected = [row for row in entries if row["itemBaseId"] in options.ids] if options.ids else [
        row for row in entries if row["sample"]]
    if not selected or len(selected) > 24:
        raise ValueError("Select between 1 and 24 explicit item IDs")
    if options.ids and set(options.ids) != {row["itemBaseId"] for row in selected}:
        raise ValueError("Every requested ID must exist in the footprint table")
    options.output.mkdir(parents=True, exist_ok=True)
    renderer = Path(__file__).with_name("Render-InventoryIcon.py").resolve()
    manifest = []
    with zipfile.ZipFile(options.assets) as archive, tempfile.TemporaryDirectory(prefix="hywind-icons-") as work:
        index = item_assets(archive)
        for row in selected:
            width, height = row["invWidth"] * 64, row["invHeight"] * 64
            if width < 64 or height < 64 or width > 256 or height > 256:
                raise ValueError(f"Unsupported footprint: {row['itemBaseId']}")
            item, sources = resolve_item(archive, index, row["itemBaseId"])
            paths = {key: archive_path(item[key]) for key in ("Model", "Texture", "Icon")}
            for path in paths.values():
                if path not in archive.namelist():
                    raise ValueError(f"Missing asset: {path}")
            props = item.get("IconProperties") or {}
            if not all(key in props for key in ("Rotation", "Scale", "Translation")):
                raise ValueError(f"No complete authored IconProperties for {row['itemBaseId']}; do not invent a pose")
            temp = Path(work)
            model, texture, properties, raw = [temp / name for name in
                                               ("item.blockymodel", "texture.png", "properties.json", "render.png")]
            model.write_bytes(archive.read(paths["Model"]))
            texture.write_bytes(archive.read(paths["Texture"]))
            properties.write_text(json.dumps(props), encoding="utf-8")
            result = subprocess.run([str(options.blender), "-b", "--python", str(renderer), "--",
                                     str(model), str(texture), str(raw), str(properties)],
                                    capture_output=True, text=True, timeout=180)
            if result.returncode or not raw.is_file():
                raise RuntimeError(f"Blender failed for {row['itemBaseId']}: {result.stdout[-1200:]} {result.stderr[-1200:]}")
            stock_icon = archive.read(paths["Icon"])
            icon, fit, pose = fit_visible_image(raw, width, height, stock_icon)
            output_name = f"{row['itemBaseId']}-{row['invWidth']}x{row['invHeight']}.png"
            icon.save(options.output / output_name)
            manifest.append({"itemId": row["itemBaseId"], "familyId": row["familyId"],
                             "displayName": row["displayName"], "itemFamily": row["itemFamily"],
                             "invWidth": row["invWidth"], "invHeight": row["invHeight"],
                             "sourceItemAsset": index[row["itemBaseId"]],
                             "sourceModelPath": paths["Model"], "sourceTexturePath": paths["Texture"],
                             "sourceIconPath": paths["Icon"], "iconProperties": props,
                             "iconPropertiesSource": sources["IconProperties"],
                             "poseSource": "resolved native IconProperties plus shipped icon alpha silhouette",
                             "propertiesApplication": "Authored Rotation and Scale applied to 3D model; native Translation recorded; spatial fit replaces native 64px framing to center within the footprint",
                             "stockPoseAlignment": pose,
                             "outputPng": output_name,
                             "renderMethod": "Blender Cycles blockymodel reconstruction + authored IconProperties pose + stock-silhouette roll check + alpha-bounds uniform fit",
                             "fit": fit, "stockIconBytes": stock_icon})
            print(f"GENERATED {row['itemBaseId']} {width}x{height} -> {options.output / output_name}")
    contact_sheet(manifest, options.output)
    for row in manifest:
        del row["stockIconBytes"]
    (options.output / "manifest.json").write_text(json.dumps({"schemaVersion": 1, "slotPixels": 64,
                                                                "items": manifest}, indent=2), encoding="utf-8")
    print("PROOF", options.output / "contact-sheet.png")


if __name__ == "__main__":
    main()
