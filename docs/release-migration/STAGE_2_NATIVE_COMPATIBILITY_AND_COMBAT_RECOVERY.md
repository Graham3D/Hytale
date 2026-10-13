# Chapter I migration — Stage 2 native compatibility and combat recovery

Date: 2026-10-12. State: **ENGINEERING_READY / QA_PENDING**, subject to the
post-commit verification receipt. This branch depends on the unaccepted Stage 1
source `63bf6a616e9031e33fb722a1e30f515abe6bb7c5`, including its world-ID
and encounter-admission repairs. Its branch is
`codex/chapter1-stage2-native-combat-recovery`. Do not integrate its unaccepted
ancestors into `main`. The protected release save is `UserData/Saves/Hywind`;
the pre-release `RPG` save is historical reference only. Neither save, the
installed plugin, nor the installed Hytale server was modified in Stage 2.
The primary checkout's unrelated artwork and protected `AGENTS.md` changes
were preserved.

## Root cause and restoration choice

The installed Chapter I server is the stock `0.7.0` binary, revision
`648ebf375a034beabf119d7d790cd11d4f665ff4`, SHA-256
`c3b5985b9e72727bf3bcf220923d7e61e78fe0f37cd1cd9c2a73f51e4f408ff8`.
It has none of the four neutral native hook types or their six call-site
insertions. The deployed INV1 plugin contains some hook classes, but their
presence does not instrument native methods. Its historical pre-release JAR
hash gate correctly reports `PACKBOUND_NATIVE_PATCH_VERSION_MISMATCH` and
keeps production promotion disabled. That also prevents damage and projectile
receipt provider installation. Its ordered-tick population fallback runs
without the final native selection callback. The prior patched pre-release
server SHA-256 was `a032a64e03390ca0aeb6c03c3b1600eaa892c8bc833d1bf3ac39307ccdf3cb20`;
it is not a release deployment candidate.

The release API offers event and system observation, but no equivalent public
callback at these exact boundaries: before the three native mutation writes,
before each original `Damage` event invocation with its interaction-chain and
component identity, between original projectile `shoot` and `addEntity`, or
after native population reconstruction and before selection. The existing
exact-bytecode patcher was therefore adapted to the reviewed release binary.
No combat kernel, damage coefficient, projectile construction, encounter
probability, or population policy was rewritten.

## Exact call-site audit

Five stock release target class bytes match the old patcher's original
pre-release class pins exactly. The sixth, `DamageEntityInteraction`, has a
different class hash; its `attemptEntityDamage0` instruction sequence has the
same 459 normalized operations as the historical patched method after
removing the five injected receipt instructions. The original native damage
event creation and single `CommandBuffer.invoke(target, damage)` boundary
remain in place. Current release method descriptors and operand positions
were inspected from the installed JAR before updating the pins.

| Native class and method | Reviewed insertion | Original release class SHA-256 |
|---|---|---|
| `ClearEntityEffectInteraction.firstRun` | Before the one `EffectControllerComponent.removeEffect(ref, index, accessor)` | `20f5765988dcb491a865a19ca591966f78c19d8c46cbf2174e0d46d1b6ec235f` |
| `ChangeStatInteraction.firstRun` | Before the one `EntityStatMap.processStatChanges` | `d3b7ef2eec083689ead816ba485c7aba52abb00b41dde029e9acf2bd1fb2da07` |
| `ChangeStatWithModifierInteraction.firstRun` | Before the one resolved `processStatChanges` | `7c3e0fe4fb601940b0e5fec214edcdd228ed441593e645b471b90597542595f2` |
| `DamageEntityInteraction.attemptEntityDamage0` | Immediately before each original `CommandBuffer.invoke(target, damage)`; receipt receives component index | `842fe1dccc7c49feaffc8fb961f346767b66315711e91477710922d6e0f7f9f6` |
| `LaunchProjectileInteraction.firstRun` | After original `ProjectileComponent.shoot`, before the one `addEntity(originalHolder, SPAWN)` | `973c5e280b171f59c575b9e3cd4b4d0741e538eb40c8699867d16360eb13938e` |
| `WorldSpawningSystem.tick` | After `recalculateWorldCount`, before the first of two `getActiveSpawnJobs` calls | `c4392e0e579032f3b6f3596d065f010879cedd8d72e01defa4cf82acfff869de` |

For the three mutation leaves, a denied policy decision still throws the
native `ChainCancelledException(Failed)` before the writer. Release
`SimpleInstantInteraction.tick0` calls `firstRun` before continuing its normal
`SimpleInteraction` path, so a plain return would have permitted `Next`; the
native exception prevents that. Admitted calls retain the original writer
arguments. The damage hook stamps only the existing `Damage` metadata and
never invokes damage itself. The projectile hook receives the original holder
and checks its UUID after callback; the native queue and durability operations
remain in their original order. With no provider, all four hooks return without
altering the native operation.

## Artifact integrity and runtime ownership

