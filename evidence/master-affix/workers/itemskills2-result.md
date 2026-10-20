# Item skills continuation evidence (WA-144–150)

Status: item skill and player Aura production paths in this ownership are implemented and pass the focused offline matrix. **WA-144–150 are not claimed fully wired or enabled**: the main-owned receipt coefficient and plugin registration, plus summons3-owned Sentinel child/Aura lifecycle calls, are still absent from the shared checkout. Native connected contacts and the full build were not exercised.

## Shared receipt contract requested from main owner

`HytaleDamageLifecycleSystems.AppliedReceipt` now has `acceptedDamage` (observed in the shared checkout). It still needs a typed **proc coefficient** from the accepted native root. `GearHitSource` supplies item UUID, root, contact and frozen snapshot; the facade rejects missing source evidence. The application observer must report cancelled/blocked outcomes after native Apply. No reflection or estimate from HP delta supplies the coefficient. Status/procs receipt users need the same semantic fields.

## Exact registration contract for main

Construct `new NativeItemAffixBindings(loadouts, skillExecutionSystem, AppliedReceipt::acceptedDamage, AppliedReceipt::procCoefficient, sentinelChildPort)` after the native skill and summon owners exist. The `procCoefficient` method is the requested main-owned receipt addition; `sentinelChildPort` is the summons3-owned later world-step child executor. Register `bindings.availabilityTick()` after `gearEquipment.new Tick()` and before skill execution, register `bindings.blockObserver()` in the native damage registry, add `bindings.applicationObserver()` to the existing `gearReceiptObservers` list consumed by one `HytaleDamageLifecycleSystems.Application`, and register `bindings.removal()`. Call `bindings.detach(uuid)` at disconnect and `bindings.worldUnload(worldUuid)` at world unload. Do not create a second Application system. `HytaleSupportSystem` already synchronizes player item Auras on its registered tick.

The managed basic attack producer now registers an envelope for WA-145/146 even when no other vector affix is present; its Gather attaches the exact gear hit. `NativeItemAffixBindings.sentinelBlock(receipt,now)` is the typed WA-147 route for the summon owner after an actual successful Sentinel block. Sentinel Aura API: `SkillExecutionService.boundItemAura(world,sentinel,boundItem,skill,source,derivedStats,equipment,target)`, then `HytaleSupportSystem.activateSentinelItemAura(context,boundItem,now,port)`; each Sentinel tick calls `tickSentinelItemAuras`, and removal/death/transfer calls `detachSentinelItemAuras`. The port must validate bound source, world, range, LOS and eligible recipients. `SupportRuntime` shares recipient aggregation between player and Sentinel projections and bypasses NPC Mana only for D08 item tokens.

Main owns capability admission and Skill UI projection. The shared `RpgSkillTreeProjectionService` now calls `RpgLoadoutService.skillAvailable(player,skillId)` for WA-144 temporary access; `learnedSkill(player,skillId)` distinguishes learned ownership. The native skill port now returns zero WA-121–133 bonus for unlearned grants. Do not enable any IDs until full registration and connected evidence are accepted.

## Implemented and verified here

- `SkillExecutionService.itemChild` builds fixed rank-one Fire/Frost Bolt/Minor Heal contexts with an unlinked canonical plan, zero resource cost/cooldown, frozen source item, and no learned/loadout requirement. `HytaleSkillExecutionSystem.enqueueItemChild` drains on the following player tick. It uses native target, hostile, protected-target and LOS checks, then existing projectile/support executors. Projectile source lookup uses the child's exact item UUID even when its frozen equipment snapshot contains other items. Child damage sets NoProc through native metadata. `GearItemPower` provides a frozen magic or local Physical source sample for legal melee/ranged/focus carriers.
- `SupportRuntime.activateItemAura`, `endItemAura`, `itemAuras` and item tokens keep manual and item activations distinct under the four-Aura/field budget. The same strongest recipient projection loops include both. Player Emanatism/Pedanticism reserve capacity; Thorns pays canonical activation and quarter-second upkeep. Removal releases reservation and fields without a Mana refill. D08 Sentinel item tokens skip NPC Mana cost/upkeep and use transient support sessions.
- `HytaleSupportSystem.syncItemAuras` projects from validated equipment each tick and ends absent sources before ordinary support projection. Native validity rejects a removed item. Public Sentinel helper methods are ready for summons3's bound-item lifecycle.
- `NativeItemAffixBindings` owns the trigger runtime, typed post-application observer, actual post-block observer, availability tick, and removal cleanup. Main only constructs/registers/calls these surfaces.

## Focused test matrix

| Path | Positive | Negative/cleanup | Result |
| --- | --- | --- | --- |
| WA-144 availability | ordinary assigned slot, base rank 1, paid canonical cast, two grant sources | last source withdrawal, reconnect plan rejection, invalid selector, learned ownership preserved | JUnit passed |
| WA-145/146 admission | exact item, rank-one Fire/Frost child, shared owner lock | replay, NoProc, chance miss, reflected/blocked controls | JUnit passed |
| WA-147 admission | actual successful block queues self Minor Heal | ordinary hit/failed block on the same contact/replay/8 s lock | JUnit passed |
| Child context | all three canonical rank-one profiles, no passive order, no resource/cooldown charge, frozen item | wrong target rejected | JUnit passed |
| WA-148 manual/item overlap | separate tokens, strongest one recipient bonus | item removal preserves manual; disconnect releases without refill | JUnit passed |
| WA-149 sustain | activation and quarter-second upkeep payment, duplicate sources yield one strongest reflection | removal leaves no Aura or Mana refund | JUnit passed |
| WA-150 Pedanticism | 20% reservation, 15% cooldown recovery | removal clears recipient rate without Mana refund | JUnit passed |
| D08 pure runtime | free item projection, no NPC Mana charge | world invalidation removes contribution | JUnit passed |
| Sentinel context factory | bound-item rank-one plan without Sentinel player loadout | zero resource quote and no linked passives | JUnit passed |

Offline `javac --release 25` compiled the changed native and core production sources together with current shared dependencies; the exact projectile-source correction and final trigger/runtime/facade changes were recompiled against the current shared checkout. A direct JUnit Platform launcher reran the four focused classes against that output: **16 tests, 16 passed**. This proves domain/executor construction, not connected native contacts, main registration or Sentinel owner calls.

## Verification boundary

No Gradle, server/client, connected QA, auth, deployments, commit, or push are authorized. Capability IDs remain main-owned and are not enabled by this worker.
