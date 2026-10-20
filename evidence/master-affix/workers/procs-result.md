# Signature proc worker result — 2026-09-30

## Status

**Scope unfinished. None of WA-135, WA-136, WA-137, WA-138, WA-139, WA-140, WA-142, WA-143 is fully production integrated or native verified yet.** I did not alter `GearAffixRuntime.ENABLED`; the existing gate still excludes these IDs. No Gradle, server, game, deployment, commit, or push was run. Tests below were written, not run. No 160/160 claim. WA-134 remains with the status owner; WA-141 remains with the action/dual-contact owner.

## Owned files added

- `src/main/java/com/inigmasgames/hytalerpg/gear/GearSignatureProcRuntime.java`: actual item source snapshot consumer with keyed chance hook, Deadly noncritical Physical outcome, Crushing survivor/cap/ICD, Barbed stored fragments, Riveting rating view and lock, Cull execute request, Resolute timed incoming Less, Retribution pair reflection, and credited Kill Burst. Synchronized bounded replay ledger, queue limits and explicit actor/world cleanup methods. It does not construct a fake combat engine.
- `src/main/java/com/inigmasgames/hytalerpg/execution/hytale/NativeGearSignatureProcs.java`: bounded, world-epoch guarded next-world-tick native `DamageSystems.executeDamage` child dispatcher, actual Hytale `Damage` metadata with target HP observed before each component, native hostile/LOS burst query, protected execute callback boundary.
- `src/main/java/com/inigmasgames/hytalerpg/execution/hytale/NativeGearSignatureReceipt.java`: `HytaleDamageLifecycleSystems.AppliedObserver` bridge, plus `RawBefore`/`RawAfter` native HP observation for metadata-free NPC melee; requires authenticated accepted-strike/melee facts from the combat owner; separate credited-death callback.
- `src/main/java/com/inigmasgames/hytalerpg/execution/hytale/NativeGearSignatureDefenseFilter.java`: native ordered Filter contribution for WA-138 and WA-140, requiring the defense owner's actual unbroken rating and K; native direct-action witness excludes metadata-free periodic/environmental damage.
- `src/test/java/com/inigmasgames/hytalerpg/gear/GearSignatureProcRuntimeTest.java`: legal single-affix QA carrier previews; deterministic positive/control/negative cases for each of the eight IDs; actual native Damage envelope and queue drain positive/rejected/same-tick-control assertions; native `Damage` Filter amount mutation for armor break and fortify, with unchanged control and world-cleanup assertions. `GearEffectSnapshot` construction is controlled test input, not proof of native valid-equipment admission.

## Exact implemented operation and remaining boundary

| ID | Owned operation | Required native completion |
| --- | --- | --- |
| WA-135 | Chance, 4% Common/2% Elite survivor HP, 2x precrit Physical cap, 1 s source/target lock, queued Physical child | Authenticated post-all-channel HP/contact facts and proc coefficient; register later-tick dispatcher. Boss opt-in policy remains absent. |
| WA-136 | Local noncritical-only Physical double, stable strike/purpose roll, NoProc exclusion | Combat owner calls `deadly` in `GearCombatEffects.attack` after ordinary critical selection and before native damage assembly/mitigation; preserve elemental vector. |
| WA-137 | Three-charge/six-second oldest fragment records, three-record bound, owner/target consumption, queued child | Actual complete contact callback; native registration. |
| WA-138 | 15% rating break for 3 s, strongest-only, 5 s source/target lock and capped D01 Filter ratio | Combat/defense owner provides *actual* unbroken NPC/player Defense rating and K to `DefenseView`; verify installed native armor Filter order and target protection. |
| WA-139 | 5% normal-MaxHealth survivor threshold, Boss/PvP/script veto, once-target accepted execute request; vetoed requests may retry on a later valid hit | Implement `NativeGearSignatureProcs.Execution.request` through existing protected execute/death reward owner. No direct entity deletion or HP write is supplied here. |
| WA-140 | Melee chance, 5% Less incoming direct Hit for 3 s, no refresh, 5 s owner lock across swaps | Accepted melee classification on outgoing receipt; register defense Filter before shield/application. |
| WA-142 | Valid equipped shield total, flat Physical reflect, 0.5 s wearer/attacker lock, Reflect/NoProc native child | Incoming direct hostile melee classification and receipt; register post-apply observer, metadata-free raw native receipt systems and dispatcher. |
| WA-143 | Credited-death-only 35% premitigation channel vector, 2 m native hostile/LOS query, 1 s owner lock, NoProc child | Death reward owner calls separate `creditedKill` only after deduplicated credited death receipt; source vector and root must be retained. |

## Exact cross-owner hooks

