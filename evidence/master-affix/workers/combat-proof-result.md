# Combat proof completion handoff — 2026-09-30

## Files and verification

Added only `src/test/java/com/inigmasgames/hytalerpg/gear/MasterAffixCombatCompletionTest.java` and this handoff. No production, fixture, gate, or proof-map file was edited. Direct `javac --release 25` against the installed public server JAR, existing compiled project classes, and cached JUnit jars succeeded. A direct JUnit launcher run found **12 tests; 12 passed**. This was an isolated JVM run, not a Gradle, game, server, authentication, deployment, or native ECS run. No JUnit XML was produced.

The new tests use the real catalog, authored QA roll values, mapped native carrier bindings, `GearDropGenerator.eligible`, `GearEquipmentResolution.resolve` with explicit candidate capabilities, and each named production consumer. The capability set is a test-only argument to the package-private candidate equipment policy; the production gate remains unchanged. Each control has the same base, rarity, intrinsic stats, and item level as its one-roll positive.

## Per-ID methods

| IDs | Exact method in `MasterAffixCombatCompletionTest` | Exercised owner and boundary |
| --- | --- | --- |
| WA-003 | `wa003MappedLegalFocusChangesAcceptedNativeMagicContactOnly` | Mapped legal wand, `ManagedCarrierDamageInteraction.resolve`; same-base unrolled, projectile contact, absent/wrong item, zero coefficient. No live native Gather. |
| WA-157, WA-158 | `wa157And158IndependentlyMoveNativePhysicalEndpoints` | Independent minimum/maximum range changes; `ManagedGearDamageInteraction.samplePower` searches a deterministic root that actually samples each endpoint, then `GearCombatEffects.attack` resolves that contact. Same-root unrolled and rejected item source. No native ECS leaf execution. |
| WA-069, WA-070, WA-071 | `shieldAffixesIndependentlyChangeDefenseOwnerProjectionAndWrongSlotCannotApply` | Separate shield roll, unrolled shield, `GearDefenseEffects.resolve/reduction`, wrong-slot exclusion. Proves managed mitigation projection, not observed engine HP loss. |
| GA-159, GA-160 | `ga159And160IndependentlyChangeNativeArmorProjection` | Separate local armor roll through `GearAffixRuntime.protection` and `GearDefenseEffects.resolve`; same-base unrolled and broken exclusion. Proves native effect input, not observed engine HP loss. |
| WA-066, WA-067 | `wa066And067SeparatelyIncreaseOnlyTheirPeriodicTicks` | Independent poison/bleed `GearStatusRuntime.admit` and `applyPeriodic` into `PeriodicStatusRuntime`; unrolled, wrong channel, NoProc, wrong status and wrong source, then an actual periodic tick. |
| WA-141 | `wa141QualifiedTwinAddsDistinctFortyPercentNoProcContact` | `NativeGearActionProfiles.primary`, `NativeTwinAssaultEligibility`, native shared power sampler and two `GearCombatEffects.attack` contacts; offhand has distinct item ID, 40% power, no crit, `NoProc` root. Unrolled main, missing/wrong offhand and wrong damage source reject. Native selector/Gather contact still needs connected execution. |
| WA-001, WA-002, WA-006, WA-007 | `localPhysicalGlobalPhysicalAndElementalIncreasesKeepSourceAndChannelBoundaries` | Same-base unrolled physical/global/elemental combat envelopes; rejected source and physical versus elemental channel checks. |
| WA-017..022, WA-029..034 | `localElementalFlatsAndPenetrationUseTheirOwnNativeChannel` | Six individual native channel hits or penetration/resistance reductions; same-base unrolled, wrong local source and wrong channel. The penetration assertion uses `GearCombatEffects.resisted`, not the native NPC Filter. |
| WA-010, WA-013, WA-065, WA-068, WA-078, WA-082 | `criticalDotDurationAllResistanceAndDisplacementHaveUnrolledAndInvalidConditions` | Crit accepted versus unrolled and cannot-crit; separate Burn periodic coefficients/duration and unrolled; all nonphysical resistance with physical exclusion; actual swept displacement planner with unrolled and empty recipient snapshot. |
| WA-134 | `wa134BleedAdmissionHasUnrolledWrongChannelAndNoProcControls` | `GearStatusRuntime.admit` into canonical periodic owner and regeneration suppression; unrolled chance miss, wrong channel, NoProc. |
| WA-135..140 | `wa135Through140LegalSignatureContactsHaveUnrolledAndNoProcControls` | Separate legal single-roll `GearSignatureProcRuntime` contacts, unrolled and NoProc; Crushing, Barbed discharge, armor break, Cull, Fortify, and Deadly with critical rejection. |
| WA-142, WA-143 | `wa142And143UseIncomingMeleeAndCreditedKillOnly` | Incoming melee reflection and credited kill burst at `GearSignatureProcRuntime`; unrolled, reflected/uncredited, NoProc and duplicate receipt. |

## Remaining proof boundaries for main

- **WA-069..071 and GA-159..160:** the native `HytaleGearEquipment.BeforeArmor`/engine `FilterDamage` path was not simulated. The tests prove separate accepted owner projections; they do **not** prove final native damage loss. Keep actual-loss proof fields open.
- **WA-141:** the test reaches the real native action profile and shared calculation, but does not run `NativeTwinAssaultGate.tick0`, `ManagedGearDamageInteraction.Calculator`, selector collision, or `HytaleDamageAdapter.ManagedGearGather`. Keep connected additional-hit and native metadata proof open. The child root and `noProcChild` classification are asserted; no live metadata receipt is asserted.
- **WA-003, WA-157/158, WA-017..022, WA-029..034:** calls use the production resolvers and accepted frozen snapshots, but no native ECS collision/filter was run. WA-003 NoProc is not a condition on Magic Power; zero coefficient, absent source and wrong identity are its relevant negative cases.
- **WA-013/065/068:** the new tests separately enter the periodic owner, but use a controlled Burn application. They do not qualify native on-hit proc chance or other status types.
- **Signature WA-135..143:** the new tests close single-roll controls and NoProc/condition branches at the real proc owner. Existing `GearSignatureProcRuntimeTest`, `GearStatusAdmissionTest`, and `SentinelSignatureConsumerTest` contain further native envelope and Sentinel checks. No connected damage/death receipt was run here.
- **Sentinel:** this class does not bind a Sentinel lease or native combat actor. Do not register Sentinel inheritance from these tests. Existing `SentinelSignatureConsumerTest` can be reviewed separately for WA-135..143.

No concrete production bug was found in these bounded owner calls; no production fix or patch note is requested. Main must run its permitted fresh per-ID JUnit/XML and native qualification before updating the proof map or capability gate.
