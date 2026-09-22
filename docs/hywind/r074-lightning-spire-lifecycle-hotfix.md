# R074 Lightning Spire Lifecycle Hotfix

## Outcome

R074 fixes Lightning Spire terminating in the same native tick in which it was deployed. The deployed artifact is `Hywind 0.1.0-merge.29`.

## Root cause and correction

The connected trace recorded `LIGHTNING_SPIRE_EMERGENCE`, `LIGHTNING_SPIRE_DEPLOYED`, and `LIGHTNING_SPIRE_DESTROYED` at the same timestamp. Deployment queued the native carrier through Hytale's `CommandBuffer`, but the immediate lifecycle check read its health from the underlying `Store`. Before command-buffer materialization, that lookup returned no health map and the code incorrectly classified the carrier as destroyed, producing only the destruction explosion.

- Native health is now read through the active `CommandBuffer`, which can see queued entity state.
- A phase-aware lifecycle gate reports an unobserved carrier as `MATERIALIZING` during emergence rather than dead.
- Once valid positive health has been observed, missing or depleted health is still treated as genuine destruction.
- A carrier that never materializes is rejected when the Spire reaches READY, preventing an immortal or invisible construct.
- Destruction traces now include phase, native state, reference validity, health-map presence, current/minimum health, and whether a healthy carrier had previously been observed.
- R073's two-second emergence and complete positional audio lifecycle remain unchanged.

## Automated validation

- Same-tick command-buffer materialization regression test: PASS.
- Observed removal, zero-health destruction, and READY-without-materialization tests: PASS.
- Lightning Spire runtime and asset-contract tests: PASS.
- `gradlew clean check`: PASS.
- JAR verification: PASS; 5,927 entries, 2,228 classes, 2,090 CustomUI documents, and all authored icon hashes verified.
- Isolated native server smoke: PASS with the exact deployed candidate.
- Final deployed restart validation: PASS twice with no legacy first-party plugin discovery or scoped failures.

## Deployment

- Installed JAR: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\Hywind.jar`
- SHA-256: `7E0D53D7598C660EBEA38F713445A9AEF13C46926E0D0CB70C5432EA50F95B6B`
- Rollback snapshot: `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260922T002703Z`

## Connected-client verification remaining

Cast Lightning Spire and confirm that the authored carrier rises for two seconds instead of immediately exploding, then becomes attackable and remains READY for five seconds. Verify its R073 spawn, idle, hit, shockwave, and despawn audio cues. If any termination occurs, the new trace fields identify the exact native health state. This is the only remaining acceptance boundary.
