# Stage 08 — line, beam, tether and orbit (R027)

## Status and authority

**IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION**. Both local cohorts passed;
the stage is not connected PASS. Stage 07's local gate is committed as `61fcc86`;
its final artifact is preserved under `evidence/stage-07/cohort-c/` for rollback.
Native casting remains unverified after R024. No live deployment is authorized or
performed by this implementation program. There is no connected Stage 08 PASS.

Authority: owner attachment `4be7b0e7-6165-4222-9c28-5ef5ca1faddf`, master v1.2
MD-18–20, 00.3/00.4, 02.1/02.2, 03/04, Phase 08 and individual SK-031/034/056/057/
063/068/080/081. All eight individual records and the exact phase were read before
implementation. Master SHA remains
`750483855846FF6DB2564B4D6D626C2A12F1AC6EBBBBF12232B2C3ECCF667010`.

## Planned bounded cohorts

| Cohort | Skills | State |
|---|---|---|
| A, prescribed pilots | Wind Cutter, Void Beam, Ball Lightning | local engineering gate passed; connected UNVERIFIED |
| B | Root Lash, Lightning Bolt, Chain Lightning, Orbiting Shadow Blades, Life Drain | local engineering gate passed; connected UNVERIFIED |

The later continuous-program authorization supersedes the old owner-approval pause
between cohorts, but not cohort limits, full regressions, reports or rollback.

## Audit and intended ownership

Pinned target remains installed pre-release 0.7.0-pre.1. `Capture-Stage08Api.ps1`
records exact bytecode for collision, bounds, transforms, live aim, NPC hostility,
native stats, damage causes and finite debug geometry. Existing HytaleAreaQueries,
HytaleDamageAdapter and the Stage 02 resource/cooldown services remain the native
authority. Shipped Wind and Lightning damage channels were found at
`Server/Entity/Damage/`; the existing project RPG_Void channel is retained. No
particle/model/sound ID is assumed from the master's candidate list.

Planned shared infrastructure: finite connection ownership, exact line/front and
radial pulse geometry, fixed-time integration and per-target ledgers. The three
pilots must prove independent local fixtures before the second cohort is added.
Wind Cutter remains LINE, not PROJECTILE. Ball Lightning is a moving pulse origin,
not a native collision projectile. Void Beam must pay each 0.25-second upkeep slice
before applying its 0.45 * 0.25 coefficient and start cooldown only when
the channel ends. Aim sampling, obstruction, invalid-owner/equipment cleanup and
long simulation-gap handling must be explicit.

The existing catalog incorrectly labels Void Beam as discrete/upfront-cost damage
despite its authored channel/upkeep contract. That semantic boundary must be
corrected with the runtime so Echo/Skill Delay cannot accidentally repeat a
channel. Shared field capacity must include both Stage 06 areas and Stage 08 owned
connections; adding a second independent eight-field allowance would violate the
owner's cap. These are necessary shared integration changes, not resource rebalance
or projectile redesign.

## Evidence still required

The cohort A local gate below records exact counts, hashes, retained regressions
and normal isolated three-mod boot/stop. No connected Stage 08 evidence exists.
Connected checks must separately establish endpoint/visual alignment, blocked
beams, actual damage and Mana mutations, interruption, native aim delivery, orb
wall stops, shared blade cooldowns and teardown. Key-up delivery is not assumed
from R024's instant native execution bridge.

## Cohort A implementation and reasoning — 2026-09-07

Implemented the prescribed three pilots, not the five remaining Stage 08 skills.
The immutable `ConnectionProfile` and `ConnectionShape` feed one finite
`ConnectionRuntime`. World access is reacquired through `ConnectionWorldPort` on
the owner's actual world tick; the runtime keeps UUIDs and immutable geometry,
not a command buffer or native entity reference. The native adapter uses existing
NPC attitude/protection/death filters, real BoundingBox AABBs, CollisionModule
block sweeps, and the existing HytaleDamageAdapter calculation/Gather/Filter/
Apply/Inspect path. Visual geometry is never hit authority.

### Geometry, timing and resource contracts

