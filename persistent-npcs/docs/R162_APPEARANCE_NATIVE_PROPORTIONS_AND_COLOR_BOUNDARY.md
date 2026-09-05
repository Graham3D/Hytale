# R162 Appearance Native Proportions and Color Boundary

Status: deployed geometry-correction candidate on 2026-09-05. Connected review is pending. R161 is the immediate rollback.

## Scope and result

R162 is the narrow visual-pipeline pass requested after connected R161 review. It preserves CosmeticRegistry authority, the PlayerSkin draft/save representation, texture/variant/gradient semantics, CosmeticsModule validation, CharacterPreviewComponent, preview coalescing/generation checks, Save/Discard, the 20-card atlas ceiling, and the zero-runtime-PNG rule.

Two visibly separate findings were established:

1. The connected card distortion was a UI geometry bug. The immutable sources are 92x149, but R161 mounted them inside 92x100 card hosts and inset the image by 15 pixels on both horizontal sides. R162 now uses an exact 92x149 host and a full 92x149 AssetImage viewport. The source ratio is preserved rather than stretched, and the image-obscuring name plate is removed. The complete name remains on the card tooltip.
2. Hytale's live color-aware cosmetic card renderer is an internal client MyAvatar component, not a reusable static icon path or a server Custom UI component in 0.6.3. The permitted two-card flat-mask probe cannot reproduce the native gradient material. R162 therefore does not add an inaccurate tint overlay or resume runtime image generation. The central CharacterPreview remains the exact color preview.

This is intentionally not a redesign and does not begin Profile Editor polish.

## Native MyAvatar evidence

Installed source inspected:

```text
Client/Data/Game/Interface/MainMenu/MyAvatar/MyAvatarPage.ui
SHA-256 A5DD7C8F174455A45C012092AA61058DB8C498B0DD715955B2D03CF1FF1DF2A9
```

The native constants are:

```text
PartPreviewWidth = 92
PartPreviewHeight = 149
PartPreviewMargin = 10
PartPreviewRows = 3
PartPreviewsPerRow = 5
```

There is no static part-card document in the installed MyAvatar resources. The installed client executable contains `MyAvatarPagePartsTab`, `PartPreviewComponent`, `UpdatePartPreviewVisibilities`, and `ColorOptionGrid`. These types are internal client code. The installed server JAR has generic UI command/event builders but no `PartPreviewComponent`, server cosmetic-card renderer, or Custom UI binding that accepts a PlayerSkinPart/model/material state.

`PlayerSkinPart` exposes model, greyscale texture, gradient set, texture choices, and variants. It exposes no `Icon` or `IconProperties`. The referenced `.blockymodel` documents have no icon property. `Common/Icons/ModelsGenerated` contains entity/model icons but no matching CharacterCreator icon for the audited cosmetics.

Representative mappings:

| Cosmetic | Model | Material selection | Static native icon |
| --- | --- | --- | --- |
| `Wide_Neck_Shirt` | `Cosmetics/Undertops/Tshirt.blockymodel` | `Wideneck_Tshirt_Greyscale.png` + `Colored_Cotton` + GradientId | none |
| `VNeck_Shirt` | `Cosmetics/Undertops/Tshirt.blockymodel` | `VNeck_Greyscale.png` + `Colored_Cotton` + GradientId | none |
| `ApprenticePants` | `Cosmetics/Pants/Pants_Large.blockymodel` | direct `Brown`, `BrownLight`, or `Black` texture | none |
| `LeatherPants` | `Cosmetics/Pants/Pants_Straight.blockymodel` | `Leather_Greyscale.png` + `Faded_Leather` + GradientId | none |
| `Morning` haircut | `Characters/Haircuts/Morning.blockymodel` | `Morning_Greyscale.png` + `Hair` + GradientId | none |
| `BobCut` haircut | `Characters/Haircuts/BobCut.blockymodel` | `BobCut_Greyscale.png` + `Hair` + GradientId | none |

For a native swatch change, the model and greyscale texture stay fixed, the icon path stays nonexistent, and only GradientId/material state changes. The native client rerenders the model-backed PartPreviewComponent.

## Two-card Custom UI color probe

The required bounded probe uses two cosmetics sharing `Colored_Cotton`:

- `UNDERTOP:Wide_Neck_Shirt`
- `UNDERTOP:VNeck_Shirt`

The audit tool is `tools/audit_appearance_two_card_composition.py`. It reads the immutable retired base/material buffers and the installed native 256-entry gradient LUTs. It creates and writes zero images. The comparison gives the flat-mask approach more freedom than Custom UI actually supplies: each tunable pixel may choose its own ideal opacity along the selected swatch color. Even under that favorable lower bound, no tested tunable pixel exactly matches native output.

| Cosmetic | Color | Mean absolute RGB error lower bound | P95 | Maximum | Exact pixels |
| --- | --- | ---: | ---: | ---: | ---: |
| Wide Neck Shirt | Green | 8.98 | 26.47 | 43.00 | 0.00% |
| Wide Neck Shirt | Purple | 4.43 | 14.64 | 34.99 | 0.00% |
| V-Neck Shirt | Green | 9.67 | 27.40 | 33.38 | 0.00% |
| V-Neck Shirt | Purple | 4.98 | 15.23 | 23.40 | 0.00% |

