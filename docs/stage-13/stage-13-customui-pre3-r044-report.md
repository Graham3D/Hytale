# R044 — pre.3.1 CustomUI schema compatibility correction

Date: 2026-09-19  
Branch: `RPG`  
Status: implemented, packaged, deployed, isolated-smoke verified; connected join proof required.

## Connected failure

R043 passed RPG setup, but the client disconnected during `GameLoading` with:

```text
Failed to load CustomUI documents
Failed to parse file Pages/ProfileInventory/GridCommon.ui (15:20)
Could not find field CursedIconPatch in type ItemGridStyle
```

The document was owned by the installed `ImmersiveNPCs-0.6.3-R170-CREATIVE-FULL-PROFILE-GENERATION.jar`, not by HyARPG or CanvasUI. Hytale `0.7.0-pre.3.1` replaced the former `ItemGridStyle` members `CursedIconPatch` and `CursedIconAnchor` with `EphemeralIconPatch` and `EphemeralIconAnchor`. The installed native `Client/Data/Game/Interface/InGame/Common.ui` confirms the replacement names.

## Correction

- Advanced the paired RPG/Canvas revision counter to `R044`.
- Corrected all three affected ImmersiveNPC UI documents:
  - `Common/UI/Custom/Pages/ProfileInventory/GridCommon.ui`
  - `Common/UI/Custom/Pages/NativeInventoryProbe/GridCommon.ui`
  - `Common/UI/Custom/Pages/ImmersiveNpcProfile.ui`
- Preserved the existing `CursedSpiral.png` texture and anchor geometry; only the pre.3.1 field names changed.
- Added a reproducible compatibility repack tool that streams every JAR entry into a new archive and fails unless the obsolete fields are removed.
- Updated the corresponding ImmersiveNPC source resources so a later plugin rebuild does not reintroduce the incompatibility.

The R170 compatibility candidate contains exactly 3,939 entries, matching the original. Entry-level SHA-256 comparison proves only the three UI documents above changed. No Java class, NPC data, voice data, profile, world data, or RPG gameplay asset changed.

## Validation

- Complete retained RPG/native/CanvasUI suite: PASS, 2,453 tests.
  - RPG: 2,358
  - native control: 67
  - CanvasUI: 28
- CustomUI structural validator: PASS.
- CanvasUI pre.3.1 startup smoke: PASS.
- Exact isolated three-mod R044 smoke: PASS with clean shutdown and all retained Stage 13 gates.
- Corrected R170 JAR entry audit: PASS; 3 changed UI entries, 3,936 byte-identical entries.
- Obsolete `CursedIconPatch`/`CursedIconAnchor` fields remaining in corrected JAR: zero.

## Candidate hashes

- `HyARPG.jar`: `087B2C4E1AE0EC5489475630E75A513308D27191C922C00240C2420099347B30`
- `CanvasUI-0.1.0.jar`: `AEBDD23B470CFFB1C0C18CC07BACAE91CF9E2B7F9F42C397F2C7D1316D98385E`
- corrected `ImmersiveNPCs-0.6.3-R170-CREATIVE-FULL-PROFILE-GENERATION.jar`: `ED8BFDD6006B685F52FE1A466BBE6FAE98DA08E1E1BBFD826626EB9C8C2ED1BE`

## Connected gate

Launch the normal RPG save and confirm the client proceeds beyond `GameLoading`, the top-right revision reads `R044`, and no `Could not find field CursedIconPatch` error appears. Headless server smoke cannot prove client-side CustomUI parsing.

## Deployment evidence

- Full pre-deployment save/mod-data backup: `evidence/canvas-ui/cursor-hud/R044/final/before/save/20260919T134521Z/RPG`.
- Three-mod rollback archive SHA-256: `C5E1E5F3499A34E70547870631FF19998E6860056380112B5740C06E7DA946C2`.
- Corrected R170 artifact retained at `evidence/canvas-ui/cursor-hud/R044/final/artifacts/ImmersiveNPCs-0.6.3-R170-CREATIVE-FULL-PROFILE-GENERATION.jar`.
- Built, retained-artifact, and live R170 hashes match: `ED8BFDD6006B685F52FE1A466BBE6FAE98DA08E1E1BBFD826626EB9C8C2ED1BE`.
- Built and live HyARPG hashes match: `087B2C4E1AE0EC5489475630E75A513308D27191C922C00240C2420099347B30`.
- Built and live CanvasUI hashes match: `AEBDD23B470CFFB1C0C18CC07BACAE91CF9E2B7F9F42C397F2C7D1316D98385E`.
