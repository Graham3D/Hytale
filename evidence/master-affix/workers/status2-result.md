# Status worker continuation evidence

## State

Implementation is **unfinished**. No Gradle run, native registration, connected gameplay run, commit, or push was performed. The current ENABLED gate must remain. Constructor-only snapshot tests are not proof of equip admission. The earlier `status-result.md` remains the evidence for the original WA-013/056/064/065–068/079–081 work; this file records the continuation.

## Changed files

- `combat/status/StatusService.java`, `RpgStatusType.java`, `GearStatusRuntime.java`: canonical status types, chance admission, control duration, independently expiring Slow stacks and locks, Fear source/break ownership, Rending regeneration reduction.
- `execution/hytale/HytaleAreaStatuses.java`, `AreaNpcControlSystem.java`: native control effects and actual NPC steering; NPC Slow uses the selected exact magnitude once.
- New `execution/hytale/NativeGearStatusObserver.java`, `NativeStatusPhysicalMiss.java`, `NativeAffixFearBreak.java`, `NativeAffixHostileDisplacement.java`; new `combat/hytale/NativeHealthRegenerationSuppression.java`.
- New server assets `RPG_Affix_Stun.json`, `RPG_Affix_Silence.json`, `RPG_Affix_Blind.json`.
- `combat/status/ControlledGearSnapshot.java`, `GearStatusAdmissionTest.java`, `execution/hytale/NativeAffixStatusProjectionTest.java`.

## ID operation and proof limits

| IDs | Implemented owned path | Remaining boundary |
| --- | --- | --- |
| WA-053–055, WA-134 | `GearStatusRuntime.admit` admits one Bleed/Burn/Poison chance from a positive applied channel; deterministic tests enter the real `PeriodicStatusRuntime`. Rending weighted source attribution starts 4 s strongest-only 50% positive native Health regeneration reduction. | Main must register the native observer with a skill-owner `PeriodicAdmission` using the actual Burn/Poison/Bleed profile, context, stack cap and current `PeriodicStatusRuntime.Port`. Native regen decorator is installed by the observer after Rending succeeds. Confirm whether any NPC Health writer bypasses `EntityStatMap` regeneration. |
| WA-056–057 | Shared contact and D04 admission routes into existing Chill threshold/Electrified stacks; native projection is queued by observer. | Merge skill opportunities before existing skill status admission; multi-channel contact and proc budget from accepted envelope. |
| WA-058 | Five independently expiring 10% Slow stacks, 5 s source-skill lock, .75 s target interval, five-stack cap and bounded memory. Exact NPC steering scaling. | Player movement still uses discrete `RPG_Chill_*` effects, so exact player Slow/WA-081 projection is incomplete. Gear observer currently uses stable `GEAR_BASIC_ATTACK` source ID; skill producers must pass canonical SkillId. |
| WA-059–063 | Stun/Silence/Blind/Fear/Root canonical timed admission, immunity/control history, native assets and NPC action/steering projection; source-owned Fear retreat and later-root break. | Silence manual skill gate and player Blind vignette require skill/UI owners. Register `NativeAffixFearBreak`; verify native effects against installed assets and actual player/NPC actions. |
| WA-064, WA-079 | Existing D04 chance uses source penetration and live target resistance once. | All skill and item opportunities must share it. |
| WA-065–068 | Existing `GearStatusRuntime.applyPeriodic` uses the source-owned periodic runtime and frozen modifier/duration input. | All real skill/item package creation sites must call it. |
| WA-080 | Incoming hard-control duration applies target valid snapshot, including Chill-to-Frozen threshold. | Call target-snapshot overload for every non-gear skill/native admission. |
| WA-081 | Strongest exact Slow reduction works in StatusService and native NPC steering. | Exact player movement modifier remains missing; do not enable. |
| WA-082 | `NativeAffixHostileDisplacement.apply` reads live valid NPC target gear, scales the hostile request before `AreaDisplacementPlanner`, then performs bounded collision/support checks and native transform movement. | Skill owner must replace hostile NPC push/pull/knockback call sites with this adapter. Player hostile displacement needs its supported native movement request adapter. Self motion must retain existing path. |

