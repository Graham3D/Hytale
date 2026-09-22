# R075 Lightning Spire QA Correction

## Outcome

R075 corrects the missing Lightning Spire emergence presentation, extends the READY phase to ten seconds, restores the intended all-player melee charge interaction, and makes every major presentation transition visible in detailed skill traces. The deployed artifact is `Hywind 0.1.0-merge.30`.

## Trace cross-check

The latest connected trace proved a healthy two-second deployment and a five-second READY lifetime, but contained no accepted Spire melee-hit events. Consequently, no gauge, per-hit Shock model, or shockwave could have been triggered during that recording. The trace did contain a successful `LIGHTNING_SPIRE_EMERGENCE` presentation event for `Debug_Ability_Ground_Crack_Parts`; inspection of the installed Hytale assets proved that particle-system ID does not exist. The old helper treated a non-throwing spawn request as success and therefore produced a false-positive trace while the client rendered nothing.

## Corrections

- Added and packaged `Hywind_Lightning_Spire_Emergence`, composed from Hytale's native ground-hit crack and smoke spawners. Its two-second crack lifetime covers the full emergence window.
- Added startup-fatal resolution validation for that exact particle-system ID. R075 cannot report a successful server boot if the asset is missing.
- All authored particle presentation calls now validate the particle system before spawning and emit an explicit `_FAILED` trace event when resolution fails.
- READY lifetime is now ten seconds after the unchanged two-second emergence phase.
- Added a base-pivot procedural emergence wobble so the base stays planted and the narrow top carries most of the visible shake. The existing hit spring uses the same bottom-heavy pivot behavior.
- Every native player melee witness can charge the Spire, including the caster and other players. These attacks remain zero-damage. Non-player hostile damage still reaches native health and can destroy the Spire.
- Accepted hits continue to play Metal Break, spawn the animated three-plane Shock carrier at the top prism, and update the authored gauge in ten-percent increments.
- Every tenth accepted hit still queues the six-block animated hemisphere shockwave, Portal Open sound, heavy Lightning damage, two Electrified stacks, and `Laser_Impact` on each actually damaged target.
- Detailed traces now identify `LIGHTNING_SPIRE_READY`, `LIGHTNING_SPIRE_FRIENDLY_HIT` with gauge percentage and Shock model, and `LIGHTNING_SPIRE_SHOCKWAVE` with model, radius, wave sequence, and impact particle.

## Automated validation

- Focused Lightning Spire runtime, lifecycle, and asset-contract tests: PASS.
- Full clean verification before the startup assertion: PASS (2,389 RPG tests plus retained module gates).
- One unrelated timing-sensitive trace-archive test failed once during an incremental rerun and passed immediately in isolation.
- Final JAR verification: PASS; 5,929 entries, 2,228 classes, 2,090 CustomUI documents, and all authored icon hashes verified.
- Isolated native server smoke: PASS; startup logged `RPG_LIGHTNING_SPIRE_ASSETS ... result=PASS` for the exact emergence asset.
- Final deployed restart validation: PASS twice with the exact installed hash, no legacy first-party plugin discovery, no scoped failures, and all persistent data roots retained.

## Deployment

- Installed JAR: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\Hywind.jar`
- SHA-256: `AD63291A6FB790E2F803547981AD00786451580669911B6A979AB099C7E9E038`
- Rollback snapshot: `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale-rollback\hywind-deploy-20260922T010809Z`

## Connected-client verification remaining

Cast Lightning Spire and verify the visible ground crack/smoke persists through the two-second rise, the top visibly wobbles while the base remains planted, and READY lasts ten seconds. After READY, strike it with the caster and another player: every accepted hit should show Metal Break, top-prism Shock, and the increasing gauge without lowering Spire health. The tenth hit should display the expanding shockwave and damage hostile targets with `Laser_Impact`. A detailed trace should contain the new READY, friendly-hit, and shockwave events. Enemy damage and natural expiry should still destroy/despawn the Spire normally.
