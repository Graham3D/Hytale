# Mantle of Flame — execution seam implementation and native producer boundary

> Historical checkpoint, superseded by the owner's NORMALIZED_FIRE_V1 amendment. Current deployment and coverage are documented in [R032-AQ report](stage-13-mantle-of-flame-aq-report.md). The old stop below is retained as history, not the current deployment status.

2026-09-13 · branch `RPG` · HEAD before/after `25b85cb2819351a0d727e33132b2f57013cda024`.

**STATUS: BLOCKED at native resolved-source capture. MANTLE IS NOT PLAYABLE. NOT PACKAGED. NOT DEPLOYED. NOT PUSHED.**

The project-owned execution/component/decision seam is now implemented and tested. This is no longer the previous “no envelope implementation exists” state. However, the required vanilla source producer is not connected to it. The new task's §49 safety stop applies to that remaining integration, not to missing connected visual QA. A partially universal skill has not been exposed in the catalog or installed for testing.

## What was implemented

### Immutable source execution

`combat/damage/WeaponDamageExecution.java` carries:

- world, actor, root, execution and authored tick identity;
- explicit item and delivery provenance;
- derived/proc permission and already-resolved source critical state;
- immutable, bounded source components with stable component identity, semantic channel, source amount, provenance and direct-weapon/proc eligibility.

Identical repeated component records collapse once; conflicting records for the same component ID reject. Components are ordered deterministically. Blank/oversized/control-character IDs, nonfinite/negative amounts, overflow and component-budget violations reject. Source Fire is not multiplied by victim count. Spell, Burn, other periodic, summon, trap, environmental, reflection and derived provenance cannot claim direct weapon identity. A FIRE channel alone does not qualify.

An enum value such as `NATIVE_RANGED` is a contract supplied by an authenticated producer; constructing that value in a test is explicitly **not** proof that vanilla ranged execution supplies it.

### One execution-owned conversion decision

`combat/damage/WeaponExecutionLedger.java` supplies bounded root ownership, with one `WeaponFireDecision` per complete execution/tick identity. Duplicate access returns the same object; changing its source input rejects. Actor/root/world mixing rejects. There is no time-window deduplication or eviction that could permit replay. Closing the ledger revokes existing decisions.

The existing `execution/RootEffectBudget.java` owns this ledger lazily, so ordinary current skills do not allocate its map. This is integration with the existing root owner, not a new global combat cache.

`combat/damage/WeaponFireDecision.java` implements:

1. One complete recipient preflight callback per execution.
2. Maximum 64 accepted recipients, no duplicate/self recipient, and validated positive generated amounts before mutation.
3. One fractional aggregate cost, `TotalMaxMana * .01 * (generatedFire / sourceFire) * compiledCostFactor`.
4. Existing `RpgResourceService` affordability/reserve/commit/finish ownership, including existing pending holds. There is no parallel Mana pool or write-behind durability boundary.
5. Inactive/ineligible pass-through without query/payment.
6. Zero-recipient suppression with no charge or pulse.
7. Insufficient-Mana result with zero debit, original components intact, and one deactivation callback.
8. One consistent suppression answer for all direct recipients; unrelated channels remain unchanged.
9. A single-use pulse claim; accepted work cannot replay/refund when a later recipient disappears.
10. Non-retryable failure after a throwing native resource writer; no fabricated successful conversion following uncertain mutation.

The coordinator accepts recipient magnitudes already calculated by the owning compiler/runtime. It does **not** implement an alternate modifier engine, native mitigation or a second critical roll. The .25 conversion coefficient and Aura presentation are not secretly hardcoded into this generic decision coordinator.

### Native damage transport

`combat/hytale/HytaleDamageAdapter.java` adds a nonpersistent `WeaponComponent` witness and `applyWeaponComponent` entry point. The witness references the same execution decision and specific component ID. Attachment rejects a foreign actor/root and FIRE/non-FIRE cause mismatch. Existing scalar overloads continue through the unchanged native `DamageSystems.executeDamage` path.

The native fixture proves that the pinned `Damage` metadata system can carry this reference without changing an amount. It does **not** prove that unmodified vanilla interactions construct the reference or that a complete connected Mantle cast executes. No native producer system was registered, and existing damage does not automatically enter the new overload.

## Remaining concrete native boundary

### Newly checked public-hook alternatives

