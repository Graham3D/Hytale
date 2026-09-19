# R037 — Fireball Charge, Ballistic Preview, and Fire-Trail Correction

Date: 2026-09-14 (America/New_York)  
Branch: `RPG`  
Source checkpoint: `25b85cb` plus the cumulative working-tree implementation  
Connected baseline: R036  
Release state: **IMPLEMENTED / PACKAGED / DEPLOYED / CONNECTED-VERIFIED: NO**

## Scope and authority

R037 implements the requested Fireball hold/release lifecycle, four earned charge stages, progressive movement penalty, a caster-only ballistic preview and 3.5 m endpoint ring, and RPG-local removal of the unwanted Fire Bolt and Fireball line trails. It does not replace RPG authority for Mana, cooldown, damage, Burn consumption, passive continuation, projectile collision, or explosion victim deduplication.

No native Hytale asset was edited globally. The installed build audited for this revision was Hytale `0.7.0-pre.2`.

## Implementation

### Native input lifecycle

- `RPG_Ability_Fireball` now resolves `Root_RPG_Fireball_Charge` instead of the immediate generic bridge.
- The root uses the registered `RPG_FireballCharge` interaction, built on Hytale's native `ChargingInteraction`.
- `AllowIndefiniteHold=true` permits holding after the four-second cap.
- `RPG_ActivateSkill` is the release callback. Key-down/hold updates charge state; it does not launch or pay for the skill.
- Release creates the normal `SkillExecutionRequest` with the snapshotted `fireballChargeStage`. Existing commit logic then performs final validation, charges Mana once, starts cooldown once, and dispatches one root Fireball.
- Terminal actor/runtime cleanup removes the stage movement effect and presentation state. A future charge replaces any stale bounded per-actor input entry.

The exact server-smoke audit originally failed because Hytale compiles branched interactions into `LabelOperation`/`JumpOperation` wrappers. The final audit unwraps `Operation.NestedOperation`, requires exactly one `NativeFireballChargeInteraction` and one `NativeSkillActivationInteraction`, permits only compiler-generated jumps besides those interactions, and verifies client wait/remote synchronization. This correction prevents the audit itself from throwing during asset-load dispatch.

### Charge math

| Stage | Held time | Damage multiplier | Level-1 coefficient | With owned Burn | Movement multiplier |
|---:|---|---:|---:|---:|---:|
| 0 | `[0,1)` s | 1.00 | 0.900 | 1.1250 | 1.00 |
| 1 | `[1,2)` s | 1.10 | 0.990 | 1.2375 | 0.90 |
| 2 | `[2,3)` s | 1.20 | 1.080 | 1.3500 | 0.80 |
| 3 | `[3,4)` s | 1.30 | 1.170 | 1.4625 | 0.70 |
| 4 | `>=4` s | 1.40 | 1.260 | 1.5750 | 0.60 |

The charge multiplier is added once to the root snapshot's multiplicative modifier bucket. Descendants inherit that snapshot; no continuation independently recharges. Burn remains a separate 1.25 multiplier and keeps its prior ownership and consume-after-accepted-damage contract.

Movement penalties use short, refreshed RPG-owned native entity effects (`RPG_Fireball_Charge_Slow_1` through `_4`) rather than changing base movement values. Stage 0 has no slow effect.

### Ballistic preview

`FireballTrajectory` is the shared pure solver used by both preview and launch. It preserves the R036 authored values:

- horizontal target range: 22 m;
- horizontal speed basis: 16 m/s;
- flight time: horizontal distance / 16, clamped to 0.65–1.40 s;
- downward acceleration: 12 m/s²;
- unchanged projectile collision radius and 3.5 m explosion radius.

The preview:

- recomputes at 10 Hz while held;
- samples a bounded 17-point trajectory;
- sweeps each segment through the same world-clear query contract and binary-refines the first blocked segment;
- sends short-lived `RPG_Fireball_Aim_Point` particles only to the caster;
- draws a bounded 16-point ring of radius 3.5 m at the resolved endpoint, offset 0.08 m upward;
- emits aggregate preview telemetry at no more than 2 Hz;
- creates no collision entity and schedules no damage.

The ring is currently horizontal for both ground and wall endpoints because the pinned packet path exposes a position/direction but not a reliable collision normal from the shared query. Connected wall readability remains a QA item.

### Native Fire charge audit

`Fireball_Charge_To_4`

