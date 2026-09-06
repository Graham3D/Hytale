# Stage 04 R013 — Strike, Movement, and Reaction Runtime

## Status

```ini
Stage04 = IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION
Stage04Pass = false
Revision = R013
Version = 0.0.6
TargetHytaleVersion = 0.7.0-pre.1
PlayerStateSchema = 2
EarliestUnprovedBoundary = CONNECTED_ABILITY_INPUT_TO_NATIVE_EFFECT
Stage05SafeToBeginProvisionally = true
```

Stage 04 is implemented, locally verified, bare-server smoke-tested, and deployed.
It is not marked `PASS` because the owner is unavailable to perform the required
connected-client observations. Stage 03 likewise remains
`IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION`; this revision does not retroactively
claim its connected UI gate.

The Stage 04 entry exception was explicitly authorized for this situation: Stage 03
has a successful build, complete automated regression result, successful bare-server
startup, no known code/runtime blocker, and is waiting only on connected evidence.

## Revision scope

R013 replaces the Stage 03 activation placeholder for six pilot skills with shared,
server-authoritative family execution:

| Skill | Family | Authoritative profile |
|---|---|---|
| Quick Slash | Strike | Swords/Longswords/Daggers; 5 Stamina; 0.8 s cooldown; 2.6 m, 120° arc; 0.85x weapon power |
| Heavy Swing | Strike | Longswords/Maces/Battleaxes; 9 Stamina; 0.45 s wind-up; 1.8 s cooldown; 3.0 m, 140° arc; 1.45x weapon power |
| Shield Bash | Strike/Control | actual offhand Shield; 8 Stamina; 5 s cooldown; 2.2 m, 60° single-target assist; 0.70x Heavy power; 0.6 s Stagger |
| Quickstep | Movement | no weapon; 8 Stamina; 2.5 s cooldown; 4.0 m dash over 0.22 s; no damage |
| Pounce | Movement/Strike | no weapon; 8 Stamina; 5 s cooldown; target within 8 m; 0.25–0.8 s leap; 1.5 m landing radius; 1.05x Light power from authored innate 20 |
| Riposte | Reaction/Strike | Swords/Longswords; 8 Stamina; 7 s cooldown; 0.8 s reaction window; 3.0 m counter; 1.35x Light power |

No other skill was made executable. The canonical roster remains exactly 87 skills
and 66 passives. CanvasUI source and its deployed R008 JAR were not changed.

## Architecture and authority

The runtime follows this production path:

```text
Ability1..Ability4 input
  -> equipped SkillId
  -> current CompiledSkillPlan
  -> common activation and family validation
  -> CombatSnapshot
  -> resource/cooldown commit
  -> SkillExecutorRegistry family dispatch
  -> Hytale damage/status/movement authority
  -> idempotent cleanup
  -> automatic skill trace
```

The shared contracts are `SkillExecutionService`, `SkillExecutionRequest`,
`SkillExecutionContext`, `SkillExecutionResult`, `SkillExecutionPort`,
`SkillFamilyExecutor`, `SkillExecutorRegistry`, and `SkillInstanceLifecycle`.
Profiles are loaded from `rpg/runtime/stage-04-skills.json`; the implementation does
not create an executor class per fantasy skill and does not use an 87-branch switch.

Before commitment the common service checks actor usability, slot identity, compiled
plan validity, supported family/profile, equipment, family prerequisites, resources,
cooldown, and incompatible active state. A pre-commit rejection consumes no resource,
starts no cooldown, and creates no world effect. A successful activation captures the
snapshot and stable `rootCastId`/`skillInstanceId`/`correlationId`, commits resource and
cooldown, then dispatches. R013 correctly rolled back validation/commit failures. During
the later R014 projectile hardening pass, a deterministic fault test exposed that R013's
post-commit executor-throw path terminated without refunding the already committed cost
or clearing its cooldown. R014 repairs that shared boundary with the Stage 02 committed
resource refund plus cooldown clear. The six R013 executors had no known throw after
successful validation, but the stronger rollback claim applies from R014 onward.

### Strike

