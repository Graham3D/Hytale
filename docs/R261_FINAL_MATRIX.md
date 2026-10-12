# R261 Final requirement audit

Source: `codex/r261-final`, based on the verified R261FDEV checkpoint
`60e718532b463cbb0ffc8503f0ae0049d55c56e6`. The reviewed implementation
source `da4f49cca5eb6cc88a7b7d13cd31e15ce8841a49` passed exact-commit
offline release verification and was fast-forwarded to GitHub `main`. The
active R260 JAR and RPG save are unchanged. Connected-game acceptance is
pending the owner's separate deployment decision.

| Requirement | Implementation | Automated evidence | Status |
| --- | --- | --- | --- |
| Permanent Barrier HP; no timer or passive regeneration | `CycloneBarrierLedger`, `CycloneArmorPresentation` | `CycloneDurableLiveTest.presentationIsInfiniteAndUsesAnActualStatusIcon`, `CycloneBarrierLedgerTest.forcedDebitSurvivesRestartWithoutRefill` | Offline covered |
| Base 100 HP, effective WIS/INT, level 20 +60% | `CycloneArmorFormula`, `SkillExecutionService.finishDurableCyclone` | `CycloneDurableLiveTest.balanceUsesEffectiveBaselineAndSixtyPercentAtTwenty`, `liveActivationFreezesEffectiveStats` | Offline covered |
| Minimum 100 spendable Mana; full unreserved payment | `SkillExecutionService.durableCycloneCost`, `CycloneActivationTransaction` | `CycloneDurableLiveTest.reservationAndMinimumAreEnforcedBeforePayment`, `castChargesAllSpendableManaAndCreatesOnlyDurableBarrier`, `finalCommitRequotesTheEntireCurrentSpendableBalance` | Offline covered |
| Recast only after depletion and cooldown | `CycloneBarrierLedger.begin`, cooldown authority | `CycloneDurableLiveTest.activeRecastRejectsAndDepletedGenerationMayRecastAfterCooldown` | Offline covered |
| Durable post-mitigation absorption, overflow, duplicate receipts, no unforced credit | `CycloneBarrierLedger.debit`, `CycloneDamageStage` | `CycloneBarrierLedgerTest.concurrentHitsAreSerializedAndOverflowIsExact`, `duplicateAndStaleReceiptsCannotDebitTwice`, `nativeDamageCanOnlyBeReducedAfterConfirmedForcedWrite` | Offline covered |
| Interrupted activation, storage failure, stale session, death | `CycloneActivationTransaction`, journal replay, native death hook | `CycloneBarrierLedgerTest` fault/race cases; `CycloneDurableLiveTest.deathForfeitsBarrierWithoutRestoringMana` | Offline covered |
| Paid Barrier HP survives reconnect/restart without double charge or refill | Native Mana value and zero effect generation marker, `CycloneNativePaymentMarker.recover`; native storage owns the saved BSON record | `EnemyNativeHealthTest.recoveredNativePaymentPreservesOnlyProvenPlainBarrier`, `nativeEquivalentSavedStatFixtureRequiresMatchingPaidGeneration`, `recastRetainsOnlyPaymentMarkersNeededBySurvivingSharedChildren` | Focused pass; an unsaved marker forfeits protection without another charge |
| Reflective Ward after reconnect | `CycloneReflectionJournal` owns frozen attribution, coefficient and forced claim receipts; `HytaleSupportSystem` restores only exact paid generation | `CycloneReflectionJournalTest`, `EnemyNativeHealthTest.reflectiveRecoveryRequiresBothLoadedNativePaymentAndFrozenBehavior`, `CycloneDurableLiveTest.reflectiveWardUsesOnlyConfirmedDebitAndNoFiniteShield` | Focused pass; an interrupted claim may lose one reflection but cannot replay it |
| Shared Aegis LP-056, 50% child, 8 m/LOS, one recipient | Native selector in `HytaleSupportSystem`, `SharedAegisRecipientSelector`, `CycloneSharedAegisLedger` parent intent and child journal; caster retains payment ownership | `SharedAegisRecipientSelectorTest`, `CycloneSharedAegisLedgerTest`, `CycloneDurableLiveTest.sharedAegisLinkAllowsPaidParentActivation` | Focused pass; NPC and player native ECS behavior awaits connected acceptance |
| Mutual trusted player ally consent | `TrustedPlayerAllies` forced UUID journal and `/rpg trusted`; Friendly/Revered NPC eligibility unchanged | `TrustedPlayerAlliesTest`, `CycloneSharedAegisLedgerTest.revocationAndRenewedConsentCannotReviveAnOldChild` | Focused pass; available only with durable Cyclone gate |
| Independent recipient recovery and lifecycle | ReadyPath async native caster proof, NPC prepared lease, session-bound child debit, death forfeiture and closed intent compaction | `CycloneSharedAegisLedgerTest` recovery, stale session, caster death, corruption, grant retry, write failure, concurrent-hit and compaction cases | Focused pass; native save flush is not a forced cross-store transaction |
| Natural Health near 388 at level 99; effective STR +2 HP, INT +2 Mana | `R261CProgression`, native stat projection | `R261CProgressionTest`, `EnemyNativeHealthTest.playerCapacityRecalculationPreservesNativeCurrentAndClampsOnReduction` | Offline covered |
| Stackable Mana Before Life affix at 60% cap; saved equipment identity | Gear affix parser/snapshot and `ManaBeforeLifeStage` | `ManaBeforeLifeAffixTest`, `GearAffixQaSuiteTest`, `Qa159PackTest` | Offline covered |
| Damage order Barrier → Mana Before Life → Health, Managuard/reservations | Native damage bridge and `ManaBeforeLifeStage` | `ManaBeforeLifeStageTest`, `R261CManaDamageReceiptTest`, `R261CCombatLatencyTest` | Offline covered; native timing awaits connected QA |
| Authoritative Health, Mana, Barrier HUD | Native stat read and `BarrierView` | `R261CResourceHudTest` | Offline covered; visual layout awaits connected QA |
| Sequential, contended, independent-player latency | Per-player forced journal monitors; resource coordinator still has a global native mutation lock | `CycloneDamageStageTest`, `CycloneBarrierLedgerTest`, `R261CCombatLatencyTest`, `R261FinalCombatLatencyTest` | Focused profile; no connected frame guarantee; forced storage remains synchronous |
| R260 compatibility and protective gate | `hywind.cyclone.durable` defaults false; R260 JAR remains installed | Full offline `check`, package validator, Guardian receipt at the published implementation commit | Offline verified; connected acceptance pending |

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
which explains the low p50. A later active-stage offline profile, with a
fresh paid own barrier, Shared Aegis child, Reflective Ward claim, downstream
finite shield, and Mana Before Life on every sample, measured 40 hits:
p50 10.057 ms, p95 10.515 ms, p99/worst 16.757 ms. Accumulated forced-write
time was 91.480 ms for own debits, 154.686 ms for child debits, and 149.373 ms
for reflection claims; measured journal monitor wait was below 0.1 ms total
for each stage in this sequential sample. These are wall-clock samples on this
host, not deterministic thresholds. The native damage call stack still blocks
on `FileChannel.force(true)` for each absorbed hit. `R261CCombatLatencyTest`
also measured eight active independent players at p99 3.900 ms and eight
threads on one player at p99 33.338 ms in the mixed barrier/Mana bridge.
The resource coordinator still serializes native mutation across players, but
the forced journal I/O dominates these offline samples. No connected frame or
native client timing is established.

## Release gate and remaining acceptance

The full Gradle `check` passed 4,146 JUnit tests across 464 reports without a
failure, error, or skip. JaCoCo completed, strict SpotBugs passed, and package
and asset checks passed. Guardian verified the published branch, and GitHub
`main` contains the exact implementation commit after a non-force fast-forward.
The post-merge offline `check` passed on that same commit. The final packaged
JAR remains outside the active RPG save pending separate deployment.

## Owner connected-game checklist after a future completed release

On a backed-up active RPG save only after a verified release is separately
deployed: cast at levels 1 and 20; check Mana spend, HUD, particle, and
cooldown; take damage with and without Mana Before Life; deplete and recast;
reconnect and restart with remaining Barrier HP; repeat with Reflective Ward
and Shared Aegis and inspect the ally's 8 m/LOS behavior; change gear and
world, then verify Health/Mana values and that older skills still function.
The candidate is ready for a separate deployment decision; the owner's
connected-game acceptance remains outstanding.
