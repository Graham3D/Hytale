# Quick Slash native-equivalent redesign — implementation decision gate

Date: 2026-09-13. **NOT DEPLOYED. No redesign implemented or packaged.**

Status: `BLOCKED_AT_REQUESTED_NATIVE_REUSE_GATE`. Current Quick Slash and the installed R032-AR build remain unchanged. No revision increment, commit, push, live-save operation, or gameplay edit was made in this task.

## Request and decision

The owner requests two legitimate equipped-weapon Light Attacks at twice normal speed, each carrying 37.5% of the complete ordinary Light Attack source composition. One RPG activation must own both executions, one Stamina payment, one cooldown, normal cancellation, source provenance, and bounded passive/Mantle behavior.

Sections 7 and 26 explicitly require stopping rather than shipping an approximation when neither supported native invocation with execution-scoped timing nor an existing authoritative native-equivalent source/execution path satisfies that contract. Section 27 makes packaging/deployment conditional on implementing the redesign and passing its regressions. Those specific instructions govern this task rather than the earlier general deployment preference.

**Decision: stop at that gate.** Native attack invocation exists; the missing boundary is not the ability to call an interaction. It is a supported, execution-scoped accelerated Light Attack contract that preserves native timing, selection, full source composition, provenance and ownership. The inspected current APIs do not provide that timing override. The alternative existing Mantle producer supplies a much narrower Fire-only source, not the complete authoritative Light Attack required by route B.

This is an evidence-bounded finding about the installed implementation and current repository, not a claim that Hytale can never support this feature or that a broader asset/engine extension would be impossible.

## Baseline and reproducibility

- Repository: `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale`.
- Branch: `RPG`; HEAD: `25b85cb2819351a0d727e33132b2f57013cda024`.
- Existing cumulative AQ/AR uncommitted work was present and preserved. HEAD alone does **not** describe that working tree or the installed cumulative AR artifact.
- `gradle.properties`: `rpg_revision=R032-AR`, `hytale_version=0.7.0-pre.2`.
- Native input: `C:\Users\Zemio\AppData\Roaming\Hytale\install\pre-release\package\game\latest`.
- `Server/HytaleServer.jar` SHA-256: `9CDC1E77DADFDAD0B1D849977CDAD5C2752207F538272FB3D76D4E915546B25E`.
- `Assets.zip` SHA-256: `0E35DF2D4E4A803F7752CB300FA7C9FE7ACDF62B4311978DA95C7AFF7ABBC126`.

The installed JAR was inspected using JDK 25 `javap -c -p`, rather than relying on remembered 0.7.0-pre.1 behavior. Shipped attack JSON was read directly from this Assets.zip. Local evidence is under [evidence/quick-slash-native-reuse-audit](../../evidence/quick-slash-native-reuse-audit/):

- `audit.json`: native hashes, installed mod manifest inventory, selected shipped attack assets, exact test results, and Quick Slash passive verdicts.
- `Collect-Audit.ps1`: read-only native/live inspection and test-result collection; emits JSON to stdout. It does not deploy or modify a save.
- `InteractionManager.javap.txt`, `InteractionChain.javap.txt`, `InteractionContext.javap.txt`, `Interaction.javap.txt`, `RootInteraction.javap.txt`, `ReplaceInteraction.javap.txt`, `DamageEntityInteraction.javap.txt`, `DamageCalculator.javap.txt`, `ImpactModifiers.javap.txt`, `IInteractionSimulationHandler.javap.txt`.
- `focused-baseline-tests.log`, `focused-native-control-tests.log`.

These evidence files are local repository-folder outputs, not a claim they have been pushed to GitHub. The checked-in-style Markdown report is the shareable summary.

## Route A: native invocation exists, scoped timing is missing

### What exists

`InteractionManager` exposes `startChain`, `initChain`, `executeChain` and `queueExecuteChain`. `InteractionContext` exposes `forInteraction`, `execute`, `fork`, held-item context, metadata and interaction-variable resolution. Thus native execution can be initiated without reproducing native collision in the RPG Strike executor.

This is necessary but not sufficient. The normal eligible weapon Primary roots include native charging and combo selection. Invoking Primary is not an API promise to execute exactly one uncharged swing with an independently selected playback rate.

### Timing ownership in the inspected bytecode