- Wind Cutter: full-width 1.2 m, 2.5 m high horizontal prism; 0.3 m front depth,
  20 m/s, maximum 16 m and 0.8 s. Each world update queries the swept interval
  between the previous and current front positions, clipped to the first wall.
  One ledger entry per victim prevents overlapping front sweeps from repeating
  the 0.95 coefficient. It remains LINE/WAVE, never PROJECTILE.
- Void Beam: current native HeadRotation direction is sampled for each authored
  0.25-second slice; endpoint clips at the first blocking surface within 22 m.
  The beam uses a 0.8-by-0.8 m oriented prism. The record provides width 0.8 but
  no separate beam height: matching the other cross-sectional axis to that width
  is an explicit engineering interpretation, not a claimed authored number.
  Origin is feet +1.35 m. Twenty maximum slices each use coefficient
  `0.45 * 0.25 = 0.1125`, totaling 2.25 over five seconds. No hit occurs at spawn.
- Upkeep uses `RpgResourceService.evaluateUpkeep`, reserve/commit/finish against
  the native resource port. It preserves fractions (for example 5 Mana/s for a
  quarter-second is 1.25, not the upfront integer-rounded 2). No existing
  activation-cost, stat, regeneration, reservation or cooldown formula changed.
  Void Beam spends 1 Mana per unmodified slice before damage; insufficiency ends
  it before the unpaid hit. A bounded target query is performed first so an
  overflow can reject the whole slice without charging. Native `setStatValue`
  can suppress writes, as verified in the pinned bytecode: the adapter therefore
  also compares actual before/after values with the requested debit (1e-4
  tolerance) and rejects damage if the debit was not observed.
- Channel cooldown is started once, from the snapshotted modifiers, on
  termination rather than commitment. The CHANNEL lifecycle prevents another
  incompatible cast while it is active. Native incoming post-filter damage,
  world change, invalid/dead owner, committed equipment change, RPG Frozen/
  Stagger/Fear and native Stun interrupt. `EffectControllerComponent.hasEffect`
  was audited: it resolves the native asset index and checks the active-effect
  map. Root is intentionally not a channel interrupt because it prevents
  translation, not attacks. Key-up/hold delivery is not asserted or fabricated;
  the bounded channel runs to interruption or its five-second maximum.
- Ball Lightning: a moving pulse origin, not an interaction-bearing projectile.
  Speed 5 m/s, travel cap 18 m, five-second total lifetime. It stops at a wall or
  range but remains until expiry. Six pulses occur at 0.75, 1.5, 2.25, 3, 3.75
  and 4.5 seconds, each coefficient 0.35, radius 1.8 m and cylinder height 3 m.
  It begins at feet +1.35 m; no initial contact hit or repeated collision damage
  is added. Each pulse has a distinct ordinal while duplicate target candidates
  in that pulse are deduplicated. Expanded Radius affects the cylinder through
  the compiled modifier and retains its existing magnitude penalty.

### Shared safety and retained boundaries

`OwnedFieldBudget` is shared with Stage 06 AreaRuntime: eight fields per owner,
128 globally, not an allowance per family. Whole-query overflow beyond 64
candidates rejects; a field retains at most 256 distinct victim identities and
rejects further additions instead of evicting identities and allowing re-hits.
Backward/repeated time cannot repeat a slice. A simulation gap exceeding one
second terminates the connection instead of applying historical damage against
present-day geometry. Smaller irregular intervals drain only the bounded authored
slice schedule. Maximum lifetime validation is 120 seconds and maximum configured
pulse count is 256. Native teardown releases field capacity idempotently.

Void Beam's incorrect discrete/upfront/direct-hit/CAN_CRIT catalog semantics were
replaced by CHANNEL/PERIODIC/HAS_UPKEEP semantics and non-critical ticks. Echo and
Skill Delay now reject that channel; no fake periodic crit rolls are introduced.
Finite Wind Cutter and Ball Lightning reuse the existing paid release scheduler,
immutable targeting, Echo identity and snapshot modifiers. Tests cover delayed
aim preservation and Expanded Radius + Potency + Echo without a second charge.
Projectile passives reject the LINE/BEAM/ORB pilots. All 87 skills and 66 passives
remain in the catalog; there are now 30 executable profiles (27 retained +3).

