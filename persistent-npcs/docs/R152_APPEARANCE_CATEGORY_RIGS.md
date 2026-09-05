# R152 — fixed category reference-card composition

Date: 2026-09-05. Baseline: clean `main` at
`d7780c47e8bbcbe9b60c7f5e9bfdfd7dca7f8d9d` (verified equal to remote main).
Status: **implemented, deterministic PASS, deployed; connected approval pending**.
This is the bounded R151 renderer correction, not a UI/backend redesign.

## Composition contract

`tools/bake_appearance_thumbnails.py` now selects a rig using category alone.
Projection accepts no cosmetic ID, geometry, or AABB. Scale is `298 / verticalSpan`;
the world-space target is projected with the fixed camera. Every rig uses the full
`[0,0,184,298]` crop, the same pinned neutral skeleton pose, and zero adaptive safety
expansion. Outlying geometry is clipped; it does not change center or zoom.

The installed native `MyAvatarPage.ui` declares 92×149 part cards, 10px gaps and the
`#2f3a4f` background. Those display dimensions and the user's native Overtop screenshot
guided this correction. No undocumented PartPreviewComponent work was reopened.
The supplied screenshot, not a newly driven native-client session, is the visual
proportion reference. Native connected approval remains a user gate.

| Categories | Rig | Yaw / pitch | World target | Vertical span | Neutral context |
| --- | --- | --- | --- | --- | --- |
| Haircut, Head Accessory | head_shoulders | -8° / 0° | 0,86,0 | 70 | Head, neck, shoulders, face details |
| Face, Eyes, Eyebrows, Mouth, Facial Hair, Face Accessory | face | 0° / 0° | 0,88,0 | 55 | Tight head/neck and face details |
| Ears, Ear Accessory | ear | -35° / 0° | 0,87,0 | 66 | Three-quarter head/neck and face details |
| Undertop, Overtop | torso | 0° / 0° | 0,54,0 | 62 | Neck/chest/belly/pelvis, arms/hands, thighs, underwear; **no head/face** |
| Pants, Overpants, Underwear | lower_body | -5° / 0° | 0,36,0 | 54 | Pelvis, legs/feet, underwear |
| Shoes | feet | -20° / 12° | 0,29,3 | 54 | Calves/feet only |
| Gloves | hands | -8° / 0° | 0,55,0 | 84 | Torso/arms/hands/thighs, underwear; no head/face |
| Cape | rear_body | 165° / 0° | 0,61,0 | 110 | Rear-three-quarter neutral body/underwear |
| Body Characteristic, Skin Feature | body | -8° / 0° | 0,62,0 | 100 | Full neutral body and face/underwear |

Neutral world landmarks are from the pinned Player.blockymodel: head center y=88,
neck=75, chest=71, pelvis=51, hands≈45, thighs=39, feet≈27. Full skeleton traversal
continues to resolve attachment sockets even when context shapes are masked out.
The existing attachment transforms, UV mapping, representative gradient lookup,
lighting and depth-buffer rasterizer remain unchanged. Selected cosmetic geometry
is not filtered away by the neutral context mask.

Overtop and Undertop share the exact same front-facing rig: world y=23..85 and
x≈-19.14..19.14. Short tops, coats, straps, scarves and large tunics retain the same
neckline/waist landmarks. No bald head, eyes, eyebrows, mouth or ears are added to
clothing cards. Outer arm/garment edges can meet the native-style portrait crop;
unusually large hair/hat tips or long garments are not auto-fitted.

## Scope and color policy

All 590 entries were rebaked (584 distinct image hashes; none unavailable). Filename
keys, the three-column deterministic index contract, 184×298→92×149 pipeline and
all 590 UI patch references remain unchanged. UI files are byte-unchanged relative
to R151. The only Java change is the HUD revision constant. Search, scrolling,
selection, live preview, validation, fallback, draft/save/discard, persistence,
R149 stats, gear/inventory, voice and player restoration code were not changed.

R151 and R152 thumbnails use **baked representative native colors**, not the active
draft's selected gradient or variant. This is explicitly recorded in provenance.
No cosmetic×all-colors expansion or unverified runtime tinting was added. The live
appearance preview still represents the selected draft. This pass corrects framing.

## Provenance and verification

`provenance.json` now includes all rig definitions, category→rig mapping and all 590
entry→rig hashes, plus consumed installed asset hashes and renderer/library metadata.
Renderer source hashing normalizes to UTF-8/LF, avoiding Git/Windows newline drift.
Release validation checks the renderer source, same-category rig fingerprints,
installed sources, and every packaged PNG hash.

- Full `test.ps1 -SkipLive` suite: **PASS**, including the 8,100-case conversation
  matrix (zero stale commits, malformed executions or resource leaks), R149 S1,
  appearance authority, inventory/gear and voice isolation tests.