- Exact asset: `Server/Particles/Combat/Fire_Stick/Fireball_Charge_To_4.particlesystem`.
- It contains staged child-spawner delays at approximately 0.0, 0.5, 1.0, and 1.5 seconds with approximately one-second child lifetimes.
- Shipped Flame Staff content attaches it to `PrimaryItem/Origin_Projectile` at scale 0.2.
- It is presentation-only: it does not spawn an authoritative projectile, apply impact, spend resources, or deal damage.
- It was adopted as one continuous native attached charge presentation. Gameplay stage timing remains server-clock authoritative and does not depend on particle frames.
- Its stock visual sequence does not naturally provide a persistent four-second stage display, so connected QA must confirm acceptable behavior during long Stage-4 holds.

`Fire_Charged1` through `Fire_Charged4`

- These are delayed static projectile-like bursts, with observed authored delays near 2.2, 4.2, 6.0, and 8.3 seconds.
- They are not persistent one-through-four charge-stage indicators and were not adopted.

Native Flame Staff roots

- Shipped charged roots 0–3 mutate native essence/durability or MagicCharges and then dispatch native projectile configs.
- `Weapon_Stick_Fire_Shoot_Base` confirms native Charging interaction conventions.
- Those roots informed input/presentation structure only. They were not reused for gameplay because doing so would create a second resource/damage/projectile authority.

### Trail root cause and correction

- Fire Bolt's stock `Fire_Projectile` contains the explicit child `Fire_Projectile_Fire_Trail`. R037 supplies `RPG_Fire_Projectile_No_Line`, which retains the shipped core/spark child assets but omits only that trail child.
- Fireball's stock `Fire_Charge1` contains long-lived static core/spark particles. When their carrier moves, retained particles paint the observed linear wake. R037 supplies `RPG_Fireball_Orb_No_Line`, reusing the shipped fire sprites with short bounded lifetimes.
- The skill catalog continues to identify the authored visual intentions as `Fire_Projectile` and `Fire_Charge1`; only RPG projectile model instances select the derivative no-line systems.
- Impact assets, projectile velocity, ballistic arc, collision, damage, range, and unrelated native projectiles are unchanged.

## Important affected files

- `src/main/java/com/inigmasgames/hytalerpg/execution/projectile/FireballCharge.java`
- `src/main/java/com/inigmasgames/hytalerpg/execution/projectile/FireballTrajectory.java`
- `src/main/java/com/inigmasgames/hytalerpg/input/NativeFireballChargeInteraction.java`
- `src/main/java/com/inigmasgames/hytalerpg/input/HytaleAbilitySkillInputAdapter.java`
- `src/main/java/com/inigmasgames/hytalerpg/input/NativeAbilityBridgeAudit.java`
- `src/main/java/com/inigmasgames/hytalerpg/execution/SkillExecutionRequest.java`
- `src/main/java/com/inigmasgames/hytalerpg/execution/SkillExecutionService.java`
- `src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSkillExecutionSystem.java`
- `src/main/resources/Server/Item/RootInteractions/RPG/Root_RPG_Fireball_Charge.json`
- `src/main/resources/Server/Item/Items/RPG/Abilities/RPG_Ability_Fireball.json`
- `src/main/resources/Server/Entity/Effects/RPG/RPG_Fireball_Charge_Slow_1.json` through `_4.json`
- `src/main/resources/Server/Particles/RPG/RPG_Fireball_Aim_Point.particlesystem`
- `src/main/resources/Server/Particles/RPG/RPG_Fire_Projectile_No_Line.particlesystem`
- `src/main/resources/Server/Particles/RPG/RPG_Fireball_Orb_No_Line.particlesystem`
- `src/main/resources/Server/Models/Projectiles/RPG_Fire_Bolt.json`
- `src/main/resources/Server/Models/Projectiles/RPG_Fireball.json`
- `src/test/java/com/inigmasgames/hytalerpg/Stage13FireballChargeR037Test.java`
- `src/test/java/com/inigmasgames/hytalerpg/R024NativeInteractionAssetTest.java`
- `tools/Run-Stage13CohortSmoke.ps1`

## Validation evidence

Focused validation:

- `:test --tests Stage13FireballChargeR037Test --tests R024NativeInteractionAssetTest`: PASS.

Final retained validation:

- `gradlew clean check jar --no-daemon`: PASS.
- Tests: 2,415; failures: 0; errors: 0; skipped: 0.
- CustomUI validation: PASS.
- Native-control tests: PASS.
- CanvasUI build/tests: PASS.
- Icon synchronization: all nine supplied skill icons already current; no JAR mutation required.

