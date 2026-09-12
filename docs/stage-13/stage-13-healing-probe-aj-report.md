# R032-AJ — isolated presentation probe harness correction

**NOT DEPLOYED TO LIVE.** Implemented and packaged; connected AJ rendering remains **UNVERIFIED**. The live AH JAR and save/mod data were not replaced or edited. No production Healing Beam, encounter, persistence, resource, ability or HUD implementation was changed.

Date: 2026-09-12. Branch: `RPG`. Starting HEAD: `8ada3c01951eed34844527937790e3247b380a66`. Native runtime: Hytale **0.7.0-pre.2**, revision `b41721d651ef241809e402f6c3371781b2ea5f84`. The owner explicitly classified the failed AI controls as a harness failure and authorized only probe repair plus separate encounter investigation. See the [AJ connected checklist](healing-probe-aj-checklist.md).

## 1. Prior connected evidence

Reviewed `run/healing-probe-ai-4109064b136748e5b3f4c35d9f1337cb`, server `logs/2026-09-12_16-40-28_server.log`, and RPG `logs/rpg/skill-trace.jsonl`. [Native pin and evidence hashes](../../evidence/stage-13/cohort-aj/visibility-boundary-audit.json) preserve provenance. The raw authentication transcript is not published because it may contain authorization credentials.

Authenticated Direct Connect worked: all eight commands reached the probe. Every request failed with `NATIVE_NPC_SPAWN_FAIL_INVALID_POSITION` before visual creation. Skill-trace failure lines: 342, 357, 375, 401, 421, 439, 451 and 463, from 20:45:45 to 20:46:51 UTC. There were 56 probe records: eight requests, eight failures, eight cleanup events, 24 release receipts and eight summaries. No target resolution, visual creation or effect attachment was reached. All summaries reported zero watched entity/component updates. This is **not rendering evidence**.

The common `create()` method unconditionally spawned `RPG_Summon_Decoy` at the player's Y, six metres along world -Z. That is not a terrain-projected ground placement operation. Worse, the prerequisite also blocked standalone modes. The previous isolated audit tested model/effect construction but never exercised this command setup boundary.

## 2. Correction and rationale

### Target dependencies

| Controls | Entity requirement | AJ behavior |
|---|---|---|
| WORLD | Caster only | Existing stock finite world particle along initial aim. |
| EMPTY / VISIBLE | Caster only | Existing carrier, fixed world endpoint along initial aim. |
| BEAM | Caster only | Existing geometry-only native Beam comparison. |
| STAFF_ONCE / STAFF_OVERWRITE | Caster holding audited staff | Existing effect attached to caster, no NPC. |
| RECIPIENT_ONCE / RECIPIENT_OVERWRITE | Living recipient | Native flat-fixture mannequin or borrowed runtime UUID. |

`none` is now the explicit standalone argument. Legacy standalone `native` syntax is compatible but does not spawn an NPC. Recipient-only target validation is retained. Setup, liveness/death checks, frame updates and cleanup now handle an absent target safely. Standalone receipts include `TARGET_NOT_REQUIRED` and `npcSpawnAttempted=false`.

Chat reports `STARTED`, `FAILED` and `ENDED`; a setup failure explicitly says it is not a rendering result. Notification transport failures cannot prevent cleanup. The existing ten-second deadline, 20 Hz update ceiling, bounded receipts, one run/world, four-world ceiling, native tick instrumentation, deferred world mutation, finite effects and generation/ownership guards remain intact.

### Native-validated flat fixture

The launcher creates a **new** flat world rather than editing/reusing the failed AI world. The checked-in config uses installed `FlatWorldGenProvider`: Soil_Grass from Y=0 inclusive to Y=64 exclusive. `GlobalSpawnProvider` uses `Id=Global`, placing the player at 8.5,64,8.5. Automatic ambient NPC/block-spawner/spawn-marker activity is disabled **only in this disposable diagnostic world**, making unrelated natural combat unnecessary for a visual test. This is not a production encounter workaround.

