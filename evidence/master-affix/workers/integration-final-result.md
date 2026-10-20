# Master Affix integration handoff — 2026-09-30

The owner correction in `docs/MASTER_AFFIX_SCOPE_CORRECTION.md` governed this pass. No base skill was created or altered here. Ameliorate is not an affix integration dependency; Simulacrum retains its finite 20 Mana source cost. WA-112 is a generic successful-cleanse receipt and WA-155 remains uncalibrated.

## Production registrations and effect-bearing paths

`HyArpgPlugin` now constructs one `NativeSentinelItemAuras`, gives it to the support and summon owners, constructs `NativeItemAffixBindings` with `AppliedReceipt::acceptedDamage`, `AppliedReceipt::procCoefficient` and the concrete `sentinelChildPort`, registers its availability, genuine post-stamina block and removal systems, and passes it to the Sentinel's genuine post-block observer. The same native `Application` observer list contains signature, recovery, status and item observers. Status registration includes `GearStatusBindings.NativeAcceptance` in Gather, the status observer, periodic tick/removal, `NativeStatusPhysicalMiss` and `NativeAffixFearBreak`. Signature registration includes prechain, defense filter, raw before/after, child dispatch, removal, death and applied receipt. World removal and player detach clear corresponding ledgers. Player and Sentinel Aura lookups share `NativeSentinelItemAuras`' strongest-valid Mana regeneration, cooldown recovery and Thorns projections.

`AppliedReceipt.procCoefficient()` reads `NativeGearAttackAcceptance.find(world,actor,hit)` for accepted native gear hits or the real `HitProcRuntime.coefficient(context)` for authored skills. No pellet receives a fabricated coefficient of one. Native status acceptance uses the same committed hit identity before Apply. Accepted damage before absorption remains available when HP loss is zero. Status and item observers ignore canceled or blocked components and can admit a later accepted component. `GearStatusBindings` uses the skill owner's actual `PeriodicStatusRuntime`; Sentinel bound-item packages now advance on Sentinel ticks and cancel on actor/world removal. The native miss filter uses a deterministic keyed draw for authored contacts and a bounded per-actor native sequence for raw contacts.

An absorbed skill hit contributes no authored skill status chance, preserving the existing Health-loss gate while an item status may still use the accepted hit. For periodic statuses the shared D04 draw chooses the existing skill package when the skill succeeds and the generic item package on item-only success. Natural termination removes the world/source tracking entry for both player and Sentinel packages.

`GearSignatureBindings` now authenticates every direct channel of one contact, including channels with `canProc=false`; it waits for the positive authored channels only. A canceled or blocked first channel cannot veto a later accepted channel. A zero-valued vector entry cannot hold the aggregate open. Reflection completion entries are kept only for melee contacts and are bounded. The three registered signature native adapters carry `NativeRpgTickMetrics` spans.

## Offline verification

- Direct `javac --release 25` compiled the touched signature, status, item, miss, Fear, support, skill and plugin registration sources against the installed pre-release server JAR and current project classes. No Gradle was invoked.
- Focused integration and existing status tests: **7 passed, 0 failed** (`GearSignatureIntegrationTest`, `NativeStatusPhysicalMissIntegrationTest`, `GearStatusAcceptanceIntegrationTest`, `GearStatusBindingsTest`). They cover canceled-first-channel aggregation, zero-channel completion, absorbed acceptance without a manufactured skill package, stable miss draws and one status opportunity per root/victim.
- `Stage13NativeTickMetricsTest`: **3 passed, 0 failed** after trace wrapping.
- Existing `NativeSentinelItemChildrenTest` and `NativeAffixStatusProjectionTest`: **4 passed, 0 failed**. Existing `ItemSkillChildExecutionTest`: **2 passed, 0 failed** after compiling the current source `RpgCatalog` with its corrected 97-skill expectation.
- Three existing `ItemSkillTriggerRuntimeTest` methods passed. Its `completedItemHitsQueueFixedRankNoProcBoltWithSharedOwnerLock` fixture failed because it reuses one root for a purported later cast. The owning test must use a new root to exercise a new authored execution; the runtime's one-opportunity-per-root behavior is intentional and required. Details are in `integration-needs.md`.
- `git diff --check` on edited tracked Java reported no whitespace errors. No game/server/auth, deployment, commit or push was run.

## Main-owned final qualification and external boundary

Main still owns final Gradle, capability gates, audit, deployment and connected verification. The skill direct loop now appends authored component identity to its contact correlation; channels within one vector keep one contact. The actions worker must finish literal `RpgProcSelector`/`RpgProcCoefficient` interaction variables; `integration-needs.md` records the exact interface. The current checkout scan did not find either variable in `src/main/resources`, so a status or signature hit requiring a native envelope will fail closed until that producer is authored. This handoff does not claim those IDs enabled or 160/160 connected proof.

The remaining external observation for WA-155 is client light radius calibration in metres and the visible renderer ceiling. No conversion or renderer cap was inferred from the server byte codec.
