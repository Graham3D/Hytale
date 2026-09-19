# R032-AW — Summon Skeleton Archers connected-QA correction

Date: 2026-09-14  
Branch: `RPG`  
Source checkpoint: `25b85cb2819351a0d727e33132b2f57013cda024` plus the preserved cumulative working tree  
Runtime pin: Hytale `0.7.0-pre.2`  
Status: **IMPLEMENTED, PACKAGED, DEPLOYED; CONNECTED QA REQUIRED**

## Connected evidence and root cause

The R032-AU session was reviewed from:

- `Saves/RPG/logs/2026-09-14_15-28-26_server.log`
- `Saves/RPG/mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/skill-trace.jsonl`

At `2026-09-14 19:30:19`, the `flat_world` world thread stopped with:

```text
NullPointerException: Cannot invoke DamageCause.getId() because damageCause is null
  at Damage.<init>
  at HytaleDamageAdapter.applyResolved
  at HytaleSkillExecutionSystem$Port.damage
  at HytaleSkillExecutionSystem.lambda$configureSummons$0
  at HytaleSummonSystem$DamageGuard.lambda$handle$0
```

The native Skeleton Archer arrow had correctly reached RPG's filter/ownership boundary. Its native damage was cancelled, and RPG began the controlled replacement calculation (`DAMAGE_CALC_BEGIN` through `CRIT_ROLL`). The summon profile uses the `PHYSICAL` element, but the shared connection-cause resolver handled only Wind, Lightning, Void, Nature, and Necrotic. It therefore passed a null `DamageCause` into Hytale's `Damage` constructor. The resulting world-thread failure unloaded `flat_world`; the apparent player teleport into darkness/terrain was fallout from that world failure, not an authored summon movement or teleport operation.

The same trace also showed repeated effective-level-1 casts creating separate live one-archer leases. R032-AU had the correct level formula, but did not define same-skill recast replacement.

## Corrections

### Physical damage and world-thread safety

- `PHYSICAL` now resolves to Hytale's native `DamageCause.PHYSICAL` before summon damage is prepared.
- Summon validation rejects before payment if its authored element cannot resolve a native cause.
- `HytaleDamageAdapter` rejects a missing cause with bounded `NATIVE_DAMAGE_CAUSE_MISSING` instead of allowing Hytale's constructor to throw an opaque null dereference.
- The deferred native-arrow conversion is enclosed by a final fail-closed boundary. Any unexpected conversion exception revokes and removes only that summon, emits `SUMMON_REJECTED` with phase `NATIVE_ARROW_CONVERSION`, and cannot unwind through `Store.consume` to stop the world.

### Level-1 singleton and recast replacement

- The existing effective-level formula remains authoritative: level 1 creates exactly one archer.
- A new cast of `summon_skeleton_archers` atomically removes the owner's previous same-world, same-skill batch before reserving the new batch.
- Replacement admission subtracts the outgoing batch while checking strict owner/global caps; it never transiently exceeds capacity and does not silently truncate.
- Old native entities are removed, their leases are revoked, and `SUMMON_TERMINATED(reason=REPLACED, deathPact=false)` is emitted. Replacement cannot trigger Death Pact, corpse ownership, drops, rewards, or duplicate termination.
- Other summon skills retain their previous stacking/admission behavior.

### Idle/follow behavior

- Each living ranged summon continuously updates its native leash point from the caster's authoritative transform.
- With no hostile target, it stays in native `Idle` behavior inside 8 m (approximately the requested 20–30 ft radius).
- Outside 8 m it immediately enters the shipped `ReturnHome` state. Hytale's native pathfinder seeks the updated owner leash point; the player is never moved and is never assigned as a combat target.
- At 6 m it returns to `Idle`. The 8 m/6 m hysteresis prevents state oscillation.
- A hostile target interrupts follow and restores the normal native Skeleton Archer combat target.
- `SUMMON_FOLLOW_STATE` records transitions to `FOLLOWING`, `IDLE`, and `COMBAT` without per-tick trace spam.

### Linked arrow passives

