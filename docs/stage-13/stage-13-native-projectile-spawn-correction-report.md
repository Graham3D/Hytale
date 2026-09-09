# Stage 13 O — native Fire Bolt construction correction

Date: 2026-09-09. Branch: `RPG`. Baseline: Correction N, commit `183601dbe46f3633c45d3f822852c7ea8738a4d2`. Revision/version remain R032 / 0.0.25; distinguish candidates by SHA-256, not the filename or banner.

**Outcome: corrected test JAR deployed.** Fire Bolt reaches `PROJECTILE_SPAWNED` through the actual installed native projectile allocator in an isolated three-mod server, with a valid inserted entity, native physics and velocity. The queued rollback control also passes. All **2,128 retained tests pass**, with no failures, errors or skips. **Corrected connected-client rendering, motion, collision and damage remain UNVERIFIED. Stage 13 is not PASS.**

## 1. Connected evidence and exact earliest failure

The owner's N connected result establishes Quick Slash working and Fire Bolt passing weapon/power validation. Input/projection are not reopened in O.

Read the real RPG server log `2026-09-09_18-20-29_server.log`, skill trace and UI trace. The relevant connected window is **22:20:29–22:22:18 UTC** (18:20:29 local). Later rejoin/idle sessions in the same trace file are excluded from these counts. [Connected evidence](../../evidence/stage-13/cohort-o/connected-failure-evidence.json) records source hashes, counts and all three correlated failing Fire Bolt pipelines; raw server authentication logs are not copied into this packet.

- 19 native input observations / activation requests; 11 validation passes, commits and dispatches.
- 16 strike query records; this count is not a claim of 16 separate paid casts.
- Three Fire Bolt `PROJECTILE_SPAWN_REQUEST` records and three `ATOMIC_BATCH_ROLLBACK` rejections with `IllegalArgumentException`; zero `PROJECTILE_SPAWNED`.
- The eight total projectile-rejection records include five other `INVALID_MAIN_HAND` records. These are not counted as eight allocator exceptions.
- Three UI trace records in the window; no evidence that UI/projection caused the allocator failure.
- Twenty `NATIVE_RPG_TICK_SUMMARY` records, zero raw tick samples; N's NORMAL aggregation was functioning. The two native slot-conflict records at teardown remain outside this correction.

The old class-only rollback event did not identify the exception message or native stack frame. O reproduced it using the **loaded asset registry entry**, not a JSON approximation:

```text
ProjectileModule.spawnProjectile(actor, commandBuffer, loadedFireBoltConfig, origin, direction)
  -> new Interactions(config.getInteractions())
  -> new EnumMap(map)
  -> IllegalArgumentException: Specified map is empty
```

`Projectile_Config_RPG_Fire_Bolt` intentionally has `"Interactions": {}`: native interactions must not duplicate RPG damage/gameplay. The resolved map is `java.util.Collections$EmptyMap`. Hytale's `Interactions(Map)` constructor calls `new EnumMap(map)`. An empty non-EnumMap has no recoverable enum key type, so Java throws the exact exception above. This is before native model/physics setup and before entity insertion. No impact/damage code is reached.

The native codec unit regression independently demonstrates the same native constructor failure. The isolated server control demonstrates it with the **actual installed/loaded config**. Native bytecode evidence is retained in [ProjectileModule](../../evidence/stage-13/cohort-o/native-projectile-module.txt) and [construction/CommandBuffer](../../evidence/stage-13/cohort-o/native-construction-bytecode.txt).

The initially considered equal model-scale bounds are **not** the cause: installed `HytaleMathUtil.randomFloat(min,max)` safely handles equal bounds. Model scales/artwork were not changed.

### Second construction defect exposed by the first fix

Fixing only the enum-map boundary allowed allocation to return, then the stronger native integration failed at:

```text
IllegalStateException: Invalid entity reference!
Ref.validate -> Store.__internal_getComponent -> CommandBuffer.getComponent
  -> HytaleSkillExecutionSystem.spawnProjectileCarrier [PHYSICS_COMPONENTS]
```