1. `Interaction` stores authored `runTime`; its packet includes that runtime. Native animation duration is resolved from item animation assets and their configured speed.
2. `InteractionManager.serverTick` obtains elapsed operation time from `InteractionEntry`. The multiplier in that method is `TimeResource.getTimeDilationModifier()`, a world resource, not a per-Quick-Slash playback scale.
3. `InteractionChain.setTimeShift(float)` writes an offset. It is not a multiplicative execution clock.
4. `InteractionContext.setTimeShift(float)` sets the chain offset and, for a nonfork root, calls `InteractionManager.setGlobalTimeShift(type, value)`. It is not an isolated attack-speed lease.
5. `PlayAnimation` carries entity ID, animation profile ID, animation ID and slot. It has no per-play speed field. The existing faster RPG animation profiles are distinct presentation assets, not native interaction timing controls.
6. `InteractionSettings` exposes click-skip policy; `RootInteractionSettings` exposes chain-skip policy and cooldown. The inspected configuration and simulation-handler surfaces do not add an execution speed parameter.

Advancing timestamps would skip/shift phases, not prove a native selector's swept hit interval and client animation executed at twice speed. Changing the world time resource or shared Primary timing is outside scope. Merely playing a faster animation while retaining normal native hit timing would not satisfy the gameplay/visual correspondence requirement.

### Shipped phase evidence

Representative first uncharged swing paths from the installed assets:

| Weapon path | Wind-up | Native selector | Recovery padding | Authored path sum | Requested half-duration sum |
|---|---:|---:|---:|---:|---:|
| Sword `Weapon_Sword_Primary_Swing_Left` | 0.117 s | 0.050 s | 0.167 s | 0.334 s | 0.167 s |
| Daggers `Weapon_Daggers_Primary_Swing_Left` | 0.069 s | 0.069 s | 0.069 s | 0.207 s | 0.1035 s |
| Longsword `Longsword_Swing_Left` | 0.229 s | 0.104 s | 0.187 s | 0.520 s | 0.260 s |

These are **static authored path sums**, not measured connected attack durations. They exclude time spent deciding charging/holding, client synchronization, tick quantization and surrounding combo state. Longsword also has a parallel 0.166-second effects branch; that is shorter than the selector-plus-padding branch. Other combo entries can differ. No effective accelerated duration has been measured or implemented.

The family paths are structurally different:

- Sword uses `Charging -> Chaining -> Replace` and a horizontal selector; charging at 0.2 seconds selects its alternative branch.
- Daggers uses its own four-entry chaining sequence, Stab-shaped selectors for the inspected swing, and a 0.2-second charge branch.
- Flame Longsword uses `Longsword_Attack`, with inline charging/chaining, an inline parallel selector/effects layout, and a charged branch at 1.565 seconds.

`InteractionContext.setInteractionVarsGetter` and `ReplaceInteraction` can select replacement **root asset IDs**. They do not supply a numeric time scale recursively to the selected graph. Existing replacement hooks do not uniformly cover wind-up, inline selectors, padding and animation speed across these families.

Creating derived attack graphs could reuse portions of native selectors. However, making this route satisfy the request now would require owning family-specific replacement timing graphs, preserving inline forks and item variables, controlling charge/combo selection, mapping presentation profiles, and integrating skill provenance and source normalization. That is not an already-supported one-execution timing override; it is the broader native attack graph replication/adaptation this bounded task explicitly disallows. No such graph generator or alternative combat engine was added.

### Damage modifiers were not overlooked

`DamageEntityInteraction.scaleByDamageAttribute` can multiply a calculated channel map using `ImpactModifiers`, **when the damage leaf declares `damageAttribute`**. It returns without that scaling when the attribute is absent. Context modifier snapshots and elemental overrides exist; it would be inaccurate to say native damage has no modification mechanisms.

Those mechanisms do not by themselves provide the missing accelerated execution clock, nor an authenticated complete pre-recipient source envelope including all supported affixes/imbues. Intercepting resulting per-victim damage would also be too late to supply Mantle's required execution-wide source authority and risks repeating source/proc accounting per victim.

## Route B: current source envelope is not a complete Light Attack producer

Inspected production files:

- `combat/damage/WeaponDamageExecution.java`
- `combat/hytale/NativeWeaponFireProducer.java`
- `combat/hytale/ManagedWeaponFireInteraction.java`
- `combat/power/NativeItemPowerRegistry.java`
- `execution/hytale/HytaleEquipmentAdapter.java`
- `execution/support/WeaponImbueContacts.java`, `SupportRuntime.java`, `SupportWorldPort.java`

All Java paths in this report are relative to `src/main/java/com/inigmasgames/hytalerpg/`.

