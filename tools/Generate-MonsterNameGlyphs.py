"""Generate the three exact-color promoted-name glyph atlases and Hytale models.

The quad/atlas layout follows the MIT-licensed TextUtils model approach:
https://github.com/Flo12344/TextUtils (Copyright 2026 Florent Daerden).
Lato Bold is distributed under OFL 1.1 in tools/fonts/hywind-name.
Requires Pillow only while regenerating assets, never at game runtime.
"""

from pathlib import Path
import json
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
FONT = ROOT / "tools/fonts/hywind-name/Lato-Bold.ttf"
COMMON = ROOT / "src/main/resources/Common/Items/RPG/NameGlyphs"
SERVER = ROOT / "src/main/resources/Server/Models/RPG/NameGlyphs"
COLORS = {"Champion": "#1d4dff", "Unique": "#a000ff", "SuperUnique": "#ff9100"}
CELL = 64
CHARS = [chr(code) for code in range(33, 127)]


def model(char: str, col: int, row: int) -> dict:
    return {
        "nodes": [{
            "id": "1", "name": f"glyph_{ord(char):04X}",
            "position": {"x": 0, "y": 0, "z": 0},
            "orientation": {"x": 0, "y": 0, "z": 0, "w": 1},
            "shape": {
                "type": "quad", "offset": {"x": 0, "y": 0, "z": 0},
                "stretch": {"x": 1, "y": 1, "z": 1},
                "settings": {"isPiece": False, "size": {"x": CELL, "y": CELL},
                             "normal": "+Z", "isStaticBox": True},
                "textureLayout": {"front": {"offset": {"x": col * CELL, "y": row * CELL},
                                            "mirror": {"x": False, "y": False}, "angle": 0}},
                "unwrapMode": "custom", "visible": True, "doubleSided": True,
                "shadingMode": "flat",
            },
        }],
        "format": "prop", "lod": "off",
    }


def write_json(path: Path, value: dict) -> None:
    path.write_text(json.dumps(value, separators=(",", ":")) + "\n", encoding="utf-8")


def main() -> None:
    COMMON.mkdir(parents=True, exist_ok=True)
    SERVER.mkdir(parents=True, exist_ok=True)
    font = ImageFont.truetype(str(FONT), 42)
    # Widths are frozen from the distributable font; runtime never loads a font file.
    metrics = [round(font.getlength(char) / CELL, 6) for char in CHARS]
    write_json(ROOT / "src/main/resources/rpg/enemies/promoted-name-glyph-metrics.json",
               {"font": "Lato Bold", "cell": CELL, "firstCodepoint": 33, "advances": metrics,
                "spaceAdvance": round(font.getlength(" ") / CELL, 6)})
    source = ROOT / "src/main/java/com/inigmasgames/hytalerpg/execution/hytale/PromotedNameWidths.java"
    source.write_text("package com.inigmasgames.hytalerpg.execution.hytale;\n\n"
                      "/** Generated from OFL-licensed Lato Bold by Generate-MonsterNameGlyphs.py. */\n"
                      "final class PromotedNameWidths {\n"
                      "    static final double[] ADVANCES = {" + ",".join(map(str, metrics)) + "};\n"
                      f"    static final double SPACE = {round(font.getlength(' ') / CELL, 6)};\n"
                      "    private PromotedNameWidths() { }\n}\n", encoding="utf-8")
    for index, char in enumerate(CHARS):
        write_json(COMMON / f"U{ord(char):04X}.blockymodel", model(char, index % 16, index // 16))
    for rarity, color in COLORS.items():
        atlas = Image.new("RGBA", (1024, 512), (0, 0, 0, 0))
        draw = ImageDraw.Draw(atlas)
        for index, char in enumerate(CHARS):
            x, y = index % 16 * CELL, index // 16 * CELL
            draw.text((x + CELL // 2, y + CELL // 2), char, font=font,
                      fill=color, anchor="mm", stroke_width=0)
            write_json(SERVER / f"HywindName_{rarity}_U{ord(char):04X}.json", {
                "Model": f"Items/RPG/NameGlyphs/U{ord(char):04X}.blockymodel",
                "Texture": f"Items/RPG/NameGlyphs/{rarity}.png",
                "HitBox": {"Min": {"X": 0, "Y": 0, "Z": 0},
                           "Max": {"X": 0.01, "Y": 0.01, "Z": 0.01}},
                "MinScale": 0.1, "MaxScale": 1,
            })
        atlas.save(COMMON / f"{rarity}.png", optimize=True)


if __name__ == "__main__":
    main()
