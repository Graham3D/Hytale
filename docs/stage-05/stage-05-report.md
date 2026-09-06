# Stage 05 R015 — Representative Projectile Cohort

## Status

```ini
Stage03 = IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION
Stage04 = IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION
Stage05 = IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION
Stage05Pass = false
Revision = R015
Version = 0.0.8
TargetHytaleVersion = 0.7.0-pre.1
PlayerStateSchema = 2
EarliestUnprovedBoundary = CONNECTED_NATIVE_PROJECTILE_FLIGHT_TO_IMPACT_CALLBACK
Stage06SafeToBegin = false
```

R015 implements the complete, bounded six-skill Stage 05 cohort. The clean build,
118-test regression suite, current-build API/asset audit, JAR inspection, CustomUI
validation, and isolated three-mod server smoke all pass. Stage 05 is not marked
`PASS`, because a real connected client still needs to prove native flight, collision,
payload application, presentation, inventory mutation, and teardown behavior.

Stage 04 is allowed to remain `IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION` under the
owner-unavailable exception: its automated and smoke gates pass and no engineering
blocker is known. This revision does not begin Stage 06.

## Revision boundary

- Starting commit: `e5fc9d3e0f85db75bb44a70a44f671739b38c793` (R014 evidence).
- Ending implementation commit: `78b314a1fe1ed7046a398462769e00b2254eb3b5`.
- RPG artifact: `HytaleRPG-0.0.8.jar`.
- RPG artifact SHA-256: `6A6917C1AD363DAC7FE3E0BEBFE1DD0A74BDC288A3E8140A3EE60A97DBEC4504`.
- Target: Hytale `0.7.0-pre.1`; pinned server JAR SHA-256
  `EC57E9BD6E2CA3CB16CC5883D42B04A0C64D382DEE532C5BC1CFCF68421E1EE3`.

R014 was a provisional two-projectile pass based on the earlier Fire Bolt/Snipe scope.
The later, authoritative Stage 05 brief defines six different representatives and
explicitly excludes Snipe. R015 removes Snipe from the executable runtime while
preserving it in the canonical 87-skill catalog.

## Implemented cohort

| Skill | Exact executable contract |
|---|---|
| Fire Bolt | Staff/Wand; 8 Mana; 1.4 s cooldown; 24 m at 24 m/s; 0.30 m radius; 0.95x Magic Power; Burn for 4 s with four 0.10 snapshot-power ticks |
| Frost Bolt | Staff/Wand; 8 Mana; 1.5 s cooldown; 24 m at 22 m/s; 0.30 m radius; 0.85x Magic Power; exactly one Chill stack request |
| Arcane Bolt | Wand/Spellbook; 7 Mana; 1.2 s cooldown; 26 m at 26 m/s; 0.28 m radius; plain 0.90x Magic Power hit |
| Stone Bolt | Staff/Wand; 8 Mana; 2.2 s cooldown; 20 m at 17 m/s; 0.45 m radius; 1.20x Magic Power; 1.5 m minor knockback request |
| Quick Shot | Bow/Crossbow; 4 Stamina; 0.9 s cooldown; 28 m; native 0.075 m arrow bounds; audited 30 m/s Bow or 40 m/s Crossbow launch; 0.80x Light Power; one `Weapon_Arrow_Crude` |
| Axe Toss | Battleaxe; 8 Stamina; 5 s cooldown; 20 m at 18 m/s; 0.45 m radius; 1.20x Heavy Power; projectile representation only—no weapon inventory transfer |

The total catalogs remain exactly 87 skills and 66 passives. The six Stage 04 pilots
also remain executable. Fireball, Arcane Missiles, Blunderbuss Shot, Explosive Flask,
Bomb Toss, Cold Blast, Snipe, and all other unselected projectiles remain unavailable.

## Executor architecture and authority

The implementation uses one data-driven `ProjectileFamilyExecutor`, not six
skill-specific executors:

```text
Ability1..Ability4
  -> equipped skill + CompiledSkillPlan
  -> actor/equipment/config/live-budget/ammo/resource/cooldown validation
  -> immutable equipment + CombatSnapshot capture
  -> resource commit + cooldown start
  -> ProjectileFamilyExecutor
  -> RpgProjectileService builds ProjectileExecutionPlan
  -> Hytale ProjectileModule creates native carrier
  -> StandardPhysicsProvider supplies authoritative entity/terrain contact
  -> RPG validates/deduplicates target and resolves snapshot payload
  -> Hytale DamageSystems runs Gather -> Filter -> Apply -> Inspect
  -> optional status/control payload
  -> explicit terminal result and registry cleanup
```