## Exact cross-owner registration and calls

1. **Main (`HyArpgPlugin` / phase registration):** keep the existing `HytaleDamageLifecycleSystems.BeforeApplication` immediately before `DamageSystems.ApplyDamage`. Pass a **composite** `AppliedObserver` to `HytaleDamageLifecycleSystems.Application` that calls the existing `GearAppliedHitRecovery.observed` and `NativeGearStatusObserver.observed` once per native applied receipt. Instantiate the latter with the shared `StatusService`, one world/actor lifecycle-owned `GearStatusRuntime.Contacts`, and a complete `Opportunity`. Register `NativeAffixFearBreak(statuses)`. Register `NativeStatusPhysicalMiss(statuses)` **in place of** `HytaleDamageLifecycleSystems.ElectrifiedPhysicalMiss` to avoid two rolls; the new filter reads the outgoing actor, includes Blind's 50% rule and leaves native damage submission in its existing phase. Existing `AreaStatusProjectionSystem` and `AreaNpcControlSystem` must stay registered. Call `contacts.forget(actor)` and `statuses.forgetSource(actor)` on source logout/death/world transfer.
2. **Combat/skill owners:** fill `NativeGearStatusObserver.Opportunity` from actual accepted hit context: hostile/protection classifier; `SupportNativeEffects.control`/boss profile; existing non-gear Status Resistance; bounded item proc coefficient from the authored shot/area/channel budget; skill chance before resistance; and the existing deterministic, journaled RNG for `draw(type, receipt, purpose)`. Preserve the root/contact identity. Current managed gear receipt has channel and frozen gear snapshot but does **not** carry an accepted proc coefficient or skill opportunity. Do not return a blanket coefficient of 1 for pellets/areas/continuous hits.
3. **Resources/skill worker (`HytaleSkillExecutionSystem`, `SkillExecutionService`):** provide `PeriodicAdmission` that invokes `GearStatusRuntime.applyPeriodic` on the existing `PeriodicStatusRuntime` with the skill's canonical profile, committed action context and existing native damage Port. Merge same-status skill and item opportunity before the skill's existing status callback, preserving skill-only RNG/results. Apply target valid gear to each incoming control admission. Gate manual skills on SILENCE while preserving Primary/Secondary basic attacks. Project a player Blind vignette through supported UI/effect assets.
4. **Skill movement owner:** replace hostile displacement calls at `HytaleSkillExecutionSystem`'s `applyKnockback`, area push/pull and any other hostile movement request with `NativeAffixHostileDisplacement.apply` or its `plan` using the same native collision/support predicates. Existing self-dash/teleport paths stay separate.
   The current `apply` adapter requires a native NPC role and grounded motion; implement the player hostile-motion equivalent before claiming universal WA-082 coverage.
5. **Player movement owner:** add a supported exact single native movement contribution for `statuses.strongestSlow(id, GearNativeItems.effects(target, accessor).snapshot())`. Current five-percent effect selection rounds the result and cannot satisfy WA-081 for players. Retain the gate until this is solved.

## Checks

- Wrote deterministic positive/control/negative tests using legal controlled gear and the production `StatusService` plus real `PeriodicStatusRuntime`. Tests cover WA-053–055/057–063/082/134, local proc scope versus global Status Penetration, Slow locks/expiry, native steering and miss policy; earlier worker tests cover WA-013/056/064–068/079–081.
- Inspected installed public `Steering`, `EntityEffect`, `HealthRegenState`, `RegeneratingValue`, and `CommandBuffer` APIs with `javap`; used existing supported `Steering.scaleTranslation`, `RegeneratingValue.regenerate`, and native EffectController calls.
- Parsed all three new server effect JSON files. Targeted `git diff --check` completed with no whitespace errors (only existing line-ending warnings).
- Tests were **not run** under worker instruction. Native observer, actual skill periodic port, player exact movement, proc coefficient, registration order, and connected QA are unverified. No claim of completed 160-affix coverage.
