# R206-U7P5 — QA encounter effects and persistence recovery

## Connected R205 finding

The owner confirmed R205 QA profile selection. `/rpg spawn Larva_Void unique stoneskin manaburn reflective` was accepted, then combat produced `RPG_ENCOUNTER_FAILURE boundary=NATIVE_SPAWN_CONTEXT detail=ENCOUNTER_EFFECTS_UNAVAILABLE`; subsequent QA spawns returned `ENCOUNTER_PERSISTENCE_UNAVAILABLE`. The connected trace recorded successful damage contribution, followed by effects and runtime uncertainty while the encounter WAL reported no failure. There was no frozen death receipt for the hit enemy.

The failure is in the existing encounter effects owner, not a missing plugin registration or a premature invocation. `HytaleEncounterRewards.captureDeath` calls `PersistentEncounterRuntime.finishDeath` on `DurableEncounterEffects` after the damage receipt is durable. `EncounterContributions.death` previously created a player `Share` for a QA actor with combat credit. `DeathPlan` rejects any non-economic Master Enemies actor with nonempty shares (`INELIGIBLE_ENEMY_REWARDS`). This exception escaped the worker and set its persistent `failure` field. The next effect reservation then threw `ENCOUNTER_EFFECTS_UNAVAILABLE`; `HytaleEncounterRewards.Tracking.onEntityAdded` reported it as `NATIVE_SPAWN_CONTEXT`. Its `safely` wrapper marked the entire runtime unavailable, so `birthIo` rejected later QA spawns with `ENCOUNTER_PERSISTENCE_UNAVAILABLE`. The latter errors were consequences of the first local death-plan failure, not proof of a failed WAL.

## R206 repair

- The shared encounter ledger now freezes QA and other non-economic Master Enemies deaths with zero player shares, even when damage was credited. The death still passes through the existing durable store and pack terminal-receipt owner. QA remains ineligible for XP, learning, pity and item rewards. The same ledger excludes non-economic actors from provisional mastery eligibility.
- Production death finalization now disqualifies one actor durably if its plan has a recognized, pre-write local validation error. It writes the existing exclusion tombstone before releasing that actor's death ticket; the effects worker can then process the next encounter. A failed tombstone write, WAL failure, or unknown exception still follows the global reward fail-closed path. No persistence bypass was added.
- The QA classifier and authored-profile fallback in R205 are unchanged. This revision makes no command syntax change.

## Offline verification and owner QA

The focused `QaEncounterEffectsRecoveryTest` covers two credited QA pack actors closed in one session with no rewards, a second native attachment after the first death, and a local invalid plan that writes an exclusion while the next QA actor still completes. It checks that the effects worker and runtime remain available. `Stage13EncounterNativeHandoffTest` checks that ordinary economic death delivery still awards its expected XP.

- `:test --tests com.inigmasgames.hytalerpg.enemies.QaEncounterEffectsRecoveryTest --tests com.inigmasgames.hytalerpg.Stage13EncounterNativeHandoffTest :jar --no-daemon` passed: four focused tests, native proc asset audit, and CustomUI validation (88 documents). No standalone server was started.
- Packaged `manifest.json`: `Version=0.2.0-R206-U7P5`, `RpgRevision=R206-U7P5`.
- R205 recovery JAR: `evidence/r206-qa/HyARPG-R205-U7P5.jar`, SHA-256 `D68155E8602FFC3C5111B880E787C093446C2EADCB4372F7A45CADAEB4578EFD`, outside Saves.
- Sole active RPG save mod: `Hytale/data/pre-release/Saves/RPG/mods/HyARPG.jar`, SHA-256 `74B9AD14942182E9F080800EA59D6E1D6BF2A8688F7D1F60452EA687CCEDEBB9`. Built and installed checksums match.

After restarting the world, run `/rpg spawn Larva_Void unique stoneskin manaburn reflective`, hit or kill the spawned enemy, run the same command again, then run `/rpg spawn Trork_Warrior unique stoneskin manaburn reflective`. Confirm each is accepted, `ENCOUNTER_EFFECTS_UNAVAILABLE` and `ENCOUNTER_PERSISTENCE_UNAVAILABLE` do not appear, and QA kills award no XP or loot. Connected acceptance remains with the owner.
