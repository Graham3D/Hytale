# Stage 13 L — first-join world crash correction

2026-09-09. Branch `RPG`; baseline `84e29cb57175d9b6ade33654f38c77305ff5c91d` (Stage 13 K).

**Status: corrected, locally validated, packaged and deployed for connected retest.** Connected rejoin success is still unverified. This is a bounded startup-order correction, not a new gameplay stage or persistence redesign.

## Connected failure evidence

The owner-installed K JAR matched SHA256 `9083F89CB224BAC55B5D1B1CB9F5F5BA90C104D23A55B447A8E8029B4156B06F`. The actual RPG server log `2026-09-09_14-14-20_server.log` records a world-thread exception at **2026-09-09 18:14:32 UTC**, immediately after ClientReady/player join:

```text
IllegalStateException: PLAYER_PERSISTENCE_NOT_READY
RpgLoadoutService.readHolder:621
RpgLoadoutService.getPresentationView:363
NativeAbilityProjectionService.reconcile:144
NativeAbilityProjectionService.tick:83
NativeAbilityProjectionTickSystem.tick:26
```

The world then stops and removes its players. This incident is not the earlier CustomUI document-parser failure. The earliest failing boundary is a loadout presentation read before asynchronous player persistence is ready, not skill execution or damage.

Evidence: [stack excerpt](../../evidence/stage-13/cohort-l/connected-crash-excerpt.txt), [original log identity](../../evidence/stage-13/cohort-l/connected-crash-source.json). Only the relevant excerpt is published, not the complete authenticated connected-server log.

## Cause and bounded correction

The existing player-ready coordinator correctly defers ability/HUD installation until persistence and support initialization are ready. However, the independent native ability-projection ECS tick used `sessions.computeIfAbsent`. Its first tick could create a session before that deferred callback, immediately read the loadout, and allow the intentional nonblocking `PLAYER_PERSISTENCE_NOT_READY` exception to escape onto Hytale's world thread.

Changes:

1. `NativeAbilityProjectionService.tick` now requires an already-installed session. It cannot create one before readiness, or resurrect one after detach.
2. Production wiring supplies the memory-only `loadouts::ready` predicate. Installation, tick reconciliation, loadout mutation reconciliation and status reads defer while persistence is not ready. No repository load, wait, sleep, or synchronous disk fallback is added to the world tick.
3. Only the deferred ready callback installs the session. Once installed and ready, normal native-container replacement repair and Ability2/Ability3 projection continue unchanged.
4. A pending existing session reports `PLAYER_PERSISTENCE_NOT_READY` to the diagnostic status caller; it does not manufacture an empty persisted player state. Genuine persistence errors are not caught indiscriminately or cleared.

The only changed production source files are `NativeAbilityProjectionService.java` and `Phase00Plugin.java`. The [JAR differential](../../evidence/stage-13/cohort-l/jar-differential.json) shows changes only to the projection class and its nested classes, plus plugin wiring. No packaged resources changed. Consequently this candidate does not alter XP/HUD artwork, native resource ownership, ability assets, input adapters, executors, resource/reward formulas, encounter WAL, shield escrow, or save formats.

## Validation and its limits

Three new tests execute the real projection service with native AbilitySlots containers and the real loadout service:

- Even an already-loaded player cannot be enrolled by an ECS tick before the explicit ready callback; a detached session cannot be recreated by ticking.
- Cold join and an intentionally held repository load do not trigger an extra read, block ticking, install a premature session, or throw. After that load completes, explicit installation/reconciliation works.
- An existing session pauses while readiness is false and resumes native-container replacement reconciliation when ready.

The first test-fixture run failed because a bare native AbilitySlots constructor creates zero-capacity storage. The fixture was corrected to the actual native default ability capacity; no production assertions or retained tests were weakened.

The focused tests then passed. The coherent candidate received **one complete retained validation run**:

```powershell
.\gradlew.bat :test :nativeControlTest :canvas-ui:test --rerun build --console=plain
```