The installed 0.7.0-pre.2 API includes `ProjectileLaunchEvent.isLaunchedByWeapon()` and `ProjectileLaunchContext.isLaunchedByWeapon()`. These are useful explicit weapon-launch flags; the old melee-only basic-hit observer is not the only available provenance primitive.

However, those objects carry projectile/type/weapon-flag information, not a resolved complete elemental damage batch, the sample that produced it, or its source-component identities. `ImpactModifiers` adds source attribute/elemental-cause snapshots but not a complete resolved damage receipt. Interaction-chain start/operation identity is also available, but that alone does not publish later calculation output.

### Exact shipped counterexample

Installed asset:

`Server/Item/Items/Weapon/Longsword/Weapon_Longsword_Flame.json`

Its three ordinary swing damage bindings specify Fire 31 with `RandomPercentageModifier = .15`. The charged stab specifies Fire 39 with the same variance. Thus “the native source is always the registry's 31” is not a valid reconstruction of the native result.

The new native test executes the installed `DamageCalculator.computeDamageRange()` and `calculateDamage()` using the shipped ordinary-swing values. The range is **26.35–35.65**. Each evaluation creates a fresh result map and the native method samples its random modifier again. Recorded samples are retained as observations, not used as probabilistic pass/fail assertions.

This distinction matters for the proposed seam: calling the public calculator from an observer is a **new evaluation**, not reading an already-owned source result. Reading the first victim's damage is also not the requested solution. The envelope must be populated by the actual source owner; the envelope cannot establish that ownership merely by declaring the received amount source-only.

### Exact native construction location

Captured bytecode in `evidence/mantle-of-flame-implementation/api/DamageEntityInteraction.txt` shows `DamageEntityInteraction.attemptEntityDamage0(...)`:

- selects calculator variants using victim angle/target information;
- calls `DamageCalculator.calculateDamage(double)` at offset 429;
- converts elemental causes at 436 and applies source attribute scaling at 443;
- creates the per-victim damage array at 710;
- attaches `DamageCalculatorSystems.DAMAGE_SEQUENCE` to **only array entry zero** at 721–759;
- invokes each event separately through the command buffer at 966;
- publishes the private `QUEUED_DAMAGE` context value after that loop at 983–995.

Neither the private method nor its local resolved map/array exposes a public interception callback that accepts an execution envelope. The split entries do not all carry the same publicly readable authored source/component identity. A native `DamageSequence` can expose a calculator/hit count, but not the private sequence owner as a complete execution/component receipt.

The project-owned seam can transport and consume evidence once supplied. It cannot turn a post-selection scalar, a launch flag, or a second calculator evaluation into the original complete source batch. Supplying nominal registry values, choosing the first victim's result, or independently rolling another amount would replace this missing witness with a different source policy.

### Why the alternatives were not silently shipped

| Alternative | Why it does not establish the required boundary |
|---|---|
| Move basic recovery observer earlier | Still a per-victim event; does not publish the complete source set or make ranged source ownership complete. |
| Use public native calculator getter | Returns a calculator, not its already-resolved result; another evaluation samples again. |
| Read `Damage.initialAmount` | Already downstream of victim-dependent calculator selection; no complete component identities. |
| Use launch/interaction flags alone | Proves part of provenance, not the complete source amounts and authored component batch. |
| Override a subclass getter | The private native routine reads its protected calculator field directly; existing shipped instances are not replaced by defining a subclass. |
| Mutate a shared asset calculator temporarily | Not execution-local; unsafe across actors/worlds/reentrancy. |
| Replace all relevant shipped damage operations with a project evaluator | A takeover of the native calculation/interaction boundary, with additional native semantics to preserve; not a verified observation seam. Not introduced under the prohibition on replacing core interaction behavior. |
| Reflect private queue/locals or compensate after damage | Explicitly prohibited and unsafe for precommit ordering. |

This is a limitation of the verified integration paths under the task's authority constraints, **not a claim of universal engine impossibility**. A supported source-evaluation publisher, or an explicitly established project-owned native calculation extension that emits the authoritative batch before recipient processing, is still required. No unverified alternative is presented as working production integration.

## Gameplay/content status

No production native melee/ranged producer is registered. RPG root ownership and native witness transport exist, but current weapon-derived skill executors have not been changed to fabricate source components from their old victim-adjusted scalars.

Flame Weapon's existing .30 coefficient, Burn ownership and native contact capability gate remain unchanged. The coordinator tests accept explicitly supplied direct imbue components and exclude Burn; they do not fake production Flame Weapon availability. Native basic 4%/12% recovery was not modified.