- Arc, Fork, and Chain are explicitly linkable to Summon Skeleton Archers.
- Their RPG-owned secondary payloads begin only after an actual native Skeleton Archer arrow contact is observed. The native arrow launch, bow animation, flight, and primary contact remain untouched.
- The primary native damage remains cancelled and replaced exactly once by RPG damage. Linked secondary payloads do not add another resource charge, cooldown, mastery root, or native primary hit.
- Arc selects one distinct visible hostile within 8 m at 60% magnitude.
- Fork selects up to two distinct visible hostiles within 8 m at full magnitude.
- Chain selects up to two sequential nearest, distinct visible hostiles within 8 m per hop.
- One shared visited set prevents the primary or a previous branch target from being reused. Selection is deterministic by distance then stable ID and is bounded to 64 candidates/five secondary branches.
- `SUMMON_ARROW_CONTINUATION` records every applied branch or `NO_ELIGIBLE_TARGET`. These linked payloads are mechanically tied to native arrow contacts; connected client presentation of extra branch arrows is not inferred from local tests and remains a QA observation.

## Files affected by this correction

- `combat/hytale/HytaleDamageAdapter.java`
- `execution/hytale/HytaleSkillExecutionSystem.java`
- `execution/hytale/HytaleSummonSystem.java`
- `execution/hytale/SummonProjection.java`
- `execution/summon/SummonRegistry.java`
- `execution/summon/SummonArrowContinuations.java`
- `links/CompatibilityService.java`
- `diagnostics/RpgTraceEventType.java`
- `rpg/catalog/skills.json`
- `SummonSkeletonArchersTest.java`
- `SummonNativeDamageCauseTest.java`

No player teleport, movement, gameplay-resource, cooldown, persistence, HUD, Healing Beam, Blizzard, Mantle, or unrelated skill behavior was changed.

## Verification

Focused tests covered singleton replacement, restored ownership after failed replacement reservation, level boundaries, passive compilation/branch limits, native cause mapping/guards, native `ReturnHome`/`Idle` integration, and world-thread quarantine structure.

Final retained validation:

| Suite | Tests | Failures | Skipped |
|---|---:|---:|---:|
| Main RPG suite | 2,328 | 0 | 0 |
| Native-control suite | 66 | 0 | 0 |
| CanvasUI suite | 21 | 0 | 0 |
| **Total** | **2,415** | **0** | **0** |

The exact R032-AW JAR passed the isolated three-mod Hytale server smoke: exactly three JARs, plugin enable, revision/build identity, native ability bridge, all retained Stage 05–13 startup gates, clean boot, and clean shutdown. Local validation proves structure and startup only; it does not claim connected client AI, animation, projectile presentation, or damage behavior.

## Package and deployment

- Deployed JAR: `HyARPG.jar`
- JAR SHA-256: `48370E917124398AFEF3713708DF72F15908C3C61FB00E961851DAB062FDD54C`
- Live path: `C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HyARPG.jar`
- Three-mod archive: `evidence/stage-13/cohort-aw/HyARPG-R032-AW-three-mods.zip`
- Archive SHA-256: `B8F48151112BA35A865EA1A61851B1EFD504AF12802E3DFD73E49B292C1B6251`
- Previous live JAR SHA-256: `61B1FE71F82ABCDA19ED8795CDE7EA3B8C140789DEDBDE3022862749D22AE5A0`
- Rollback backup: `evidence/stage-13/cohort-aw/before/20260914T201901Z`
- RPG mod-data backup: 24 files; zero files changed by deployment.

The installed JAR was reopened after deployment. Its embedded build metadata reports `R032-AW`, Stage 13, version `0.0.25`, and Hytale `0.7.0-pre.2`; its live SHA-256 exactly matches the tested/package artifact. The repository was not pushed.

## Minimal connected QA

1. Fully restart Hytale, join the RPG save, and confirm the top-right revision is `R032-AW`.
2. Equip Summon Skeleton Archers at effective level 1 and cast once: exactly one archer should emerge.
3. Cast again before expiry: the old archer must disappear and exactly one replacement must emerge.
4. With no enemies, stand still near it: it should idle. Move more than about 8 m away: it should path toward you, stop within about 6 m, and never move or attack you.
5. Spawn a hostile NPC and let the archer shoot. Confirm the world remains stable, the player is not displaced, and each arrow produces one controlled damage result rather than native plus RPG double damage.
6. Link Arc, Fork, and Chain separately, then together. Put several hostile NPCs within 8 m of the primary victim and verify the linked secondary damage occurs on distinct visible targets after an arrow hits.
7. Review `skill-trace.jsonl` for `SUMMON_TERMINATED` with `REPLACED`, `SUMMON_FOLLOW_STATE`, `SUMMON_ATTACK`, `SUMMON_ARROW_CONTINUATION`, and complete `DAMAGE_GATHERED -> DAMAGE_FILTERED -> DAMAGE_APPLIED -> DAMAGE_INSPECTED` sequences sharing the summon cast identity.

R032-AW remains **CONNECTED-UNVERIFIED** until these client observations are completed.
