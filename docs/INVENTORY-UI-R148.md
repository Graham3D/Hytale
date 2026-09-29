# Inventory visual pass R148

The supplied R147 screenshot shows three independent upper panels and one lower inventory panel. The stock page supplies the reference for inset controls, separation between decorative frames, and coherent item art. R148 keeps the RPG layout and native 15×5 spatial bag while correcting its panel geometry.

| Area | R147 issue | R148 change |
| --- | --- | --- |
| Navigation and upper panels | Ribbon artwork almost touches the three panel borders. | 12 UI units of vertical clearance below the ribbon. |
| Upper panel frames | 12-unit gaps leave frame ornamentation visually crowded. | 16-unit gaps; upper widths still total exactly 1216 units, matching the lower panel. |
| Gear panel | Outer weapon and offhand slots sit against the frame borders. | 12-unit horizontal insets, with the character preview reduced to 296 units to preserve the 480-unit panel width. |
| Inventory panel | Inventory title begins in the header's decorative corner; upper and lower panel frames nearly meet. | 16-unit title inset and 16-unit row separation. Search and sort stay aligned to the right. |
| Generated icons | Blender viewed the wrong face for Hytale's +90° icon yaw and ignored rotated/mirrored texture-atlas regions; several icons lost visible surfaces. | Camera now views the authored face and maps 90°/270° atlas rectangles with the correct dimensions. Both real shortbows use X/Y positions 0, X/Y/Z rotation 0/90/0, and scale 0.4. |

The [pose contact sheet](../art/spatial-icon-prototype/pose-batch-output/contact-sheet.png) and [manifest](../art/spatial-icon-prototype/pose-batch-output/manifest.json) contain 20 bounded samples with the applied transforms. Only nine visually usable samples are used by the live bag. The spear, several armor pieces, and other samples whose texture reconstruction still has missing surfaces continue using Hytale's shipped `ItemIcon`; the gallery remains available for further renderer work. The proof does not imply that all Hytale items are render-ready.

The current authoritative footprint catalog assigns both `Weapon_Shortbow_Iron` and `Weapon_Shortbow_Copper` **2×4**. The requested style rubric assigns short bows **2×3**. This pass keeps existing bag geometry and save positions; changing live footprints requires a guarded spatial-layout migration and is not an art-only adjustment. No nonexistent long-bow proxy is presented as a real family.

Offline gate: `python tools/Test-PoseBatch.py` validates exact PNG canvas dimensions, transparency, no edge clipping, catalog agreement, and bow pose. `gradlew check` and `tools/Test-HyArpgPackage.ps1` validate the JAR. Connected appearance still needs the user's in-game QA.
