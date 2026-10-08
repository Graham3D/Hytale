# R226-U7P5 — native Selector hit ownership repair

## Connected evidence

The active RPG log `2026-10-08_10-23-03_server.log` records two Unique Trork QA packs accepted at 14:25:20 and 14:25:35. At 14:25:25 and 14:25:43 the leaders' first native battleaxe hits reached `NativeEnemyActions.scope` without a matching accepted action snapshot. Hytale logged `Exception while ticking entity interactions! Removing!` and removed each leader and pack. The leader removal records have `defeated=false`, confirming that these were interaction failures, not ordinary kills. The second pack used `ME-007` (Lightning Enchanted); the weapon-visual owner had already reported that region unsupported while allowing gameplay to continue. The disappearance therefore does not depend on the new weapon tint.

## Narrow repair

The installed `0.7.0-pre.5.1` native `SelectInteraction` duplicates the attack context and calls `InteractionContext.fork` for each `HitEntity`. That native method copies the parent chain ID and gives the hit a non-null fork ID. The fork's initial root is the generated HitEntity root seen in the log, not the accepted battleaxe root. The previous `NativeEnemyAction.owns` required the executing fork object to remain reachable through the mutable parent `forkedChains`/`newForks` maps; the previous start-path cleanup independently inferred completion from a `Finished` state and visible fork list. Neither is an authoritative ownership test for a per-target Selector hit.

`NativeEnemyAction.owns` now matches a certified hit to an already-captured root using the server-issued negative chain ID and a non-null native fork ID. It still requires the exact actor and owner, action type, active nonsimulation entry, and the exact certified operation leaf. The original offense snapshot is still captured at `InteractionChainStartEvent`; no snapshot, damage calculation, proc, or affix is created at impact. `NativeEnemyActions.start` retains an accepted root while Hytale's own `InteractionManager.getChains()` still owns that exact root object. The missing-snapshot path remains fail-closed and now records bounded chain/owner/operation facts if a different mismatch remains.

## Verification and deployment

- Focused `NativeEnemyActionsStartTest` and `EnemyNativeOutgoingTest` passed. The new regression proves a detached native fork with the same accepted server chain ID matches, while another chain ID, zero ID, and an unrelated nonforked root do not.
- `compileJava`, native patch hash verification, CustomUI validation, spatial grant audit, and `verifyHyArpgJar` passed. No standalone server or client was launched.
- Active RPG save JAR: `C:/Users/Zemio/AppData/Roaming/Hytale/data/pre-release/Saves/RPG/mods/HyARPG.jar`.
- R226 deployed SHA-256: `334F1329DB79F6BB808A539CD0570A286468944BF2BA04926D0A1E1341364457` (matches validated build).
- R225 backup outside Saves: `C:/Users/Zemio/.codex/deployment-backups/R226-U7P5/HyARPG-before-R226-AE41BAFA.jar`, SHA-256 `AE41BAFAF49FEF02C397E7DA366C443806B208923518D96D88540465E3A195AB`.

Connected acceptance remains with the owner: restart the world, spawn `/rpg spawn Trork_Warrior unique normal`, allow it to land several melee hits, and confirm the pack remains. If `RPG_ENEMY_ACTION_SNAPSHOT_MISSING` recurs, its added fields identify the remaining guard dimension without broad tracing.