Recipient creation verifies the actual flat provider, loaded chunk, 5×5 grass floor and six blocks of empty clearance. It calls the installed `NPCPlugin.spawnNPCWithColumnProbe` at column 8,2, Y hint 64, retaining Hytale's validator. No guessed player-height exact placement, validation bypass, terrain editing or fake combat subsystem is introduced. The native role remains the existing friendly Mannequin decoy with real Health and EffectController components. Owned recipients are nonserialized and removed on completion/failure; borrowed targets are not despawned.

The exact API was verified in a generated flat world. The guarantee applies to that pristine fixture, not arbitrary modified worlds: old/nonflat worlds, unloaded chunks, changed floor/clearance and excessive distance from the fixture are explicitly rejected. The launcher never reuses a world. Native bytecode confirms the old exact path uses `SpawningContext.setExact`/`validatePosition`, while the column method uses `set`/`canSpawn`. Boot decoded and re-serialized the expected native provider, spawn point and dimensions.

### Isolation and unchanged behavior

Explicit startup flags and `inigmasgames.rpg.healingprobe` permission remain mandatory; canonical real-path isolation checks are unchanged. `New-HealingProbeAI.ps1` keeps its familiar filename but selects AJ artifacts and a new `healing-probe-aj-<UUID>` directory. It verifies archived/native hashes, uses **authenticated loopback Direct Connect**, copies permission grants and exactly three mods, and never copies live gameplay state or server tokens. Browser authentication remains an owner action.

No production visual assets were changed. Stock directional-emitter extent limitations, material appearance, staff-tip accuracy and production tether acceptance remain unresolved/unapproved. Standalone core controls capture a point six metres along initial aim; that is a diagnostic endpoint, not a production targeting change. World-particle early stop still has only the native finite lifetime/tail, not a proven per-instance cancellation handle.

## 3. Separate encounter/persistence investigation — not fixed here

Server line 1369, 20:45:50 UTC: `NATIVE_CONTRIBUTION_INSPECT`, `NoSuchElementException: No value present`, `awardsUnavailable=true`. This is approximately five seconds after the first harness failure. Later probe failures still stop at the same NPC placement boundary, not at an encounter prerequisite.

The reproducible state mismatch is between `PersistentEncounterRuntime` and `HytaleEncounterRewards.damage()`:

1. `attachNative()` publishes an attachment before asynchronous classification/load completion.
2. `attachPrepared()` can legitimately return false with no loaded spawn descriptor, e.g. an unclassified NPC without a saved encounter.
3. The attachment can remain present. `observing()` checks presence/nonclosing state rather than attachment success, so it can be true while `attaching()` is false and `spawn()` is empty.
4. The damage observer treats these predicates as sufficient to call `runtime.spawn(...).orElseThrow()` during role comparison. They do not guarantee a descriptor exists.

The separate [diagnostic program](../../tools/diagnostics/EncounterAttachmentAudit.java), run against current compiled classes with a fresh isolated FileEncounterStore, reproduced:

```text
accepted=false observing=true attaching=false spawnPresent=false
missingDescriptorThrow=true message=No value present
persistenceUnavailable=false connectedProof=false gameplayChanged=false
```

[Diagnostic output](../../evidence/stage-13/cohort-aj/encounter-attachment-diagnostic.txt) is evidence of the existing bug, not a release assertion defining desired behavior. Production code and retained tests were not altered. The connected warning did not include the NPC ID/full inspect stack, so its exact enemy classification history cannot be reconstructed; the reproduction establishes a concrete matching failure path.

The uncertainty cascade is distinguishable from a disk-write failure. At 20:45:50.594, metrics show 158 attachment contexts, zero WAL contexts, no pending WAL work, `wal.failure=false` and `runtime.uncertain=false`. At 20:45:51.617 the runtime becomes uncertain while WAL failure stays false; at 20:45:52.621 the effects worker records its first failure. `HytaleEncounterRewards.safely()` calls `rejectIncompleteNativeObservation()` after a failed post-Apply observation. That sets the runtime unavailable flag, causing subsequent guarded work to throw `ENCOUNTER_PERSISTENCE_UNCERTAIN_RESTART_REQUIRED`.

