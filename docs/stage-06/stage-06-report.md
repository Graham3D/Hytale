# Stage 06 — spatial families (R025 work in progress)

`Stage06 = IMPLEMENTATION_IN_PROGRESS`. No complete local stage gate or connected
PASS is asserted. R024 remains the prior archived rollback; R023 remains deployed.
Stages 07–13 have not started. The program continues after the bounded cohorts.

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

## Remaining Stage 06 work — not a capability-blocked or completed claim

Before the stage gate can pass, finish the other twelve skills in cohorts of at
most six; scheduled/stratified impacts, periodic source packages, disjoint inner
payloads, swept pulls, roof/terrain anchoring, warning timing and all templates.
Complete Expanded Radius, Skill Delay and Echo through shared runtime seams,
including valid preexisting projectile consumers; preserve Potency 15%.

Also extend the audited control registry with later relevant roles; add
budget/load/adversarial and native teardown tests; rerun full regressions and isolated
smoke; archive the final stage build, report its exact gates and commit Stage 06
independently. These are implementation tasks, not fabricated Hytale API blockers.

Connected requirements include actual RPG ability input, legal/rejected ground
placement, friendly/protected/boss control, Health loss, correct visible footprint,
warning-to-hit timing, movement/status behavior and death/logout/world cleanup.
No unit test, asset audit or isolated smoke closes any of those client gates.