No Mantle Aura resource-mode schema, catalog/profile registration, Link compatibility extension, mastery dispatch, dev grant, icon, VFX/tint, or Ember Golem combat kit was installed. These are unfinished feature work, not “awaiting visual QA.” Existing resource, persistence, escrow, exact-once, native input, HUD, Healing Beam and Blizzard behavior remains unchanged.

Current counts remain:

- 89 skills; 67 passives.
- 5,963 skill/passive combinations; 2,211 unordered passive pairs.
- 23 UNASSIGNED skills.
- Wall of Fire → Ember Golem remains `PROPOSED`, unchanged.

The requested completed feature would make Mantle → Ember Golem `PROPOSED` and Wall of Fire `UNASSIGNED` (absent an approved unused replacement source), resulting in 90/67, 6,030 combinations and 24 UNASSIGNED. That future assignment/count is not represented as current data.

VFX remains the previous audited inventory only. No Fire_AoE2 constituents were promoted to connected-verified selections or new Aura/proc assets. No Impact_Fire/red-flash binding is active for Mantle.

## Tests and failures

Replaced the old six-test `MantleFireBoundaryAuditTest` that asserted the seam was absent. Its historical result remains in the previous audit evidence; it is no longer a source test.

New tests:

- `WeaponFireDecisionTest`: **49 passing tests**, including source provenance classes, immutable/deduplicated components, existing root ownership, all required base Mana count anchors through 64, fractional exact payment, supplied magnitude/cost factors, source independence from victim values, zero targets, multi-tick separation, insufficient Mana, pending resource holds, overload-before-mutation, terminal cleanup and uncertain-writer non-retry.
- `MantleNativeSourceContractTest`: **5 passing tests** using the installed calculator, shipped Flame Longsword values, public launch-event values and actual native metadata attachment/mismatch rejection.

These are not end-to-end native melee/ranged combat tests. The modifier-factor cases test the coordinator's arithmetic inputs, not a completed Mantle Link compiler. There is no claim that fixture recipient amounts prove native mitigation, automatic mastery or client VFX.

An initial native fixture setup failed because an indexed Hytale AssetStore requires `setReplaceOnRemove`. Fixed the fixture configuration, then both focused suites passed. No assertion was weakened and no gameplay gate was relaxed.

One complete final retained invocation:

```powershell
.\gradlew.bat -PhealingProbeLiveTest=true :check :canvas-ui:check --rerun-tasks --console=plain
```

**2,346 tests passed; zero failures, errors or skips:** 2,264 RPG, 61 native-control, 21 CanvasUI. Exit 0, 1m41s. The existing compatibility sweep ran against the unchanged 89×67 catalog. No 90×67 matrix is claimed. CustomUI validation passed for 34 documents. Existing deprecation/native-access warnings remain.

No production changes were made after this full run. Evidence includes the complete log, XML results, native calculator observations, pinned native disassembly and shipped item source.

## Package/deployment/rollback

No candidate JAR or supporting archive was produced for this incomplete feature. No live JAR was replaced, no duplicate mod was introduced, and no save/mod-data state was altered. No Git commit or push was performed. The working tree contains the implementation and evidence for review.

The installed AP rollback baseline remains the active JAR itself:

`C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar`

Verified unchanged SHA-256:

`E11720D6AC54BFCD7FD294308E908E124967EFAE39B4E935C016AD2AA24CD285`

No new rollback copy was needed because replacement was not attempted. The prior AP backup remains untouched. No Mantle command or connected QA procedure is provided because that would imply a playable build which does not exist.

## Files changed in this task

- Added `combat/damage/WeaponDamageExecution.java`, `WeaponExecutionLedger.java`, `WeaponFireDecision.java`.
- Extended `combat/hytale/HytaleDamageAdapter.java` and `execution/RootEffectBudget.java`.
- Added `WeaponFireDecisionTest.java`, `MantleNativeSourceContractTest.java`; replaced the prior uncommitted boundary-audit test.
- Updated `build.gradle` to run the native source contract tests in the existing isolated native-codec JVM.
- Added `tools/Audit-MantleNativeProducer.ps1`, this report and `evidence/mantle-of-flame-implementation/`.

User art and unrelated pre-existing changes were preserved. **Implementation seam: yes. Complete Mantle feature: no. Packaged/deployed/connected-verified: no.**
