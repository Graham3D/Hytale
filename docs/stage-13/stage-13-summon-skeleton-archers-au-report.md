# R032-AU — Summon Skeleton Archers implementation report

Date: 2026-09-14  
Branch: `RPG`  
Source checkpoint: `25b85cb2819351a0d727e33132b2f57013cda024` plus the preserved cumulative working tree  
Runtime pin: Hytale `0.7.0-pre.2`  
Status: **IMPLEMENTED, PACKAGED, DEPLOYED; CONNECTED QA REQUIRED**

## Scope and authority

This cohort adds the approved active skill `rpg.skill.summon_skeleton_archers` without changing vanilla Skeleton Archer assets or introducing a second summon runtime. It extends the existing Stage 10 summon admission, formation, ownership, cleanup, passive, trace, and controlled-damage systems.

The final production values for cost, cooldown, duration, per-archer Health/damage, weapon requirement, and acquisition source remain unauthored. R032-AU therefore marks the canonical acquisition as `UNASSIGNED` and uses an explicitly tagged development fixture for connected mechanical QA. The fixture uses Spellbook, zero Mana, zero cooldown, 20 seconds, 0.60 Health factor, and 0.55 damage coefficient. These are not declared final balance values.

## Native asset and spawn audit

The installed `Assets.zip` was inspected before implementation:

- Native role: `Server/NPC/Roles/Undead/Skeleton/Skeleton/Skeleton_Archer.json`.
- Native model inheritance resolves to `Server/Models/Undead/Skeleton.json`.
- The model owns a native `AnimationSets.Spawn` entry, including the shipped emergence animations and associated native audio.
- Hytale's NPC spawn flow adds `NewSpawnComponent` for normal spawn lifecycle processing.
- The installed intelligent-NPC template declares a 1.5-second spawn lock.

RPG invokes `NPCPlugin.spawnNPCWithSpaceValidation(...)`, the same supported native NPC allocation boundary used for a normal NPC spawn. It does not call a custom animation or manually reposition an archer to imitate emergence. Each requested position is first resolved to legal ground and retains the existing separated batch formation.

The RPG summon tick now returns immediately while `NewSpawnComponent` exists. Consequently, no RPG target query, marked-target change, leash update, navigation influence, attack claim, or controlled damage can run during the native spawn state. Once Hytale removes the component, the existing summoned-ally behavior begins. Multi-summon batches are allocated at their separated positions in the same operation, allowing native emergence to run concurrently.

## Effective skill level and summon count

`EffectiveSkillLevel` is now carried through preparation, revalidated during commit, and captured in `SkillExecutionContext`. It is calculated from canonical mastery level plus authoritative item-granted skill levels exposed by `SkillExecutionPort`.

The skill count is:

```text
1 + floor((EffectiveSkillLevel - 1) / 2)
```

Verified boundary values are 1→1, 2→1, 3→2, 19→10, 20→10, and 21→11. The formula continues above mastery level 20 when an authoritative item-level bonus exists. R032-AU does not fabricate any item bonus source.

The obsolete eight-summon owner cap was replaced by bounded admission: 32 per owner and 256 globally. Admission uses the complete resolved batch count. It rejects an over-cap cast before mutation and never truncates a valid batch; level 20 therefore creates exactly 10 archers or the cast fails atomically.

## Ownership, allegiance, AI, damage, and rewards

- Every archer lease carries the caster UUID, root context, native entity UUID, effective profile, expiry, and one-pulse-per-attack accounting.
- A native attitude provider makes owned RPG summons friendly to players and each other while deriving hostility against NPCs from the NPC's prepared native attitude toward the caster.
- After spawn lock, the native Skeleton Archer role retains its native ranged targeting, navigation, bow draw, and projectile presentation.
- Native projectile damage from the summon is cancelled at the filter boundary. The observed hostile impact claims one bounded attack ordinal and dispatches the existing RPG-controlled summon damage path, preventing native/RPG double damage while retaining native ranged presentation.
- Summons are non-serialized, drop no death items, grant no XP/loot/reward credit, and cannot farm allies or other owned summons.
- Existing owner disappearance, world teardown, death, expiry, leash, rollback, removal, and exactly-once termination handling remain authoritative.

## Generic passive behavior