`ProjectileExecutionPlan` carries the original `rootCastId`, `skillInstanceId`, a
stable generation-zero `projectileInstanceId`, owner UUID, SkillId, compiled-plan
hash, immutable `CombatSnapshot`, generation, continuation-budget map, remaining root
budgets, spawn timestamp, config, origin, normalized velocity, radius, maximum range,
and derived maximum lifetime. An equipment change after commit cannot mutate any
in-flight calculation or Quick Shot carrier selection.

`ProjectileLifecycleRegistry` rejects duplicate IDs and plans exceeding the canonical
limits (`maxDerivedGeneration=3`, `maxSpawnedEffectsPerRootCast=48`,
`maxTriggeredSecondariesPerRootCast=16`). `ProjectileInstance` owns total-path travel,
elapsed lifetime, hit-target deduplication, and its single terminal record.
`RpgProjectileService` exposes the requested spawn, enemy-contact, terrain-contact,
forward-termination, and owner-cancellation seams. Death/logout/unload removes every
owned carrier, emits cancellation/termination, and leaves no registry entry.

## Native API and installed-asset audit

R015 inspected the installed `0.7.0-pre.1` server and `Assets.zip`, rather than
guessing identifiers. The automated audit proves:

- `ProjectileModule.spawnProjectile`, the StandardPhysicsProvider component, and its
  native impact callback are present;
- `InventoryComponent.getCombined(HOTBAR_STORAGE_BACKPACK)` plus transactional
  `removeItemStack`/`addItemStack` are present;
- `Projectile_Config_Arrow_Base` has launch force 30, the Crossbow arrow config has
  launch force 40, and `Arrow_Crude` has native +/-0.075 hit-box bounds;
- Skeleton Scout's shot selects `Skeleton_Scout_Arrow`, whose parent is
  `Arrow_FullCharge`, preserving the authored Signature source lineage;
- all referenced particle systems exist: `Fire_Charged1`, `Block_Gem_Sparks`,
  `Dust_Sparkles_Fine`, `Block_Break_Stone`,
  `Bow_Signature_Projectile_Sparks`, and `Impact_Blade_01`;
- the Fireball, Ice Bolt, Stone, crude-arrow, and Trork stone-axe model sources exist.

Fire, Frost, Arcane, Stone, Quick Shot Bow/Crossbow, and Axe Toss each have an owned
RPG `ProjectileConfig`. All use Hytale's Standard physics and impact callback. Their
`Interactions` maps are empty on purpose: native movement/collision remains
authoritative, while the RPG submits the only damage payload. This prevents a native
weapon interaction and an RPG snapshot payload from damaging the same contact twice.

Quick Shot reuses the installed crude-arrow model, hit box, Bow/Crossbow launch speeds,
standard physics, and authoritative inventory container. It consumes exactly one real
crude arrow and never mints ammo. Axe Toss uses a projectile visual derived from the
installed Trork stone-axe model but only reads the equipped Battleaxe for validation
and Heavy power; it does not transfer or destroy the equipped item.

## Collision, payload, and cleanup behavior

Every valid entity contact follows:

```text
native contact
  -> candidate validation/protection check
  -> per-projectile target dedup
  -> snapshot-owned DamageCalculationService result
  -> HytaleDamageAdapter / DamageSystems.executeDamage
  -> actual Health inspection
  -> Burn, Chill, or knockback request when authored and damage landed
  -> ordinary first-contact termination
```

Crit chance and result come only from the Stage 02 snapshot/calculation. There is no
projectile crit formula. Fire Bolt schedules the existing bounded RPG Burn only after
actual Health loss. Frost Bolt uses `StatusService` for one Chill stack. Stone Bolt
uses Hytale's `KnockbackComponent` and declines control for protected or boss targets.

Terrain contact emits its own result and terminates. Total observed path reaching the
range cap emits `PROJECTILE_MAX_RANGE`; derived lifetime expiry emits
`PROJECTILE_EXPIRED`. Native carrier disappearance, payload exceptions, actor death,
logout, and world teardown cancel explicitly and clean both maps. There is no
per-frame trace spam.

Spawn is ordered as required: feasibility and ammo availability are checked before
snapshot/cost commit; resource and cooldown commit precede native creation. A
synchronous native dispatch failure uses the existing explicit transaction rollback:
ammo is restored, the pending cost is refunded, cooldown is cleared, any carrier is
removed, and `PROJECTILE_SPAWN_REJECTED` records the failure class. Nothing is silently
left half-committed.