`StrikeGeometryService` provides reusable frontal-arc, line/thrust,
single-target-assist, and bounded radius selection. Native world positions and actor
facing drive the query. Candidate searches are spatially bounded and enforce range,
height, angle/line width, target cap, protected-target policy, per-instance/hit-index
deduplication, and monotonic repeat timing. Gameplay hits are independent of VFX and
are submitted through `HytaleDamageAdapter` and `DamageSystems.executeDamage`, retaining
the Stage 02 Gather -> Filter -> Apply -> Inspect lifecycle and correlation IDs.

Heavy Swing revalidates after its 0.45 s wind-up. Weapon change, incoming actual native
damage, death, disconnect, player/world drain, or rejoin cancels pending work
idempotently.

### Movement

The adapter obtains the requested direction from native `ClientMovement.wishMovement`,
uses swept `CollisionModule.findCollisions` checks, clamps the path before obstruction,
and applies bounded world-thread movement through `Player.moveTo`. It never disables
collision globally and never changes worlds. Fall-distance state is preserved.
Movement instances clean up on completion, death, disconnect, rejoin, or world drain.

Pounce uses the same movement primitive plus a deliberately small bounded landing
query; it does not introduce the Stage 06 generic area/ground-zone executor.

### Reaction and native control

Riposte arms a bounded one-shot state. The pinned Hytale API exposes a legitimate
defensive outcome as `Damage.BLOCKED`; the Stage 02 Inspect observer consumes that
event once and queues the counter for the following world tick, avoiding nested damage
execution. Expiry and teardown are idempotent.

Shield Bash uses the installed native `Server/Entity/Effects/Status/Stun.json` effect
with an authored 0.6 s duration override and overwrite semantics. The Stage 02
protected/boss policy is evaluated first. Boss identity is not inferred from a name or
health heuristic: outbound `UpdateBossBar.entityNetworkId` is tracked per world and
matched to the target's native `NetworkId`; hide/disconnect/world teardown removes the
identity. If native effect application fails, the provisional kernel status is removed
and the trace records `STATUS_REJECTED`; no false success is reported.

### Presentation

Family code calls `LinkTreeVfxService`, which may delegate to the optional HTDevLib
adapter. R013 deliberately ships no invented particle IDs. Missing optional presentation
is a gameplay-neutral no-op, while mechanics remain authoritative.

## Trace contract

Automatic `skill-trace.jsonl` coverage includes:

```text
SKILL_ACTIVATION_REQUEST
SKILL_VALIDATION_PASS
SKILL_VALIDATION_REJECTED
SKILL_COMMITTED
EXECUTOR_DISPATCH
STRIKE_QUERY
STRIKE_TARGET_ACCEPTED
STRIKE_TARGET_REJECTED
STRIKE_HIT
MOVEMENT_BEGIN
MOVEMENT_CLAMPED
MOVEMENT_END
MOVEMENT_CANCELLED
REACTION_ARMED
REACTION_TRIGGERED
REACTION_EXPIRED
REACTION_CANCELLED
SKILL_TERMINATED
```

Each native damage hit continues through the existing Stage 02 damage trace under the
same root, instance, and correlation identities. `/rpg trace` is neither required nor
implemented.

## API audit

The audit was performed against the installed `HytaleServer.jar` SHA-256
`EC57E9BD6E2CA3CB16CC5883D42B04A0C64D382DEE532C5BC1CFCF68421E1EE3`.
It verified:

- `Damage.BLOCKED` metadata and the existing native damage lifecycle;
- swept collision lookup, `Player.moveTo`, and `ClientMovement.wishMovement`;
- authoritative main/offhand inventory reads;
- entity-effect lookup and duration override, including the installed Stun asset;
- native NPC protection/invulnerability state;
- outbound boss-bar observation and `UpdateBossBar.entityNetworkId` to `NetworkId`
  matching.

The machine-readable result is `evidence/stage-04/R013/api-audit.json`.

## Verification results

The final clean gate recorded:

| Gate | Result |
|---|---|
| Gradle build | PASS |
| Complete RPG + CanvasUI tests | PASS — 99 tests, 0 failures, 0 errors, 0 skipped |
| Required packaged entries | PASS |
| CustomUI package/source validation | PASS |
| Canonical catalogs | PASS — 87 skills, 66 passives |
| Stage 04 pilot profile | PASS — 6 skills |
| Player persistence schema | PASS — schema 2 unchanged |
| CanvasUI source unchanged | PASS |
| Bare-server/save startup | PASS — all three mods discovered; RPG setup/enabled/ready; no RPG failure |

Deterministic coverage includes validation failures and no-consume behavior, commit
ordering, cooldown, snapshot/modifier flow, arc/range/line geometry, target deduplication,
repeat-index timing, Quick Slash damage requests, Heavy Swing cancellation, Shield Bash
policy, movement distance/clamp/cancellation, Pounce landing payload, reaction expiry and
one-shot trigger, duplicate-event rejection, family registry dispatch, boss identity,
native API signatures, Stage 01B/02/03 regressions, and CanvasUI regressions.

Evidence:

- `evidence/stage-04/R013/verification.json`
- `evidence/stage-04/R013/api-audit.json`
- `evidence/stage-04/R013/server-smoke-summary.json`
- `evidence/stage-04/R013/server-smoke.txt`
- `evidence/stage-04/R013/installation.json`

The smoke harness intentionally terminates the otherwise healthy dedicated server;
its recorded process exit code `9` is expected and is not a startup failure.

## Build and deployment

The deployed RPG save contains exactly the established three mods:

| Mod | SHA-256 |
|---|---|
| `CanvasUI-0.1.0.jar` | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| `HYTALEDEVLIB-0.5.0.jar` | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |
| `HytaleRPG-0.0.6.jar` | `1A648404196DB6DFC03E8A89E5E3700FFABA9663992463D0A5440A1910ECB380` |

Deployment target:
`C:/Users/Zemio/AppData/Roaming/Hytale/data/pre-release/Saves/RPG/mods`.
The previous `HytaleRPG-0.0.5.jar` is retained at
`evidence/stage-04/R013/rollback/HytaleRPG-0.0.5.jar`.

## Connected evidence still required

Local tests cannot prove live input delivery, real item identity in the owner's
inventory, native NPC spatial/damage behavior, rendered movement and collision,
connected Stun/BLOCKED events, HUD timing, client stability, or restart persistence.
Those observations are deliberately left pending in `client-verification.md`.

Stage 04 can become `PASS` only after the checklist demonstrates real Quick Slash
damage with Gather -> Filter -> Apply -> Inspect, failure-without-cost, Heavy Swing
wind-up, correct Shield Bash policy, collision-safe Quickstep, Pounce landing damage,
one-shot Riposte, resource/cooldown behavior, healthy four-slot HUD, no RPG-scoped
exceptions, and restart/rejoin persistence.

## Known limitations and deferrals

- Connected-client verification is pending; this is the only Stage 04 gate not yet
  evidenced.
- Final art/audio is deferred. Native/placeholder presentation remains intentionally
  subordinate to gameplay authority.
- Only the six named pilots are executable. Remaining Stage 04 records stay disabled.
- Full Burst/Nova/Cone/Ground/Trap/Wall/Overhead behavior remains Stage 06.
- Production Link Tree interaction remains under the previously recorded CanvasUI /
  Noesis decision and is not changed by R013.

## Rollback

Stop the RPG world, remove `HytaleRPG-0.0.6.jar`, and restore
`evidence/stage-04/R013/rollback/HytaleRPG-0.0.5.jar` to the RPG save's `mods`
directory. Keep the existing CanvasUI and HytaleDevLib JARs. Player schema remains v2;
do not replace or downgrade player JSON files.

## Closure decision

```ini
Stage04 = IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION
Stage04Pass = false
EarliestUnprovedBoundary = CONNECTED_ABILITY_INPUT_TO_NATIVE_EFFECT
Stage05SafeToBeginProvisionally = true
```

There is no automated, server-startup, architectural, persistence, or RPG-scoped
exception blocker. Under the owner's explicit authorization, Stage 05 may begin as a
separate revision while Stage 04 remains non-PASS pending connected evidence.