`WeaponDamageExecution` is an immutable multi-component **contract** with identity, provenance and eligibility checks. Having enum values for `WEAPON_AFFIX` and `FLAME_WEAPON` does not establish production producers for those components.

The production constructor search finds its concrete producer in `NativeWeaponFireProducer`. That producer:

- explicitly accepts `Weapon_Longsword_Flame` under native `Primary`;
- audits four managed native damage leaves;
- resolves one canonical randomized Fire source per authored execution, shared across victim forks;
- emits one `FIRE` component with `WEAPON` provenance, `NATIVE_MELEE` delivery and `critical=false`;
- sends that source through Mantle preflight before returning the Fire amount to the native damage calculator;
- does not publish the native Physical/other component map as a complete ordinary Light Attack envelope.

`ManagedWeaponFireInteraction` delegates the actual native leaf but only replaces its Fire amount. It explicitly rejects angled/targeted calculator configurations and sequential modifiers outside its supported source contract. It is not a general native attack resolver.

`NativeItemPowerRegistry` resolves an authored scalar weapon/magic power descriptor. It is sufficient for the existing RPG damage formula, not normal native source composition, animation, weapon interactions or affix semantics. Reusing this scalar at 0.375 would be precisely the forbidden generic approximation.

Flame Weapon has catalog/profile content and a pure `WeaponImbueContacts` consumption model. `SupportWorldPort.rootWeaponContactAvailable()` defaults false; no production override was found. `SupportRuntime` gates IMBUE with `NATIVE_ROOT_WEAPON_CONTACT_ID_UNAVAILABLE`. The search found no production producer emitting `FLAME_WEAPON` into `WeaponDamageExecution`. Therefore the requested complete normalized source cannot honestly be described as already authoritative and available.

## Existing Quick Slash ownership audit — preserved, not redesigned

### Damage and timing

- `rpg/runtime/stage-04-skills.json` retains Strike, SWORD/LONGSWORD/DAGGER eligibility, two repeats and coefficient **0.85 per hit**, with Physical strike presentation/cause.
- `HytaleSkillExecutionSystem.executeStrikeHit` queries the RPG `StrikeGeometryService`, uses a per-hit/victim ledger, plays native-derived presentation, and calls the RPG scalar damage kernel. It does not execute a native Light Attack root.
- `NativeStrikeFeedback` explicitly separates animation from native root execution. Its current Quick Slash profiles use **3× native animation speed**, from the prior implementation. This has **not** been changed to the newly requested 2× behavior.
- Current scheduled intervals are Sword approximately 0.138889 s, Longsword 0.173611 s and Daggers 0.092593 s. The base two-hit action window is twice that interval; hit zero dispatches immediately. These values are animation-derived and are not the native phase sums above.
- The current generic total is two 0.85 coefficient payloads before applicable modifiers/crit. It is **not** 75% of a normal native attack. No new tooltip or damage claim was introduced.

### Commit, crit, recovery, snapshot and mastery

- `SkillExecutionService` retains activation/commit validation, reservation, durable preparation, one commit and family dispatch. Authored base cost remains **5 Stamina once**, cooldown **0.8 seconds once**; existing passive modifications still apply.
- Its snapshot retains equipped item/power, attributes, modifiers and mastery magnitude. Current damage rolls RPG critical chance in the damage call; this is not proof of native Light Attack critical semantics.
- `NativeBasicAttackObserver` authenticates native Primary item roots and observed Health loss, and excludes RPG damage metadata. Simply invoking the ordinary Primary root without explicit skill provenance would risk the observer treating it as ordinary basic-hit recovery.
- Existing Quick Slash damage uses RPG metadata, rather than that native-basic recovery path. No new native invocation/provenance code was installed, so no new 4%/12% recovery path was opened.
- `MasteryRootBudget` retains meaningful-result/eligibility/manual-root checks and root deduplication. No mastery grant path was altered or inferred from two hit events.
- `StrikeRepeatSchedule` remains bounded; the existing executor/action-lock lifecycle owns scheduled follow-up work. No additional callbacks, weapon polling or stale-hit cleanup path was introduced.

### Passives and Mantle