1. **Combat owner (`GearCombatEffects` and `HytaleDamageAdapter`, not edited):** call `GearSignatureProcRuntime.deadly(snapshot,itemId,stableStrikeId,physical,critical,itemAttack,noProc,procCoefficient)` before constructing the native component vector. Extend the accepted `GearHitSource` or associated immutable contact metadata with source actor, exact item, stable root/strike/contact, original noncritical premitigation Physical, accepted postcrit premitigation channel composition, proc coefficient, delivery melee/ranged, and NoProc/Reflect flags. Current `GearCombatEffects.Hit` lacks coefficient, delivery and noncritical vector; `GearHitSource` has only hit/contact/channel. No invented amount from `Damage.getAmount()` is acceptable.
2. **Application and main owner (`HytaleDamageLifecycleSystems.Application`, `HyArpgPlugin`, not edited):** instantiate one `GearSignatureProcRuntime` with the existing stable keyed RNG (`Roll.sample(stableEventPurpose)`), one `NativeGearSignatureProcs`, and `NativeGearSignatureReceipt` with `Facts` that aggregates all native channel components for one contact. `Facts.outgoing` must supply actual post-application HP loss, survivor HP, normal MaxHealth, encounter rank (`COMMON`/`SPECIALIST` → `Kind.COMMON`, `ELITE`/`MINIBOSS` → `Kind.ELITE`, `BOSS` → `Kind.BOSS`, player → `Kind.PLAYER`), protected/script veto, hostility and exact source item. `Facts.incoming` must supply actual hostile direct melee and the wearer snapshot from `GearNativeItems.effects(wearer,buffer).snapshot()` for WA-142. Compose this observer with the recovery/status observers; register `BeforeApplication` before native `ApplyDamage`, then `Application` after it. Register `NativeGearSignatureReceipt.RawBefore` before native `ApplyDamage` and `RawAfter` after it for metadata-free incoming NPC melee; `Facts.rawIncoming` must classify actual native melee interaction (`Damage.INTERACTION_TYPE`), hostility and guarding wearer without inventing a gear hit. Register `NativeGearSignatureProcs.Dispatch` as an entity ticking system. Register `NativeGearSignatureDefenseFilter` after native Filter and before `SupportDamageSystems.Shield`/`ApplyDamage` with the true D01 `DefenseView` adapter. A native channel component must not trigger the post-hit proc before the full logical contact is complete.
3. **Death reward owner (`HytaleEncounterRewards`, not edited):** after its once-only credited death decision, call `NativeGearSignatureReceipt.creditedKill(contact,store,source)` with the original killing direct hit's root, legal snapshot, coefficient and premitigation channel composition. Burst children are tagged `TRIGGERED` with `canProc=false` and cannot be passed back as credited direct kill inputs. Current generic `AppliedReceipt` alone does not prove reward credit.
4. **Execute/defense owner:** implement `NativeGearSignatureProcs.Execution.request` returning true only for an accepted request through the existing scripted protection and canonical death/reward path. Implement `NativeGearSignatureDefenseFilter.DefenseView.rating` using actual unbroken target rating and K, including NPCs; do not project a second armor layer. WA-139 and WA-138 remain incomplete without these implementations.
5. **Lifecycle owner:** call `runtime.clearActor` on source/target despawn and `runtime.clearWorld` plus `nativePort.clearWorld` on world unload. The 8,192 replay claim budget and 4,096 pending-child budget fail closed with explicit exceptions; a long-lived generation/retirement policy for roots is still needed before production enablement.
6. **Gate owner:** add IDs to `GearAffixRuntime.ENABLED` only after callbacks, adapter order, supported carriers and deterministic tests compile and pass. Existing valid-equipment snapshot admission excludes these IDs now.

## Known risks and evidence limits

- The tests do not call a registered native event system or an existing execute owner. The native adapter assertion proves that the bounded queue drains into a Hytale `Damage` object with its cause and metadata, and that a rejected child submits nothing; it does not prove connected combat or server registration.
- `HytaleDamageMetadata.noLeech()` currently returns false for `TRIGGERED`. The existing `GearAppliedHitRecovery` rejects these children because it requires `Origin.DIRECT` and `canProc=true`; any other leech consumer must honor the new child `noLeech()` contract or metadata must gain an explicit child flag through the combat owner.
- Native child damage is submitted no earlier than `World.getTick()+1`; protected/hostile preflight and normal native Filter remain active. The current class does not submit from Filter or recurse from an applied receipt; it rechecks native target HP before each channel component.
- Cull's execute callback has no implementation because no existing safe execute service was found in this worker's owned files. Direct Health mutation would bypass the required script and reward protections.
- Boss/PvP opt-in for WA-135/138/139 is intentionally absent. Boss/PvP is rejected by default; a future accepted explicit opt-in policy is required for those optional paths.
- Only files listed above were edited. Unrelated dirty files were preserved.













