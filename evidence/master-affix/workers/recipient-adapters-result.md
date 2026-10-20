# Sentinel native recipient adapters — 2026-09-30

The owner correction in `docs/MASTER_AFFIX_SCOPE_CORRECTION.md` governed this work. Base skills, costs, damage, targeting, cooldowns, and acquisition were not changed. The pre-existing dirty workspace was preserved.

## Completed wiring

- Added `GearNativeItems.recipientEffects` alongside the unchanged `effects` method. It keeps player equipment on the existing path, rejects invalid or foreign-store refs, and delegates native recipients to the registered live `HytaleSummonSystem.sentinelEffects` source. That method requires actor UUID/world, registry lease, native life, owner life, and active durable binding before returning the exact one-item snapshot.
- Wired the selector into the status admission and periodic target checks (`GearStatusBindings`, `NativeGearStatusObserver`), area status duration/slow observation (`HytaleAreaStatuses`), NPC steering slow (`AreaNpcControlSystem`), hostile displacement (`NativeAffixHostileDisplacement`), and recipient healing (`HytaleSupportSystem`). The status observer's proc coefficient remains its existing receipt value.
- Changed `HytaleDamageAdapter.GearResistanceFilter` query to all targets. Player resistance and critical behavior remains on its existing branch. Native targets only use qualified critical parts and a nonempty live recipient snapshot; native resistance stays in `HytaleSummonSystem.DamageGuard`. Removed the guard's duplicate Sentinel critical reduction. The pre-existing metadata JSON correction in the damage adapter was preserved.
- Registered the selector, `sentinelBlockCostObserver`, and summon world-unload cleanup in `HyArpgPlugin`.
- Opened only `WA-079`, `WA-080`, `WA-081`, `WA-082`, `WA-084`, and `WA-111` in `IronSentinelAffixes`. `WA-083`, `WA-147`, and `WA-141` remain gated for the documented native-source reasons.

## Offline verification

Direct `javac --release 25` compiled the eleven scoped production files, including `HyArpgPlugin`, against the installed pre-release server JAR, current compiled checkout and cached dependencies. The isolated JUnit run in `evidence/master-affix/recipient-adapters` passed 17/17 tests across `IronSentinelAffixInheritanceTest`, `SentinelSourceConsumerTest`, `GearStatusBindingsTest`, and `SentinelDefenseInheritanceTest`. The focused tests cover the six forge gates and three closed controls, canonical status/chill/slow/displacement/healing/critical calculations with bound and empty snapshots, and missing-target rejection. No JUnit launcher was placed in `src/test`.

A connected native `Store` was not constructed under the offline-only constraint. The wrong-world, dead actor/owner, inactive binding and missing owned-item branches of the existing `sentinelEffects` method were reviewed in source but not independently exercised through a live native store. These tests establish owner logic and compilation, not connected game behavior.

No Gradle, game/server, authentication, deployment, commit, push or subagent was run.
