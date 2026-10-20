# Signature procs continuation — 2026-09-30

## Result and evidence boundary

The previously missing native bindings are implemented in `GearSignatureBindings`: authenticated accepted-strike facts, complete multi-channel receipt aggregation, actual HP loss and pre-absorption accepted damage, encounter rank, normal maximum Health, native melee reflection, protected native Cull submission, native Defense rating inversion, stable keyed RNG, credited-death burst handoff, and actor/world cleanup. `NativeGearAttackAcceptance` provides the frozen prechain gear and attribute-derived crit baseline, the accepted-strike source ledger, Deadly application before native component assembly, and a required authored selector/proc-coefficient protocol. `HytaleEncounterRewards` now has a **separate signature-only callback**, invoked on the native world queue after the durable once-only death decision. Recovery dispatch was not changed.

**Production enablement is still pending the signature native envelope, authored selector coefficients, Sentinel capture, and native tests.** While this worker ran, main registered the facade and action2 called its leaf `commit(...)` in the shared worktree. The eight IDs remain outside `GearAffixRuntime.ENABLED`. No claim of connected native verification or 160/160 completion is made. This work is a concrete facade and hooks, not a no-op adapter or a callback checklist.

## Exact owner wiring

`HyArpgPlugin` (main owner) now has this registration after `difficultyCombat`, `summonSystem`, and `gearEquipment` exist:

```java
private com.inigmasgames.hytalerpg.execution.hytale.GearSignatureBindings gearSignatures;
// During registration:
gearSignatures=new com.inigmasgames.hytalerpg.execution.hytale.GearSignatureBindings(difficultyCombat,summonSystem);
getEntityStoreRegistry().registerSystem(new com.inigmasgames.hytalerpg.gear.NativeGearAttackAcceptance.Prechain());
getEntityStoreRegistry().registerSystem(gearSignatures.defenseFilter());
getEntityStoreRegistry().registerSystem(gearSignatures.rawBefore());
getEntityStoreRegistry().registerSystem(gearSignatures.rawAfter());
getEntityStoreRegistry().registerSystem(gearSignatures.dispatch());
getEntityStoreRegistry().registerSystem(gearSignatures.new Removal());
getEntityStoreRegistry().registerSystem(gearSignatures.new Death());
gearReceiptObservers.add(gearSignatures.receipt());
encounterRewards.configureSignatureKill(gearSignatures);
// RemoveWorldEvent: gearSignatures.clearWorld(worldId).
// shutdownRpg: gearSignatures.close(); gearSignatures=null.
```

Register before the existing `HytaleDamageLifecycleSystems.Application` observer loop. The systems carry explicit dependencies: `Prechain` after equipment `Use`; defense Filter before `BeforeAbsorption`, Shield and native Apply; raw Health observation around Apply. `Application` already carries `acceptedDamage()`, `executionContext()`, and `nativeDamage()` from main's updated `AppliedReceipt`. The main owner should include the signature receipt in the same `gearReceiptObservers` loop as recovery and status; there is no second damage application.

`ManagedGearDamageInteraction.Calculator.calculateDamage` (action2 owner) now uses the prechain record from `NativeGearAcceptedContext.require(...)` and passes its snapshot and baseline crit to `GearCombatEffects.attack`. It calls `NativeGearAttackAcceptance.commit(...)` before native component assembly. The remaining action-side protocol is to author and carry the exact selector/coefficient variables for each strike and projectile pellet. The intended call is:

The prechain system captures every valid managed gear chain because the action leaf and projectile launcher now use its frozen baseline. `commit(...)` returns the original Hit for gear without these signature attack affixes.

```java
hit=NativeGearAttackAcceptance.commit(acceptance.world(),acceptance.actor(),hit,
        launch==null,getId(),call.context().getInteractionVars());
```

The native selector must author `RpgProcSelector=<exact damage interaction ID>` and `RpgProcCoefficient=<finite value 0..1>` in the interaction variables. Do this for every authored strike/pellet with a unique strike root. The helper rejects absent, mismatched or out-of-range metadata. It never assigns all pellets coefficient 1. The leaf's native **damage** coefficient remains the existing baseDamage / baselinePower ratio, which is distinct from proc coefficient. A missing prechain must fail closed, not recapture live gear or crit. `ManagedGearProjectile` (native4 owner) must carry the same accepted chain through launch and impact; a released projectile cannot use the shooter's later gear/crit. For explicit managed skill contact, attach its authenticated `SkillExecutionContext` and an accepted source record at committed cast/launch.