Result: **2,093 tests, zero failures, zero errors, zero skipped** (2,041 root, 31 isolated native tests, 21 CanvasUI). The new native tests run in the existing isolated Hytale-logger JVM, not omitted from validation. All K test case identities were checked against the retained evidence. Existing crash/fault-recovery and storage tests remain in the full suite. No separate new performance claim is made.

The build's CustomUI check validated all 32 documents. An isolated, offline, loopback-only server loaded exactly the three mods, resolved the retained native asset/registration audits, booted and shut down normally with exit code zero. Cohort L was explicitly added to the smoke script's existing later-cohort checks, preserving those assertions.

Evidence: [full validation output](../../evidence/stage-13/cohort-l/full-validation.txt), [test identities/results](../../evidence/stage-13/cohort-l/test-results.json), [smoke summary](../../evidence/stage-13/cohort-l/server-smoke-summary.json), [package/deployment result](../../evidence/stage-13/cohort-l/startup-hotfix.json).

**Neither these tests nor a no-player server smoke prove connected rendering, native input delivery, world joining, animation, or casting.** The previous K smoke passed precisely because it did not exercise this connected first-player ordering. This correction adds direct local coverage of that missing ordering, but still requires a real rejoin.

## Artifacts, deployment and rollback

The code version stays **R032 / 0.0.25**; **cohort L and SHA256** identify this correction. Do not identify same-version JARs by filename alone.

| Artifact | SHA256 |
|---|---|
| [RPG JAR](../../evidence/stage-13/cohort-l/artifacts/HytaleRPG-0.0.25.jar) | `1E4A5CAA1344CE71C8701EBCFBFCB3883BD288DC90BE9732066338A5F6A5B0F6` |
| [Three-mod ZIP](../../evidence/stage-13/cohort-l/Hytale-RPG-Stage13-L-startup-hotfix.zip) | `271C41613A94F32826D7BD8FE45BAE4D57BF44DD5CDDABD38B9A4052D90A95A5` |
| CanvasUI-0.1.0.jar | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| HYTALEDEVLIB-0.5.0.jar | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |

The ZIP contains exactly those three JARs; every decompressed entry was hashed. With no Hytale server process running, the RPG JAR was replaced at:

```text
C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HytaleRPG-0.0.25.jar
```

The two supporting mods were already correct and were not replaced. The final live folder still contains exactly three JARs, all verified against the package. No world, character, persistence, or other save data was edited or migrated. Seventeen non-JAR mod-data files were fingerprinted during final verification.

Deployment tooling notes: the first atomic replacement call rejected PowerShell's conversion of `$null` to an empty backup path, before replacing the target. It was corrected to `[NullString]::Value`. The successful replacement was followed by a verifier mismatch because a resumed `.pending` JAR had been counted as save data; the verifier now excludes only that exact staging path. The final idempotent verification confirmed the installed candidate, backup and supporting hashes without another replacement. Neither issue was a Hytale runtime or save-data failure.

The exact prior K JAR is retained in [rollback](../../evidence/stage-13/cohort-l/rollback/HytaleRPG-0.0.25.jar), verified against its original hash above. To roll back, stop the game/server and replace only the installed RPG JAR with that backup. No save rollback is needed for this format-preserving correction. K remains known to have this first-join race; rollback is diagnostic recovery, not a claim that K is safe from this crash.

## Minimal connected retest / remaining issues

1. Restart Hytale and enter the RPG world once. Confirm it stays connected rather than returning to the crash screen.
2. Run `/rpg loadout` and `/rpg dev ability-status`; verify persisted skills and native projections after readiness.
3. Rejoin once more and check the loadout remains intact. Test equipped abilities separately; joining successfully alone is not casting proof.

If joining still fails, inspect the new session log's earliest exception before changing any executor or persistence architecture. Stage 13's existing unverified connected/native tick, Snipe range, Bone Cage collision, Guard route and native basic-cadence gates remain as described in the [K testing-build report](stage-13-test-build-report.md). This hotfix does not promote those gates to PASS.