The probe also evaluated a still-more-generous shared composition model: an arbitrary immutable RGB detail value plus one arbitrary immutable per-pixel opacity multiplied by the selected flat color. Wide Neck Shirt still had 6.16 mean / 9.49 P95 RGB error and V-Neck Shirt had 6.44 mean / 9.59 P95 error across green and purple; exact parity remained 0.00%. Actual Custom UI offers less compositing control than this mathematical lower bound.

The reason is structural: `PatchStyle` exposes texture path, borders, flat color, and area; a Group mask can clip a flat fill. Neither surface accepts GradientSet/GradientId, a 256-entry LUT, a cosmetic model, a multiply blend, or a shader/material binding. A flat fill cannot express the native LUT's hue-shifting shadows and highlights. Applying it over the whole image would also tint skin/mannequin/trim, while a region mask still loses the native material response.

Result: `REJECTED_NATIVE_COLOR_PARITY_NOT_REPRODUCIBLE`.

The safest remaining fallback is the one deployed here: immutable representative gallery artwork for selection plus the exact live CharacterPreview for the active cosmetic/color. A future true parity implementation requires Hytale to expose its client PartPreviewComponent or an equivalent Custom UI model/material thumbnail property. Per-color/per-session PNGs and runtime asset IDs remain forbidden.

## UI correction

- Source: 92x149 RGBA, unchanged hashes and provenance.
- Host: changed from 92x100 to 92x149.
- Image viewport: changed from left/right 15-pixel insets to exact full-host bounds.
- Gutter: retained at 10 pixels.
- Columns: retained at five.
- Realization/page ceiling: retained at 20; the existing scrolling viewport handles the fourth row without expanding the client-resource budget.
- Labels: graphical cards use their tooltip instead of covering the lower image. The icon/text fallback remains for semantic `None` and a missing immutable reference.
- Selection: existing independent gold selected underline remains unchanged.

Category framing is recorded using the existing immutable category rigs: waist-to-feet for pants/overpants, shoulders-to-waist for tops, lower legs/feet for shoes, head/shoulders for hair/head accessories, close head framing for facial parts, three-quarter head for ear parts, torso/arms/hands for gloves, rear-body for capes, and full-body fallback elsewhere.

## Bounded diagnostics

R162 adds these exact diagnostics without per-frame logging:

- `APPEARANCE_NATIVE_ICON_RESOLVED`
- `APPEARANCE_NATIVE_ICON_MISSING`
- `APPEARANCE_ICON_FRAMING_RESOLVED`
- `APPEARANCE_COLOR_ICON_STATE_CHANGED`
- `APPEARANCE_COLOR_ICON_STATE_UNCHANGED`

The current 0.6.3 path emits `APPEARANCE_NATIVE_ICON_MISSING` once per referenced cosmetic in a page instance, followed by the immutable fallback framing record. A real color click emits `APPEARANCE_COLOR_ICON_STATE_UNCHANGED` once for that event while recording that the central preview did change. Records include cosmetic ID, model/variant, icon path, framing, texture/gradient state, and gallery color-state result.

## Deterministic validation

The full deterministic suite passed with `test.ps1 -SkipLive`. The new R162 gate verifies:

- exact 92x149 host/source geometry and full image bounds;
- removal of the image-obscuring graphical-card name plate;
- retained tooltips and selected state;
- category framing classifications for Pants, Undertop, and Haircut;
- required native-icon/framing/color diagnostics;
- the isolated two-card audit and its rejected result;
- no runtime thumbnail creation, write, recolor, upload, or mask compositor in production;
- all prior appearance interaction, catalog, preview, persistence, inventory, gear, stats, voice, cognition, and player-isolation tests.

Compilation produced only the existing Hytale deprecation and deterministic-test Unsafe warnings.

## Deployment and rollback

Exactly one project JAR is active:

```text
C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R162-NPC-APPEARANCE-NATIVE-PROPORTIONS.jar
SHA-256 899C4211E8DFD77DB097D20286917A5BE708B03FE8B1DBEBDA0739DF59A35B01
```

Immediate rollback:

```text
C:\HytaleRollback\NpcAuthoringStudio-Appearance-R161-2026-09-05-R162NativeProportions\ImmersiveNPCs-0.6.3-R161-NPC-APPEARANCE-NATIVE-CARD-BINDING.jar
SHA-256 2B698271C33E4E08D9FB96960C0F5636AA2A69373CADE49E757B6F5D4BC29E8D
```

## Connected validation

Fully exit and restart Hytale before this test so the UI atlas is rebuilt from R162.

1. Confirm the HUD revision says `R162-NPC-APPEARANCE-NATIVE-PROPORTIONS`.
2. Open `/npc update hoit`, then Appearance.
3. Compare Pants, Undertop, and Haircut against native MyAvatar. Confirm cards are tall 92x149 portraits, character/cosmetic proportions are no longer squeezed, and names no longer cover the lower artwork.
4. Page and scroll through at least four rows. Confirm consistent 10-pixel gutters, clean clipping inside the options viewport, correct tooltips, and the gold selected underline.
5. Change several colors. Confirm the central NPC preview changes exactly. The gallery remains representative-color artwork by design in this candidate; it must not display a flat incorrect tint.
6. Repeatedly change color, selection, page, search, and category. Confirm no Loading state, progressive lag, dropped images, atlas warnings, or runtime asset creation.
7. Save/reopen/restart and then test Discard. Confirm exact PlayerSkin-compatible persistence and no player appearance/equipment mutation.

R162 stops at the documented Hytale 0.6.3 Custom UI color-renderer boundary. No large replacement renderer or unrelated editor work was started.
