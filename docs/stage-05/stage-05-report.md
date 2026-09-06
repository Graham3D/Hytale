# Stage 05 R014 — Projectile Family Pilot Runtime

## Status

```ini
Stage03 = IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION
Stage04 = IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION
Stage05 = IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION
Stage05Pass = false
Revision = R014
Version = 0.0.7
TargetHytaleVersion = 0.7.0-pre.1
PlayerStateSchema = 2
EarliestUnprovedBoundary = CONNECTED_ABILITY_INPUT_TO_NATIVE_PROJECTILE_IMPACT
```

R014 implements, tests, smoke-tests, and deploys the first shared Projectile-family
cohort. It does not mark Stage 05 `PASS`: native connected-client flight, collision,
impact, damage, inventory, rendered presentation, and cleanup observations remain
required. Stage 03 and Stage 04 retain their prior non-PASS statuses.

Phase 05 normally enters after the Phase 04 core outcome contract passes connected
testing. The owner's explicit provisional exception authorizes this revision because
R013 has a successful build, complete regression suite, bare-server startup, no known
non-client blocker, and is waiting only on connected evidence.

## Bounded scope

Only two Phase 05 pilots become executable:

| Skill | Authoritative profile |
|---|---|
| Fire Bolt | Staff/Wand; 8 Mana; instant; 1.4 s cooldown; 24 m; 24 m/s; 0.30 m radius; 0.95x Magic Power; Burn for four 1 s ticks at 0.10 resolved offensive power each |
| Snipe | Bow; 12 Stamina; instant fully charged release; 10 s cooldown; audited-development fallback 48 m, 45 m/s, 0.10 m radius, gravity 0; 2.00x uncharged Weapon Power; consumes one `Weapon_Arrow_Crude`; no charged-basic recovery |

The canonical catalogs remain exactly 87 skills and 66 passives. The six R013 pilots
remain executable through the same registry. Remaining Phase 05 skills remain
unavailable. CanvasUI source and its deployed R008 JAR are unchanged.

## Shared runtime and authority

R014 extends the existing common execution path rather than adding skill-specific
executor classes:

```text
Ability input
  -> equipped SkillId and current CompiledSkillPlan
  -> actor/equipment/family/ammo/resource/cooldown validation
  -> immutable CombatSnapshot and stable correlation IDs
  -> one resource commit and one cooldown start
  -> shared PROJECTILE executor
  -> native ProjectileModule carrier and StandardPhysicsProvider collision
  -> RPG-owned terminal decision
  -> HytaleDamageAdapter / DamageSystems.executeDamage
  -> bounded status schedules and cleanup
  -> automatic trace
```

`Stage04SkillProfiles` is retained as the public class name to avoid needless API
churn, but now loads the versioned Stage 04 and Stage 05 runtime data sets. The
`SkillExecutorRegistry` has one forwarding executor per mechanical family, including
one `PROJECTILE` entry. There is no per-skill executor or growing 87-skill switch.

The profile's projectile identity, collision bounds, speed, range, damage coefficient,
status payload, and ammo rule are captured at activation. The carrier map retains the
same `rootCastId`, `skillInstanceId`, `correlationId`, `CombatSnapshot`, and
`CompiledSkillPlan` until impact, expiry, cancellation, or fizzle.

## Native projectile audit and collision design

The pinned server exposes:

- `ProjectileModule.spawnProjectile` with creator, command buffer, config, origin,
  and direction;
- `ProjectileModule.getStandardPhysicsProviderComponentType`;
- `StandardPhysicsProvider.setImpactConsumer` and the six-argument native impact
  callback;
- server-authoritative combined inventory and transactional item removal/addition;
- installed crude-arrow, fire-projectile particle, fireball model, arrow model, Burn
  particle, and Burn model assets.

R014 packages two native projectile configs and two exact-bound model records. Their
native `Interactions` objects are deliberately empty. Native standard physics owns
swept block/entity collision and movement, while the RPG callback owns the one allowed
damage submission. This avoids a native interaction hit plus an RPG hit for the same
contact. A terrain hit, valid target hit, invalid/protected target fizzle, range expiry,
lifetime expiry, actor teardown, and unexplained native removal are distinct terminal
paths.

`ProjectileFlight` sums the observed path segments rather than measuring only straight
line displacement. Maximum lifetime is derived from `maxDistance / speed` (1.0 s for
Fire Bolt, 48/45 s for Snipe), so both distance and time are bounded. Per-plan live
projectile admission is checked before cost.

## Damage and Burn

A valid target hit is limited to one target and submits the snapshot-owned calculation
through the Stage 02 adapter. It preserves the native:

```text
DAMAGE_GATHERED -> DAMAGE_FILTERED -> DAMAGE_APPLIED -> DAMAGE_INSPECTED
```

chain and records pre-mitigation amount plus actual authoritative Health loss.

