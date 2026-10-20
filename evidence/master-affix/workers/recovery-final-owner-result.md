# Recovery and support owner proof — 2026-09-30

## Exact offline proof

| ID | Method and measured owner result |
| --- | --- |
| WA-094 | `GearAppliedHitRecoveryOwnerTest#wa094AcceptedPostMitigationHitCreditsHealthOnce`: admitted gear and accepted post-ApplyDamage metadata enter `GearAppliedHitRecovery.accepted`; default `GearRecoveryRuntime` pays actual Health through a `NativeResourcePort` credit. Duplicate, unrolled, cancelled, blocked and NoLeech controls pay zero. |
| WA-095 | `GearAppliedHitRecoveryOwnerTest#wa095AcceptedPostMitigationHitCreditsManaOnce`: same callback, default runtime and native credit port measure Mana increase; controls pay zero. `MasterAffixResourceCompletionTest#wa095PaysActualManaOnAcceptedHitOnly` now also uses the default runtime. |
| WA-096 | `GearAppliedHitRecoveryOwnerTest#wa096AcceptedPostMitigationHitCreditsLifeLeechOnce`: leech derives from 30 observed Health loss despite 100 premitigation damage, then credits Health once; controls pay zero. The existing separate Sentinel self-heal proof remains `MasterAffixSentinelRecoveryTest#boundNpcAcceptedHitPaysOnlyNpcHealth`. |
| WA-097 | `GearAppliedHitRecoveryOwnerTest#wa097AcceptedPostMitigationHitCreditsManaLeechOnce`: the same postmitigation receipt credits Mana once; controls pay zero. `MasterAffixResourceCompletionTest#wa097PaysActualManaLeechOnAcceptedHitOnly` now uses the default runtime. |
| WA-098 | `GearRecoveryRewardDispatchTest#wa098AcceptedSharePaysDefaultOwnerOnceWithinLiveCreditWindow`: `HytaleEncounterRewards.dispatchRecovery` sends a frozen admitted share to `GearCreditedDeathRecovery.accepted`, then the default runtime credits native Health. The duplicate is tested after its one-second payout window; unrolled and uncredited recipients have no credit. `MasterAffixResourceCompletionTest#wa098CreditsKillHealthOnceAndRejectsIneligibleDeath` also uses the default runtime. Both callback and payout use the current monotonic time domain, so the three-second credit has not expired. |
| WA-111 | `GearReceivedHealingOwnerTest#wa111AdmittedPlayerRecipientChangesActualRequestAndClipsNativeCredit`: admitted player-recipient gear enters the `HytaleSupportSystem.receivedHealing` helper used by the actual heal callsite. `HealingCalculationService.direct` produces the request once; a `NativeResourcePort` measures credited Health and cap clipping. Same-heal unrolled and broken recipient controls return the base request. |
| WA-112 | Existing canonical receipt proof only: `GearSupportConsumerTest#genericStatusOwnerCleanseEmitsOnlyAfterActualRemovalAndGrantsRespite`. No new cleanse implementation or proof was requested. |

`GearAppliedHitRecovery.observed` calls the extracted `accepted` method after native source, hostility and protection checks; the tests exercise the same callback with post-ApplyDamage receipt values. `HytaleSupportSystem.Port.heal` calls `receivedHealing` for the recipient snapshot before the existing native Health write. No base skills or shared skill/summon owners were edited.

## Verification and limits

Direct `javac --release 25 -sourcepath ''` compiled the two owned production sources and four focused test sources against the installed Hytale server JAR, existing local classes/resources and cached dependencies. The direct JUnit launcher ran `GearAppliedHitRecoveryOwnerTest`, `GearRecoveryRewardDispatchTest`, `GearReceivedHealingOwnerTest`, and `MasterAffixResourceCompletionTest`: **18 found, 18 passed, 0 failed**. `git diff --check` on tracked owned files passed.

This is deterministic offline owner-adapter proof. A native ECS world and connected HP event were not run. No Gradle, build, game, server, authentication, deployment, commit, registry or gate change was performed.
