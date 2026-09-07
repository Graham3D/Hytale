# Stage 06 — spatial families and first four Links (R025)

`Stage06 = IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION`. The local engineering
gate is complete; connected PASS is not asserted. R024 remains the prior archived
rollback; R023 remains deployed. The authorized program advances into Stage 07.

## Authority and first cohort

Master v1.2, especially 00.3/00.4, 04.1–04.3, geometry contracts and Phase 06,
supersedes the older HUD/input prose in v1.1. The owner confirmed that the shipped
Fireball cast during R023 despite zero inbound watcher observations; the separate
R024 report documents the Quiche/server-execution correction. No subsequent
connected RPG cast has yet established that correction's runtime gate.

The first cohort is Ground Slam, Frost Nova and Root Snare, exactly as Phase 06
specifies. Their numeric profiles use 5 m / 5.5 m / 2.5 m radii, 3 m height,
1.55 / 1.10 / 0.40 damage coefficients and unchanged authored costs/cooldowns.
Ground Slam's linear edge falloff bottoms at 70%; Frost Nova requests two Chill
stacks; Root Snare arms at 0.5 s, lasts 15 s and chooses the nearest susceptible
entrant deterministically. A boss receives its authored 35% Slow for 3 s.

## Shared implementation and reasoning

- `AreaGeometry` intersects collision bounds, not just target centers. Sector
  clipping uses the authored full angle; rectangle dimensions are full lengths,
  not half-widths. The vertical interval is independent of horizontal geometry.
- `AreaRuntime` owns finite fields, per-impact/per-target ledgers, trap arming and
  expiry, and explicit candidate overflow rejection. Current live caps are eight
  fields per owner and 128 globally. A trap expires without an expiry explosion.
- Native queries inspect actual ECS bounds. The old center-index query plus a
  fixed one-meter pad could miss large bodies; it is not reused for area hits.
  The bounded full-bounds query rejects worlds above its 4,096-NPC scan budget
  and rejects candidate overflow rather than silently dropping paid hits. This
  conservative first implementation still needs the performance/hardening pass.
- Native NPC attitude, invulnerability and block collision are consulted. Ground
  placement requires an actual upward-facing collision surface on the aim ray;
  the adapter does not invent a point behind a wall or float a trap in midair.
- Area payloads call the existing shared calculation and `HytaleDamageAdapter`.
  Its added `applyObserved` overload executes the same native path and returns
  cancellation/amount/Health observations. Existing callers retain the original
  public `apply` method. Canceled native damage cannot apply the area's status.
- A synchronous area failure after a possible hit retains its paid resource and
  cooldown; refunding it could make repeated partial hits free. The old Stage
  04/05 rollback behavior is unchanged and its regression tests remain retained.
- Native status projection is separated from status authority. The new status
  assets contain no damage. A transient ECS marker reconciles strongest-only
  Slow at 5 Hz; it does not retain target references in a global owner map.
- The installed NPC movement audit exposed an important distinction: NPC speed
  reads effect `HorizontalSpeedMultiplier`, but `isMovementBlocked` inspects
  active interaction effects, not entity-effect `MovementEffects`. Likewise the
  player-facing entity-effect Ability flags do not establish NPC attack control.
  `AreaNpcControlSystem` therefore clears native voluntary steering after native
  avoidance and before steering; Root preserves rotation/attacks. Frozen clears
  steering and interrupts native active/queued interactions, and Stagger only
  interrupts actions. `ActionAttack` was audited to queue native interactions.
  No role replacement, animation-speed hack or persistent AI-state edit is used.
  This installed-code integration still requires connected control testing.
- The explicit control registry classifies audited Skeleton Elite roles as ELITE
  and the shipped example-boss roles as BOSS. Unknown roles use an explicitly
  project-authored COMMON control policy, not inferred native ranks or enemy XP
  eligibility. Native protection/observed boss identity wins; native zero
  knockback scale also prevents displacement. No substring classification.
- Shared status authority now applies rolling ten-second hard-control resistance
  (1, 0.5, 0.25, then rejection), elite duration scaling and protected-target
  rejection. Chill stays at four during Frozen immunity; a later legal hit may
  cross the threshold. Late inspection no longer extends immunity beyond the
  actual thaw time. Native NPC removal drops status and resistance memory.
- Status trace records explicitly identify kernel authority, not proven native
  behavior. Native projection exceptions are logged once per marker and retry at
  two-second intervals rather than throwing/logging every world tick. Area
  queries require real native UUIDs; fabricated fallback IDs cannot own statuses.
