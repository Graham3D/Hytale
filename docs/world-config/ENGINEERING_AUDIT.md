# World configuration integration audit — R235

Status: **historical R235 audit**. The owner subsequently authorized the full configuration and native weighted-population implementation. See [OPERATOR.md](OPERATOR.md) for the current binding, supported controls, and limits. The earlier blocker assessment below predates the verified `WorldNPCSpawnStat.setExpected` per-role native selection seam.

## Input and current state

- Proposed input: `C:/Users/Zemio/Downloads/hywind-world-config.proposed.json` (schema 1, `world-tuning-v2-population-balance-proposal`). No separate engineering-brief file was supplied in this request.
- Active save: `C:/Users/Zemio/AppData/Roaming/Hytale/data/pre-release/Saves/RPG`.
- Current authoritative density setting is `mods/InigmasGames_HytaleRPGPhase00Audit/world-spawn-density.json`, presently **8.0x**. The proposed JSON says 1.0x. Preserve the active operator value during migration unless the operator explicitly changes it.
- The owner confirmed that the shipped rarity defaults must remain R235: Normal Champion 1.6% / Unique 6.4%; Nightmare 2.8% / 11.2%; Hell 4% / 16%. The proposal's flat 2% / 6% is **not** the approved shipped default.

## Existing owners to bind

| Proposed controls | Current owner / integration seam |
| --- | --- |
| Native environment spawn multiplier | `WorldSpawnDensitySettings` and `NativeWorldSpawnDensity`; `/rpg spawns` writes the former and the latter projects native per-environment targets/caps. Migration needs one authoritative `world-config.json` value, not a second competing density file. |
| Production rarity, pack sizes and caps, rarity Health/direct damage factors, new immunity chances, per-affix XP/quantity bonuses | `EnemyBalance`, used by `NativeEnemyBirthDecision`, `EnemyBirthPlanner`, `EnemyPackCapacity`, `EnemyImmunitySelection`, `EnemyRewardContext`, and `EnemyAffixSnapshot`. |
| Random affix weights and disabled random affixes | `EnemyAffixRegistry` and `EnemyAffixSelection`; changing weights needs a new immutable selection policy for future births, while existing actors retain their frozen affix snapshots. |
| XP | `EnemyRewardContext.xpFactor()` -> `EnemyRewardRegistry.Spawn.rewardXp()`. |
| Equipment quantity | `EnemyRewardContext.bonusEquipmentSlots()` -> `GearLootService`; native base equipment profile and 16-slot cap must remain authoritative. |
| Skill acquisition | `EnemyRewardContext.learningChance()` -> `LearningSources`; source mapping and pity are separate existing owners. |
| Combat and active packs | `HytaleDifficultyCombat`, `NativeEnemyBirthAttachment`, `HytaleEncounterRewards` and `EnemyAffixSnapshot`; rebind requires the balance revision associated with a saved descriptor. |

No world-config controls have been bound yet. The proposed JSON must **not** be copied into the active save unchanged: its `spawnPopulationBalance.enabled=true` would currently be a silent no-op, and its 1.0x density and flat rarity numbers would override accepted values.

## Wildlife/hostile population boundary — substantial work

The installed Hytale SDK's `WorldSpawnData` exposes aggregate actual/expected counts, environment indexes, active jobs and `trackNPC`/`untrackNPC`. `WorldEnvironmentSpawnData` exposes aggregate actual/expected counts plus per-role `WorldNPCSpawnStat` entries. `ChunkEnvironmentSpawnData` exposes possible/unspawnable role sets and a per-chunk expected count. `SpawnJobData` exposes the selected role, flock size, environment, budget used and native spawn configuration.

`WorldSpawningSystem.createRandomSpawnJob` chooses and budgets a native job. `WorldSpawnJobSystems` executes the job and native flock delivery. None of the inspected public methods provides a hostile/wildlife category budget or a pre-admission callback with a category share. The current `NativeWorldSpawnDensity` scales total native targets; it does not classify categories. The current `NativeEnemySpawnGroups` captures actual natural hostile groups after native job selection for Elite promotion; it is too late to reserve hostile headroom before wildlife jobs are selected.

Implementing 65% hostile / 35% wildlife with a 40% wildlife soft threshold requires a verified category classification for every candidate native spawn role, a category count consistent with individual flock members and native despawn, and a narrow admission point **before** the native job commits its budget. It must preserve native total/per-environment/chunk caps, suppression, eligibility and job accounting. Declining a wildlife job after it has reserved capacity, or altering native role weights without reconciling native accounting, would risk the same natural-spawn rollback failures previously fixed in R234. There is no safe small configuration-only binding for this control in the inspected SDK.

Per the owner's time-bound instruction, implementation stops at this boundary. The 65/35/40 control is **blocked**, not implemented or silently ignored. No new native interception, spawn engine, wildlife deletion or category accounting has been added.

## Reload and persistence constraints for a subsequent pass

`EnemyBalance` is immutable and its `revision` is included in encounter descriptors. `EnemyAffixSnapshot.resolve` requires a matching balance revision, including on load/rebind; `NativeEnemyBirthAttachment` currently holds one fixed balance. `EnemyPackCapacity` also holds one fixed policy while retaining active reservations. A correct reload must build and validate an immutable candidate, preserve old balance revisions for active/saved encounters, then publish only the new-birth policy atomically. Pack-cap changes must grandfather existing reservations. Editing a JSON value without these changes would either do nothing or break recovery.

The proposed `roleOverrides`, `worldOverridesByWorldId`, per-era Health/damage/reward multipliers, equipment drop-chance multipliers and encounter-rank spawn-weight multiplier have no proven direct binding in this audit. They must be explicitly rejected or documented as blocked until their authoritative consumers and persistence semantics are verified; they must never be accepted as inert settings.

No compile, tests, package or connected QA were run because no code changed. The active `Hywind.jar` remains R235-U7P5.
