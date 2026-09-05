"""R153 compact material buffers for private on-demand cards, not color atlases.

Base RGBA + gray index/lighting mask per real variant. Native palettes stored once.
Only the selected cosmetic writes tint pixels; context/trim/occlusion stay intact.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
from bake_appearance_thumbnails import Baker, W, H, rig_hash


class ColorSources(Baker):
    def __init__(self, archive):
        self.selected = None
        self.materials = {}
        super().__init__(archive)

    def texture(self, part):
        pixels, spec = super().texture(part)
        if part is self.selected and spec.get("GreyscaleTexture") and spec.get("GradientSet") and not spec.get("Textures"):
            raw = np.array(Image.open(io.BytesIO(self.read("Common/" + spec["GreyscaleTexture"]))).convert("RGBA"))
            self.materials[id(pixels)] = raw
        return pixels, spec

    def capture_material(self, tex, u, v, light, mask, x, y):
        dest = self.mask[y:y+mask.shape[0], x:x+mask.shape[1]]
        values = np.zeros((*mask.shape, 4), dtype=np.uint8)
        raw = self.materials.get(id(tex))
        if raw is not None:
            samples = raw[np.clip(v, 0, raw.shape[0]-1), np.clip(u, 0, raw.shape[1]-1)]
            grey = (samples[:,:,0] == samples[:,:,1]) & (samples[:,:,1] == samples[:,:,2])
            values[:,:,0] = samples[:,:,0]
            values[:,:,1] = round(light * 255)
            values[:,:,2] = grey * 255
            values[:,:,3] = 255
        dest[mask] = values[mask]

    def source(self, category, spec):
        self.materials.clear()
        self.mask = np.zeros((H, W, 4), dtype=np.uint8)
        self.selected = spec
        base = self.render(category, spec)
        return base, Image.fromarray(self.mask)


def digest(path): return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("assets", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    baker = ColorSources(args.assets)
    palettes = {}
    for group, colors in baker.gradients.items():
        palettes[group] = {}
        for color, desc in colors.items():
            lut = np.array(Image.open(io.BytesIO(baker.read("Common/"+desc["Texture"]))).convert("RGB"))
            if lut.shape[1] != 256: raise ValueError("Non-native gradient size")
            palettes[group][color] = lut[lut.shape[0]//2].reshape(-1).tolist()
    entries = {}
    count = 0
    for category, parts in baker.parts.items():
        for part in parts:
            key = category + ":" + part["Id"]
            variants = part.get("Variants", {})
            rows = {}
            for variant in sorted(variants) if variants else [""]:
                spec = {**part, **variants.get(variant, {})}
                spec.pop("Variants", None)
                textures = spec.get("Textures", {})
                colors = sorted(textures) if textures else [""]
                for color in colors:
                    actual = dict(spec)
                    if textures: actual["Textures"] = {color: textures[color]}
                    identity = key + "\t" + variant + "\t" + color
                    name = hashlib.sha256(identity.encode()).hexdigest()[:24]
                    base, mask = baker.source(category, actual)
                    base.save(args.output/(name+".png"))
                    mask.save(args.output/(name+"-mask.png"))
                    rows[variant+"\t"+color] = dict(base=name+".png", mask=name+"-mask.png",
                        baseSha256=digest(args.output/(name+".png")), maskSha256=digest(args.output/(name+"-mask.png")),
                        gradient=spec.get("GradientSet", "") if not textures and spec.get("GreyscaleTexture") else "")
                    count += 1
            entries[key] = dict(defaultVariant=sorted(variants)[0] if variants else "", sources=rows, rigHash=rig_hash(category))
        print(category, "sources", count, flush=True)
    palette_path = args.output/"palettes.json"
    palette_path.write_text(json.dumps(palettes, separators=(",", ":"))+"\n", encoding="utf-8")
    result = dict(version=1, size=[W,H], entries=entries, paletteSha256=digest(palette_path),
        sourceCount=count, sourceHashes=dict(sorted(baker.sources.items())),
        bakerSha256=hashlib.sha256(Path(__file__).read_text(encoding="utf-8").encode()).hexdigest(), geometrySha256=hashlib.sha256(Path(__file__).with_name("bake_appearance_thumbnails.py").read_text(encoding="utf-8").encode()).hexdigest(),
        materialRule="Exact grayscale only; 256-column native gradient; byte lighting error <= 1 RGB level; fixed context")
    (args.output/"index.json").write_text(json.dumps(result, indent=2)+"\n", encoding="utf-8")
    print("COMPLETE",len(entries),"cosmetics",count,"material sources; no cosmetic x gradient-color expansion")


if __name__ == "__main__": main()
