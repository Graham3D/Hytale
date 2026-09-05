"""All-source integrity and actual palette/material QA. Outputs no runtime state."""
import argparse
import hashlib
import json
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
from bake_appearance_thumbnails import W, H, rig_hash
from probe_native_cosmetic_colors import NativeColorProbe, native_gradient

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT/"src/main/resources/appearance-color-sources"


def recolor(base, mask, lookup):
    out = base.copy()
    selected = (mask[:,:,2] == 255) & (mask[:,:,3] != 0)
    rgb = lookup[mask[:,:,0]].astype(np.uint32) * mask[:,:,1,None] // 255
    out[:,:,:3][selected] = rgb[selected].astype(np.uint8)
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--assets", type=Path)
    args = parser.parse_args()
    index = json.loads((SOURCES/"index.json").read_text())
    palettes = json.loads((SOURCES/"palettes.json").read_text())
    assert index["version"] == 1 and len(index["entries"]) == 590
    assert index["paletteSha256"] == hashlib.sha256((SOURCES/"palettes.json").read_bytes()).hexdigest()
    for field, file in [("bakerSha256", "bake_appearance_color_sources.py"), ("geometrySha256", "bake_appearance_thumbnails.py")]:
        assert index[field] == hashlib.sha256((ROOT/"tools"/file).read_text(encoding="utf-8").encode()).hexdigest(), field
    count = 0
    for key, entry in index["entries"].items():
        assert entry["rigHash"] == rig_hash(key.split(":")[0])
        for row in entry["sources"].values():
            count += 1
            for kind in ["base", "mask"]:
                path = SOURCES/row[kind]
                assert hashlib.sha256(path.read_bytes()).hexdigest() == row[kind+"Sha256"]
                assert Image.open(path).size == (W,H)
            base = np.array(Image.open(SOURCES/row["base"]).convert("RGBA"))
            mask = np.array(Image.open(SOURCES/row["mask"]).convert("RGBA"))
            tinted = (mask[:,:,2] == 255) & (mask[:,:,3] != 0)
            assert not np.any(tinted & (base[:,:,3] == 0)), key
            for color, lut in list(palettes.get(row["gradient"], {}).items())[:2]:
                output = recolor(base, mask, np.array(lut).reshape(256,3))
                np.testing.assert_array_equal(output[~tinted], base[~tinted], err_msg=key)
                np.testing.assert_array_equal(output[:,:,3], base[:,:,3], err_msg=key)
    assert count == index["sourceCount"] == 685
    # Explicit native exact-gray predicate, including colored texels with same red value.
    tex = np.array([[[80,80,80,200],[80,79,80,255],[0,20,0,255]]], dtype=np.uint8)
    lut = np.repeat(np.arange(256,dtype=np.uint8)[:,None],3,axis=1)
    lut[:,0] = 255
    actual = native_gradient(tex,lut)
    np.testing.assert_array_equal(actual[0,1:],tex[0,1:])
    assert actual[0,0,0] == 255 and actual[0,0,3] == 200
    if args.assets:
        native = NativeColorProbe(args.assets)
        names = ["PuffyJacket", "Tartan", "BunnyHoody", "LongBeltedJacket", "RobeOvertops", "ThreadedOvertops"]
        sheet = Image.new("RGB", (W*6,(H+28)*2+28), "#2f3a4f")
        draw = ImageDraw.Draw(sheet)
        draw.text((8,7),"Private material reconstruction: Purple / Green; invariant trim, context and category camera", fill="white")
        for r, color in enumerate(["Purple","Green"]):
            for c, name in enumerate(names):
                part = next(p for p in native.parts["OVERTOP"] if p["Id"] == name)
                native.selected, native.color = part, color
                expected = np.array(native.render("OVERTOP", part))
                row = index["entries"]["OVERTOP:"+name]["sources"]["\t"]
                base = np.array(Image.open(SOURCES/row["base"]))
                mask = np.array(Image.open(SOURCES/row["mask"]))
                output = recolor(base,mask,np.array(palettes[row["gradient"]][color]).reshape(256,3))
                assert np.max(np.abs(output.astype(int)-expected.astype(int))) <= 1, name
                x,y=c*W,r*(H+28)+28
                image=Image.fromarray(output)
                sheet.paste(image,(x,y),image)
                draw.text((x+4,y+H+4),color+" / "+name,fill="white")
        folder=ROOT/"docs/R153_COLOR_COMPARISON"
        folder.mkdir(exist_ok=True)
        sheet.save(folder/"OVERTOP-Purple-Green.png")
    print("PASS: 590 cosmetics / 685 sources; hashes, rigs, native exact-gray tint, trim/context/alpha isolation; reconstruction <= 1 RGB level")


if __name__ == "__main__": main()