`ProjectileModule.spawnProjectile` queues `CommandBuffer.addEntity(holder, SPAWN)` and returns its pending Ref. `CommandBuffer.getComponent(ref,...)` reads the Store immediately; it does not read pending holders. The RPG code incorrectly tried to fetch physics/velocity through that still-invalid Ref. The [first failed native attempt](../../evidence/stage-13/cohort-o/attempt-1/server-smoke.txt) is retained, including its [candidate hash](../../evidence/stage-13/cohort-o/attempt-1/server-smoke-summary.json). Its normal boot checks passed but its construction regression failed; that build was **not deployed**. A boot-only smoke result was not treated as spawn proof.

## 2. Bounded correction and reasoning

### NativeProjectileSpawnConfig

[NativeProjectileSpawnConfig.java](../../src/main/java/com/inigmasgames/hytalerpg/execution/hytale/NativeProjectileSpawnConfig.java) is a per-spawn view of the already-loaded native config:

1. Expose an empty **typed** `EnumMap<InteractionType,String>` when the source roots are empty. Nonempty roots remain unchanged. No dummy interaction, damage root, gameplay branch, or asset-registry mutation is introduced.
2. Delegate every public native ProjectileConfig instance method to the source. A reflection regression detects newly introduced native config methods that the wrapper has not explicitly handled. Source model, launch force, offsets, sounds, packet representation and physical settings remain authoritative.
3. Wrap only `PhysicsConfig.apply` to call the **real source physics implementation** with the original holder/actor/velocity/accessor/prediction arguments, then retain the native-created holder for this spawn. No copied allocator, reflection into ECS internals, manual buffer flush, or replacement physics is used.
4. Configure native physics and Velocity directly on that holder before the queued insertion is processed. The preexisting RPG plan velocity remains unchanged. A physics-apply failure keeps its original exception and precise failure stage.

### HytaleSkillExecutionSystem spawn construction

[HytaleSkillExecutionSystem.java](../../src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSkillExecutionSystem.java) still calls `ProjectileModule.get().spawnProjectile(...)` and still installs the same impact consumer. The holder solves only construction timing; downstream impact/damage mechanics are unchanged.

Native `CommandBuffer.putComponent` queues continuation-component installation, so the existing continuation setup can remain on the buffer. FIFO ordering is native insertion, native spawn hook, continuation put, then spawn acknowledgement. `PROJECTILE_SPAWNED` is now emitted from a queued callback **only when the inserted Ref is valid and that exact carrier is still tracked**. It is not fabricated at plan creation or merely because native allocation returned.

Rollback must also account for a pending Ref. The direct spawn catch and batch rollback paths now queue `tryRemoveEntity` even while the Ref is not yet valid. The native removal command checks validity when it executes, after insertion. The previous early `isValid()` guard could skip removal of a pending entity. Removing carrier tracking before queue drain suppresses the success acknowledgement of a rolled-back batch. Resource, cooldown and ammunition semantics are not changed.

### Failure diagnostics

[ProjectileSpawnDiagnostics.java](../../src/main/java/com/inigmasgames/hytalerpg/execution/hytale/ProjectileSpawnDiagnostics.java) preserves the original exception type and first marked boundary, adding bounded failure-only metadata to immediate, scheduled and child-spawn rollback events:

- `spawnStage`: `CONFIG_LOOKUP`, `NATIVE_ALLOCATION`, `PHYSICS_COMPONENTS`, `VELOCITY_ASSIGNMENT`, `CONTINUATION_SETUP`, `IMPACT_CALLBACK`, `SPAWN_EVENT`, `PRESENTATION`; errors before entering a carrier are `PRE_CARRIER_BATCH`.
- `spawnFailureCode`: exact known empty-map signature is `NATIVE_EMPTY_ENUM_MAP`; other errors have a stage-specific code.
- `spawnError`: exception class, maximum 80 characters.
- `spawnFailureOrigin`: first native/EnumMap stack frame, maximum 192 characters, when available.

Arbitrary exception messages/paths/tokens are not emitted into normal traces. The stage/class/origin distinguishes the requested boundaries without hiding or suppressing the original failure. Existing correlation/root/skill/projectile IDs remain on the events. This does not introduce per-tick telemetry or change N trace routing.

## 3. Strongest native integration and evidence limit

An opt-in [NativeProjectileSpawnAuditCommand](../../src/main/java/com/inigmasgames/hytalerpg/execution/hytale/NativeProjectileSpawnAuditCommand.java) is registered only with `-Drpg.projectileSpawnAudit=true`. It refuses a world outside the configured isolated root and refuses any connected players. Normal gameplay does not register this command. It is **not** an owner QA command.