`GearCombatEffects.needsNativeEnvelope` (combat2 owner) must admit signature-equipped local attacks (WA-135/137/138/139/140/143; WA-136 is premitigation-only). `HytaleDamageAdapter.ManagedGearGather`/explicit projectile submission must attach the **same frozen Hit object** and a stable per-target, per-pellet contact ID to every channel. `GearSignatureBindings` rejects source-object drift and calls procs only when all positive native channels for that contact have returned from Apply. WA-142 also observes metadata-free native Physical melee via `Damage.INTERACTION_TYPE`; it needs no outgoing item hit.

`HytaleSkillExecutionSystem.configureSummons` Sentinel attack lambda owns another exact accepted-strike call site. When `NativeGearAttackAcceptance.hasAttackSignature(item)` is true, immediately after `GearCombatEffects.attack(...)` and before its `applyGearResolved` channel loop, use `NativeGearAttackAcceptance.commit(store.getExternalData().getWorld().getWorldConfig().getUuid(),lease.entity(),hit,NativeGearAttackAcceptance.SENTINEL_SINGLE_ROOT_PROC_COEFFICIENT,true,lease.token()+"/attack/"+attack)` and submit that returned Hit object. The Sentinel callback submits one target per authored attack ordinal, so this explicit single-root proc coefficient is 1. `lease.coefficient()` remains the distinct damage scalar and must not be used as a proc chance factor. The facade then validates `HytaleSummonSystem.authorizedSentinelAttack(lease,metadata,gearSource)` again at Apply. Its incoming WA-142 reflection path uses `lease.boundEffects()` and the same hostile-to-owner test as Summon DamageGuard. This Sentinel call site is not edited here because `HytaleSkillExecutionSystem` is outside the assigned file ownership.

After those owner edits compile and native positive/control-negative assertions pass, main may enable WA-135–140 and WA-142–143 in `GearAffixRuntime.ENABLED`. Preserve the existing separate WA-134/141 ownership and gates.

## Implemented semantics

- WA-135/137/138/139/140/143 consume one complete accepted contact with frozen item, coefficient, premitigation vector, post-application survivor HP, actual summed HP loss, hostility and rank. Canceled or absorbed channel components cannot independently proc.
- WA-136 doubles only noncritical Physical before native component assembly. The accepted registry retains the original noncritical Physical for Crushing/Barbed caps.
- WA-138 reads the installed native Physical armor/effect reduction and inverts it to the same D01 rating before applying its rating break. The inverse-ratio calculation cancels the arbitrary K scale and does not add a second armor reduction.
- WA-139 queues a terminal `CULL` child for the next world tick through `DamageSystems.executeDamage`, after NPC/hostility/death/script veto checks. The normal native Gather/Filter/Apply/DeathReward path remains authoritative; no direct Health write or entity deletion is used.
- WA-142 uses actual native HP loss and a `Damage.INTERACTION_TYPE` Physical melee witness. Managed multi-channel incoming attacks wait for all components. Reflect children are marked reflected, NoProc and NoLeech.
- WA-143 stores the killing contact and its nearby hostile/LOS target IDs at Apply, then submits burst children only after `HytaleEncounterRewards` accepts its deduplicated death. This survives dead-entity removal before the durable decision; source death and world unload cancel it. The runtime replay ledger prevents duplicate burst on repeated callback.
- Strike/contact maps, child queues, replay claims, timed locks and fragments are bounded and retired or cleared by actor/world lifecycle. The native filter never submits a child recursively.

## Verification performed without Gradle

Manual `javac` of all changed production sources and targeted tests against the installed pre-release server JAR and existing compiled project classes: **passed**. Manual JUnit Platform launcher of asset-free tests: **9 found, 9 passed**, covering WA-136, WA-137, WA-139, accepted-strike source drift, prechain freeze/cleanup, selector protocol, credited-death child dedup/control-negative, and ordinary managed gear with no proc metadata. The earlier full seven-test suite was attempted: three passed and four native Damage construction assertions stopped with `DamageCause.getAssetStore()==null` in a standalone JVM. Those assertions need the native asset bootstrap; this is test-environment evidence, not a proc pass or an implementation impossibility. No Gradle, native server/game/auth, deployment, commit, push, or extra agent was run.

## Remaining native proof before enablement

Main must observe emitted child `Damage` after a positive proc, unchanged control damage, protection/script rejection, armor break using an armored NPC, no double proc per multi-channel/pellet contact, projectile launch/swap snapshot persistence, native raw melee Retribution, and one Kill Burst after credited death with zero burst from a child/replay. The current standalone test environment cannot initialize the native DamageCause asset store and did not run those connected assertions.
