# First-pass armor pose test

This is an eight-item **offline render comparison**. The [contact sheet](output/contact-sheet.png) shows each item's shipped pose at left and the requested test pose at right. [The manifest](output/manifest.json) records the item asset, model, texture, icon, inherited settings source, both exact poses, output PNG paths, and whether the paired PNG pixels are identical. The [pose table](poses-v1.json) is isolated from gameplay footprint and item assets. No mod JAR, world, player save, or deployed asset was changed.

Chest/body armor uses `Translation=[0,0]`, `Rotation=[10,-30,-5]`, `Scale=0.45`, fitted uniformly and centered on 128×192. Head and hands use 128×128; legs use 128×192. Those three families retain each item's authored rotation and scale and change only `Translation` to `[0,0]`.

| Sample item | Test rotation X/Y/Z | Test scale | Final canvas | Result |
| --- | --- | ---: | --- | --- |
| `Armor_Iron_Chest` | 10 / -30 / -5 | 0.45 | 128×192 | Changed; a little more front-facing, still visually fragmented. |
| `Armor_Leather_Heavy_Chest` | 10 / -30 / -5 | 0.45 | 128×192 | Changed; flatter and less readable than its shipped pose. |
| `Armor_Iron_Head` | 22.5 / 45 / 22.5 | 0.5 | 128×128 | Identical after X/Y reset; box-like helmet remains. |
| `Armor_Bronze_Ornate_Head` | 29.815 / 41.065 / 15.19 | 0.5 | 128×128 | Identical after X/Y reset. |
| `Armor_Iron_Hands` | 22.5 / 45 / 22.5 | 0.92 | 128×128 | Identical after X/Y reset. |
| `Armor_Leather_Raven_Hands` | 22.5 / 45 / 22.5 | 0.8 | 128×128 | Identical after X/Y reset. |
| `Armor_Bronze_Legs` | 22.5 / 45 / 22.5 | 0.5 | 128×192 | Identical after X/Y reset. |
| `Armor_Leather_Raven_Legs` | 22.5 / 50.065 / 22.5 | 0.67 | 128×192 | Identical after X/Y reset. |

Every test item has `Translation=[0,0]`; the baseline X/Y values are in the manifest. The six head/hands/legs pairs are **pixel-identical** because this prototype already crops each visible model and centers it in the final footprint. Native 64×64 icon `Translation` is recorded but not applied to that final framing. Zeroing the values therefore cannot improve this renderer's output unless a later pipeline intentionally preserves native offset before spatial placement. The chest pairs differ because their rotation changed; the scale change on Heavy Leather is also normalized away by the final visible-bounds fit. Stock-silhouette roll alignment selected the same roll for each baseline/test pair, so it did not cause the chest differences.

The new chest template is **not ready as a family default**. It helps the front view of Iron slightly but worsens Heavy Leather, and the blockymodel reconstruction still makes chest/head armor look like separated boxes. Head and hands are visibly weak; legs are somewhat readable, but zeroed offsets do not change them. These results justify continued *bounded* armor renderer refinement, especially model hierarchy, UV interpretation, camera parity, and an explicit choice about spatial framing. They do not justify a full armor batch or changes to the weapon pipeline.

To regenerate and validate from the repository root with Python/Pillow and Blender installed:

```powershell
python tools/Generate-ArmorPoseTest.py --assets "$env:APPDATA\Hytale\install\pre-release\package\game\latest\Assets.zip" --blender 'C:\Program Files\Blender Foundation\Blender 5.0\blender.exe'
python tools/Test-ArmorPoseTest.py
```
