# Resource, economy, and summon gameplay completion — 2026-09-30

## Scope and execution

Created only three new JUnit source files and this note. No existing source, proof map, gate, asset fixture, Gradle task, game/server/auth process, deployment, or commit was changed or run. The three test classes compile with direct `javac` (JDK 25) against existing compiled classes, resources, cached JUnit jars, and the installed server jar. A direct JUnit Platform Launcher run found **18 tests: 12 passed, 6 failed**. All six failures are positive summon assertions currently blocked by production capability checks. They were not rewritten to branch on the closed gate.

Candidate equipment admission in these tests invokes the existing package-private `GearEquipmentResolution.resolve(..., capabilities)` through reflection from the required packages. It uses mapped authored QA fixtures, real requirements, and intact slot candidates. The economy tests also reconstitute non-QA persisted items, then use the normal public production equipment admission for WA-151. Reflection is limited to accessing the existing candidate policy and the existing cross-package Wolf cast harness; no test substitutes a gameplay consumer or mirrors a reward calculation.

## Affix → test method → actual consumer

| Affix | Method in new test class | Owner called and observed | Result |
| --- | --- | --- | --- |
| WA-009 | `MasterAffixResourceCompletionTest.wa009ChangesActualHeavySwingReleaseTimeAndRemovalRestoresIt` | `SkillExecutionService.request` / `activeWindupSeconds`, then `completeWindup` | PASS: pending release time, cost, unrolled and removed control |
| WA-012 | `wa012SeparatelyChangesActualCooldownAndRemovalRestoresIt` | `SkillExecutionService.completeWindup` / committed `CombatSnapshot.cooldownSeconds` | PASS: unrolled and removed control; windup unchanged |
| WA-095 | `wa095PaysActualManaOnAcceptedHitOnly` | `GearRecoveryRuntime.onAttack` / `pay` and `NativeResourcePort.restoreResourceAtMost` | PASS: mana credited, unrolled and cancelled controls |
| WA-097 | `wa097PaysActualManaLeechOnAcceptedHitOnly` | Same hit and resource-credit owners | PASS: independent source, unrolled and cancelled controls |
| WA-098 | `wa098CreditsKillHealthOnceAndRejectsIneligibleDeath` | `GearRecoveryRuntime.onKill` / `pay` and native resource port | PASS: duplicate reward, unrolled and ineligible controls |
| WA-103 | `wa103SeparatelyChangesCommittedCanonicalMinorHeal` | `SupportRuntime.execute` / `SupportMagnitude.healing` / committed `SupportWorldPort.heal` | PASS: own roll, unrolled, removed, capped, unaffordable controls |
| WA-104 | `wa104SeparatelyChangesCommittedCanonicalMinorHeal` | Same canonical heal owners | PASS: independent roll and same controls |
| WA-106 | `wa106KeepsPaidFriendlyTetherInsideExtendedReachOnly` | `ConnectionRuntime.healingTether` / paid `SupportMagnitude.tetherHealing` | PASS: extended second pulse, unrolled range break, target removal |
| WA-109 | `wa109ShortensOnlyControlAdmittedDuringActiveChannel` | `StatusService.apply` | PASS: unrolled, idle-channel, and non-hard-control exclusions |
| WA-113 | `MasterAffixSummonCompletionTest.wa113ChangesActualSummonAttackCoefficient` | `SummonRegistry.reserve` / `Lease.coefficient` | FAIL: lease coefficient equals control; `enabledPercent` returns zero. Broken-source exclusion is present after positive assertion. Final native attack contact is still unproved. |
| WA-114 | `wa114ChangesActualSummonMaximumHealth` | `SummonRegistry.reserve` / `Lease.maximumHealth` | FAIL: lease health equals control; gate closed. Native HP application still unproved. |
| WA-115 | `wa115ChangesActualPhysicalDamageGuardFromCapturedNativeArmor` | `HytaleSummonSystem.incomingResistance` with captured native Physical protection | FAIL: guard contribution equals control; gate closed. Final incoming damage still unproved. |
| WA-116 | `wa116ChangesActualElementalDamageGuardOnly` | `HytaleSummonSystem.incomingResistance` for Ice and Physical | FAIL: Ice contribution equals control; gate closed. Final incoming damage still unproved. |
| WA-117 | `wa117ChangesActualClaimedAttackCadence` | `SummonRegistry.reserve` / `activate` / `claimAttack` | FAIL: attack interval equals control; gate closed. Broken-source control and claim assertions follow the positive. |
| WA-119 | `wa119ExtendsActualNaturalExpiryAndAttackWindow` | `SummonRegistry.reserve` / `activate` / `claimAttack` | FAIL: expiry equals control; gate closed. Broken-source and post-expiry controls follow the positive. |
| WA-151 | `MasterAffixEconomyCompletionTest.wa151ProductionSourceChangesRealLootWeightsAndSelection` | `GearEquipmentResolution.resolve`, `GearLootService.death`, `GearDropGenerator.generate` | PASS: valid non-QA equipment supplies MF to a frozen credited source; a deterministic source changes selected rarity and weights; duplicate delivery cannot reroll; source identity is retained |
| WA-152 | `wa152CreditedGoldAwardIsOncePerDeathAndControlHasNoBonus` | candidate equipment admission, `EncounterContributions.DeathPlan.reward`, `RpgLoadoutService.awardEarned` | PASS: credited Gold increases once; duplicate is rejected; separate unrolled bonus control awards only base Gold |
| WA-154 | `wa154OnlyLegalReducedItemEquipsLowerStatActor` | `GearRequirements.combine` / public `GearEquipmentResolution.resolve` | PASS: legally authored reduced item equips at lower permanent stat, same-base unrolled gate rejects; removed and broken candidates have no authority |