- Disc readability uses a native procedural geometry template, supplied the same
  radius as the authoritative footprint. This is a template request, not proof
  that the connected client rendered it. No native HUD/resource controls changed.
- Nature/Void use project-owned DamageCause IDs inheriting the audited Elemental
  resistance class. Unknown channels reject; they are not silently Physical.

The native Rune items remain trigger-only: Primary slot, zero native cost,
CostType None, zero native cooldown and the unchanged R024 bridge. Ground Slam
uses its shipped Rune icon; the other two currently use the verified area Rune
template icon, explicitly a placeholder, with distinct names.

## Evidence so far

Twenty-one new deterministic tests exercise bounds/height, falloff, single hits,
trap lifecycle, LOS/overflow rejection, per-cast identity, one cost/cooldown,
placement rejection before payment, partial-failure conservation and strongest
Slow/expiry, control rank/precedence, rolling resistance, Frozen immunity and
native steering policy. The complete retained build passed: **171 tests, zero
failures/errors/skips**, including the retained native-control and CanvasUI suites.
Machine-readable totals and hashes are in `evidence/stage-06/cohort-a/`.

The first isolated three-mod server booted normally and resolved all eight new
status assets and both damage channels. Its wrapper failed two stale assertions
still expecting version 0.0.17; the actual log correctly reported R025/0.0.18.
Those assertions were corrected and the first failed summary was preserved.
The rerun's raw log and gate result remain independent of connected evidence.
The latest normal smoke also registered the ordered NPC-control system and
reached `Hytale Server Booted`, then stopped cleanly. It does not exercise an NPC
or client. Cohort-A RPG JAR SHA-256:
`D9396CE6B7B657F99D6BA067845045140DF7AB2DCD9AE5659A463504ECA6036E`.
No live mod was replaced. R024 rollback hash remains
`FA6C2AAB3E232665D78336EB64334C98048C38821388E2C869AD057E85C14D1F`.

## Cohort B — finite zones, cones, mine and periodic source packages

The second bounded cohort adds Powder Mine, Cold Wave, Venom Spray, Blizzard,
Wall of Fire and Poison Cloud. No Stage 07 work is included. Canonical counts
remain 87 skills and 66 passives. Native items remain zero-cost/zero-cooldown
trigger bridges; resources and damage remain RPG-owned.

### Decisions and exact mechanics

- Powder Mine separates its 2.5 m trigger cylinder from its 4 m blast cylinder,
  arms after 0.5 s, expires after 20 s without exploding, and damages each eligible
  blast target once. Cold Wave uses the full 70-degree cone, 12 m range and 2.5 m
  height, with three Chill stacks inside 6 m and two outside. Venom Spray uses
  its full 65-degree cone, 8 m range, 0.9 direct coefficient and seven-second Poison.
- Wall of Fire uses full 10 m by 2 m dimensions, perpendicular to horizontal
  placement aim. Its 0.45 coefficient is damage **per second**, not per update.
  Poison Cloud likewise integrates 0.30 per second. Both last eight seconds;
  0.25 s ticks multiply by elapsed duration, and status application has a separate
  one-second per-target interval. A server gap over one second cancels the field
  rather than inventing historical target positions. Shorter catch-up intervals
  use currently observed targets, not reconstructed movement history.
- Blizzard has sixteen radius-2 m sub-impacts in its radius-6 m field, no hidden
  full-field damage, and a per-root/per-target 0.75 s damage interval. The bounded
  deterministic pattern uses a root/skill seed, equal-area radial strata and
  inset centers so the entire child footprint remains inside the advertised field.
  **Timing interpretation:** instant commitment starts a 0.25 s warning lead-in;
  authored impact offsets 0 through 7.5 s then run from that active epoch. Thus
  the first impact is 0.25 s after commitment and total field ownership is 8.25 s.
  This reconciles the required warning with an authored first offset of zero;
  it is explicit implementation judgment, not connected timing evidence. Lag
  cannot shorten an actually issued warning. A missing/changed warned surface
  consumes that impact without damage or retargeting to an unwarned point.
- Sub-impact placement uses native upward-floor collision and parent-to-impact
  LOS. Cone origin does not require the caster to stand on a ground surface.
  Rectangle/cone presentation uses bounded native line geometry matching those
  shapes; discs retain the native disc template. Rendering is unverified and
  placeholder icons/geometry are not final visual-quality acceptance.

