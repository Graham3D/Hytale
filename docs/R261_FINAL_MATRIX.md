# R261 Final requirement audit (R261FDEV checkpoint)

Source: published R261-C `e1c5562728b4617ac2dfab4c3a8f10445cc36b92`
and this branch's reviewed development changes. These statuses describe
offline evidence; none establishes connected-game acceptance. **Do not merge
R261 into `main` or deploy this candidate while a release blocker remains.**

| Requirement | Implementation | Automated evidence | Status |
| --- | --- | --- | --- |
| Permanent Barrier HP; no timer or passive regeneration | `CycloneBarrierLedger`, `CycloneArmorPresentation` | `CycloneDurableLiveTest.presentationIsInfiniteAndUsesAnActualStatusIcon`, `CycloneBarrierLedgerTest.forcedDebitSurvivesRestartWithoutRefill` | Offline covered |
| Base 100 HP, effective WIS/INT, level 20 +60% | `CycloneArmorFormula`, `SkillExecutionService.finishDurableCyclone` | `CycloneDurableLiveTest.balanceUsesEffectiveBaselineAndSixtyPercentAtTwenty`, `liveActivationFreezesEffectiveStats` | Offline covered |
| Minimum 100 spendable Mana; full unreserved payment | `SkillExecutionService.durableCycloneCost`, `CycloneActivationTransaction` | `CycloneDurableLiveTest.reservationAndMinimumAreEnforcedBeforePayment`, `castChargesAllSpendableManaAndCreatesOnlyDurableBarrier`, `finalCommitRequotesTheEntireCurrentSpendableBalance` | Offline covered |
| Recast only after depletion and cooldown | `CycloneBarrierLedger.begin`, cooldown authority | `CycloneDurableLiveTest.activeRecastRejectsAndDepletedGenerationMayRecastAfterCooldown` | Offline covered |
| Durable post-mitigation absorption, overflow, duplicate receipts, no unforced credit | `CycloneBarrierLedger.debit`, `CycloneDamageStage` | `CycloneBarrierLedgerTest.concurrentHitsAreSerializedAndOverflowIsExact`, `duplicateAndStaleReceiptsCannotDebitTwice`, `nativeDamageCanOnlyBeReducedAfterConfirmedForcedWrite` | Offline covered |
| Interrupted activation, storage failure, stale session, death | `CycloneActivationTransaction`, journal replay, native death hook | `CycloneBarrierLedgerTest` fault/race cases; `CycloneDurableLiveTest.deathForfeitsBarrierWithoutRestoringMana` | Offline covered |
| Paid Barrier HP survives reconnect/restart without double charge or refill | Native Mana value and zero effect generation marker, `CycloneNativePaymentMarker.recover` | `EnemyNativeHealthTest.recoveredNativePaymentPreservesOnlyProvenPlainBarrier`, `CycloneBarrierLedgerTest.nativePaidGenerationReconcilesOnlyRemainingProtectionAfterRestart` | **Partial:** plain barrier only; no native save flush or connected lifecycle proof |
| Reflective Ward after reconnect | Frozen live cast context in `HytaleSupportSystem`; uncertain generations forfeit | `CycloneDurableLiveTest.reflectiveWardUsesOnlyConfirmedDebitAndNoFiniteShield` covers current session only | **Release blocker:** cast context is not durable |
| Shared Aegis LP-056 derived 50% ally barrier, 8 m/LOS, durable recipient accounting/recovery | Finite R260 path exists; durable path rejects before payment | `CycloneDurableLiveTest.unsupportedSharedAegisRejectsBeforeManaOrCooldown`; finite `Stage09FinalSupportPassivesTest` | **Release blocker:** no durable ally authority or restart proof |
| Natural Health near 388 at level 99; effective STR +2 HP, INT +2 Mana | `R261CProgression`, native stat projection | `R261CProgressionTest`, `EnemyNativeHealthTest.playerCapacityRecalculationPreservesNativeCurrentAndClampsOnReduction` | Offline covered |
| Stackable Mana Before Life affix at 60% cap; saved equipment identity | Gear affix parser/snapshot and `ManaBeforeLifeStage` | `ManaBeforeLifeAffixTest`, `GearAffixQaSuiteTest`, `Qa159PackTest` | Offline covered |
| Damage order Barrier → Mana Before Life → Health, Managuard/reservations | Native damage bridge and `ManaBeforeLifeStage` | `ManaBeforeLifeStageTest`, `R261CManaDamageReceiptTest`, `R261CCombatLatencyTest` | Offline covered; native timing awaits connected QA |
| Authoritative Health, Mana, Barrier HUD | Native stat read and `BarrierView` | `R261CResourceHudTest` | Offline covered; visual layout awaits connected QA |
| Sequential, contended, independent-player latency; no global simulation stall | Per-player journal force, globally synchronized resource coordinator | `CycloneDamageStageTest`, `CycloneBarrierLedgerTest` latency cases, `R261CCombatLatencyTest` | **Release review:** full ECS timing and resource lock contention unproven |
| R260 compatibility and protective gate | `hywind.cyclone.durable` defaults false; R260 JAR remains installed | Full offline `check`, package validator, Guardian receipt required at exact development commit | Pending final verification |

