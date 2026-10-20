# Summons worker result (2026-09-30)

## Files changed

- `src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleSummonSystem.java`
- `src/main/java/com/inigmasgames/hytalerpg/execution/summon/SummonRegistry.java`
- `src/main/java/com/inigmasgames/hytalerpg/execution/summon/SummonNativeMovement.java`
- `src/main/java/com/inigmasgames/hytalerpg/execution/summon/IronSentinelAffixes.java`
- `src/main/java/com/inigmasgames/hytalerpg/execution/summon/IronSentinelStatProjection.java`
- `src/main/resources/Server/Entity/Effects/RPG/Summon/RPG_Summon_Pursuit_048.json` through `_360.json` (313 exact 0.1 pp native variants)
- `src/test/java/com/inigmasgames/hytalerpg/SummonAffixLeaseTest.java`
- `src/test/java/com/inigmasgames/hytalerpg/execution/summon/IronSentinelAffixInheritanceTest.java`
- `src/test/java/com/inigmasgames/hytalerpg/execution/summon/IronSentinelRestoredLeaseTest.java`
- `src/test/java/com/inigmasgames/hytalerpg/execution/summon/SummonNativeMovementTest.java`

No unrelated dirty files were reverted. No build, game launch, server, commit, or push was run.

## Production paths and exact status

`HytaleSummonSystem.execute` now obtains the valid owner snapshot from `GearNativeItems.effects(owner,store).snapshot()` and supplies it to new `SummonRegistry` reservation overloads. The lease freezes that source across later gear changes. Old overloads remain for existing callers. `SummonRegistry.Lease` also freezes one bound source item for the Sentinel; the stat projection and native DamageGuard read that bound snapshot. Decoys receive no combat minion damage/health/rate/resistance or Pursuit effect. Bound item minion modifiers remain OWNER_ONLY.

| ID | Actual consumer added in this ownership | Remaining boundary |
| --- | --- | --- |
| WA-113 | Lease coefficient uses a ratio of the accepted Increased bucket with/without V, so the existing `HytaleSkillExecutionSystem.configureSummons` damage callback consumes the captured additive result for created attacks, including Sentinel. | Main must enable ID after native tests; dynamic victim-time outgoing buckets in the shared damage callback still need parity review. |
| WA-114 | Lease maximum Health; existing native spawn callback projects `EntityStatMap` MAX and initializes new HP once. | Main must enable ID; existing Sentinel restore cannot reconstruct owner gear snapshot yet. |
| WA-115 | None. | Shared Defense rating/mitigation owner must expose a summon DefenseView with authoritative level and native chassis rating; avoid a second percentage reduction. |
| WA-116 | `HytaleSummonSystem.DamageGuard` uses the frozen lease value for six native elemental causes and applies one capped reduction. Physical, unknown causes and decoys are controls. | Main must enable ID; native cause and other mitigation order still need full regression. |
| WA-117 | Lease melee claim interval changes; this reaches the existing RPG melee attack scheduler. | Native skeleton archer shoot/animation/release interval is unchanged; do **not** enable as complete until a supported native attack timing profile is wired. |
| WA-118 | `SummonNativeMovement.refresh` applies an exact short `EntityEffect` on the NPC each 0.1 s. `HytaleSummonSystem` ticks before `RoleSystems.BehaviourTickSystem`. Pinned `NPCEntity.getCurrentHorizontalSpeedMultiplier` bytecode reads active effects' `HorizontalSpeedMultiplier`, so native pathfinding consumes this factor. | Main must enable ID and validate asset map availability on the packaged build. Effect failure emits `SUMMON_REJECTED/NATIVE_PURSUIT` and preserves actor. |
| WA-119 | Lease finite expiry uses the committed owner value; Sentinel remains persistent. | Main must enable ID. |
| WA-120 | No edit in this ownership. Concurrent resource owner added `GearResourceModifiers.factor(gear, type, false, profile.summon()!=null)` at preparation and commit in `SkillExecutionService`. | Resource owner must verify Simulacrum full-pool drain, item sacrifice, Economy stacking and deterministic native payment; main must retain gate until then. |

All listed WA-113–120 IDs remain outside `GearAffixRuntime.ENABLED` at this handoff. The new consumers check that gate. No new ID is claimed runtime-functional yet. The controlled snapshot constructor used by tests is not production equipment admission. `HytaleGearEquipment.collect` currently excludes a whole item unless all of its affixes are enabled, so main must stage mixed items deliberately.

## Sentinel D12