### Proven shared-runtime corrections

The prior Burn map was keyed by owner/victim, allowing different source skills
to overwrite each other. The shared periodic runtime now keys source packages
by owner, skill, victim and Burn/Poison kind. Burn refreshes one source stack;
Poison adds up to three source stacks and retains at most twelve victim stacks,
ranked by resolved per-stack offensive snapshot strength with stable identity
tie-breaking. A weaker refresh cannot reduce the retained stronger snapshot.
Accrual is integrated before stack/snapshot changes, with one-second ticks and
proportional final remainders. Newly added stacks do not damage earlier time.
Competing packages settle before the Poison cap is enforced: an expired package
cannot displace a live one, and eviction preserves already-earned fractional
damage. Native-failure paths consume pending work before callbacks to prevent
replay; a gap over two seconds drops unobserved periodic catch-up.

This runtime calls the existing calculation/native damage path for every paid
tick; it never writes Health or manufactures downstream native events. DoT
damage uses the resolved offensive base before the direct-hit coefficient and
does not crit or seed triggered copies. Fire Bolt retains its existing native
Projectile damage channel and authored Burn coefficient/duration. New area Burn
and Poison use verified native Fire/Poison channels. Native visual effects carry
no damage. Projection is reconciled on the victim's world thread and owner
teardown preserves packages owned by other casters.

Two catalog corrections follow explicit master content rather than new balance:
Venom Spray is INNATE base power 20, not an unaudited weapon-power requirement;
periodic Wall of Fire/Poison Cloud do not advertise CAN_CRIT. Native Bomb items
also required an equipment-boundary correction: the installed items expose
`Family=Bomb` with an empty Weapon breakdown. The adapter now reads this actual
family tag. The audited Weapon_Bomb/Weapon_Bomb_Fire throw/projectile/explosion
inheritance resolves EntityDamage 20, recorded as their fallback power. RPG
does **not** run the native explosion, consume another item, or damage terrain.
Unaudited bomb variants remain missing-power rejections.

### Cohort-B evidence

The complete retained build passed **190 tests, zero failures/errors/skips**.
The normal isolated three-mod server resolved nine area profiles, ten status
assets and both project damage channels, booted and stopped with exit code 0.
It retains shipped-asset/animation and offline-auth warnings in the raw log;
the gate does not label the entire engine log warning-free. Cohort-B JAR SHA-256:
`AD835309D55CA1958159992CBC5A57FB2D669CD7D897BD6BD071E2471D3EA935`.
Aggregate totals, build hashes and boot results are recorded in
`evidence/stage-06/cohort-b/verification.json` and its smoke summary.
These are local engineering evidence only. R023 remains live; no connected
RPG input, zone rendering, NPC control or periodic Health loss is proven by them.

## Cohort C — pulls, pulses and overhead/bombardment payloads

This cohort adds Vortex, Earthquake, Meteor, Comet, Avalanche and Void Cataclysm.
All fifteen Phase 06 skill profiles now exist, but the four-link integration and
full stage hardening gate below are still outstanding.

- Vortex integrates 0.35 per second at quarter-second intervals for six seconds.
  Pull requests integrate 1.5 m/s, stop at the 1.5 m core and are separate from
  damage. Vortex's catalog CAN_CRIT flag is removed for the same master periodic
  rule used by Wall of Fire and Poison Cloud.
- The pull planner uses horizontal segments at most 0.25 m long, native swept
  collision and ground-support checks. It never changes target height or snaps
  an airborne NPC onto the field. The installed `Role.isOnGround()` delegates
  to the current native motion controller's `onGround()`, verified in bytecode.
  Native zero knockback scale and project control resistance suppress motion;
  elite scale is 0.5, boss/protected scale is zero absent an authored opt-in.
- Earthquake emits exactly four 0.55 pulses at 0, 1.125, 2.25 and 3.375 s, with
  requested 0.4 s Stagger through the existing shared resistance system. Its
  field expires at 4.5 s. There is no deformation or block destruction path.
- Meteor and Comet retain 0.9 s interruptible cast wind-ups, followed by 1.2 s
  and 1.3 s warnings. A native 12 m vertical collision check rejects blocked
  descent before commitment and during the warning; the warned ground must
  still resolve to the same surface at impact. A bounded procedural sphere
  samples the final 0.6 s descent at up to 20 Hz, from 12 m to ground. It is
  presentation-only, not a native projectile or damage authority. Their inner
  and outer coefficients are disjoint per target; Meteor's separate 3 m Burn
  tier does not add another direct hit. Comet requests five inner/three outer
  Chill stacks through the existing threshold policy.
