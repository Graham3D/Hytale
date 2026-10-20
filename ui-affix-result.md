# UI affix projection result

## Files changed

- `src/main/java/com/inigmasgames/hytalerpg/ui/inventory/AdvancedStatsViewModel.java`
- `src/test/java/com/inigmasgames/hytalerpg/ui/inventory/AdvancedStatsAffixProjectionTest.java`
- `src/test/java/com/inigmasgames/hytalerpg/RpgSkillTreeAffixProjectionTest.java`

## Projection

Advanced Stats now reads the accepted `GearAffixRuntime.Effects.snapshot()` and shows nonzero, actor-wide elemental increased/penetration buckets, status modifiers, resource payment/regen terms, healing/support terms, summon terms, and currency/material utility. Elemental buckets come from `GearCombatEffects.attack`; cost reductions come from `GearResourceModifiers.reduction`. Other rows show typed frozen snapshot contributions with their gameplay scope stated explicitly. The existing native resource maximum, derived crit/cooldown, Magic Find, and six capped resistance rows remain owner projections. Local weapon flats/conversions, item-only status chances, conditional hit values, and a supposed effective native light radius are omitted because this view has no contributing item, target, or measured native light calibration. The raw all-skill roll row was removed; the Skill Tree shows effective ranks per learned skill through its shared rank resolver.

## Regression evidence added

- Advanced Stats tests compare crit, crit multiplier, cooldown, attribute, and capacity projections with `GearAffixRuntime.Effects.derive`, including critical/cooldown caps and unequip baseline. A separate currently enabled cooldown/attribute/capacity case goes through `GearAffixRuntime.effects`; all six resistance tests remain.
- All newly added row IDs have frozen snapshot value and empty-snapshot checks. Elemental rows compare all six channel buckets with `GearCombatEffects.attack`. Status resistance/penetration compare `GearStatusRuntime.chance`; resource cost rows compare `GearResourceModifiers.reduction`; tether and material reach compare `GearSupportModifiers.primaryReach` and `NativeAffixMaterialPickup.reach`.
- New Skill Tree tests use a real `RpgLoadoutService` fixture: learned-only +2 all-skill ranks at base rank 20 and another learned rank, unlearned exclusion, UI rank versus a committed paid `SkillExecutionService` cast, unequip restoration, and a source-bound Borrowed Arts rank-one temporary assignment with no saved learning.

## Proof limits

- No Gradle or server/client run was performed in this lane, per main's instruction. `git diff --check` on the tracked view-model file passed; the two new test files remain untracked for main to run/review.
- The broad support, regeneration, summon, currency, and material rows have snapshot agreement and empty-snapshot checks here, but this UI test does not compare every one with a complete corresponding gameplay event. Existing owner suites such as `GearSupportConsumerTest`, `GearResourceConsumerTest`, and `SummonAffixLeaseTest` cover some paths; QA/main must combine those results with this projection test. This is not proof that every displayed field reaches native gameplay.
- Tests construct frozen snapshots directly; they do not prove all affixes pass the current `GearAffixRuntime.ENABLED` admission gate or native equipment validity. Production page input is the accepted current-effects snapshot. Native light radius still needs the required native4 measured calibration data before an effective-radius row can be truthful.
- This work makes no 160/160 runtime or connected-game claim.
