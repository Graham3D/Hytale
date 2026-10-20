# Native contact proof handoff — 2026-09-30

## Scope and run

Changed only `NativeTwinAssaultGate.java`, new `NativeAffixDefenseContactTest.java`, new `NativeAffixTwinCommitTest.java`, and this handoff. The gate now calls its package-private pure `decide` method before storing its unchanged `Commit` metadata. No base skill, value, Gradle, registry, resource, server, game, auth, commit, push, or deployment work was done. The workspace has extensive concurrent changes outside this ownership.

Direct `javac --release 25` used the installed public pre-release `HytaleServer.jar`, existing compiled project classes, and cached JUnit jars. Direct isolated JUnit launcher: **7 found, 7 passed, 0 failed**. JVM used `-Djava.util.logging.manager=com.hypixel.hytale.logger.backend.HytaleLogManager`. No Gradle or XML run.

## Defense contact evidence

Each of `wa069NativeFilterInput`, `wa070NativeFilterInput`, `wa071NativeFilterInput`, `ga159NativeFilterInput`, and `ga160NativeFilterInput` uses its own legal mapped, accepted single-roll item and same-base unrolled item. The test checks the runtime's accepted-equipment protection projection at level 60; both broken and wrong-slot candidates are rejected and contribute zero. It selects the exact `RPG_Gear_Protection_<tenth>` file named by `HytaleGearEquipment.project`, decodes it with the installed native `EntityEffect` codec through `NativeAssetTestFixtures`, installs an active native effect, and calls the installed `DamageSystems.ArmorDamageReduction.getResistanceModifiers`. The native Physical and Projectile percent modifier equals the projected `tenth/1000` and is greater than the same-base unrolled modifier. Every case passed independently.

Installed `DamageSystems$ArmorDamageReduction` bytecode was inspected with `javap -c -p`: its `handle` is in `DamageModule.getFilterDamageGroup()`, calls `getResistanceModifiers` with active effects, reads the matching damage cause, applies flat then percent reduction, and writes `Damage.setAmount`. `HytaleGearEquipment.BeforeArmor` orders `project` before that group. `NativeGearSignatureDefenseFilter` is the WA-138/140 break/fortify factor after the group; `HytaleDamageAdapter.GearResistanceFilter` is for nonphysical resistance and critical excess. Neither substitutes for this armor path.

**Remaining:** These tests invoke the exact native modifier lookup but not `ArmorDamageReduction.handle` on an ECS `Damage` event, so they do not measure final `Damage.amount` or HP loss. The installed handler requires `ArchetypeChunk`, `Store`, `CommandBuffer`, and a world. The existing offline asset fixture provides codec registries, not those ECS objects. Keep actual-loss acceptance open until a bounded native ECS fixture or connected damage receipt runs. No parallel test-only damage calculator was added.

## WA-141 commit evidence

`realGateCommitKeepsDistinctAcceptedOffhandAndRejectsInvalidSources` calls the same `NativeTwinAssaultGate.decide` now called by native `tick0`. A legal accepted dagger main and different same-base offhand produce a `Commit` with the actor, chain, main identity, offhand identity, and frozen accepted snapshot. Incompatible utility, same identity, wrong base, unrolled main, absent accepted offhand, and chance miss return no commit. `nativeCodecBindsGateCheckpointAndManagedOffhandDamageLeaf` decodes the authored gate selector and offhand selector with the installed native interaction codec and fixture dependencies; both pass.

**Remaining:** No native `InteractionContext`/selector collision was constructed, and no `ManagedGearDamageInteraction.Calculator.calculateDamage` invocation consumed the gate metadata. The tests therefore do **not** prove the emitted distinct 40% `NoProc` damage contact, wrong damage source rejection at the consumer, native Gather metadata, or HP loss. They also do not claim connected QA. Do not promote WA-141 final contact acceptance from this run.

Minimal next seam for main to review in the existing consumer owner `ManagedGearDamageInteraction.Calculator`: extract its offhand commit validation and hit creation into a package-private pure method called by `calculateDamage` after native context/source checks. Inputs should include the retrieved `NativeTwinAssaultGate.Commit`, chain id, actor, held main, accepted chain context, and strike root; output should be the optional actual `GearCombatEffects.Hit` (or rejection). Keep its existing `.4` coefficient, offhand identity, sampled offhand power, `WA141/NoProc` root, and noncritical choice in that method. A new deterministic test could then feed the real gate commit into that production-called method and verify wrong/same/ineligible sources plus the produced native channel amount. This consumer is outside this worker's permitted production files, so it was not edited.

## Bugs for main

No production bug was confirmed. The observed missing native effect index during an intermediate run was a **test fixture lifetime issue**: `EntityEffect` caches its first registered asset map across fixture opens. The test now seeds the native map returned by `EntityEffect.getAssetMap()`, and the seven-test combined run passes. Native damage amount and WA-141 consumer proof are coverage gaps, not claimed bugs.