- Avalanche's first hit remains at 0.5 s, then once each second through 5.5 s.
  The implementation supplies a 0.25 s per-impact warning (duration is template
  judgment; the master requires a local warning but does not supply its number).
  Ice/stone alternate Cold+Chill and Earth+0.5 s Stagger. Maximum three hits per
  target/root. Unlike Blizzard's zero-offset reconciliation, no epoch shift is
  necessary because its first authored impact already allows warning time.
- Void Cataclysm starts its eight-second field after the 1.25 s cast. Its eight
  radius-2.5 m impacts occur at offsets 0..7 s with five per-target/root hits
  maximum. A separate final radius-10 m hit at 8 s is permitted once even if a
  target reached the sub-impact cap or missed every sub-impact. Its susceptible
  3 m pull request runs immediately before damage. The 4 m core is presentation
  only; it never creates an extra damage tier or packet.
- The Tier IV/V profiles explicitly select the bounded 256-candidate apex query
  profile; ordinary skills retain 64. Neither path silently truncates a query.
  Every new native item remains a trigger-only Primary Rune with unchanged
  native HUD ownership, zero native cost and zero native cooldown.

New deterministic fixtures cover these exact schedules, disjoint tiers, roof
rejection, missing warned terrain, capped sub-impacts, separate final ledgers,
grounded/collision-constrained pulls, and 12 m descent sampling. The shared
activation fixture now exercises all fifteen profiles, including resource-free
wind-up followed by one commitment. Its initial duplicate-cast assertion exposed
fixture precedence, not an engine failure: Void Cataclysm leaves only 35/100 Mana,
so affordability correctly rejects before cooldown. The test now checks both
rejections separately without changing production resource logic.

The complete cohort-C build passed **199 tests, zero failures/errors/skips**.
Its normal three-mod isolated boot resolved all fifteen area profiles, reached
network-ready startup and stopped cleanly with exit code 0. JAR SHA-256:
`0DD129ECBE65E865E0E35BD77154DC74DDE231576AB2A8F6FB8D77E8B1C692E1`.
Aggregate regression/boot results and hashes are captured separately in
`evidence/stage-06/cohort-c/`; prior cohort artifacts and R024 remain available.
Connected rendering, timing, movement, native interaction and Health loss remain
UNVERIFIED. There has been no live deployment during this implementation program.

## Cohort D — first four Links and stage-local hardening

The four-passive cohort is Potency, Expanded Radius, Skill Delay and Echo. No
resource/cooldown formula, HUD, XP artwork, native slot policy, player schema or
Stage 04/05 numeric profile is changed. Compiled plan schema advances from 2 to 3
because the plan hash now includes typed release/geometry operations. Player
schema remains 3, and saved loadouts are recompiled rather than discarded.

Expanded Radius multiplies each declared radius by 1.25 once, including trap
trigger/blast and bombardment child/core radii, but not height, angle, wall
length/width or a projectile's collision radius. It contributes a single 10%
Less scalable-payload bucket. Utility fields do not acquire damage from it.
Potency remains a single 15% Increased bucket. The combined fixture exposed an
existing shared integration error: SkillExecutionService added Potency before
CombatSnapshotFactory added it again. Removing the service's duplicate addition
restores the canonical 15%, not a new balance value. A plain Quick Slash regression
now guards this boundary as well as the area/projectile combinations.

The bounded SkillReleaseScheduler reserves capacity before payment (six roots
per owner, 256 globally). Skill Delay commits cost/cooldown/snapshot/target once,
then releases after two seconds with 35% More. One pending primary per logical
slot remains enforced even if the loadout changes. Echo schedules one child
0.45 seconds after the actual primary release, at 70% magnitude, without a new
payment or cooldown; Delay+Echo gives 1.35*0.70=0.945 before other modifiers.
The child shares rootCastId/correlationId, receives its own skillInstanceId and
snapshot identity, inherits the committed offensive values and cannot Echo itself.

CommittedTarget contains world/point/direction/optional original target UUID,
not a retained native command buffer. Each scheduled release obtains a fresh
world-thread port and revalidates life, world, required equipment identity,
current range/LOS, ground/roof and capacity. No current aim replaces the saved
point. The original leap target is looked up via the installed EntityStore UUID
API, not by acquiring a new enemy. Delayed strikes retain their committed
geometry; movement validates the old destination; projectiles launch from the
current legal muzzle toward the old point. Invalid releases cancel without
refund or replay. Native ammunition remains the existing projectile release
adapter's responsibility; an Echo never consumes it a second time. Death,
disconnect and world-drain hooks cancel the release queue through the same
existing execution teardown. These hooks are code-audited, not connected proof.

