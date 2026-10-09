"""Focused offline check of the shipped promoted-name models, atlases and font license."""

import io
import json
from pathlib import Path
import zipfile

from PIL import Image

root = Path(__file__).resolve().parents[1]
jar = root / "build/libs/HyARPG.jar"
colors = {"Champion": (0x1D, 0x4D, 0xFF), "Unique": (0xA0, 0, 0xFF),
          "SuperUnique": (0xFF, 0x91, 0)}

with zipfile.ZipFile(jar) as package:
    files = set(package.namelist())
    assert "META-INF/licenses/Lato-OFL.txt" in files
    metrics = json.loads(package.read("rpg/enemies/promoted-name-glyph-metrics.json"))
    assert metrics["font"] == "Lato Bold" and len(metrics["advances"]) == 94
    assert metrics["advances"][ord("W") - 33] > metrics["advances"][ord("i") - 33]
    for rarity, rgb in colors.items():
        texture = f"Items/RPG/NameGlyphs/{rarity}.png"
        image = Image.open(io.BytesIO(package.read("Common/" + texture))).convert("RGBA")
        assert image.size == (1024, 512)
        pixels = {pixel[:3] for pixel in image.getdata() if pixel[3] > 0}
        assert pixels == {rgb}, (rarity, pixels)
        for codepoint in range(33, 127):
            glyph = f"U{codepoint:04X}"
            model = json.loads(package.read(f"Server/Models/RPG/NameGlyphs/HywindName_{rarity}_{glyph}.json"))
            assert model["Model"] == f"Items/RPG/NameGlyphs/{glyph}.blockymodel"
            assert model["Texture"] == texture
            blocky = json.loads(package.read(f"Common/Items/RPG/NameGlyphs/{glyph}.blockymodel"))
            assert blocky["lod"] == "off"

print("PROMOTED_NAME_ASSETS PASS: 282 colored models, 94 glyph models, exact rarity pixels, metrics, OFL license")