The three added native ItemAbility assets use the already audited shared Rune
template icon/model and R024 effect-free bridge. All 30 projection assets are
automatically checked for native Cost=0, CostType=None, Cooldown=0. Native Hytale
continues to own Health/Mana/Stamina, Signature Move and AbilitySlots. No HUD,
CanvasUI, XP geometry, Ability4 policy, Stage 04/05 numeric profile, projectile
config or projectile-family implementation changed. Shared fallback wireframes/
cylinder outlines have finite lifetimes and a 20 Hz active-presentation ceiling;
their client appearance is unverified, not bespoke art.

### Local results and failures encountered

1. The first retained run exposed three inventory integration failures: missing
   new native trigger assets and two original pilot tests counting every non-area
   profile as Stage 04/05. Added the trigger assets and retained the six+six
   original pilot checks by explicitly excluding connection profiles. The R021
   asset check was strengthened to validate every executable profile, not only
   the original twelve.
2. Initial new test compilation used an incorrect local cooldown accessor name
   and omitted an unrelated required port method. Corrected the fixture to the
   real `canActivate` interface and an assertion-only projectile stub.
3. An initial delayed-wave test used nonexistent passive ID `delay`. Corrected
   it to canonical `skill_delay`; fixture passive equipment must now itself
   succeed, preventing unknown IDs from masquerading as compatibility rejection.
4. All **24** new Stage08ConnectionTest cases passed. Complete `clean build`
   passed **298 tests, zero failures/errors/skips**, including all retained
   Stage 01B, Stage 02, corrections, native-control, Stage 04–07 and CanvasUI
   tests. These include engine-neutral timing/geometry, not connected behavior.
5. Normal isolated three-mod server startup and console `stop` completed with
   exit code 0. R027/.20 was discovered and enabled; network booted; all retained
   asset gates, R024 bridge audit and new three-profile/three-damage-channel gate
   resolved; no native trigger asset rejection. Packaged CustomUI validation
   passed for nine RPG documents. This proves asset loading and isolated boot,
   not input delivery, rendering or native connected damage.

Evidence: `evidence/stage-08/cohort-a/{test-results,verification,
server-smoke-summary}.json`, full `server-smoke.txt`, pinned bytecode under `api/`.
Capture records source HEAD `61fcc86` plus a dirty worktree: that is build
provenance before this cohort commit, not a false assertion that the artifact
came from clean Stage 07 source.

Artifact `artifacts/HytaleRPG-0.0.20.jar` SHA-256:
`1CF2AA248E70162149AA80E7A150F0D27B4F26CB7EF41103D4BFCE8C99A41AC1`.
Rollback `rollback/HytaleRPG-0.0.19.jar` SHA-256:
`7684E120C2A1C314D3054077761BF90232CB73E0961C5B2AC6CA463425F87ACF`.
These scripts refuse to overwrite an already archived cohort with a different
build. The live RPG save/mods and R023 Rune-control recovery journal were not
written. Cohort A's local gate permits automatic continuation to cohort B; it
does not close Stage 08 or resolve the connected native-casting gate.

## Cohort B — completion of local Stage 08 scope, 2026-09-07

Cohort A was committed/pushed as `da2f1b1` before adding these five skills. They
extend the same typed connection runtime, world port and owned-field budget;
there is no per-skill combat engine. The full profile registry now has 35
executable skills, including all eight assigned to Stage 08. The catalog still
has exactly 87 skills and 66 passives. Completing this stage does not imply that
the remaining earlier non-pilot skill profiles or later stage passives exist.

### Targeting and finite connections

`ConnectionTargeting` performs a conservative server-aim query against actual
native collision bounds and the first solid obstruction. Root Lash uses its
authored 0.7 m full-width, 2.5 m-high line. Chain Lightning and Life Drain have no
authored aim-assist cone; they use a narrow 0.02-by-0.02 m targeting ray-prism.
That 2 cm selection tolerance is an explicit engineering choice, not a new skill
damage width or a fabricated client target packet. The nearest intersecting
bound wins, then stable UUID breaks ties. Range uses distance to actual bounds,
LOS is required, and the whole acquisition rejects beyond 64 candidates.

