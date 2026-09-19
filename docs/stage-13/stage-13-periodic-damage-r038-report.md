# R038 — Lethal Periodic-Damage ECS Safety Correction

Date: 2026-09-14 (America/New_York)  
Branch: `RPG`  
Source checkpoint: `25b85cb` plus the cumulative working-tree implementation  
Connected baseline: R036  
Release state: **IMPLEMENTED / PACKAGED / DEPLOYED / CONNECTED-VERIFIED: NO**

## Scope

R038 corrects the world-ending failure observed while testing Fire Bolt with Volley, Fork, and Chain. It does not change projectile continuation, damage formulas, Burn magnitude/duration, skill costs, cooldowns, targeting, movement, persistence, HUD, or player coordinates.

## Connected evidence and root cause

The affected server log is:

`C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\logs\2026-09-14_22-27-44_server.log`

At 2026-09-15 02:30:02 UTC, the sixth Burn tick for root `input-23-e336e0f9` reduced target `7195...` from 1 Health to 0. The matching skill trace reached native damage application and then terminated the status with `NATIVE_PERIODIC_FAILURE_IllegalStateException`.

The server stack is decisive:

`PeriodicStatusRuntime.tick -> HytaleSkillExecutionSystem.periodicPort -> Port.damage -> HytaleDamageAdapter.applyResolved -> DamageSystems.executeDamage -> DeathComponent.tryAddComponent -> Store.addComponent -> Store.assertWriteProcessing`

The exception was `IllegalStateException: Store is currently processing! Ensure you aren't calling a store method from a system.`

The world subsequently stopped. Hytale removed the player from `zone3_taiga1_world`, added the player to fallback world `default`, and reused the old position `(1482,101,-2480)`. That world/coordinate mismatch produced the apparent Abyssal/black-void teleport. No RPG teleport event occurred.

Volley/Fork/Chain increased the number of Burn sources and made the bug easier to encounter, but none of those passives moved the player.

## Correction

`HytaleSkillExecutionSystem` now propagates the current `CommandBuffer<EntityStore>` from its owning tick system into `PeriodicStatusRuntime` and the periodic `Port`.

Every native damage dispatch owned by that shared execution port now selects:

- the current `CommandBuffer` while running inside an ECS system;
- the `Store` only for a caller outside ECS processing.

This preserves the existing damage lifecycle and native death authority. It changes only the structural-write transport. A lethal hit can still add `DeathComponent`, but Hytale queues that structural mutation through its supported command-buffer path instead of attempting a forbidden direct Store mutation during processing.

The pinned Hytale 0.7.0-pre.2 bytecode was re-audited. `DamageSystems.executeDamage(Ref, CommandBuffer, Damage)` delegates to `CommandBuffer.invoke`, whereas the generic `ComponentAccessor` overload invokes through whichever accessor the caller supplies. R038 deliberately supplies the active command buffer in system context.

## Regression coverage

`Stage13PeriodicDamageEcsSafetyR038Test` proves:

- the installed Hytale build exposes the native command-buffer damage overload;
- the player tick passes its command buffer into periodic status processing;
- the periodic port retains that buffer;
- observed and already-resolved native damage use the buffer-aware accessor.

Existing `Stage06PeriodicStatusTest` continues to prove deterministic tick integration, final fractional ticks, bounded catch-up, source isolation, poison caps, and no replay after a native failure.

Focused validation passed. The first full-suite attempt completed 2,350 regular tests with seven failures solely because Hytale was launched during the run; all seven were the icon updater's intentional `Close Hytale and its server completely` process-safety gate. No product assertion failed in that attempt.

After Hytale closed, the clean retained validation completed successfully:

- `gradlew clean check jar --no-daemon`: PASS;
- regular tests: 2,350 passed, zero failures/errors/skips;
- native-control tests: 67 passed, zero failures/errors/skips;
- CustomUI validation: PASS;
- CanvasUI compile/check: PASS;
- nine supplied skill icons synchronized into 13 expected JAR entries;
- exact three-mod isolated smoke: PASS;
- native projectile construction/rollback audit: PASS;
- server startup and clean shutdown: PASS.

Smoke evidence is stored at `evidence/stage-13/cohort-r038-final/server-smoke-summary.json` and `native-spawn-integration.json`. The smoke tested the exact deployed candidate hash.

## Files changed for R038

- `gradle.properties`
- `src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSkillExecutionSystem.java`
- `src/test/java/com/inigmasgames/hytalerpg/Stage13PeriodicDamageEcsSafetyR038Test.java`
- `tools/Run-Stage13CohortSmoke.ps1`
- this report

## Packaging, deployment, and rollback

Candidate and deployed JAR SHA-256:

`9450A6585EEA75B38027A7D23FCB989E2F35D7256F94348D563AC7815423B858`

Deployed path:

`C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar`

The post-copy hash exactly matches the icon-synchronized, smoke-tested candidate. The deployed JAR contains `rpg.revision=R038`, `rpg.version=0.0.25`, `rpg.stage=13`, and `hytale.version=0.7.0-pre.2`.

Three-mod archive:

`evidence/stage-13/revision-r038/HyARPG-R038-three-mod-test.zip`

Archive SHA-256:

`3C83E505AC04B209E67A388B933852F83CC22154FE88A7F1DE8F2043D23EF4F4`

Archive members are exactly:

- `CanvasUI-0.1.0.jar` — `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6`;
- `HYTALEDEVLIB-0.5.0.jar` — `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230`;
- `HyARPG.jar` — `9450A6585EEA75B38027A7D23FCB989E2F35D7256F94348D563AC7815423B858`.

Rollback backup:

`evidence/stage-13/revision-r038/live-backup-20260914-231858`

It contains the prior live `HyARPG.jar` (SHA-256 `23312C061E8945BDB7C15FB87A875AE7462A24CF823AA6CA1B39BF33FCFE9B7C`) and the matching RPG mod-data directory (34 files at capture). No live save or mod-data file was deleted or rewritten during deployment.

## Connected QA required

Local tests and isolated smoke cannot prove connected native death handling. After deployment:

1. Confirm the top-right revision reads `R038`.
2. Equip Fire Bolt with Volley, Fork, and Chain as in the failing session.
3. Apply Burn to several damageable enemies, including at least one enemy low enough for a Burn tick to be lethal.
4. Confirm the enemy dies normally, the world remains loaded, and the player is not moved.
5. Confirm the trace contains the lethal `BURN_TICK`, native damage lifecycle, and normal death/status cleanup without `NATIVE_PERIODIC_FAILURE_IllegalStateException`.
6. Confirm the server log contains no `Store is currently processing`, world crash, or fallback-world relocation.
