# Combat continuation 3 — 2026-09-30

## Result and limits

The owned direct skill path now freezes a valid contributing gear UUID before the resource payment transaction, carries it in `SkillExecutionContext` through derived children, and submits Quick Slash's authored Physical/Fire components through the existing native gear-channel damage adapter. It preserves one existing Quick Slash critical outcome, one local elemental flat contribution across authored components, the original source-Fire `WeaponFireDecision` witness, and one proc-eligible component per victim. The normal scalar skill channel path now reads the committed UUID rather than re-resolving a carrier at impact. Ambiguous same-carrier items without an explicit UUID reject before payment.

This is not a 160-affix completion or connected-game verification claim. The native equipment reader has not yet populated the new UUID field, and full compilation and tests are reserved for the main agent. `GearAffixRuntime.ENABLED` was not widened. No Gradle, server, game launch, auth, deployment, commit or push was run.

## Files changed in this continuation

- `src/main/java/com/inigmasgames/hytalerpg/execution/SkillExecutionPort.java`: optional exact managed instance UUID on `Item`, retaining the prior three-argument constructor.
- `src/main/java/com/inigmasgames/hytalerpg/execution/SkillExecutionContext.java`: frozen source UUID and child propagation, retaining old constructors.
- `src/main/java/com/inigmasgames/hytalerpg/execution/SkillExecutionService.java`: capture and validate the exact source from valid gear plus held equipment before payment, preserve it through commit.
- `src/main/java/com/inigmasgames/hytalerpg/gear/GearCombatEffects.java`: accept legitimate sword/twin action variants during source validation, and allow one local flat addition across Quick Slash's authored components.
- `src/main/java/com/inigmasgames/hytalerpg/combat/hytale/HytaleDamageAdapter.java`: attach the original weapon source witness along with the gear channel hit on the actual `DamageSystems.executeDamage` path.
- `src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSkillExecutionSystem.java`: direct damage methods and Quick Slash's `weaponDamage` now submit positive vector channels with committed item identity and a shared victim contact.
- `src/test/java/com/inigmasgames/hytalerpg/SkillGearSourceCommitTest.java`: actual `SkillExecutionService.request` positive/ambiguous/missing source and payment checks.
- `src/test/java/com/inigmasgames/hytalerpg/gear/GearCombatProductionTest.java`: mixed Quick Slash Physical/Fire through the native Damage constructor, source-Fire witness, one proc flag, local-source control and foreign-witness rejection. The preceding combat tests in this same file cover WA-004–007/010–011/017–052 native composition/Gather/Filter cases.

## Exact scope

WA-004–007, WA-010–011 and WA-017–052 have direct skill channel consumers, including Quick Slash's mixed source components. These paths consume `GearEffectSnapshot` obtained from valid equipment by the native `Port.gearEffects().snapshot()` and preserve the existing native attribute, skill coefficient, More/Less, critical and Damage application owners. WA-041–052 impact conditions use `HytaleConditionalDamage.GearGather`; main now registers `NativeNormalHealthMaximum.value(stats)` and the canonical periodic provider. WA-029–034 penetration continues through the one NPC resistance Filter and combined player resistance Filter. WA-069–071 and WA-084 still use the main armor projection and existing critical taken Filter; dynamic ArmorBreak's native Filter remains outside this worker's ownership.

## Native wiring already present

`HyArpgPlugin` registers `HytaleDamageAdapter.ManagedGearGather`, `HytaleConditionalDamage.GearGather`, `HytaleDamageAdapter.GearResistanceFilter`, and the difficulty Resistance Filter. `HytaleSkillExecutionSystem.Port.damage` calls `HytaleDamageAdapter.applyGearResolved` for each positive channel. Quick Slash now enters that same branch from `weaponDamage`; the adapter constructs native Damage and calls `DamageSystems.executeDamage`. Each component shares the root/victim contact. The source Fire `WeaponComponent` remains attached only to the authored source channel, so Fire added by gear cannot create a second sustain source.

## Required cross-owner wiring and remaining verification

1. **Native equipment owner/main:** in `HytaleEquipmentAdapter.item`, the managed branch already calls `GearNativeItems.read(stack)` and checks `GearNativeItems.canUse`. Change its `new SkillExecutionPort.Item(stack.getItemId(),nativePower.kind(),descriptor)` to pass `gear.identity()` as the fourth argument. Nonmanaged branches retain null. This is necessary for two valid items with the same carrier to execute; the current path rejects them before payment. The decoded stack has already passed native carrier/variant validation in `GearNativeItems.read`.
2. **Native skill owner:** `HytaleSkillExecutionSystem.Port.sameItem` should compare nonnull managed `gearItemId` values when release requires the committed equipment. `skillProjectileSource` should prefer `context.gearSourceItemId()` before carrier fallback. `summonProjectileContext` should preserve that field when it constructs a derived context. These methods are outside the direct-damage-only ownership here.
3. **Proc owner:** finish and verify multi-channel receipt coalescing, including the case where the first component is canceled and a later component lands. Consume `GearDefenseEffects.reduction(view,level,armorBreakFraction)` from canonical live ArmorBreak state in the native defense Filter. The helper alone does not complete that affix.
4. **Main:** compile and run focused/full tests and native harness traces with the installed server JAR. The newly written tests were not run. Confirm the managed sword/twin action variant profiles, Fire sustain witness, one critical outcome and local-source behavior in connected QA. Keep the `ENABLED` gate until consumers pass.

`git diff --check` reported no whitespace errors on tracked owned edits; Git only warned about line-ending conversion. No untracked-file test execution or connected verification is claimed.