Required target UUIDs are committed before payment even without Echo/Skill Delay.
Repeats retain that entity identity rather than silently selecting whatever the
owner later aims at. A delayed release revalidates the saved world, target,
range, LOS and equipment without imposing a second fresh-reticle selection.
`EntityStore.getRefFromUUID` was audited in the exact JAR: it returns the current
UUID-indexed entity reference. Each native target access checks reference,
hostility, protection, life state and BoundingBox again.

- **Root Lash:** exactly one selected target along 12 m; one 0.70 coefficient
  native damage dispatch and Root 1.5 seconds on an uncancelled native hit. Root
  uses the existing StatusService and audited Common/Elite/Boss control profile,
  followed by the retained Stage 06 NPC control/visual projection. No pull,
  movement write, continuous damage or extra target is added. The tether fallback
  lasts 0.25 seconds and its endpoint is the selected native target bounds.
- **Lightning Bolt:** existing 0.15-second windup/interrupt/payment lifecycle,
  then one instantaneous 24 m, 1 m-wide, 2.5 m-high line query, coefficient 1.45
  once per victim. First solid obstruction clips the line. Interrupting the
  unpaid windup preserves resources and does not start cooldown.
- **Chain Lightning:** first target is within 22 m and LOS. It hits immediately
  at coefficient 1.25, then makes at most three jumps at +0.08/+0.16/+0.24 s with
  coefficients 1.00/0.80/0.65. Each next victim is distinct and within 8 m of the
  previous impact (subject to the existing compiled radius modifier), with LOS.
  Selection is nearest valid bounds, then UUID. A still-live prior target's
  position is refreshed; death/despawn after impact preserves only that last
  impact position as the next origin. An empty or overflowing next query ends
  the chain without repeating a victim. Mana 24 and cooldown 10 s are paid once.
  It is DIRECT_TARGET/CHAIN, not PROJECTILE; the Chain passive still rejects it.

### Orbiting Shadow Blades

Four proxies at 90-degree offsets rotate at 120 degrees/s on a 2.8 m orbit,
height 1.1 m, each contact radius 0.30 m; lifetime is ten seconds. The runtime
samples fixed 0.05-second authoritative proxy positions and sweeps each sphere
between its prior/current positions. Capsule/AABB distance uses an exact
piecewise-quadratic segment-distance calculation, not the capsule's expanded
rectangular bounding box. No extra radius is added. This is a piecewise-linear
20 Hz representation of the circular path (six degrees per segment), an explicit
simulation approximation rather than a claim of continuous analytic arc contact.

All four sweeps share one native candidate query, one 64-target limit and one
per-victim 0.75-second contact cooldown. They do not each get an independent DPS
clock. The constant-large-target fixture therefore sees 14 hits at
0/0.75/.../9.75 seconds, not 56. The victim ledger remains capped at 256, and the
same one-second simulation-gap policy applies. Owner translation updates the
proxy centers; no historical NPC motion is fabricated during catch-up. The
fallback draws finite contact spheres and short swept trails, not approved final
blade art. Native owner-following geometry and presentation still need client QA.

### Life Drain and healing authority

Life Drain retains one initially committed target within 18 m. Each quarter-second
slice revalidates that target's live bounds/range/LOS, pays exactly 1.25 unmodified
Mana, then uses coefficient `0.55 * 0.25 = 0.1375`. Twenty paid slices over five
seconds total 25 Mana and coefficient 2.75. Loss of target, range or LOS ends the
tether before another debit/hit; changing aim does not retarget it. The same
channel interrupts as Void Beam apply. Cooldown 8 s starts once on channel end.
The erroneous Life Drain discrete/upfront/CAN_CRIT catalog tags were corrected
to CHANNEL/PERIODIC semantics; repeat controllers reject this channel.

Native damage inspection returns actual authoritative Health-before minus
Health-after. The new stateless `HealingCalculationService` converts only
positive finite actual Health loss: `loss * 0.60`, then the snapshot's Wisdom
healing multiplier once and applicable Increased healing (currently the typed
Potency modifier). It does not run damage scaling again, reapply INT/base power/
crit, or apply the source's Echo/Delay/area damage buckets a second time. Potency
is relevant to both damage and healing components, so it can increase the initial
damage and then the explicitly modified healing conversion. Zero actual loss
creates no healing, including fully absorbed damage; overkill cannot become
healing because only the observed Health decrement enters the conversion.

