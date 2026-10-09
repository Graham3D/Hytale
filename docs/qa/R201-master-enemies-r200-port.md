# R201-U7P5 — Master Enemies port onto exact R200 HyARPG

## Authoritative baseline

- Active-save `mods/HyARPG.jar` identified as `0.2.0-R200-U7P5`, SHA-256 `12F2E0DA1E2DF5C8794741A8F0DCBC03C0F526A0B31A731D97DB548007B9EEDA`.
- This checkout's pre-port `build/libs/HyARPG.jar` matched those bytes. The old Hywind checkout was used only as a donor for the later monster implementation. No repository rollback or save rewrite occurred.
- R200 inventory footprint catalog revision 3 and all 30 existing source inventory UI/data resources were compared with the supplied JAR and remain byte-identical.

## R201 integration

- Ported the Master Enemies v1.1 birth, pack, affix, combat, status, resistance, protection, reward, reflection, QA command, diagnostics, and UI owners into `HyArpgPlugin`.
- Ported monster level progression and its D01 Defense/elemental floor projection, plus damage-triggered, viewer-local native enemy health bars.
- Preserved R200 multi-pick loot profiles. Master Enemies quantity now appends durable `loot-quantity` children after finalized `loot-picks` results; R200 party allocation, Magic Find quality, item generator, and inventory custody remain the owners.
- Routed frozen Master Enemies learning context through R200's existing `LearningSources.Opportunity` pity owner. Routed Defense break through R200's existing `GearDefenseEffects` projection.
- Added native role adapters only for the five pinned Master Enemies assets. The original/patched server SHA-256 pair remains version pinned; no unrelated native server methods were patched.
- Retained the optional ImmersiveNPCs integration boundary and the R200 HyARPG/Tavern manifest ownership.

## Offline verification and connected status

- `./gradlew.bat :check --offline --console=plain` passed on 2026-10-04 (root tests, 441 native fixtures, CanvasUI, Tavern, package identity, spatial-grant audit, 4,578 JSON assets, 40,440 asset references). The focused R200 multi-pick/ME-quantity replay test also passed. Native server launch was not used.
- No standalone Hytale server was started. Connected gameplay acceptance is pending the owner's main-game test.

## Deployment record

- Built `0.2.0-R201-U7P5` as `build/libs/HyARPG.jar`, SHA-256 `3038B8919AEC602A01F06C2244AADA4C12ECF0B054504AA82277F9AAB994106F`.
- Backed up the active R200 JAR outside Saves at `evidence/r201-port/HyARPG-R200-U7P5.jar`, SHA-256 `12F2E0DA1E2DF5C8794741A8F0DCBC03C0F526A0B31A731D97DB548007B9EEDA`.
- Deployed to `C:/Users/Zemio/AppData/Roaming/Hytale/data/pre-release/Saves/RPG/mods/HyARPG.jar` on 2026-10-04 at 15:08 EDT. Deployed SHA-256 verified as `3038B8919AEC602A01F06C2244AADA4C12ECF0B054504AA82277F9AAB994106F`; file modified time was set to the deployment time so the active artifact is unambiguous. The R200 recovery copy remains outside Saves.
