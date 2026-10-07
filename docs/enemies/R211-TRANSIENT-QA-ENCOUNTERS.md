# R211-U7P5 — transient Master Enemies QA encounters

Deployed to the active RPG save on 2026-10-07. `HyARPG.jar` SHA-256:
`477AB7265DFA49E4D62C697AB7370B958D09A1E27C09F6814A3029B29E503C6B`.
The prior R210 JAR is preserved outside `Saves` at
`C:\Users\Zemio\.codex\backups\HyARPG\HyARPG-before-R211-U7P5-7870C18E91F2.jar`.

## Failure addressed

R210 connected QA entered `NativeEnemyBirthReservation.beginQa` and the durable encounter effects queue. The log recorded `ENCOUNTER_CONTEXT_GENERATION_BUSY` at `NATIVE_SPAWN_CONTEXT`, followed by `ENCOUNTER_EFFECTS_UNAVAILABLE` and a global fail-closed latch. The next QA request was rejected as `ENEMY_QA_WORLD_UNAVAILABLE`. This was an admission/persistence failure, not evidence that Trork's ME-002, ME-019, or ME-025 affix mechanics failed.

## Ownership boundary

- `/rpg spawn` now freezes the existing authored combat profile, role, family, roster, and affix plan before spawning. It uses `NPCPlugin.spawnEntity` on the world thread. The pre-add callback installs staged invulnerability/freeze, QA provenance, non-serialization, exact native UUID, and native death-drop suppression. Staging stays in place until the real shared state/action/combat owners and all members are ready.
- `TransientQaEncounters` holds the session-only plan, pack state, and action-root high-water. It supplies the same `EnemyActionRootBlock` format to `NativeEnemyActionAttachment`; production still obtains those blocks from `FileEncounterStore`. Both paths use the same native action and affix runtime owners. No substantive affix implementation class was changed for R211.
- The existing `EnemyBirthPlanner.planQa`, `EnemyAffixSnapshot`, `HytaleDifficultyCombat`, `NativeEnemyStateAttachment`, `NativeEnemyActionAttachment`, and `NativeEnemyBirthPublication` execute the QA actors. No QA copy of an affix exists.
- QA actor damage, status, and death do not submit economic observations or death plans to `PersistentEncounterRuntime`/`DurableEncounterEffects`. `Origin.QA` reward context remains zero-economic as a second guard. Production encounter storage, corruption fail-closed behavior, and natural/campaign ownership remain durable.
- QA failures tear down only the staged QA group and its transient state. Placement tries nine deterministic nearby anchors and reports a local `QA_SPAWN_NO_VALID_POSITION attempts=9` if none fit. QA actors are non-serialized and cannot be recovered as production births on reload.
- The R210 QA native healthbar experiment is preserved; production monster presentation is unchanged.

## Verification scope

Focused offline tests passed for 50 independent Trork plans, each of the 27 admitted IDs through the same planner, invalid input followed by valid planning, transient action-root sequence, local registry failure followed by a valid reservation, leader/minion retirement, QA reward provenance, and the existing R210 healthbar contract. `verifyHyArpgJar` passed with 18,759 entries, 2,065 classes, revision `R211-U7P5`, and zero ImmersiveNPCs payload entries. Compilation, CustomUI validation, and native patch hash verification passed. No standalone Hytale server was launched. Native spawn-null, actual terrain obstruction, and connected reward behavior remain for the owner's in-game QA; offline tests do not prove them.

## Connected QA

Restart the world, then run `/rpg spawn Trork_Warrior unique extrastrong frenzied armorbreaker` at least five times in one session. Also try `/rpg spawn Larva_Void unique stoneskin manaburn reflective`, `/rpg spawn Golem_Crystal_Frost superunique coldenchanted magicresistant bulwark`, `/rpg spawn clear`, and other bound hostile roles. A failed placement or invalid request must not prevent the next valid spawn. Check that QA deaths issue no rewards and that no QA attempt logs `ENCOUNTER_EFFECTS_UNAVAILABLE`, `ENCOUNTER_PERSISTENCE_UNAVAILABLE`, or a production global fail-closed latch.