Exact three-mod isolated smoke (`cohort-r037-final2`): PASS.

- exact mod count: 3;
- R037 discovered/setup/ready: PASS;
- generic ability root: PASS;
- compiled Fireball charging root: PASS;
- no native ability asset rejection;
- native projectile audit: PASS;
- all retained Stage 04–13 startup gates: PASS;
- clean server shutdown; process exit 0.

Smoke evidence: `evidence/stage-13/cohort-r037-final2/server-smoke-summary.json`.

The smoke proves server-side asset resolution and integration structure. It does not prove connected client input delivery, VFX, animation, movement feel, preview visibility, or impact accuracy.

## Packaging, deployment, and rollback

Candidate and deployed JAR SHA-256:

`6552ACFF459BDC9CC9191766DCAEDFA9217282A7744EB400847460F62CBE9D2F`

Deployed path:

`C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar`

The post-copy hash exactly matches the smoke-tested candidate.

Three-mod archive:

`evidence/stage-13/revision-r037/HyARPG-R037-three-mod-test.zip`

Archive SHA-256:

`C08E9883F820065EC7FD8E4E53E5639B5802743CCD2A01B64CEB76320AE245C1`

Archive members are exactly:

- `CanvasUI-0.1.0.jar`
- `HYTALEDEVLIB-0.5.0.jar`
- `HyARPG.jar`

Rollback backup:

`evidence/stage-13/revision-r037/live-backup-20260914-225036`

It contains the prior live `HyARPG.jar` (SHA-256 `170630B63204722F201D4AB9088AB8FA975F1D0034182323BAE820399B6EFD29`) and the matching RPG mod-data directory (32 files at capture). No live save data was deleted or rewritten by deployment.

## Connected QA checklist

1. Start the normal `RPG` world and verify the top-right revision is `R037`.
2. Equip Fire Bolt and Fireball in `/rpg skilltree`.
3. Cast Fire Bolt across open ground. Confirm its fire body and impact remain, but the long straight wake is gone.
4. Tap Fireball and release in under one second. Confirm one Stage-0 Fireball launches on release, follows the approved ballistic arc, explodes in the same 3.5 m area, costs 10 base Mana, and starts the one-second base cooldown.
5. Hold Fireball without releasing. Confirm the native hand/staff charge effect starts, the curved caster-only preview appears, and its endpoint ring tracks camera and player movement.
6. Release and confirm the actual impact is close to the predicted endpoint.
7. Aim at a wall. Confirm the preview stops at the wall and the Fireball impacts there.
8. Hold for approximately 0.5, 1, 2, 3, 4, and 8 seconds on separate casts. Confirm damage caps after Stage 4 and movement feels 100%, 90%, 80%, 70%, and 60% of normal across stages 0–4.
9. After each release, confirm movement immediately returns to normal and there is no lingering preview/ring.
10. Apply your own Burn with Fire Bolt, then hit with a charged Fireball. Confirm the charge increase and existing 25% Burn-consume bonus both apply, and the owned Burn is consumed once.
11. Test cancellation by beginning a charge and then switching/unequipping the skill; if practical also test death/logout. Confirm no projectile, Mana payment, cooldown, stuck slow, or orphan preview remains.
12. Review `mods\InigmasGames_HytaleRPGPhase00Audit\logs\rpg\skill-trace.jsonl` for `FIREBALL_CHARGE_START`, `FIREBALL_CHARGE_STAGE`, `FIREBALL_AIM_PREVIEW`, `FIREBALL_CHARGE_RELEASE`, and any `FIREBALL_CHARGE_CANCEL` generated by cancellation tests.

## Remaining connected limitations

- All client-visible and input-sensitive acceptance items are **UNVERIFIED** until the owner completes the checklist.
- The native stock charge particle sequence may finish its authored progression before a long Stage-4 hold; gameplay still remains capped and authoritative.
- Wall endpoint rings are emitted in a horizontal plane because a stable native collision normal was not exposed by the selected query path.
- Optional native per-stage charge sounds were not bound; no unverified sound IDs were invented.
- Key-down begins bounded presentation from the native charge interaction; final equipment/resource/cooldown validation and all mutations remain at release commit. Connected QA should confirm invalid equipment produces no harmful side effects.

No source push was performed because it was not authorized.