- The existing compiler retains Quick Slash's Multistrike exception: its two-hit pair becomes six bounded hits under the existing repeat controller, not two new recursive roots.
- `StrikeSecondaryRuntime` skips derived releases; Shockwave uses `root.effects().once("SHOCKWAVE")`, so it is not automatically multiplied by both hits or victims.
- Existing HitProcRuntime receipts, NoProc/derived exclusions, conditional-damage and leech budgets are unchanged.
- Potency, Multistrike, Ruthless, Shockwave, Lifeblood, Attunement, Executioner and Opportunist all retain `COMPILED_PROFILE_RESOLVED_CONNECTED_UNVERIFIED` for Quick Slash in the regenerated baseline matrix.
- **No claim is made that current Quick Slash emits the requested reduced Fire envelopes or two Mantle pulses.** That is part of the redesign blocked by the missing source/execution contract. Passing Mantle baseline tests does not prove the proposed Quick Slash integration.

## Validation performed

No assertions, expected values, exclusions or test implementation were changed.

| Existing baseline suite | Tests | Failures/errors/skips |
|---|---:|---:|
| MantleRuntimeTest | 6 | 0/0/0 |
| Stage11CompatibilityMatrixTest | 5 | 0/0/0 |
| Stage11HitProcTest | 38 | 0/0/0 |
| Stage11StrikeCadenceTest | 30 | 0/0/0 |
| Stage11StrikeSecondaryTest | 30 | 0/0/0 |
| Stage13NativeBasicHitTest | 22 | 0/0/0 |
| Stage13QuickSlashSpeedTest | 2 | 0/0/0 |
| Stage13StrikeClosureTest | 30 | 0/0/0 |
| WeaponFireDecisionTest | 52 | 0/0/0 |
| MantleNativeSourceContractTest, separate native JVM | 10 | 0/0/0 |
| Stage13NativeBasicPathTest, separate native JVM | 3 | 0/0/0 |
| **Total** | **228** | **0/0/0** |

The main test run completed in 13 seconds; the native-control run in 7 seconds. One initial command used the nonexistent task name `nativeAssetTest` and failed at task lookup; it ran no tests. The corrected task is `nativeControlTest`, and its successful log is retained separately. Two native classes specified in the main test filter are intentionally excluded by the existing Gradle setup; they were explicitly run in that separate native task, not counted as passing from the main run.

The compiler matrix regenerated **6,030 single skill/passive cells (90 skills × 67 passives)** plus its retained pair/property fixtures. Its output remains `build/stage11-matrix`. This is compiler/profile evidence only.

The new-design A–O assertions were **not implemented or claimed passing**. The complete retained release suite, full isolated smoke, packaging and deployment gates were **not rerun**, because the explicitly required pre-implementation gate stopped this task and there is no coherent redesign candidate. Prior AR validation is recorded in the AR report, not reclassified as validation of this proposal.

## Installed artifact and rollback

- Installed, unchanged: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar`.
- Installed SHA-256: **`D424F051F55F9B61F89A84B2C8858192C42A55992A72419E513D79E4C263659D`**.
- This exactly matches `evidence/stage-13/cohort-ar/artifacts/HyARPG.jar`, also rehashed during this task.
- Manifest inspection confirms exactly one active RPG identity (`InigmasGames:HytaleRPGPhase00Audit`) among the four live JARs. The other three are CanvasUI, HytaleDevLib and ImmersiveNPCs.
- No replacement occurred; the installed AR artifact itself remains the working baseline. Existing AR rollback/save-backup instructions remain in [the AR report](stage-13-mantle-of-flame-ar-report.md).
- Live saves, NPC data, progression, traces and configuration were not modified by this task.

## What would unblock implementation

One of the request's two permitted seams must become available:

1. A verified native one-Light-Attack invocation with **execution-local phase/playback timing** and source/provenance integration, without mutating ordinary attacks or cloning the family graphs; or
2. An existing authoritative complete weapon-hit producer/executor exposing the normal Light Attack composition and legitimate interactions across eligible weapons, not just the current special-case Fire component.

No broader implementation was attempted. In particular, this task did not silently replace the request with a 0.375× generic Physical strike, extend Mantle's source whitelist, bypass Flame Weapon's capability gate, accelerate world time, or modify native damage per victim to simulate a source envelope.

## Owner-facing status / connected QA

- **IMPLEMENTED:** audit and report only; requested redesign blocked.
- **PACKAGED:** no new candidate.
- **DEPLOYED:** no replacement; R032-AR remains installed.
- **CONNECTED-VERIFIED:** no new connected test; no new behavior to test.

There are no new commands for this redesign. Running the existing Quick Slash skill would exercise the previous behavior, not the requested two 37.5%-source native Light Attacks. Do not use that session as acceptance of this redesign.