The test runs on the native world owner thread with the actual EntityStore/CommandBuffer, a temporary nonserialized actor, loaded Fire Bolt config, and a canonical Fire Bolt compiled/snapshotted fixture. It calls the production `spawnProjectileCarrier` method, not a replacement projectile simulator. It does not activate/charge a real player skill; that part of the connected pipeline was already proven and is outside this construction fixture.

The final exact candidate test proves:

1. The unadapted loaded config throws the exact empty-map exception at the old boundary.
2. Production carrier construction returns a pending Ref without trying to read it through Store.
3. After the native command buffer drains, the entity Ref is valid; native StandardPhysicsProvider and Velocity exist; physics speed is 24; native Interactions remain empty.
4. Production trace contains one `PROJECTILE_SPAWN_REQUEST -> PROJECTILE_SPAWNED` with matching correlation, root and skill IDs and the exact Fire Bolt config.
5. A second production carrier is deliberately rolled back while pending. After queue drain its Ref is invalid and it produces **no** `PROJECTILE_SPAWNED`. Total fixture trace: two requests, one acknowledgement.
6. Temporary actors/carriers are cleaned up, and the isolated server shuts down normally.

See [native integration manifest](../../evidence/stage-13/cohort-o/native-spawn-integration.json), [correlated spawn records](../../evidence/stage-13/cohort-o/native-spawn-records.json), and [final native server output](../../evidence/stage-13/cohort-o/server-smoke.txt). The fixture at altitude 200 can produce an unloaded-chunk location warning; it verifies construction and immediately cleans up, not travel through a loaded connected scene.

Pinned installed inputs:

| Input | SHA-256 |
| --- | --- |
| HytaleServer.jar, 0.7.0-pre.1 | `EC57E9BD6E2CA3CB16CC5883D42B04A0C64D382DEE532C5BC1CFCF68421E1EE3` |
| Assets.zip | `46F6AA12DECF4F900FCFDF28ECC67568403C37A3AD0A03A4E4CF7653A324AE39` |

The smoke script now hard-fails if the native integration marker, actual current-run spawn trace, rollback control, or correlations fail. The publish script requires this integration result for the **same JAR hash**. This is stronger than asset audit, but **not connected proof of rendering, input, animation, flight, impact or damage**.

## 4. Retained validation / known gates

Focused tests while editing; then one complete coherent retained run:

```powershell
.\gradlew.bat :test :nativeControlTest :canvas-ui:test build --rerun-tasks --console=plain
.\tools\Run-Stage13CohortSmoke.ps1 -Cohort o -NativeProjectileSpawnAudit
.\tools\Publish-Stage13StartupHotfix.ps1 -Cohort o
.\tools\Verify-Stage13CastingPackage.ps1 -Cohort o
```

- **2,128 tests**: 2,070 root, 37 native-control, 21 CanvasUI; zero failed/errored/skipped. All 2,119 N test-case identities retained unchanged; nine new cases.
- Six native construction unit cases cover actual codec/constructor failure, typed compatibility, source immutability, nonempty behavior, complete delegation, holder capture and original physics failure (several assertions share a case).
- Three diagnostics cases cover all stages, original-class preservation, secret/oversized-message suppression, known error code, first-boundary precedence and pre-carrier errors.
- Existing N coverage for 197 weapons, Flame Longsword/Quick Slash, Mithril Staff/Fire Bolt, targetless casting and NORMAL/PERFORMANCE trace behavior passed unchanged.
- Full persistence, ordering, escrow, exact-once, archived-reader, crash/fault-recovery and earlier stage regressions passed unchanged. CustomUI validation checked all 32 documents.
- Exact three-mod boot/setup/assets/shutdown passed, plus the native construction/rollback test above. Full suite and isolated smoke ran concurrently; these timings are not a dedicated machine-performance qualification.
- The unchanged 64-update real-storage acknowledgement diagnostic (60 samples) measured **4.6565 / 8.2309 / 17.3272 ms p50/p95/p99**, with restored contributor counts. It remains outside the nominal 4/8 ms numbers and is **not native-tick proof**. [Raw retained metric](../../evidence/stage-13/cohort-o/durability-load.json). No durability mechanism or threshold was changed. Formal connected PERFORMANCE-mode 4 ms p95 / 8 ms p99 qualification remains unverified.
- [Packaged differential](../../evidence/stage-13/cohort-o/jar-differential.json): 17 changed/added class entries, restricted to spawn adapter/system/diagnostics and opt-in test command registration. Recompiling the large outer HytaleSkillExecutionSystem also changes its nested class metadata. Source diff is bounded to spawn construction/rollback/diagnostics/test seam. All other JAR entries are identical to N, including every resource/config, native power registries, equipment adapter, skill coordinator, trace routing and persistence classes.
- Atomic isolated N rollback and O roll-forward passed; three-entry archive hashes checked. No failing assertion was weakened, expected behavior changed to pass, or retained test removed.

