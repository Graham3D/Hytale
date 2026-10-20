# WA-094–098 recovery integration result

Production recovery wiring is implemented in the recovery owner, support Health writer, skill attack producer, and encounter death reward callback. The capability gate in `GearAffixRuntime.ENABLED` remains closed for WA-094–098 until main's integration build and tests. No game/server/auth launch, Gradle, deployment, commit, push, or connected QA was performed.

## Production changes

- `GearRecoveryBindings` constructs one gated runtime, one applied receipt observer, one credited death callback and one native payout tick. It resolves live hostile, unprotected targets through `HytaleAreaQueries` and `SupportNativeEffects.control`; reads native Health maximum and reservation aware spendable Mana; and authenticates the exact live Sentinel lease, entity, bound item, snapshot, attack ordinal and root. It exposes death and removal systems, `cancel` and `cancelWorld`.
- `GearAppliedHitRecovery` registers the first direct gear component as the accepted root even if that component was blocked, so later damage bearing channels on the same authenticated root can contribute their actual HP loss while `canProc=false`. An unregistered no proc child cannot start recovery. A projected summon without a matching live Sentinel authorization is rejected outright. It continues to require source identity, hostility, protection admission, direct origin, valid equipment and actual HP loss for payout.
- `HytaleSupportSystem.creditSelfRecovery` writes directly to native Health for a live self actor, caps against current native and supplied normal maximum, and returns actual readback gain. It does not call `healingResolved`, outgoing healing, Overheal/barrier, mastery, reward, or proc hooks. This is self recovery/regen, not outgoing healing.
- `HytaleSkillExecutionSystem.configureRecovery` registers accepted direct weapon attack roots with the already committed gear snapshot before native damage. Per contact metadata now includes the effect and hit index. Periodic, derived and triggered releases do not register.
- `HytaleEncounterRewards` captures each present candidate's valid equipment snapshot and normal Health maximum during native death. After accepted durable `finishDeath`, only actual `p.shares()` recipients with a captured snapshot receive a callback dispatched onto the native world thread. Gold rules and maps were left intact. The callback's local claim and the durable death identity both prevent replay.

The asynchronous durable callback retains only immutable snapshots, UUIDs, numbers and the world executor. It never retains an ECS `Ref` from the death callback.

The normal maximum adapter reads the native Health stat maximum. No temporary Health capacity modifier was found in the current owners; a future native temporary capacity source must be excluded there before promoting the gate.

## Exact main integration

Main owns `HyArpgPlugin`; this worker did not edit it. Add the field with the other RPG fields:

```java
private com.inigmasgames.hytalerpg.execution.hytale.GearRecoveryBindings recoveryBindings;
```

Immediately after `var summonSystem=skillExecutionSystem.configureSummons(...)` (currently around line 346), while `combatKernel`, `supportSystem`, and `bosses` are in scope:

```java
recoveryBindings=new com.inigmasgames.hytalerpg.execution.hytale.GearRecoveryBindings(
        combatKernel,supportSystem,summonSystem,bosses);
skillExecutionSystem.configureRecovery(recoveryBindings.committedAttacks());
getEntityStoreRegistry().registerSystem(recoveryBindings.deathSystem());
getEntityStoreRegistry().registerSystem(recoveryBindings.removalSystem());
```

Immediately after constructing `encounterRewards` (currently around line 361):

```java
encounterRewards.configureRecovery(recoveryBindings.creditedDeaths());
```

Keep `BeforeApplication` immediately before native `Application` (currently around lines 568–570), and register the payout tick once:

```java
getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.BeforeApplication());
getEntityStoreRegistry().registerSystem(new HytaleDamageLifecycleSystems.Application(
        combatTrace,recoveryBindings.observer()));
getEntityStoreRegistry().registerSystem(recoveryBindings.tickSystem());
```

In the existing `PlayerDisconnectEvent` callback, immediately after `UUID player = ...`, and in the `DrainPlayerFromWorldEvent` branch after the `playerRef != null` check:

```java
recoveryBindings.cancel(player);                  // disconnect
recoveryBindings.cancel(playerRef.getUuid());    // world drain
```

Register the supported `RemoveWorldEvent` alongside the existing `AddWorldEvent` registration:

```java
getEventRegistry().registerGlobal(
        com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent.class,
        event->{if(!event.isCancelled())recoveryBindings.cancelWorld(
                event.getWorld().getWorldConfig().getUuid());});
```

The death and removal systems cancel pending actor credit even when the ordinary player disconnect path is not reached. `cancel` retains paid rolling windows and root/reward claims, so reconnect cannot replay credit. The `RemoveWorldEvent` class and `WorldEvent.getWorld()` were verified against the installed server JAR public API.

## Verification

- Direct `javac` compilation passed for the recovery runtime, payout, receipt observer, credited death callback, native tick, facade, support writer, encounter reward owner, **skill execution owner**, and the current support/combat/skill dependencies against the installed public server JAR and local classes.
- JUnit Platform direct launcher: **11 found, 11 passed** (`GearRecoveryRuntimeTest` 9 and `GearRecoveryRewardDispatchTest` 2). Tests cover positive queue/payout, 4%/2% windows, reservation aware Mana, Sentinel Health only, callback dedup, absent/uncredited shares, cancellation and negative receipts. The reward dispatch test executes the same helper used by the accepted durable callback.
- `git diff --check` found no whitespace error in edited tracked files.
- The first isolated skill compilation saw temporarily unresolved symbols while other owners were editing. A later compilation with their current source for `SkillExecutionService`, `SupportCleanse`, `GearCombatEffects`, `SkillExecutionPort` and `NativeAffixHostileDisplacement` passed. Main's combined build remains the required compilation gate. No claim of native registration or connected QA is made.
