# R073 Lightning Spire Audio and Emergence

## Outcome

R073 extends Lightning Spire's emergence to two seconds and adds positional native audio for its full lifecycle. The deployed artifact is `Hywind 0.1.0-merge.28`.

## Implemented contract

- The Spire now remains emerging, invulnerable, and unable to accept charge hits for two seconds before entering READY for its existing five-second window.
- Emergence begins with native `SFX_Ability_Ground_Slam_Wave`, bounded to the two-second emergence effect.
- Native `SFX_Deployable_Totem_Slowing_Spawn` plays once at 0.9 seconds into emergence.
- READY starts the native `SFX_Portal_Void` entity effect, which uses the requested `Common/Sounds/Items/Portal/Portal_Void_LOOP.ogg` layer and is removed with the Spire.
- Every accepted player melee hit plays positional `SFX_Metal_Break`.
- Every completed ten-hit shockwave plays positional `SFX_Portal_Neutral_Open`.
- Expiry, destruction, replacement, owner cleanup, and cancellation play positional `SFX_Deployable_Totem_Slowing_Despawn` before deterministic removal.

## Automated validation

- Focused Lightning Spire runtime and asset-contract tests: PASS.
- `gradlew clean check`: PASS.
- JAR verification: PASS; 5,924 entries, 2,225 classes, 2,090 CustomUI documents, and all authored icon hashes verified.
- Isolated native server smoke: PASS with the exact deployed candidate.
- Final deployed restart validation: PASS twice with no legacy first-party plugin discovery or scoped failures.
- Persistent roots retained: `ImmersiveNPCs`, `InigmasGames_CanvasUI`, `InigmasGames_HytaleRPGPhase00Audit`, and `InigmasGames_Taverns`.

## Deployment

- Installed JAR: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\Hywind.jar`
- SHA-256: `5DD2347541CC3D80F38B05F5E7F9A5F315CAC0ECFC7ACE616C9F85BAADE79F37`
- Rollback snapshot: `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260922T001052Z`

## Connected-client verification remaining

Cast Lightning Spire and verify the Ground Slam cue at emergence start, Totem Spawn cue at 0.9 seconds, READY/attackability at two seconds, bounded positional idle loop, Metal Break on accepted player hits, Portal Open on every shockwave, and Totem Despawn on expiry or destruction. Confirm that replacing or closing a Spire leaves no orphaned idle audio. This is the only remaining acceptance boundary.