## Native persistence boundary

The Hytale 0.7.0-pre.5.1 `EntityStatValue.CODEC` serializes `Id`, `Value`, and
`Modifiers` in one stat record. `DiskPlayerStorage.save` serializes the entire
holder before its asynchronous `StorageManager.doSave` call; `BsonUtil` writes
and moves a BSON document, with backup fallback on load. There is no exposed
shared forced transaction between that save and the Hywind journal. The
generation marker is therefore a proof **only when it is present in the
loaded native stat**, not when Hywind merely issued a save or remembers a
debit in its journal. Missing and mismatched proof is still forfeited.

## Offline damage latency sample

The focused local run of `CycloneBarrierLedgerTest` measured forced journal
debits (p50/p95/p99/worst, ms): one player sequential
2.317/3.562/4.310/6.273; eight threads hitting one player
2.191/71.352/96.729/102.982; eight independent players
3.478/5.168/5.265/5.361. After correcting the R261-C benchmark so both
contended and independent actors actually pass through Barrier and Mana Before
Life, its mixed bridge sample measured one player
0.101/33.969/38.560/38.724 and eight players
0.003/3.954/4.999/5.036. Mixed samples include hits after Barrier depletion,
which explains the low p50. These are wall-clock samples on this host, not
deterministic thresholds. They exclude ECS traversal, live Managuard,
Reflective Ward, Shared Aegis, and connected rendering. The native damage
call stack still blocks on `FileChannel.force(true)` for each absorbed hit;
the resource coordinator also has globally synchronized native Mana mutation
methods. A critical connected frame impact cannot be ruled out offline.

## Required next engineering work before mainline

1. Persist enough Reflective Ward cast identity and frozen behavior to restore
   its secondary damage safely, or keep the original R261 durability
   requirement explicitly unfulfilled.
2. Design a durable shared recipient ledger tied to the caster's proven
   payment, with caster and recipient disconnect/restart behavior, duplicate
   receipt handling, concurrent hits, and storage failure tests.
3. Measure the complete native damage path and resource coordinator under
   concurrent load; resolve any proven critical simulation-thread stalls.
4. Run full exact-commit offline/Guardian gates, integrate the latest remote
   main without unrelated changes, reverify the merged commit, and package
   from verified main only after all release blockers are closed.

## Owner connected-game checklist after a future completed release

On a backed-up active RPG save only after a verified release is separately
deployed: cast at levels 1 and 20; check Mana spend, HUD, particle, and
cooldown; take damage with and without Mana Before Life; deplete and recast;
reconnect and restart with remaining Barrier HP; repeat with Reflective Ward
and Shared Aegis and inspect the ally's 8 m/LOS behavior; change gear and
world, then verify Health/Mana values and that older skills still function.
The current development branch is **not** ready for these owner QA steps.