Two additional integration problems were demonstrated by deterministic tests:

- The old projectile registration allowance counted only the primary effect,
  rejecting the legitimate Echo carrier. The generation-zero plan now reserves
  the second effect ordinal for an Echo; the primary-only path is unchanged.
  This exact shared correction is archived as a diff. The evidence guard still
  rejects unrelated changes under the earlier projectile/HUD paths. Stage 07
  will extend this seam into complete root-lifetime continuation accounting.
- Blizzard's interval and Avalanche/Void sub-impact caps belong to the root,
  not each copy. AreaRuntime now shares those ledgers between overlapping
  primary/Echo fields. Ordinary burst copies still apply independently, and a
  final Void blast has its separate final-hit ledger. Field plus sub-impact
  reservations enforce the 48-effect ceiling; owner/global limits remain 8/128.

Echo's richer canonical exclusions are now enforced centrally: corpse consumers,
deployed traps and collision-wall creators reject explicitly. Bone Cage receives
the semantic COLLISION_WALL catalog tag; the non-solid Wall of Fire remains
eligible. No new passive is introduced. Repeat-controller incompatibility still
rejects Echo+Barrage. Root Snare is classified as a deployed trap for this rule,
avoiding an apparently accepted Echo that only fails the one-deployed-trap rule.

Procedural telegraphs consume the same scaled geometry as queries. The fallback
palette follows elemental groups; preparing circular warnings use a second inset
ring, traps use a diamond marker, and impacts brighten. Native DebugUtils
FLAG_NO_SOLID is used for disc outlines so a filled ground disc is not requested.
The presentation lift is corrected to the master's 0.10 m without lifting hit
geometry. Rectangles retain their full 3 m height outline. All these current
player-owned PvE fields are non-hostile to players; no unsupported PvP boundary
semantics are implied. Client readability, z-fighting and packet rendering remain
unverified. Shapes expire in bounded durations; no infinite emitter is created.

## Final local engineering evidence

`clean build` passed **217 tests, zero failures/errors/skips**, including retained
Stage 01B/02/04/05, native-control structure and CanvasUI regressions. Catalog
counts remain 87 skills and 66 passives. The packaged CustomUI check passed.
All fifteen area profiles, ten status assets and two native damage channels
resolved in a normal three-mod isolated server; it reached network-ready boot
and clean shutdown, exit code 0. Shipped/offline warnings remain in the raw log.

The load fixture executes 96 simultaneous apex roots (12 owners at eight fields
each), 256 candidate bounds per query, with 1.25 radius scaling over 8.5 simulated
seconds. It produced 147,456 accepted fixture hits, 864 queries, 7,872 presentation
calls and 147,648 trace calls, then zero retained fields/roots. Measured runtime
was **84.020 ms** in the full build. This is an engine-neutral fixture: it excludes
native ECS collision, damage dispatch, serialization, disk logging, networking
and rendering. It is not a frame-time or live-server throughput promise. The
trace volume and procedural packet load require the Stage 13/native load audit.
Other fixtures cover malformed starts, overflow without eviction, root budgets,
claimed-release cancellation, failure cleanup and invalid-target no-replay.

Final JAR: `evidence/stage-06/cohort-d/artifacts/HytaleRPG-0.0.18.jar`.
SHA-256: `B45CD1502D29D7ADE8E46F17000081A5CCDCA6858A19FC867CB4339438235766`.
Machine records: `cohort-d/verification.json`, `load-fixture.json` and
`server-smoke-summary.json`; exact API bytecode and shared correction diff are
retained alongside them. Cohorts A–C and R024 remain archived. No live deployment
was performed. Rollback cancels pending releases/areas/status ownership on a
stopped-world transition and restores the archived R024 JAR, retaining player
progress; unsupported equipped skills are rejected explicitly, not erased.

## Connected gate still outstanding

Connected requirements include actual RPG ability input, legal/rejected ground
placement, friendly/protected/boss control, Health loss, correct visible footprint,
warning-to-hit timing, movement/status behavior and death/logout/world cleanup.
No unit test, asset audit or isolated smoke closes any of those client gates.