- `tools/test_appearance_category_rigs.py`: **4 tests PASS**. Coverage includes every
  category mapping, orthographic contracts, no clothing head/face context, actual
  renderer invariance when enormous distant geometry is added, all 590 image/rig
  hashes and dimensions, and all contact-sheet hashes/exact entry coverage.
- Two independent complete bakes matched all 590 PNG hashes, complete provenance,
  and all 40 category-sheet hashes. No system dependencies were installed.
- Built successfully against installed Hytale 0.6.3 SDK. Existing SDK deprecation/
  Unsafe warnings remain. `git diff --check` passed.
- Index SHA-256: `6C76158A10E6D02586B52F9B4CE26BDADFCF86A37435F5957EC0EC855D775C06`.
- Provenance SHA-256: `B399FE4183B8E10FCB9DF13D59D1BF22C27D60A6FE218393312B5B864DAD8FAD`.
- Contact-sheet index SHA-256: `A8958FDE623D1251C12987F34582F8474A15FFF9D645459D6A236C92252B5E6A`.

[Grouped visual inspection sheets](R152_CATEGORY_CONTACT_SHEETS/README.md) contain
every option; all 105 Overtop cards were visually compared with the native target.
Reference cards do not claim to be an exact reproduction of the native renderer's
animation/material pipeline. Actual in-game rendering at both resolutions remains
pending, not inferred from offline contact sheets or layout arithmetic.

Reproduce (use the existing bundled Python with Pillow/numpy):

```powershell
python tools/bake_appearance_thumbnails.py <installed-Assets.zip> src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcAppearance/Thumbnails --ui-index src/main/resources/Common/UI/Custom/Pages/ImmersiveNpcAppearanceThumbnails.ui --contact-dir docs/R152_CATEGORY_CONTACT_SHEETS
python tools/test_appearance_category_rigs.py
.\test.ps1 -SkipLive -ServerJar <installed-HytaleServer.jar>
.\build.ps1
```

## Deployment

Active JAR:
`C:\Users\Zemio\AppData\Roaming\Hytale\UserData\Saves\NPC\mods\ImmersiveNPCs-0.6.3-R152-NPC-APPEARANCE-CATEGORY-RIGS.jar`

- **8,451,441 bytes**, 590 packaged cards, exactly **one active project JAR**.
- Build/staging/deployed SHA-256:
  `DB6B87CFD9B49A813AC7E25C392AB9C13666B1990FF81C4616EDC3D93946F3F3`.
- HUD, manifest and build/installer artifact counters agree on R152.
- R151 preserved at
  `C:\HytaleRollback\NpcAuthoringStudio-Appearance-R151-2026-09-05\ImmersiveNPCs-0.6.3-R151-NPC-APPEARANCE-NATIVE-CARDS.jar`,
  SHA-256 `B7B328F2DFF926BB4BADF72435A59EE76F5764CE85B1E7F9E0A0EBE7C5F23A98`.
- Accepted R146 and retained R149/R150 rollback hashes unchanged. SkinSwap and
  HYTALEDEVLIB unchanged. Game and Java were stopped; broad installer was not run.
- Runtime profiles before/after: **76 files, 50,335,199 bytes**; sorted relative-path
  plus SHA-256 aggregate unchanged:
  `93DC68D4FEB07DA5EED0036FCA09B685C53672E974A253031DBE683EFF4DF58F`.
  No NPC data, recordings, world data, migration archive or distillation data changed.

## Connected review — 1920×1080 and 2560×1440

1. Confirm HUD **R152**, join, `/npc update Hoit`, open Appearance. No UI load errors.
2. Clothing → Overtop: scroll all options. Compare Puffy Jacket, Tartan, Bunny Hoody,
   Long Belted Jacket, short wraps and late-list tunics. Neck/waist/upper-thigh crop
   and scale must stay fixed, without a neutral head/face or camera zoom jumps.
3. Visit every category. Confirm head/face crops, ear three-quarter view, torso
   clothing, lower body, feet, gloves and rear capes. Check large hats/hair for
   acceptable edge cropping without different mannequin sizes between cards.
4. Search, clear, scroll to the last option, select, change color/variant, Randomize.
   Cards remain representative colors; live preview should follow the actual draft.
5. Discard/Back, then explicit Save → close/reopen → restart/reopen on the test NPC.
   Check stable appearance/identity and player skin/equipment/held-item restoration.
6. Smoke-test stats, gear/inventory, Profile Editor and non-destructive voice playback.
   Do not delete established NPC audio as a test.

**STOP for connected approval. No Profile Editor polish or further stage authorized.**