At 20:50:32 the owner entered `stop`. The shutdown exception passes through `contextLoads.close()`/`DurableEncounterEffects.close()`; its nested cause is `PersistentEncounterRuntime.guarded()` reached from `attachPrepared()`. Native world-removal/recovery messages occur during this stop as well; they do not establish a Beam/client crash. There is no WAL failure evidence at the initial transition and no basis here to claim disk corruption. The existing fail-closed policy prevents rewards from incomplete observations and was **not weakened**.

The encounter defect is deferred to its own correction. It did not block the corrected native probe integration. No encounter/journal/escrow/reward code or live state was modified; all existing durability regressions ran unchanged.

## 4. Validation and artifacts

Focused probe/policy/tick-metric tests passed while editing. The coherent candidate then passed **one complete retained run**:

```powershell
.\gradlew.bat :test :nativeControlTest :canvas-ui:test :jar --rerun-tasks --continue --console=plain
```

**2,179 RPG + 55 native-control + 21 CanvasUI = 2,255 tests**, zero failures/errors/skips. The 34-document CustomUI validator passed. Existing deprecation warnings remain. No assertion, expected gameplay behavior or test was removed/relaxed. XML, full transcript and trace archive fixtures are retained in `evidence/stage-13/cohort-aj/`.

The exact three-mod native smoke passed with the final candidate hash. It exercised real recipient column spawning, living Health, once/overwrite effects and removal. It also ran the **same `create/update/cleanup` methods as the command** for four standalone geometry controls and both recipient modes, verifying no standalone NPC and no owned entity residue. Existing five effect wrappers and once/overwrite native queue tests, non-consuming observer order, Mana replication, staff channel lifecycle, projectile and Blizzard checks were retained. This is not connected Player/staff rendering, transport delivery or long-channel visual proof.

Packaging compares every JAR entry against AI and permits changes only in probe/policy/command classes plus the plugin probe revision marker. **Production Healing Beam and encounter classes/assets, other gameplay and native identity are byte-identical to AI.** The normal HUD badge was not changed; AJ identification is via chat/startup/trace cohort.

| Artifact under `evidence/stage-13/cohort-aj/` | SHA-256 |
|---|---|
| `artifacts/HyARPG.jar` | `76008E37FFC5B38601BD8558728DCA314812707B32E1972A488B3367B4D87E3B` |
| `artifacts/CanvasUI-0.1.0.jar` | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| `artifacts/HYTALEDEVLIB-0.5.0.jar` | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |
| `HyARPG-R032-AJ-three-mods.zip` | `FFC689EFB7E507277FF7334CDF612BBAE472FD11B7062B050C8053361D277421` |

Archive entries match individually. Binary rollback **AI → AJ → AI** passed in an isolated directory. Internal identity remains `InigmasGames:HytaleRPGPhase00Audit`. [Package receipt](../../evidence/stage-13/cohort-aj/package-validation.json) records entry differences and test counts. Live AH remains hash `272E9F8365FE877152B5890D9100E123AE1127D8DC881A88D93993D3C51FE692`.

Authenticated launcher argument/copy/fixture and native boot/listen/normal-stop checks are recorded under `launcher-auth/`. They do not complete device authorization or connected rendering. Historical AI artifacts and failed connected evidence were retained.

## 5. Status

- IMPLEMENTED: harness dependencies, fixture placement and explicit failure feedback.
- PACKAGED: cumulative AJ JAR and exact three-mod archive; validation and rollback receipts.
- DEPLOYED: **NO LIVE DEPLOYMENT**. Only disposable validation directories received the candidate.
- CONNECTED-VERIFIED: **NO** for AJ. Prior AI proves authenticated command delivery plus harness failure, not rendering.

Next: run the corrected controls from the [AJ checklist](healing-probe-aj-checklist.md), correlate video/client logs with target/setup, creation/attachment, viewer queue and outbound receipts, and stop at the first missing transition. No final Healing Beam fix or Stage 13 PASS is claimed.
