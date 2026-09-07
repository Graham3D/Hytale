# Stage 08 — line, beam, tether and orbit (R027)

## Status and authority

**IMPLEMENTATION_IN_PROGRESS**. Stage 07's local gate is committed as `61fcc86`;
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
| B | Root Lash, Lightning Bolt, Chain Lightning, Orbiting Shadow Blades, Life Drain | not implemented |

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
