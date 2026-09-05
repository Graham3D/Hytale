"""Bounded offline comparison of the installed native shader's gradient rule.

Writes QA images only under build; does not modify packaged cards or runtime
assets. Native shader evidence: HytaleClient.exe at byte offset 30217644 for the
inspected 0.6.3 build. This is not a claim that native PartPreview is exposed to UI.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
from bake_appearance_thumbnails import Baker, W, H


def native_gradient(pixels, lookup):
    result = pixels.copy()
    mask = (pixels[:, :, 0] == pixels[:, :, 1]) & (pixels[:, :, 1] == pixels[:, :, 2])
    # Installed shader fetches gradient-atlas x = red * 255, no luminance estimate.
    if lookup.shape != (256, 3):
        raise ValueError("Expected the native 256-column RGB gradient")
    result[:, :, :3][mask] = lookup[pixels[:, :, 0][mask]]
    return result


class NativeColorProbe(Baker):
    def __init__(self, archive):
        self.selected = None
        self.color = None
        super().__init__(archive)

    def texture(self, part):
        variants = part.get("Variants", {})
        spec = {**part, **(variants[sorted(variants)[0]] if variants else {})}
        path = spec.get("GreyscaleTexture")
        gradient = self.gradients.get(spec.get("GradientSet"), {})
        if not path or not gradient or spec.get("Textures"):
            return super().texture(part)
        preferred = {"Skin": "02", "Hair": "BrownLight", "Colored_Cotton": "Blue"}.get(spec.get("GradientSet"))
        key = preferred if preferred in gradient else list(gradient)[min(3, len(gradient)-1)]
        if part is self.selected and self.color is not None:
            if self.color not in gradient:
                raise ValueError("Color is not in this cosmetic's native gradient set")
            key = self.color
        pixels = np.array(Image.open(io.BytesIO(self.read("Common/" + path))).convert("RGBA"))
        lookup = np.array(Image.open(io.BytesIO(self.read("Common/" + gradient[key]["Texture"]))).convert("RGB"))
        return native_gradient(pixels, lookup[lookup.shape[0]//2]), spec


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("assets", type=Path)
    parser.add_argument("--output", type=Path, default=Path("build/r153-native-color-probe"))
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    baker = NativeColorProbe(args.assets)
    original = Baker(args.assets)
    names = ["PuffyJacket", "Tartan", "BunnyHoody", "LongBeltedJacket", "RobeOvertops", "ThreadedOvertops"]
    parts = [p for p in baker.parts["OVERTOP"] if p["Id"] in names]
    sheet = Image.new("RGB", (W*len(parts), (H+36)*3+24), "#2f3a4f")
    draw = ImageDraw.Draw(sheet)
    records = []
    for row, color in enumerate([None, "Purple", "Green"]):
        for col, part in enumerate(parts):
            baker.selected, baker.color = part, color
            image = original.render("OVERTOP", part) if color is None else baker.render("OVERTOP", part)
            filename = part["Id"] + "-" + (color or "Reference") + ".png"
            image.save(args.output / filename)
            records.append(dict(file=filename, sha256=hashlib.sha256((args.output/filename).read_bytes()).hexdigest()))
            x, y = col*W, row*(H+36)+24
            sheet.paste(image, (x, y), image)
            draw.text((x+4, y+H+4), (color or "Reference") + " / " + part["Id"], fill="white")
    draw.text((8, 6), "Native grayscale-only gradient: reference / Purple / Green. Fixed R152 torso rig.", fill="white")
    sheet.save(args.output / "comparison.png")
    # Exact material test: non-gray trim and alpha are invariant for each palette.
    for part in parts:
        raw = np.array(Image.open(io.BytesIO(baker.read("Common/"+part["GreyscaleTexture"]))).convert("RGBA"))
        grey = (raw[:,:,0] == raw[:,:,1]) & (raw[:,:,1] == raw[:,:,2])
        for color in ["Purple", "Green"]:
            baker.selected, baker.color = part, color
            actual, spec = baker.texture(part)
            np.testing.assert_array_equal(actual[:,:,:3][~grey], raw[:,:,:3][~grey])
            np.testing.assert_array_equal(actual[:,:,3], raw[:,:,3])
    (args.output / "index.json").write_text(json.dumps(records, indent=2)+"\n", encoding="utf-8")
    print("PASS: native non-gray trim / alpha preserved;", len(records), "comparison cards; runtime/package unchanged")


if __name__ == "__main__":
    main()