Fire Bolt applies Burn only after a valid damaging hit with actual Health loss. The
packaged `RPG_Burn_Visual` inherits Hytale's water/immunity application conditions and
uses verified native visuals, but intentionally contains no `DamageCalculator`. RPG
authority schedules exactly four one-second, non-critical damage submissions at 0.10
snapshot-resolved offensive power each. That separation prevents Hytale's stock fixed
Burn damage from becoming an uncorrelated second damage source. Reapplication refreshes
the owned schedule and snapshot rather than stacking unbounded timers.

## Ammo, cost, and rollback

Snipe checks and consumes exactly one `Weapon_Arrow_Crude` from the authoritative
hotbar/storage/backpack combined inventory. It neither removes the equipped Bow nor
uses a visual-item copy as authority. A changed/missing arrow rejects before dispatch;
if native projectile creation fails after arrow consumption, the arrow transaction is
refunded and the projectile carrier is removed.

R014 also hardens the common commit boundary. A deterministic executor-throw test
found that R013's post-commit exception path terminated without clearing its cooldown
or refunding committed resource. R014 keeps the Stage 02 cost token pending until
successful family dispatch; a synchronous dispatch error now refunds the resource and
clears the cooldown. The R013 report was amended to state that boundary factually.

Snipe's `fullyCharged` flag is metadata for compatible future interactions only. The
projectile hit path never calls the basic-attack recovery service, so it cannot grant
the 12% charged basic recovery.

## Automatic trace contract

R014 retains all Stage 01B–04 and damage events and adds:

```text
AMMO_CHECK
AMMO_COMMITTED
AMMO_REJECTED
PROJECTILE_SPAWNED
PROJECTILE_TARGET_REJECTED
PROJECTILE_HIT
PROJECTILE_TERRAIN_IMPACT
PROJECTILE_EXPIRED
PROJECTILE_CANCELLED
BURN_TICK
```

Every derived direct hit and Burn tick uses the original three IDs. No `/rpg trace`
command is required; records are written to
`mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/skill-trace.jsonl`.

## Verification results

| Gate | Result |
|---|---|
| Clean Gradle build | PASS |
| Complete RPG + CanvasUI tests | PASS — 110 tests, 0 failures, 0 errors, 0 skipped |
| CustomUI source/package validation | PASS |
| Required JAR entries | PASS |
| Catalogs and retained profiles | PASS — 87 skills, 66 passives, 6 Stage 04 pilots |
| Stage 05 data | PASS — 2 pilots with exact authored bounds |
| Pinned projectile/inventory API audit | PASS |
| Installed asset-ID audit | PASS |
| Bare-server startup | PASS — exact three mods discovered and enabled; R014 ready marker; no RPG/Stage-05 asset failure |
| Player persistence schema | PASS — schema 2 unchanged |
| Connected client | PENDING |

Deterministic tests cover profile values, supported/invalid weapons, insufficient
resource no-consume behavior, ammo precondition rejection, one family dispatch, magic
and physical base-power snapshots, total-path distance and derived lifetime, target cap,
Burn tick contract, stable trace identity, dispatch rollback, native API signatures,
no native damage interactions in owned assets, and every retained regression suite.

Evidence:

- `evidence/stage-05/R014/verification.json`
- `evidence/stage-05/R014/api-audit.json`
- `evidence/stage-05/R014/server-smoke-summary.json`
- `evidence/stage-05/R014/server-smoke.txt`
- `evidence/stage-05/R014/installation.json`

The smoke harness intentionally stops the healthy bare server. Its established process
exit code `9` is expected and is not an RPG startup failure.

## Deployment and rollback

The RPG save contains exactly the established three JARs. Final hashes are recorded in
`installation.json`; R014 preserves the deployed R013 RPG JAR in
`evidence/stage-05/R014/rollback/HytaleRPG-0.0.6.jar`.

Rollback: fully stop the RPG world, remove `HytaleRPG-0.0.7.jar`, restore that retained
`HytaleRPG-0.0.6.jar` to the save's `mods` directory, and leave CanvasUI and HytaleDevLib
unchanged. Player schema remains v2; do not downgrade or replace player state.

## Connected evidence still required

Local and bare-server evidence cannot prove live input delivery, equipped-item power,
actual arrow inventory transactions, rendered trajectories, world collision order,
native target/terrain impacts, authoritative target Health loss, Burn timing, cleanup
during connected teardown, or restart persistence. The exact checklist is in
`client-verification.md`.

Stage 05 may become `PASS` only after connected proof shows both pilots, all terminal
paths, one-cost/one-arrow behavior, full damage correlation, no Snipe recovery, healthy
HUD/state persistence, and no RPG-scoped exception.

## Closure decision

```ini
Stage05 = IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION
Stage05Pass = false
EarliestUnprovedBoundary = CONNECTED_ABILITY_INPUT_TO_NATIVE_PROJECTILE_IMPACT
Stage06SafeToBegin = false
```

R014 has no known automated, server-startup, architectural, persistence, asset, or
RPG-scoped exception blocker. Work stops at the Stage 05 connected gate; Stage 06 does
not begin.
