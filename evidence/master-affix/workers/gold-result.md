# Gold worker result

## Scope and status

- WA-152: **runtime consumer and durable Gold admission implemented; gated, uncompiled and unverified in a connected game**. Ordinary eligible credited enemy death plans carry one frozen Gold pot. `GoldRewardPolicy` reads `/rpg/gear/gold-reward-v1.json`, initially `ordinaryBaseGold: 1`. No rank multiplier is applied. The one recipient is the eligible share with the lowest UUID string; this explicit deterministic allocation prevents multiplying Gold by party size. This policy needs owner balance/party allocation review before release.
- D09: owner `GoldBalance` contains integer Gold and exact decimal remainder. `RewardCheckpoint.advance` computes `floor(base * (1 + frozenPercent / 100) + remainder)` once. The new balance and remainder are written in the same player file replacement and accepted earned-reward receipt as XP, Insight, mastery, and learning. The existing event receipt rejects replay and changed payload. Gold has no pickup, transfer, sale, or inventory bonus path.
- WA-151: isolated QA Magic Find verification added. A protected `qaOnly` fixture remains QA. The test computes production-excluded and isolated shadow MF and quality weights through the actual `GearMagicFind.snapshot` and `GearMagicFind.weights` methods and prints both values. It grants no drop or reward. Existing `HytaleGearEquipment.magicFindBreakdown` production exclusion and `HytaleEncounterRewards` MF death snapshot remain intact.

## Native and persistence wiring

`HytaleEncounterRewards.Death` calls `died`, which reads `gearEquipment.effects(actor, store).snapshot()` from valid equipment on the native death thread. It sums non-QA `GOLD_FIND` sources only when `GearAffixRuntime.ENABLED` includes `WA-152`. The value is captured for present candidates before the durable effects worker resolves eligible shares. `PersistentEncounterRuntime.finishDeath(..., frozenGoldFind)` attaches only eligible recipient values and the configured base to `EncounterContributions.DeathPlan` before `FileEncounterStore.freezePrepared`. The frozen plan is the source for replay after restart. `DeathPlan.reward` attaches the Gold pot to the selected recipient's `EarnedReward`. `LearningSources.Opportunity.decide` preserves it. `RpgLoadoutService.awardEarned` applies the receipt to `RpgPlayerState.goldBalance` through `RewardCheckpoint`, and its committed trace includes base, percent, bonus, granted Gold, and remainder.

Schema 12 initializes `GoldBalance.INITIAL` for pre-Gold saves; `FileRpgPlayerStateRepository` checks presence, and `RpgPlayerState.normalizeShape` checks the values. Historical reward intents with no Gold checkpoint remain readable through nullable checkpoint compatibility. This is a saved reward ledger field, not a separate mutable counter.

## Changed files

- `src/main/java/com/inigmasgames/hytalerpg/progress/GoldBalance.java`, `GoldRewardPolicy.java`, `RpgPlayerState.java`, `RpgStateMigrator.java`, `FileRpgPlayerStateRepository.java`
- `src/main/java/com/inigmasgames/hytalerpg/progress/EarnedReward.java`, `RewardCheckpoint.java`, `EncounterContributions.java`, `PersistentEncounterRuntime.java`, `LearningSources.java`, `RpgLoadoutService.java`
- `src/main/java/com/inigmasgames/hytalerpg/execution/hytale/HytaleEncounterRewards.java`
- `src/main/resources/rpg/gear/gold-reward-v1.json`
- `src/test/java/com/inigmasgames/hytalerpg/Stage12EarnedRewardTest.java`, `src/test/java/com/inigmasgames/hytalerpg/gear/GearMagicFindShadowTest.java`

## Tests added, not executed by this worker

- `controlledDeathPotCommits120GoldInTheRealRewardReceipt`: 100 base + 20% = 120, duplicate pays zero.
- `baseOneGoldCarriesFractionAcrossRestartAndDuplicateDeath`: four 1-base, 25% awards yield 5 Gold with zero carry, with restart after the first.
- `interruptedGoldReceiptReplaysBalanceAndRemainderTogether`: interruption after player save recovers exactly once.
- `oneDeathPlanPaysOneGoldPotAcrossTwoCreditedRecipients`: no extra party Gold.
- `frozenDeathGoldSurvivesEncounterRestart`: frozen encounter plan and real encounter/reward store drain survive restart.
- `nonDeathRewardAndInvalidGoldPayloadDoNotChangeBalance`, `schemaElevenMigrationAddsEmptyGoldWithoutChangingEarnedXp`.
- `GearMagicFindShadowTest.qaDiscoveryKeepsProvenanceAndOnlyChangesIsolatedQualityWeights`: production exclusion and isolated real MF path, including raw Luck 100 plus controlled 15% = 0.65.

Static `git diff --check` found no whitespace errors. Gradle, Hytale launch, and connected proof were intentionally not run per task instruction.

## Cross-owner follow-up

1. Main owner: run compile and focused tests, inspect schema 12 migration and recovery against working save copies, then enable `WA-152` in `GearAffixRuntime.ENABLED` only after consumer acceptance. Its authored disposition and production generator gate also need coordinated promotion. Do not count WA-152 as production enabled yet.
2. UI owner: show Gold balance and Currency Find from the same persisted state/equipment snapshot in the existing stats view, without changing layout or parsing tooltip text. Connected trace should confirm the reward pot's base, bonus, grant, and remainder.
3. Main owner: evaluate the deterministic one-owner Gold allocation policy for parties. Changing it requires a versioned policy decision; frozen death plans and accepted receipts must never be recomputed.

No connected evidence exists. These changes do not claim 160/160 completion.