The native adapter writes Health through the existing EntityStatMap, capped by
the current native maximum. Audited EntityStatValue accessors read the actual
current/max values. `HEAL_APPLIED` records source actual loss, base conversion,
Wisdom/Increased modifiers, requested healing, Health-before/after and actual
healing received. A suppressed native stat write is therefore visible as zero
actual healing, not a fabricated successful gain. The root/instance/correlation
identity is preserved through damage, upkeep, connection and healing traces.

The shipped damage inventory contains no Necrotic cause. Added the explicitly
project-owned `RPG_Necrotic` under the same audited Elemental parent schema used
by RPG_Nature/RPG_Void. It is a distinct semantic ID, not a silent substitution
of Physical or Void. Stage 08 now validates five native damage IDs: Wind,
Lightning, RPG_Nature, RPG_Void and RPG_Necrotic.

### Full local gate and archived results

- Added 20 cohort-B tests and two bounded load tests; retained all 24 pilot tests.
  The complete clean root/native-control/CanvasUI build passed **320 tests,
  zero failures/errors/skips**. No test exclusions were introduced.
- Full isolated three-mod startup/console-stop succeeded with exit code 0;
  R027/.20 stage 08 was enabled, the network booted, and all eight connection
  profiles/five causes plus retained Stage 06 and R024 native-root asset audits
  loaded successfully. All 35 native projection assets have Cost=0,
  CostType=None, Cooldown=0. Packaged RPG CustomUI validation passed for nine
  documents; no HUD/native resource/XP/CanvasUI changes were made.
- Local engine-neutral maximum-beam load: 128 fields/128 owners/64 victims per
  slice, 163,840 damage-port calls and 2,560 upkeep calls over five simulated
  seconds; **85.5176 ms**, zero remaining fields/capacity handles.
- Local engine-neutral maximum-orbit load: 128 fields/16 owners/512 blade
  proxies/64 victims per slice, 114,688 damage-port calls over ten simulated
  seconds; **587.7001 ms**, zero remaining fields/capacity handles. Both times
  exclude fixture construction, native ECS/physics, trace I/O, network and
  rendering. They are not connected-server tick-time claims.
- A generated multi-file asset patch initially repeated the language file as
  multiple update targets and was rejected atomically. It was corrected to one
  language-file update. No partial native assets were accepted from that failed
  patch. Cohort B's subsequent focused and full regression runs passed.

Machine evidence and full server output are under `evidence/stage-08/cohort-b/`.
The verification manifest records pre-commit source HEAD `da2f1b1` and dirty
worktree provenance. Final archived `.20` JAR SHA-256:
`16F580BE625AD71CE254807EDF4966B8BDAC52096DBCB6F949ED88BF80FADB5F`.
Both cohort A `.20` and Stage 07 `.19` rollback artifacts remain preserved; Stage
07 rollback SHA is `7684E120C2A1C314D3054077761BF90232CB73E0961C5B2AC6CA463425F87ACF`.
The installed pre-release JAR/Assets hashes are unchanged from cohort A.

Live mod hash verification after this gate still finds exactly the unchanged
R023 RPG, CanvasUI and HytaleDevLib JARs. The Rune-control recovery journal is
still 345 bytes with last write 2026-09-07 14:03:12 local. No live build was
deployed, no connected session was interrupted, and no recovery state was reset.

## Remaining connected gate / automatic advancement

Stage08 = **IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION**, not PASS. The earliest
unverified boundary remains an RPG activation through R024's native execution
bridge; the owner's successful R023 vanilla Fireball control does not prove that
replacement. Once that boundary is connected-proven, verify all eight skills'
actual geometry/LOS/damage, fixed ticks, native Mana/Health mutation, channel
interruptions/cooldown timing, four-blade shared cooldown, endpoint/proxy visual
alignment, native HUD ownership and death/logout/world-change cleanup. Unit
tests and isolated asset startup do not establish any of those client outcomes.

Under the continuous-program authorization, this completed local gate permits
Stage 09 implementation without waiting for the owner to run those connected
checks. Native casting remains UNVERIFIED, not silently solved by advancement.