The patch builder accepts only the exact stock release SHA-256 above or its
own exact reviewed output with a verified stock recovery copy. It writes to
`build/native-patch/`, never into the installation. The release patched JAR is
`build/native-patch/HytaleServer-packbound.jar`, SHA-256
`e0bcd36c7a981949d2c15b1b48f1cdd7f9623373269ef03b58f2d9b9da7f6edf`.
`build/native-patch/verification.json` records both whole-JAR hashes, all six
original/patched class hashes, eleven added hook class hashes, exact patcher
source commit and dirty-state flag, and verifier results. The patcher rejects
unknown JARs, changed target class hashes, unexpected method layouts, duplicate
instrumentation, removed entries, and any changes outside those six classes
and eleven hook entries. Its opcode/operand comparison removes only the
reviewed insertions and verifies every original instruction. A separately
compiled regression harness also checks JVM class verification with
`-Xverify:all`. The output hash reproduced on repeated isolated builds.

`PackboundNativeMutationBridge` now requires **all four** hook classes to have
the reviewed patched-server JAR as code source before it installs the existing
Packbound decision. Missing/mismatched copies remain `unavailable`/`invalid`;
provider registration exceptions remain visible. Damage and projectile
providers install only after that gate. `NativeBasicAttackObserver` receives
their actual availability. The final population provider calls the existing
`NativePopulationBalance.applySafely` at the native callback; its ordered-tick
fallback runs only when that provider is absent. Production promotion also
requires the population provider. Teardown closes all providers and restores
the fallback state. `/rpg enemies status` should show `nativeHooks=ready` only
with the complete verified set; no connected behavior is inferred from that
status alone.

## Offline verification and limits

`Test-ReleaseNativePatch.ps1` builds and validates the exact server, mutates
each target method in memory to prove shape rejection, rejects an already
patched/unknown input, and verifies all six patched classes under the JVM
verifier. `NativeHookLifecycleTest` covers no-provider parity, admission,
denial, single-owner installation, teardown, and reinstallation;
`NativePatchGateTest` confirms compiled plugin hook types alone cannot enable
protected promotion. Existing original-hit, Health-loss aggregation,
projectile provenance, population, Stage 1 identity/admission, durable
encounter, and reward tests are retained and run in their existing test lanes.
Exact post-commit Gradle/JUnit, package, static-analysis, Guardian, and final
Hywind JAR SHA-256 are bound to the source HEAD in
`build/guardian/verification.json`; a HEAD-bound JAR hash cannot be embedded
in this tracked report without changing that HEAD and thus the JAR hash.

The installed release JAR remains the INV1 candidate, SHA-256
`3b80550cca85e59061c40b393a574fa42f7009180e5b711f91930be1af719b47`.
This Stage 2 candidate has **not** been installed. No standalone server or
client was launched. Pre-Stage-2 release evidence includes two Blizzard hits
that reduced Health from 15 to zero; it does not prove other skills or normal
weapon hits. Offline tests prove the original native call sites and downstream
receipt/Health aggregation contracts, but only owner-operated connected play
can prove that a real weapon hit reaches native damage, yields positive Health
loss and a matching receipt. The release deprecation and Gradle warnings are
nonfatal and were left outside this compatibility scope.

## Authorized deployment and complete rollback plan

Deployment is a **separate authorization**. With the Hytale client and local
server stopped, first re-hash the installed release server and abort unless it
is exactly the stock hash above. Verify the candidate server hash above and
the final candidate Hywind hash in the Guardian receipt. Copy the stock server
to a timestamped recovery directory outside `Saves`, verify that backup hash,
and separately back up and hash the currently deployed
`UserData/Saves/Hywind/mods/Hywind.jar`. Preserve the entire `Hywind` save and
leave historical `RPG` untouched. Install only the matching reviewed
`HytaleServer-packbound.jar` and new `Hywind.jar` at their respective release
server and Hywind mod paths. Re-hash both installed files, check the plugin
manifest's `InigmasGames:HyARPG`, `=0.7.0`, and
`R262C1S2-CANDIDATE` metadata, then launch the game for owner QA. Abort if
the installed release package or either candidate changed in the meantime.
Rollback with the game stopped by restoring the verified **original server and
prior Hywind JAR together**, then re-hash both; leave saves untouched. Never
pair this release client with the old pre-release patched server.

## Owner-operated connected QA and Stage 3 handoff

1. Start the Hywind save and run `/rpg enemies status`. Expect
   `nativeHooks=ready` and no `PACKBOUND_NATIVE_PATCH_VERSION_MISMATCH`;
   verify the population log reports `mode=NATIVE_FINAL nativeFinalSeam=true`.
2. Run `/rpg-trace combat start`, hit a normal hostile with an existing melee
   weapon, then use `/rpg-trace combat last 10`, `/rpg-trace combat inspect`,
   `/rpg-trace combat stop`. Compare `NATIVE_BASIC_HIT_OBSERVED`, original-hit
   receipt, and actual positive Health decrease for the same hit. Check that
   there is no duplicate damage or reward credit. Repeat with Blizzard and a
   different offensive skill; distinguish activation, contact, cancellation,
   zero mitigation result, and a missing observer receipt.
3. Test one native or Hywind projectile for one launch and valid impact
   identity. Use an isolated Packbound fixture to confirm leader protection,
   guard defeat, protection transition, and ordinary target damage.
4. Run `/rpg-trace spawn start`, allow normal natural spawning, then
   `/rpg-trace spawn stop`; check that Stage 1 world admission remains valid
   and population selection has no duplicate jobs.

Provide the bounded server log, combat and spawn traces, and the observed
Health/projectile/protection outcomes. Natural Elite promotion and affix
acceptance remain Stage 3; gear-drop economics and custody remain Stage 4.
