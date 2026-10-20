# Durability worker result

30 September 2026. Source inspection and deterministic adapter tests authored; **no Gradle, server, client, or connected QA run**. Neither WA-083 nor WA-153 is claimed complete or enabled in this checkout.

## Changed files

- `src/main/java/com/inigmasgames/hytalerpg/gear/NativeAffixBlockCostSystem.java`
- `src/test/java/com/inigmasgames/hytalerpg/gear/NativeAffixBlockCostSystemTest.java`
- `src/main/java/com/inigmasgames/hytalerpg/gear/NativeAffixDurabilitySystems.java` (new)
- `src/test/java/com/inigmasgames/hytalerpg/gear/NativeAffixDurabilitySystemsTest.java` (new)
- This evidence file.

No carrier assets, inventory code, gear snapshot, runtime gate, plugin registration, or combat owner file was edited.

## WA-083 · Efficient Guarding

`NativeAffixBlockCostSystem` remains an InspectDamageGroup system with `BEFORE DamageSystems.DamageStamina`. Installed `DamageStamina.handleInternal` reads `Damage.BLOCKED` and `STAMINA_DRAIN_MULTIPLIER` before calling `EntityStatMap.subtractStatValue`. Existing `apply` composes the reduction once into that metadata. The revised handler uses an active native Secondary `InteractionChain`'s held section/slot/item, checks it against the current hotbar or utility active slot, then uses `GearNativeItems.canUse` and the authoritative snapshot's `forItem(UUID)`. It rejects stale and ambiguous chains. This permits an actual utility shield to contribute while preventing a hotbar affix from being borrowed by an offhand guard.

Tests added: utility source positive; stale item, finished chain, wrong slot and ambiguous chains negative; retained blocked/cancelled/no-affix/multiplier tests. These are source-selection and native Damage metadata adapter tests, not connected block/guard-break proof. Current `GearAffixRuntime.ENABLED` excludes WA-083 and no registration exists, so production effect is still gated.

## WA-153 · Lasting Craft

Installed `DamageSystems.DamageArmor` and `DamageSystems.DamageAttackerTool` both run in InspectDamageGroup and call `ItemUtils.decreaseItemStackDurability` directly. `DamageArmor` checks `DamageCause.isDurabilityLoss`, chooses one unbroken armor slot using `RandomExtra.randomRange`, then debits section `-3`. `DamageAttackerTool` checks the same cause and `Damage.EntitySource`, then debits the source's current hotbar item in section `-1`. There is no cancellable item-specific event before either call.

`NativeAffixDurabilitySystems.Armor` and `.Attacker` reproduce those native producer conditions. The armor replacement retains the same unbroken-slot candidate selection and native random index. Stock, unaffixed, malformed, and unprevented managed losses all call the original `ItemUtils.decreaseItemStackDurability` with the same actor, stack, section and slot. For a valid equipped managed item with positive asset loss, `debit` rolls WA-153 before that call, once per Damage event and producer, using separate metadata keys. Successful prevention skips the destructive write. The attacker path additionally requires the exact item UUID in `HytaleDamageAdapter.gearHit(damage)`; a swapped-in current hotbar item cannot borrow a launched projectile's affix. Existing item-container CAS remains the custody boundary.

Tests added: legal QA snapshot with WA-153, 1-durability prevention and failure, stock/no-affix control, duplicate callback, independent armor/attacker opportunities, actual native `SimpleItemContainer` armor candidate selection, and missing/wrong/correct managed hit source identity. These invoke production decision/selection methods; the native ECS handlers and inventory writer have not been run in a connected test. Do not interpret the legal snapshot constructor as proving equipment admission.

Installed managed gear assets have positive `DurabilityLossOnHit` on weapons and armor. The current managed item JSON contains no `DurabilityLossOnDeath: true` (0 matches across 1,590 carrier files); the installed death writer checks that flag, so these carriers currently have no death-loss opportunity. `GearInstance` rejects affixes on TOOL category, so managed tool-like block use is not a legal WA-153 carrier. No separate guard durability writer was established in the inspected native block path; `DamageStamina` debits Stamina only. Recheck new shield carrier assets once catalog work lands, especially inherited loss/death fields and guard interactions.

## Required cross-owner wiring and unresolved proof

1. In `HyArpgPlugin` after the native `DamageModule` has registered its systems, remove `DamageSystems.DamageArmor.class` and `DamageSystems.DamageAttackerTool.class` from `EntityStore.REGISTRY`, then register `new NativeAffixDurabilitySystems.Armor()` and `new NativeAffixDurabilitySystems.Attacker()` through `getEntityStoreRegistry()`. Register `new NativeAffixBlockCostSystem()` in the same native damage registration area. The substitutions must be atomic at setup: never run native and replacement writers together. Verify registry `hasSystemClass` before and after, including an ordinary stock armor/weapon debit. The replacement armor system orders before native `DamageStamina`; attacker orders after it, retaining the installed producer order. If core-system removal is disallowed at plugin setup or ordering differs, leave WA-153 gated and record that exact failing experiment; no asset field was neutralized.
2. Retain the current `GearAffixRuntime.ENABLED` gate until the main integration test verifies each consumer. Only then admit WA-083 and WA-153. New catalog shield bases must have a genuine Secondary guard profile and positive Stamina cost for WA-083; run the positive test with that legal carrier. Gear validity must include the exact active utility item in its snapshot.
3. Run deterministic ECS tests that execute `Armor.handle` and `Attacker.handle` with real `ItemUtils` transactions, including transfer/CAS failure, repeated contact and restart, plus connected block success, failed block and guard-break affordability tests. Confirm managed projectile hit metadata is attached in Gather before the Inspect attacker replacement and compare impact-after-swap behavior. Confirm armor stock selection/loss distribution and nonplayer behavior remain native-equivalent.
4. Audit all new shield/weapon carrier loss fields and any managed guard or death interaction introduced by the catalog worker. The current replacements cover the two installed damage systems above; a newly introduced tool, guard, interaction `AdjustHeldItemDurability`, or death producer requires its own pre-debit route before WA-153 can be called complete. Native death inventory custody was not changed.

## Status

WA-083: adapter implemented, tests authored, registration/gate/legal shield and connected proof outstanding. WA-153: two installed damage-writer substitutions implemented, tests authored, registration/gate/full producer and connected proof outstanding. No 160-functional or release claim.