`IronSentinelAffixes.classify` now follows the exact 160 individual cards: 70 SELF_STAT, 31 ATTACK_PROC, 3 AURA, 56 OWNER_ONLY, zero semantic UNSUPPORTED. A read-only comparison of every card against the implementation found zero mismatches. OWNER_ONLY is an explicit no-transfer decision; it does not certify the player effect. Forge admission still rejects incompatible SELF_STAT/ATTACK_PROC/AURA records until a real consumer exists. It now accepts the newly wired source resistances WA-073 (Ice/Water), WA-075 (Earth/RPG_Nature), and WA-078 (all six) in the existing native `DamageGuard`, alongside the four previously supported elements. The 75% cap applies after source points combine. Baseline chassis, bound-item custody and eligibility code were not changed.

This is **semantic disposition coverage, not 160 inherited effect coverage**. Current `ADAPTED` source set has 19 IDs plus 56 intentional OWNER_ONLY exclusions; 85 compatible source effects still fail the forge gate. No attack proc or Aura was newly claimed. Source WA-113 remains OWNER_ONLY and does not boost itself when forged.

## Native wiring already present

`HyArpgPlugin` lines 345–355 construct `skillExecutionSystem.configureSummons(...)`, bind `SummonProjection`, and register `summonSystem`, `HytaleSummonSystem.Removal`, `.Death`, and `.DamageGuard`. These registrations are sufficient for the lease, native Health, movement effect and Filter paths added here. `HytaleSkillExecutionSystem.configureSummons` contains the attack callback that consumes `lease.coefficient()` and the bound item's existing physical/crit projections. No plugin registration edit was made.

## Tests and verification

Added lease reservation/activation/claim/expiry tests with legal controlled focus items, owner source provenance, control leases, elemental/physical/unknown cause controls, and bound source no-transfer tests. Added a resource asset test for every Pursuit roll from 4.8% through two possible 18% held sources (36.0%). Updated D12 counts and Water/Earth/all resistance cases. Existing Sentinel source custody and chassis tests remain. `git diff --check` passed; the D12 card comparison had zero mismatches; 313 movement JSON assets were counted and endpoints inspected. Per instruction, Gradle and native gameplay tests were not run by this worker. Conditional gate assertions document dormant values today and will exercise positive lease values when main enables the reviewed IDs; they do not establish positive production QA now.

## Cross-owner work required

1. **Main:** enable WA-113, WA-114, WA-116, WA-118, WA-119 only after focused native validation. Keep WA-115 and WA-117 gated; review WA-120 with the resource worker. Run focused tests, full suite, packaged asset validation and regenerate the audit. Do not mark 160 complete from this result.
2. **Skill execution owner:** carry `Prepared.gearSnapshot` through `SkillExecutionContext` or an equivalent committed payload to `HytaleSummonSystem.execute`. Current `GearNativeItems.effects` read occurs at release; a windup gear swap can differ from the preparation snapshot. `WA-120` must discount only finite numeric Mana, not Simulacrum's full-pool payment or a bound item/corpse.
3. **Combat/defense owner:** expose the audited summon Defense rating and native projection for WA-115, and a bound-item Sentinel hit envelope/actual HP-loss receipt with Sentinel actor/root identity for the 31 D12 ATTACK_PROC cases. `HytaleSkillExecutionSystem.configureSummons` attack callback is the exact integration hook; `HytaleSummonSystem.DamageGuard` is the defensive hook. Reuse the shared status, recovery, trigger, and target-protection adapters, not a summon-only proc engine.
4. **Native attack timing:** WA-117 needs a supported per-spawn NPC attack/animation/projectile-release profile for `RPG_Summon_Skeleton_Archer`; the current `claimAttack` interval alone cannot make native arrows fire more often. The same review should cover Sentinel attack presentation and telegraph timing.
5. **Aura/support owner:** WA-148–150 need rank-1 Sentinel-anchored Aura admission and lifecycle without NPC Mana sustain (D08). The existing player Aura must remain separately paid.
6. **Persistence/loot owner:** extend the persisted `IronSentinelBinding` handoff so the original creation owner minion snapshot survives logout/restart. `SummonRegistry.restoreIronSentinel` currently has only the bound source item and restored passive factor, so WA-113/114/116/117/118 cannot be faithfully reconstructed from the creation gear after restart. Change `HytaleGearLoot` serialization/restore transaction with custody tests; this worker did not edit that owned file.

Completion remains open for the eight minion IDs and D12 compatible effects until these boundaries and positive/control/negative native tests pass.
