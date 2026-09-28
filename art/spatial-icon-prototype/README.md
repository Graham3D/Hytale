# Spatial item icon prototype

This is an **offline art-pipeline proof**, separate from the live `FootprintCatalog`, UI assets, and player inventory. It does not change the mod JAR or any save. The seed table is [`footprints-v1.json`](footprints-v1.json); the proof is [`output/contact-sheet.png`](output/contact-sheet.png). Each sample PNG and its source metadata are listed in [`output/manifest.json`](output/manifest.json). The family settings audit is [`family-icon-settings.json`](family-icon-settings.json).

The separate [eight-item armor pose comparison](armor-pose-test/README.md) tests a 10/-30/-5 chest template and zeroed X/Y offsets for head, hands, and legs without changing weapon poses or gameplay assets.

## Render path decision

The installed pre-release `HytaleServer.jar` exposes `AssetSpecificFunctionality.getDefaultItemIconProperties(Item)` and `getModelPreviewPacketForItem(AssetPath, Item)`. The latter returns an `AssetEditorUpdateModelPreview` packet for an editor/client preview; it does **not** expose PNG pixels or a headless batch-export API. Hytale/HyCreator's interactive icon editor can save an icon through its client UI, but the available server-side helper is not a callable image generator. Running the game/editor or automating saves would violate this task's offline boundary and would not be a practical weapon-family batch workflow.

The prototype therefore reads each installed item JSON plus inherited `IconProperties`, `Model`, `Texture`, and existing `Icon` references from `Assets.zip`. A headless Blender script reconstructs blockymodel boxes and textures, lights them, and renders a transparent 512×512 intermediate. The Python driver crops **rendered 3D pixels**, fits them uniformly within the target footprint, centers them, and writes transparent RGBA PNGs. The stock 64×64 icon is a **pose reference**, not the source of generated pixels. No stock raster is enlarged or stretched independently along either axis.

The first version was wrong here: it stored `IconProperties` while using PCA and invented family polarity to pose the model. The revised tool resolves the nearest authored `IconProperties` in each item's parent chain and **requires** `Rotation`, `Scale`, and `Translation`; it fails instead of fabricating a pose. It applies the authored rotation and scale to a Y-up model viewed along Z. This corrects the edge-on result from the earlier X-facing camera. It then compares the model silhouette at 0° and 180° in the image plane with the shipped PNG silhouette, choosing and recording the closer roll. This bounded check preserves the authored item pose's direction without making up new per-item angles. The five samples select 180°; their scores and both candidate scores are in the manifest. Native `Translation` is recorded as part of the canonical seed, while final spatial framing intentionally replaces the 64×64 icon offset to center the item in a different aspect ratio. The spatial fit similarly adapts final size after the authored scale has been applied. These transformations are explicit in the manifest.

This remains an approximation of Hytale's editor renderer: the exact native camera, node transforms, UV mirror/angle handling, lighting, and PNG composition are not all reproduced. The helmet still has the wrong visual read. The shipped PNG comparison is useful for orientation, not proof of pixel-perfect renderer parity. The manifest and contact sheet mark low-similarity or ambiguous roll matches for review. An item whose settings or shipped icon are missing is rejected; a family average does not silently replace authored item data. A narrow, tall footprint also cannot use its entire height while preserving a diagonal shipped pose and uniform scale; that is an art decision for later, not a reason to silently rotate the item.

## Family-level copy-settings investigation

`tools/Audit-IconSettings.py` scans **metadata only** for installed weapon and armor families. For each item, the JSON records the effective settings, the defining item/template asset, whether they were inherited, the shipped PNG path, a most-common rotation, an exact full-settings copy candidate with a source item, and every item that differs from that candidate. This models HyCreator's “Copy settings” workflow without overriding individual authored transforms.

