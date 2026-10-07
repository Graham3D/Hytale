# R205-U7P5 — QA native encounter profile selection

## Connected report

The active RPG save's `2026-10-04_16-33-20_server.log` records the exact command `/rpg spawn Larva_Void unique stoneskin manaburn reflective`, accepted aliases `[ME-004, ME-013, ME-026]`, and rejection `QA_NATIVE_PROFILE_UNAVAILABLE:Larva_Void`. Thus parsing and native model clearance succeeded. The R204 log did not record which profile admission predicate failed. In particular, it did not print the native biome key or staged-role check. The prior ordinary-role QA classifier depended on the natural-spawn `EnemyRewardRegistry.classify` exact biome lookup despite the command's explicit QA origin.

## R205 repair

`classifyStagedQa` now resolves the same existing authored encounter profile owner directly for ordinary QA roles. It uses the exact catalogued native biome when available, another authored biome in the same native zone when the specific biome is unlisted, and the existing `Default/Zone1_Spawn/Plains_Spawn` authored profile when no native zone is available. This last case provides deterministic QA-only stats on unsupported/custom generators; it does not author a new monster baseline. Campaign Golems retain their separate authored `campaign/golem` profile. Natural spawn classification remains exact and unchanged. All QA rewards remain zero.

The QA classifier also logs `RPG_ENEMY_QA_PROFILE_REJECTED` with staged-role/world or authored-profile reasons and `RPG_ENEMY_QA_PROFILE_ZONE_FALLBACK` when it uses a different existing biome. This will identify a different remaining cause without a general trace mode.

## Offline checks and deployment

- `:test --tests com.inigmasgames.hytalerpg.execution.hytale.EnemyQaAuthoredBiomeTest --tests com.inigmasgames.hytalerpg.enemies.EnemyQaBirthPlannerTest :jar --no-daemon` passed. The new test covers exact biome, same-zone fallback, authored spawn-zone fallback, and existing Larva profile resolution in Normal, Nightmare, and Hell. The build also passed `auditNativeProcAssets` and `validateCustomUi` (88 documents).
- The packaged manifest reports `0.2.0-R205-U7P5` and `RpgRevision=R205-U7P5`. No standalone server was launched; connected acceptance is pending owner QA.
- R204 recovery JAR: `evidence/r205-qa/HyARPG-R204-U7P5.jar`, SHA-256 `D7F873F3269C078DA1B8031ADF7CF8C80DBEEC7EC13150FDE2DDC697A051F07F`, outside Saves.
- Sole active RPG save mod: `Hytale/data/pre-release/Saves/RPG/mods/HyARPG.jar`, SHA-256 `D68155E8602FFC3C5111B880E787C093446C2EADCB4372F7A45CADAEB4578EFD`, modified 2026-10-04 16:50:22 America/New_York. Built and installed checksums match.

Owner QA: restart the world, run `/rpg spawn Larva_Void unique stoneskin manaburn reflective`, then inspect the spawned enemy with `/rpg enemies inspect`. If the command still rejects, the new `RPG_ENEMY_QA_PROFILE_REJECTED` log line names the admission gate.