- `Swarm` adds one archer and applies its existing 0.75 Health/damage factors to each member.
- `Minion Empowerment` applies the existing 1.30 Health/damage and 0.75 duration factors.
- `Death Pact` remains exactly once per qualifying enemy-caused summon death.

No skill-specific duplicate implementations of these passives were added.

## Content and UI integration

- Canonical skill count is now 91; passive count remains 67.
- Stage 10 runtime profile count is now 10.
- A native `RPG_Ability_Summon_Skeleton_Archers` projection asset and language entries were added.
- The skill-tree icon index and owner CSV now reserve `SkillSummonskeletonarchers.png`. No artwork was invented. Until that correctly named file is supplied through the existing icon updater, the normal fallback icon is expected.
- Public acquisition remains blocked: signature enemy and validation state are both `UNASSIGNED`; the native Skeleton Archer is deliberately not reused as a source enemy.

## Verification evidence

Focused tests covered formula boundaries, effective item-level contribution, exact level-20 batch creation, root/owner identity, passive arithmetic, Death Pact idempotency, separated placement, admission rejection, acquisition blocking, installed native role/model assets, normal spawn API use, and absence of custom animation calls.

Retained validation:

| Suite | Tests | Failures | Skipped |
|---|---:|---:|---:|
| Main RPG suite | 2,324 | 0 | 0 |
| Native-control suite | 66 | 0 | 0 |
| CanvasUI suite | 21 | 0 | 0 |
| **Total** | **2,411** | **0** | **0** |

The exact candidate then passed the established isolated three-mod Hytale server smoke: one RPG JAR, plugin enable, R032-AU identity, native ability bridge, 10 Stage 10 summon profiles, all retained Stage 05–13 startup gates, clean boot, and clean shutdown.

Local tests and isolated smoke prove code, asset, and startup structure only. They do **not** prove client rendering, native emergence timing, bow animation/projectile presentation, connected allegiance, or connected damage. Those remain explicitly unverified.

## Package and deployment

- Built/deployed JAR: `HyARPG.jar`
- SHA-256: `9CB8B17A40F76F5AB7100475A2F242CD94E2FC23D872096BFD5A35D483B0E8C9`
- Three-mod archive: `evidence/stage-13/cohort-au/HyARPG-R032-AU-three-mods.zip`
- Archive SHA-256: `BEF1A2D02653AD3B87674838E4DAB38EF5E224B9C01A7FDE73EDF44518904E11`
- Live path: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar`
- Previous live SHA-256: `DDB5C13AEFA8A88EADD6CE73C3146264794E486A63D348DAB7C825CB8EF9A4B6`
- Backup: `evidence/stage-13/cohort-au/before/20260914T192453Z`
- Backup verification: installed JAR plus 24 RPG mod-data files; zero mod-data files changed during deployment.

The repository was not pushed.

## Connected acceptance checklist

1. Fully restart Hytale, join the RPG save, and confirm the top-right revision is `R032-AU`.
2. Run `/npc spawn Skeleton_Archer` on clear legal ground and observe the stock emergence/crawl presentation as the control.
3. Open `/rpg skilltree`, equip **Summon Skeleton Archers** into Ability2 or Ability3, and hold a Spellbook (the temporary development-fixture requirement).
4. Aim at clear legal ground within approximately 8 m and press the assigned skill key once.
5. At effective level 1, confirm exactly one archer appears and its emergence matches the control: no instant standing pop, custom animation, navigation, attack, or teleport during emergence.
6. Place a hostile NPC nearby. Confirm the archer begins native follow/ranged combat only after emergence completes, attacks hostiles, never attacks the caster/allies, produces no duplicate native plus RPG damage, and drops/grants no summon rewards.
7. Repeat with the `Swarm`, `Minion Empowerment`, and `Death Pact` links and verify their generic behavior.
8. For a level-20/item-level test fixture, confirm exactly 10 concurrently emerging archers at separated legal positions; no silent eight-summon truncation is acceptable.
9. Compare `mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/skill-trace.jsonl` for `SUMMON_SPAWNED`, `SUMMON_ACTION`, and terminal events sharing the cast identity. Report the earliest boundary if a cast rejects.

Stage 10 native presentation remains **CONNECTED-UNVERIFIED** until this checklist is observed in the client.