## Actual remaining gaps and main-owned suggestions

- **WA-110:** No new positive test was claimed. The finite friendly duration multiplier currently lives inside `HytaleSupportSystem`'s native `finiteEffect` adapter. The existing simulator's `Stage09FiniteSupportTest.Harness.finiteEffect` bypasses that adapter and applies the authored duration directly. A test that merely multiplies duration in the harness would be a math mirror. Main should place duration selection in the shared finite-support owner (called by both native adapter and offline simulator) without changing any base duration, then prove accepted friendly finite expiry against unrolled and sustained-aura exclusions.
- **WA-118:** No movement positive was claimed. `SummonNativeMovement.assetId` proves an asset mapping, while `SummonNativeMovement.refresh` is the native NPC effect consumer and returns immediately under the closed `WA-118` gate. The offline tests have no native ECS actor/effect-controller fixture to observe speed. Main should add a bounded native-effect consumer fixture or a shared applied-movement projection, then assert actual recipient speed against control and removed/decoy exclusions.
- **WA-113..117, WA-119:** `GearAffixRuntime.ENABLED` excludes these IDs. `SummonRegistry.enabledPercent` suppresses WA-113, WA-114, WA-117, WA-119; `HytaleSummonSystem.incomingResistance` suppresses WA-115 and WA-116. The six new positive tests fail exactly at those consumers. Main owns capability qualification and any gate edit after connected owner proof. The lease coefficient/HP and damage-guard checks still need final native attack, health, and incoming-loss observations; these tests must not be registered as passing full gameplay proof yet.
- **WA-151:** The passing test drives the production loot service with a frozen MF value from accepted non-QA gear. The native equipment reader-to-death-freeze handoff itself is not exercised in this offline test.
- **WA-152:** The passing test uses candidate equipment admission and the real durable Gold award owner. The native credited-death callback that freezes `WA-152` from equipped gear is not exercised, and production admission remains closed.
- **WA-154:** The passing item is a legally authored QA fixture used with public equipment admission. A separately generated non-QA reduced-requirements item has not been shown here.

No passing XML or proof-map registration was written. The direct JUnit result is intentionally red because the six summon positives do not occur with the current production gate.