Remaining issues are connected O projectile rendering/flight/impact/damage and the preexisting broader Stage 13 acceptance/performance/capability gates. Native ability teardown conflicts remain historical/out of scope. No new stage or architecture redesign started.

## 5. Artifacts, deployment and rollback

At **22:50:42 UTC**, with the native server stopped, the exact tested RPG JAR was staged, verified and atomically installed in:

```text
C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods
```

There are still exactly three mods. CanvasUI and HytaleDevLib bytes are unchanged. All **17 non-JAR mod-data files** hashed identically before/after. No live world/player data or owner `art/` files were edited; all work/evidence stays in the C: GitHub checkout, not Google Drive.

| Artifact | SHA-256 |
| --- | --- |
| [O HytaleRPG-0.0.25.jar](../../evidence/stage-13/cohort-o/artifacts/HytaleRPG-0.0.25.jar) | `B8BCCF7DCAA4E7949A32961F261504A0604E7A5671F8E32FBF1BEBBD0CEF0400` |
| CanvasUI-0.1.0.jar | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| HYTALEDEVLIB-0.5.0.jar | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |
| [O three-mod ZIP](../../evidence/stage-13/cohort-o/Hytale-RPG-Stage13-O-native-spawn-correction.zip) | `A9A67FEE85C67320D747C47918853F9E5A167353064B652627ACC7B243D3A2F5` |
| [N rollback RPG JAR](../../evidence/stage-13/cohort-o/rollback/HytaleRPG-0.0.25.jar) | `669B230DAC394A2CE1246125028C51DF446BBC5024FCBEED83059F65F8BDDF33` |

[Deployment manifest](../../evidence/stage-13/cohort-o/native-spawn-correction.json). Rollback: stop the RPG server, replace only its RPG JAR with the verified N rollback JAR, leave both supporting mods and all saves untouched, restart. N still has the reproduced Fire Bolt spawn failure.

## 6. Minimal connected retest

1. Restart/rejoin RPG. Run `/rpg-trace status`; expect NORMAL. No need to enable PERFORMANCE for this casting check.
2. `/rpg skilltree`: keep Quick Slash in skill01 and Fire Bolt in skill02. `/rpg dev ability-status`: confirm the existing native Ability2/Ability3 projection.
3. Hold a supported staff (for example `Weapon_Staff_Mithril`), with sufficient Mana and cooldown ready. Aim into empty space and press your configured Ability3 key once (R in the previous session). Confirm a visible launched Fire Bolt rather than only a slot flash.
4. Wait for cooldown, cast toward terrain, then toward a safe valid hostile target. Record visible flight/collision and actual Health change. Do not infer impact success merely from spawn success.
5. Hold `Weapon_Longsword_Flame` and press Ability2 (E) into empty space as a retained Quick Slash check.
6. Leave the world normally so trace buffers drain. Review the server log and `mods\InigmasGames_HytaleRPGPhase00Audit\logs\rpg\skill-trace.jsonl`. For each Fire Bolt, require input -> activation -> validation -> commit -> dispatch -> spawn request -> **PROJECTILE_SPAWNED**, preserving correlation/root/skill IDs. Check one resource charge/cooldown and no duplicate execution; these mechanics were not changed. Any further failure should now identify `spawnStage`, `spawnFailureCode`, class and bounded native origin.

Do not run `rpg-native-spawn-audit` in the live world: it is deliberately unavailable in a normal launch. Provide the connected log/trace plus your visual observations before marking any connected projectile gate PASS.
