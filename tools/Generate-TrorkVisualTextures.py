"""Reproducible R224 Trork presentation textures from the pinned installed native art.

Only colors existing opaque pixels. No geometry, alpha, gameplay item, or shader change.
"""
from __future__ import annotations

import colorsys
import hashlib
from pathlib import Path
import zipfile
from io import BytesIO
from PIL import Image

ASSETS = Path.home() / "AppData/Roaming/Hytale/install/pre-release/package/game/latest/Assets.zip"
OUT = Path(__file__).resolve().parents[1] / "src/main/resources/Common"
SRC = "NPC/Intelligent/Trork/Models/"
PIN = {
    "Model_Textures/Cyan_Dark.png": "863a7d2ded5eed27eafbedf2a470f995b8e0199e2d02fbe397d7ee579444e983",
    "Attachments/Warrior/Chest_Texture.png": "012ee11056e177bbcb4fc0697d4aafb430b63dd057c03227687c91a81282ec8d",
    "Attachments/Warrior/Hands_Texture.png": "2844bb64e1b5f7a5d5a189cea3ab0a3984077a086510a10b41ffbbc602c37b04",
    "Attachments/Warrior/Feet_Texture.png": "c900f04538fac19240fb2c5d2a1dedcd8efd6e1d7d4399219c571e7d570815e5",
    "Attachments/Warrior/Head_Texture.png": "64458f029a2adffcc789dd9d686c11a059c0010679cb276ae2675c7c45dfe269",
    "Weapons/Battleaxe/Stone_Texture.png": "a023c6625b9fb34fd6b7963eb604a4474d8a2d6134124571e83e498241d0152b",
}


def source(archive: zipfile.ZipFile, name: str) -> Image.Image:
    data = archive.read("Common/" + SRC + name)
    if hashlib.sha256(data).hexdigest() != PIN[name]:
        raise RuntimeError("Native Trork texture changed: " + name)
    return Image.open(BytesIO(data)).convert("RGBA")


def wash(image: Image.Image, hue: float, strength: float, region: str) -> Image.Image:
    output = Image.new("RGBA", image.size)
    pixels = []
    for r, g, b, alpha in image.get_flattened_data():
        h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
        if alpha == 0:
            pixels.append((r, g, b, alpha))
            continue
        # The original base sheet has cyan skin and brown/white details. Affect cyan only.
        mask = max(0.0, min(1.0, (s - 0.18) / 0.18)) if 0.40 <= h <= 0.65 else 0.0
        amount = strength * (mask if region == "skin" else 1.0)
        # Blend RGB with equal-luminance tint. Preserve authored shading and pixel detail.
        tr, tg, tb = colorsys.hsv_to_rgb(hue, max(0.28, s * 0.76), v)
        pixels.append((round(r * (1 - amount) + 255 * tr * amount),
                       round(g * (1 - amount) + 255 * tg * amount),
                       round(b * (1 - amount) + 255 * tb * amount), alpha))
    output.putdata(pixels)
    return output


def save(image: Image.Image, root: str, name: str) -> None:
    destination = OUT / root / "RPG/Enemies/Visual/Trork" / name
    destination.parent.mkdir(parents=True, exist_ok=True)
    image.save(destination, optimize=True)


with zipfile.ZipFile(ASSETS) as archive:
    body = source(archive, "Model_Textures/Cyan_Dark.png")
    for rarity, hue, strength in (("Champion", 0.635, 0.68), ("Unique", 0.775, 0.71),
                                  ("SuperUnique", 0.085, 0.78)):
        save(wash(body, hue, strength, "skin"), "NPC", "Skin_" + rarity + ".png")
    for part in ("Chest", "Hands", "Feet", "Head"):
        original = source(archive, "Attachments/Warrior/" + part + "_Texture.png")
        # Stone Skin shifts armor toward neutral slate while retaining native texture gradients.
        save(wash(original, 0.61, 0.43, "armor"), "NPC", "Armor_StoneSkin_" + part + ".png")
    original = source(archive, "Weapons/Battleaxe/Stone_Texture.png")
    save(wash(original, 0.055, 0.72, "weapon"), "Items", "Weapon_Fire_Battleaxe.png")
