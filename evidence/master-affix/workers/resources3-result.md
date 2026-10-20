# Resources3 production handoff

30 September 2026. No Gradle, game/server/auth launch, deployment, commit, push, or additional agent. The production affix capability gate remains closed pending the main owner's tests. No connected QA is asserted.

## Production changes

- WA-105: `FiniteSupportEffects.Effect` retains the original unscaled shield capacity. `reprojectShieldCapacity` uses that base on every live equipment revision, keeps the smaller current remaining amount, and does not restart the duration. The registered `HytaleSupportSystem` tick now calls it with `GearNativeItems.effects(ref,store).snapshot()`.
- WA-108: paid channel damage and Healing Tether pulses now carry ramp Increased in their actual `SkillExecutionContext` modifier buckets. `ConnectionRuntime` sends those snapshots to the existing damage/healing owners after payment. The zero-clamped bucket case is recoverable; coefficient ratios are no longer used.
- WA-112: added canonical Ameliorate catalog/runtime profile, native bridge item, localization, and icon entry. The learned direct-target skill uses 12 Mana, 4 s cooldown, 18 m range, Staff/Spellbook eligibility, and the standard support target validation. The production `HytaleSkillExecutionSystem` operation removes actual shared-controller Stun, Silence, Blind, Root, and Frozen through `SupportCleanse`, then synchronizes native status projection. A successful removal produces a `(world, root, caster, recipient, removedCount, time)` receipt. `HytaleSupportSystem.cleanseResolved` passes it to `FiniteSupportEffects`, which creates a 3 s, normal-Max-Health-based shield only for an accepted WA-112 snapshot and holds a 10 s caster/recipient lock across gear changes. Empty/failed cleanses grant nothing. Damage absorption uses the existing finite shield controller. Source-owned periodic statuses and Fear are outside this new skill's removal contract because their independent active owners cannot be cleared by `StatusService.remove` alone.
- Ameliorate was absent from the checkout despite the Master Affix reference. Its cast cost, cooldown, range, and proposed Outlander Priest acquisition follow the current Minor Heal support template; these newly selected gameplay values require owner review. The Healing Beam image is reused for its new distinct icon file.
- WA-121–133: `GearSkillRanks` now indexes canonical skill tags once on class initialization, retaining frozen selector/carrier and rarity gates. `projectLearned` returns base/effective ranks for UI from the same selector as execution and excludes unlearned skills. A learned `SkillExecutionService` cast is tested for all 13 IDs with positive, unequipped control, and ineligible rarity/selector negatives.
- The icon updater still rejects a running Hytale executable or actual server main invocation. It no longer mistakes a JUnit process with the server JAR only on its classpath for a running server.

## Deterministic verification

Manual `javac` compilation with the installed public server JAR, repository class output, and declared dependency JARs passed for the changed support, connection, catalog, rank, native support, and native skill execution paths. This was a focused source compile, not Gradle or a packaged build.

JUnit Platform launcher, with current source resources before prior build resources:

- 91/91 passed: Stage09BarrierSupportTest, Stage09SupportRuntimeTest, Stage13SupportTetherTest, GearSupportConsumerTest, GearSkillRankExecutionTest, GearSkillRanksTest, GearResourceConsumerTest.
- 121/121 passed: catalog/slot, Stage04 execution, Stage06 cohort B, Stage08 connection, Stage09 finite support, Stage10 cage, Stage13 casting correction, hot path, skill tree/icons, and RpgIconIndex.
- 6/6 passed: Stage11CompatibilityMatrixTest and Stage13CoverageLedgerTest.
- 9/9 passed: Stage13IconUpdaterTest against isolated temporary JARs after the process check correction.
- 1/1 passed: ReadyPathContentGrowthTest at 98, 196, and 392 skill definitions; raw output in `resources3-readypath.log`.

WA-105's test cycles 20 repeated unequip/equip reprojections after absorption and checks capacity, remaining points, and original skill instance. WA-112's tests exercise learned Ameliorate execution, successful removal, native projection callback, no status, no affix, wrong receipt world, 5 s lock, 10 s release, expiry, and actual shield absorption. WA-108's tests cover paid damage pulses, Healing Tether's real healing calculation, unpaid pulses, and a previously zero-clamped modifier bucket.

## Integration state

The existing registered `HytaleSupportSystem` and `HytaleSkillExecutionSystem` consume these paths. No callback requires a missing implementation. The main owner still controls Gradle, full-suite/build validation, capability admission, and connected QA. These focused results do not certify all 160 affixes.

Temporary `resources3-classes/` contains only the manual `javac` outputs used for these tests. The automatic command review rejected recursive removal of that directory after its resolved workspace path was verified, so the class output remains under this evidence folder. The temporary test runner and extracted manifest were removed individually.