| Family in installed assets | Rotation agreement | Exact full-settings copy agreement | Implication |
| --- | ---: | ---: | --- |
| Shortbow | 19/19 | 14/19 | Strong shared pose; preserve five individual scale/offset variants. |
| Sword | 23/23 | 22/23 | Near-uniform full template; keep one exception. |
| Battleaxe | 15/15 | 15/15 | Full-settings template is supported by this installed set. |
| Shield | 15/16 | 15/16 | One item-specific exception. |
| Longsword | 20/21 | 5/21 | Share rotation only; scale/translation vary substantially. |
| Staff | 19/26 | 2/26 | A single copy-settings template is unsafe. |
| Head armor | 20/28 | 9/28 | Armor needs subfamily/item-specific treatment. |
| Chest armor | 27/29 | 3/29 | Shared viewing angle, not shared full transform. |

The machine-readable audit also includes crossbows, daggers, axes, maces, clubs, spears, wands, gloves, and leg armor. A future batch tool can copy the candidate **only for new assets missing authored settings**, and only after checking the family, model orientation, shipped icon comparison, and exceptions. For installed assets, the order of authority is: item's own settings → parent/template settings → **fail and flag**. The copied family candidate is a reviewed authoring suggestion, not a silent generation fallback.

## Footprint table and five samples

The 64-pixel-cell seed table has the requested short bow 2×3, long bow 2×4, wand 1×2, short sword 1×3, long staff 1×4, helm/gloves/boots 2×2, belt 2×1, body armor/large shield 2×3, and bigger shield 2×4. It also reserves a crossbow target for the future weapon batch. Placeholder rows with no confirmed installed ID have `itemBaseId: null` and are **not** generated or silently mapped to gameplay. The installed assets have no Longbow item ID, so `Weapon_Shortbow_Iron` is explicitly labeled a **2×4 bow-render proxy**, not classified as an actual longbow. The generated set is:

| Sample | Footprint | PNG |
| --- | --- | --- |
| Copper Shortbow | 2×3 | `Weapon_Shortbow_Copper-2x3.png` (128×192) |
| Iron Bow, long-bow proxy | 2×4 | `Weapon_Shortbow_Iron-2x4.png` (128×256) |
| Iron Sword | 1×3 | `Weapon_Sword_Iron-1x3.png` (64×192) |
| Wizard Staff | 1×4 | `Weapon_Staff_Wizard-1x4.png` (64×256) |
| Iron Helm | 2×2 | `Armor_Iron_Head-2x2.png` (128×128) |

## Reproduce and check offline

Run from the repository root with Python 3 + Pillow and Blender 5 available. Substitute local installation paths as needed:

```powershell
python tools/Generate-SpatialIcons.py --assets "$env:APPDATA\Hytale\install\pre-release\package\game\latest\Assets.zip" --blender 'C:\Program Files\Blender Foundation\Blender 5.0\blender.exe'
python tools/Audit-IconSettings.py --assets "$env:APPDATA\Hytale\install\pre-release\package\game\latest\Assets.zip"
python tools/Test-SpatialIcons.py
```

`--ids` accepts a bounded explicit selection of IDs already in the table (maximum 24), and `--output` selects an offline destination. The next bounded batch can add bows, crossbows, swords, daggers, staffs, wands, shields, and other large weapons to structured data. It must resolve each item's own settings first, retain its shipped PNG as a pose reference, and hold low-similarity results for review. No all-game batch is implemented here.

## Visual decision

The two bows, sword, and staff demonstrate model-derived transparent artwork at multi-cell dimensions with their shipped poses as seeds. The contact sheet also makes the current limitation clear: the helmet reads too much like an open box, and texture/camera reproduction still differs from Hytale's icon output. **Do not greenlight an all-weapons batch or replace shipped icons on this proof alone.** First improve model/UV and camera parity on armor and a few additional weapon families, compare stock-silhouette similarity and visual direction, then get a visual acceptance decision from the owner. The renderer and manifest are ready for that bounded follow-up; no gameplay code or player data needs to change.