## Stage 07 continuation seam

Every generation-zero plan contains explicit zero budgets for Split, Pierce, Fork,
Chain, Ricochet, and Return. Compiled Link metadata remains attached to the plan, but
Stage 05 never executes it. Ordinary entity or terrain contact forwards directly to
terminal cleanup. `onForwardTermination` and the collision/payload adapter contracts
are the deliberate Stage 07 interception points; no continuation ordering or child
creation is implemented here.

## Trace contract

R015 adds the exact bounded lifecycle events:

```text
PROJECTILE_SPAWN_REQUEST
PROJECTILE_SPAWNED
PROJECTILE_SPAWN_REJECTED
PROJECTILE_ENTITY_HIT
PROJECTILE_TARGET_DEDUP
PROJECTILE_TERRAIN_HIT
PROJECTILE_MAX_RANGE
PROJECTILE_EXPIRED
PROJECTILE_CANCELLED
PROJECTILE_TERMINATED
```

An extra `PROJECTILE_TARGET_REJECTED` distinguishes non-damageable/protected contact.
Projectile events include the three correlation IDs, `projectileInstanceId`,
generation, caster, SkillId, and compiled-plan hash; terminal/hit events add target,
position where available, travelled distance, damage, Health loss, status, knockback,
or reason. The native damage trace retains Gather -> Filter -> Apply -> Inspect with
the same cast/skill/correlation IDs.

The deterministic trace assertions prove singular family dispatch, correlation
continuity, spawn rejection, Stage 02 crit integration, and the no-Fork-before-Stage-07
rule. The isolated server log proves automatic trace-path registration and the R015
ready marker. No connected projectile trace is claimed yet.

## Verification

| Gate | Result |
|---|---|
| Clean RPG + CanvasUI Gradle build | PASS |
| CustomUI validation | PASS — 13 documents |
| Complete retained test suite | PASS — 118 tests, 0 failures, 0 errors, 0 skipped |
| Stage 05 projectile tests | PASS — 17 deterministic tests |
| Catalog/profile gate | PASS — 87 skills, 66 passives, 6 retained Stage 04 pilots, exactly 6 Stage 05 pilots |
| Exact projectile data and Snipe exclusion | PASS |
| Required JAR entries | PASS |
| Pinned native API/asset audit | PASS |
| Isolated server smoke | PASS — exactly three mods discovered, R015 ready, plugin manager started, clean operator shutdown, no RPG/asset failure |
| Player schema | PASS — v2 unchanged |
| CanvasUI source | PASS — unchanged |
| Connected client | PENDING |

Evidence is retained in `evidence/stage-05/R015/verification.json`,
`api-audit.json`, `server-smoke-summary.json`, `server-smoke.txt`, and
`installation.json`. The smoke harness seeds a valid permissions profile to avoid the
engine's first-run asynchronous permissions-writer race, waits for normal plugin
startup, sends `stop`, and now observes exit code 0 plus `Shutdown completed!`.

## Deployment and rollback

The RPG save is constrained to exactly these three mod JARs:

| Mod | SHA-256 |
|---|---|
| `CanvasUI-0.1.0.jar` | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| `HYTALEDEVLIB-0.5.0.jar` | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |
| `HytaleRPG-0.0.8.jar` | `6A6917C1AD363DAC7FE3E0BEBFE1DD0A74BDC288A3E8140A3EE60A97DBEC4504` |

R015 preserves `HytaleRPG-0.0.7.jar` in
`evidence/stage-05/R015/rollback`. Earlier Stage 02/03/04 rollback evidence remains
untouched. To roll back, fully stop the RPG world, remove `HytaleRPG-0.0.8.jar`, copy
the retained `HytaleRPG-0.0.7.jar` into `Saves/RPG/mods`, and leave CanvasUI,
HytaleDevLib, and player schema v2 unchanged.

## Known limitations and decision

Automated and bare-server checks cannot prove a connected Ability input reaches a
rendered projectile, that the native callback reports the expected live entity/block,
that actual Health/inventory/HUD state changes correctly, or that connected teardown
is leak-free. Those are evidence gaps, not known implementation failures. The concise
owner checklist is in `client-verification.md`.

```ini
Stage05 = IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION
Stage05Pass = false
EarliestUnprovedBoundary = CONNECTED_NATIVE_PROJECTILE_FLIGHT_TO_IMPACT_CALLBACK
Stage06SafeToBegin = false
```

Work stops at this gate. Stage 06 has not begun.
