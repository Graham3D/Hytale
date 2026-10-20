# Combat/status negative owner evidence — 2026-09-30

## Exact test methods and IDs

| IDs | Test method | Measured owner boundary |
| --- | --- | --- |
| WA-017, WA-018, WA-019, WA-020, WA-021, WA-022 | `NativeAffixFinalChannelTest#wa017Through022WrongActiveSourceAndUnsupportedCarrierSubmitNoElementalChannel` | Each legal mapped sword is admitted with an unrolled item. The rolled active hit has its authored native elemental amount; the unrolled active item has none. `NativeGearAttackAcceptance.commit` rejects the rolled hit under the wrong active chain and leaves the unrolled hit unaccepted. `ManagedGearDamageInteraction.claim` rejects a witnessed unsupported Fall carrier without consuming the queued matching cause, then claims that cause once. |
| WA-029, WA-030, WA-031, WA-032, WA-033, WA-034 | `NativeAffixFinalChannelTest#wa029Through034NativeDamagePenetratesOnlyMatchingDirectCauseWithoutChangingTarget` | Each admitted legal roll and same-base unrolled control creates a real `Damage` with native cause and RPG metadata. `HytaleDifficultyCombat.penetration` changes the matching direct resistance result; wrong native cause, periodic origin, and absent RPG metadata yield zero penetration. Immunity still yields zero damage. The target resistance map, immunities, and effective value remain unchanged. |
| WA-065 | `GearStatusRuntimeTest#wa065BurnOnlyReplacesWeakerSourceWithoutRaisingCapOrOtherStatus` | Admitted rolled and unrolled sources enter `GearStatusRuntime.applyPeriodic` and the production `PeriodicStatusRuntime`. Strong Burn replaces a weaker same-source package; a later weaker refresh retains the stronger coefficient and one-stack cap. A separate Burn source and Poison/Bleed retain base magnitude. Wrong item source scoping is empty. |
| WA-068 | `GearStatusRuntimeTest#wa068PoisonAndBleedRefreshExpiryAndTickMagnitudeKeepSourceCaps` | Admitted rolled and same-base unrolled controls enter the same periodic owner for Poison and Bleed. Source cap and tick coefficient stay equal, refresh resets each authored duration, the control expires first, and the rolled package supplies only the expected final fractional tick before expiry. |

`GearStatusAdmissionTest` was compiled and run as the existing admission regression; WA-053 through WA-055 are assigned to the final-controls worker and were not duplicated here.

## Verification and production decision

Direct `javac --release 25` against the existing compiled main classes, installed pre-release Hytale server JAR, and cached JUnit dependencies passed. An isolated JUnit Platform run of `NativeAffixFinalChannelTest`, `GearStatusRuntimeTest`, and `GearStatusAdmissionTest` found **15 tests, 15 passed, 0 failed**. This is an offline native-adapter and periodic-owner result; a connected world final-HP observation remains a later gameplay check. No Gradle, build task, game, server, authentication, deployment, commit, registry change, or gate was run.

No production change is needed from these cases. `NativeGearAttackAcceptance` and `ManagedGearDamageInteraction` already reject the tested wrong source/carrier boundaries, and `HytaleDifficultyCombat.penetration` and `PeriodicStatusRuntime` produce the tested results. `NativeGearAttackAcceptanceTest` and `HytaleSkillExecutionSystem` were untouched.
