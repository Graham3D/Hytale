# Stage 11 — remaining passive primitives

R030 / 0.0.23. Status: IMPLEMENTATION_IN_PROGRESS. No connected Stage11 PASS.
Start: Stage10 local closure `2e5d928`; player schema5, compiled-plan schema9.
Work and evidence remain in the GitHub folder, not Google Drive. Live R023 is
untouched. The owner's continuous implementation authorization permits local
stage advancement but does not waive connected evidence or safety boundaries.

## Cohort A — Efficiency, Long Reach, Rapid Invocation

Three passives, within the four-passive cohort limit. Exact master LP002–004
records were read before implementation. Lingering was considered for this cohort
but split out: inherited `HAS_FINITE_DURATION` tags include flight/warning-only
lifetimes, so a generic lifetime multiplier would be incorrect.

### Findings and changes

- Efficiency's 0.85 cost multiplier was already implemented by the existing
  kernel. Integer nonzero spend remains `max(1,ceil(base*factor))`; zero remains
  zero. Upkeep remains fractional. No second cost multiplier was added. The
  compatibility parser incorrectly required an upfront spend for the canonical
  “or continuous Mana upkeep” clause, excluding Void Beam/Life Drain. That
  clause now accepts a declared upkeep component. Imported finite-cost tags also
  mislabeled pure reservation Auras; Managuard, Emanatism and Pedanticism now
  reject Efficiency rather than purporting to reduce reservation percentages.
- Long Reach and Rapid Invocation previously appeared as compiler descriptions
  without effective runtime fields. New typed `FoundationModifiers` resolves an
  immutable execution profile before family preflight, target selection, windup,
  snapshot and dispatch. It does not replace any family executor. Only named
  component fields change, using canonical record validation after conversion.
- Long Reach multiplies declared reach/placement/travel by 1.25 once. It does not
  scale target or damage radii, widths, cone angles, heights, summon leash, or
  collision sizes. Pounce's landing strike uses a field named `range` as a radius;
  that field is explicitly excluded. Native Orbit's field named `range` is also
  an orbit radius, not reach. Eleven canonical radius-only skills with imported
  `HAS_RANGE` tags now reject the link instead of leaking geometry capability.
- Conversely, a cone stores its forward reach in `AreaSkillProfile.radius`: that
  field receives reach, but not its angle or inner status threshold. A placed
  wall receives extended placement distance, not extended wall length or width.
- A traveling wave's derived travel lifetime scales with reach to preserve its
  authored speed and actually reach the extended endpoint. This is not Lingering.
  Projectile derived travel time already follows distance/speed; independent
  projectile expiry caps remain unchanged. Beam upkeep, duration and cadence,
  trap arming/status durations, and summon lifetimes remain unchanged.
- Rapid Invocation scales nonzero precommit windup by 0.80 with a 0.05s minimum.
  Instant and reaction-only skills reject it. Skill Delay, postcommit timing,
  authored cooldown, and Wisdom recovery remain unchanged.
- Effective profiles are cached by immutable authored profile and typed modifier
  values, maximum1024 LRU entries, never recomputed in native victim/tick loops.
  The compiled-plan schema increments to10 and its hash includes the typed
  foundation record. Player schema5 and owned content are preserved.

### Verification and boundaries

25 new tests cover positive and at least two negative fixtures for each primitive,
component exclusion, original-profile immutability, repeat resolution, cross-skill
plan rejection, and a real `SkillExecutionService` windup/commit/dispatch fixture.
That fixture pays8 Stamina for Heavy Swing after Efficiency, uses0.36s windup and
3.75m reach, and preserves the existing Wisdom-adjusted cooldown calculation.

Initial test failures were diagnostic-fixture errors: an invented weapon tag was
replaced with the existing `RPG_WEAPON_HEAVY` tag; the cooldown assertion was
corrected to compare the existing Wisdom calculation instead of assuming zero
Wisdom. The retained schema assertion was updated from9 to10. These did not
justify weakening production equipment validation or changing cooldown formulas.

Full retained build and isolated network smoke are recorded separately below.
Neither proves connected reach, casting timing, native input, animation, resource
presentation or client rendering. Native Health/Mana/Stamina, native AbilitySlots,
Ability4 policy and XP controls/assets are untouched. R024 native entry still
requires owner connected verification. Bone Cage remains safety-gated.

Rollback: `evidence/stage-11/cohort-a/rollback/HytaleRPG-0.0.22.jar` preserves the
Stage10 final build; no new player-state migration is introduced in this cohort.

### Cohort A local gate

- `clean build`: **687 tests PASS**, zero failures/errors/skips, including retained
  Stage01B/combat/CanvasUI/native-codec suites.
- Exact final build: `evidence/stage-11/cohort-a/artifacts/HytaleRPG-0.0.23.jar`,
  SHA-256 `7558FABC74B0D9068E603330844FC2455836E5723D2ED71BD73BCC4A5288C1D0`.
- Normal isolated three-mod startup reached `Hytale Server Booted` and clean stop,
  exit0. All60 neutral native ability assets and all retained family assets/roles
  resolved. No player connected. The earlier smoke was rerun after the final
  cone/wall audit so archived evidence matches the final binary.
- Packaged CustomUI audit:9 RPG documents PASS; native resource/XP/ability HUD,
  existing projectile assets and baseline costs are unchanged.
- Rollback SHA-256: `DB7DACA4D309C7590A3FBD7D7E35F0F74D94CEEB5BCA91D5AB8A88B1973D9A41`.
- Machine evidence: `evidence/stage-11/cohort-a/verification.json`, test case list
  and exact-build server log alongside it. Local gate PASS; connected UNVERIFIED.
  The evidence script was corrected for PowerShell5.1's JSON array wrapping;
  actual catalog counts remain87/66, not a content change.

Next authorized work: remaining Stage11 primitives in bounded cohorts, then the
complete eligibility/pair matrix and graph-fuzz closure. Do not present these
three primitives as completion of all40 Stage11 passives.

## Cohort B — Overcharge, Concentration, Lingering

Baseline: cohort A `ae687e7`. Exact LP006/007/009 master records reread in full.
Player schema remains5; compiled-plan schema11 invalidates the changed typed
profile transforms. No additional skills, HUD controls or native interactions.

### Decisions and implementation

- Overcharge adds0.25 to the existing Increased bucket and multiplies spend/upkeep
  by1.20 before affordability. With Potency, Increased is0.40, not1.15×1.25.
  Existing fractional upkeep and integer-upfront ceiling remain authoritative.
  Pure reservation payloads reject it, even with incorrect imported finite-cost
  tags. Quickstep rejects it because it has no scalable effect payload.
- Concentration adds0.30 Increased and transforms exactly the relevant footprint
  dimensions: radial radii×0.70; cone/arc angle×0.70; line/wall width×0.70.
  Radial inner/impact/status thresholds remain proportional to the radial
  footprint. It does not change placement range, height, wall length, projectile
  collision size or Orbit blade collision size. Expanded Radius remains a later
  1.25 radius factor: a5m Ground Slam becomes4.375m, not a double reduction.
- Concentration fixtures exposed imported-tag errors: Frost Bolt had an area tag
  for its collision shape; Wall of Fire/Void Beam lacked one despite their real
  affected footprints. `ProfileComponentPolicy` derives only this assessment from
  current immutable runtime records. The local eligibility result does not add
  global tags to the catalog/compiler, demonstrated by a dedicated test.
- Lingering extends finite actor, trap, zone, shield, non-control buff/debuff,
  pulse-Orb, and DoT effect duration by1.40. It multiplies the existing spend or
  attributable upkeep by1.15. An instantaneous hit with a finite Burn may extend
  that Burn; it cannot extend the projectile's flight. Imported duration tags
  alone do not qualify flight-only effects, reaction windows, crowd-control-only
  effects (including Dominate), attack sequences, or channel maximum durations.
- Periodic and repeated area schedules retain their cadence. Counts/caps that
  were derived from lifetime expand to cover the new duration, bounded at48
  impacts and256 ticks. Authored smaller per-cast hit caps remain unchanged
  (e.g. Avalanche3). Warning, arming, descent and crowd-control timing do not
  change. This avoids an apparently longer zone whose damage silently stops at
  its old lifetime-derived hit cap.
- Burn uses the existing `PeriodicStatusRuntime` duration/DPS integration. The
 4s Fire Bolt Burn becomes5.6s: five full1s slices plus0.6s, not six full ticks.
  Native visual duration uses the same resolved status field. No parallel status
  clock or damage engine was added. Related projection remains connected-unverified.
- Wolf Summon becomes28s for23 Mana. Combining Minion Empowerment yields21s;
  its existing power/Health multipliers are unchanged. Finite Reaping Storm may
  extend, but indefinite reservation Auras do not acquire a synthetic lifetime.

### Evidence scope and limitations

32 new tests include positive/two-negative primitive fixtures, additive bucket
composition, resource payment, geometry rejection, no-global-tag leakage, real
summon lease expiry, a real `SkillExecutionService`→`AreaRuntime` extended-zone
fixture, ownership cleanup, and the existing DoT runtime's fractional tail.
One test initially referenced the harness's SupportRuntime instead of its local
AreaRuntime; that fixture naming collision was corrected before build validation.

Profile policy currently refines the60 implemented runtime profiles. Unknown
future runtime records still use catalog gates and cannot execute without a
profile. The remaining27 Stage04/05 records must be audited when implemented;
their catalog presence is not completion. Component-introduced Shrapnel-only
Concentration is not granted globally to the original projectile carrier.

Connected proofs still required: changed range/footprint/telegraph, actual native
duration/status application, actual cost/upkeep and the R024 native input path.
These unit-runtime fixtures are not evidence of native/client execution.

### Cohort B local gate

`clean build`: **719 tests PASS**, zero failures/errors/skips. Normal isolated
three-mod network boot and clean exit0 PASS; all retained native assets and roles
resolved. Packaged RPG CustomUI audit9 documents PASS. No live deployment.

Artifact: `evidence/stage-11/cohort-b/artifacts/HytaleRPG-0.0.23.jar`.
SHA-256: `F7000238722525048C3A018A858F6F590DEFBE38612054356A40FBDA883AD082`.
Rollback: cohort A, SHA-256
`7558FABC74B0D9068E603330844FC2455836E5723D2ED71BD73BCC4A5288C1D0`.
Machine evidence and all test-case names are in `evidence/stage-11/cohort-b`.
Local gate PASS; connected UNVERIFIED. Six of40 Stage11 passive primitives now
have local implementation evidence;34 remain before matrix/hardening closure.

## Cohort C — Second Wind

Single primitive cohort because it changes durable cooldown state. Baseline:
cohort B `bab6226`. Master LP005 reread in full before changes. R030/0.0.23,
player schema6, compiled-plan schema12. Normal abilities retain their existing
single-charge cost/cooldown math; Second Wind adds capacity2 and recharge×1.30.

### Serial debt model and failure handling

- Extended the existing `RpgCooldownService`, not a parallel cooldown authority.
  Each skill stores at most two serial recharge debts. The head alone advances;
  elapsed time remaining after its completion advances the second. Available
  charges are `max(0,currentCapacity-pendingDebts)`. Capacity changes do not clear
  debt, reset its progress, or reprice an already-running recharge. Re-enabling
  Second Wind exposes its capacity again, not a newly refilled spent charge.
- Each activation follows the existing resource transaction and spends one
  charge. A third activation without recovery rejects. Aura/Wisdom recovery and
  the0.25s cooldown minimum remain the same formulas; queued entries preserve
  their own captured recovery values. The initial Aura-boundary fixture expected
  a0.1s cooldown and was corrected to honor the retained0.25s minimum.
- A spend receives an in-memory identity token for rollback. The old failed-cast
  cleanup cleared the entire skill cooldown, which would erase an earlier charge
  when a second cast failed. Cleanup now removes only its matching latest spend.
  Replay/out-of-order refund requests reject; failed persistence cannot publish a
  free charge. Paid persistent effects retain the earlier no-refund policy.
- `SKILL_COMMITTED` now includes charge capacity and remaining charges alongside
  the existing cost, cooldown, plan hash and correlation fields. No fake native
  charge HUD or ItemAbility cost/cooldown was introduced.

### Persistence and rollback

SavedCooldown retains remainingWork/baseRecovery and adds a bounded queued list
(at most one waiting entry). Schema5→6 migration adds an empty queue to each
existing debt without changing remaining work, player progression or loadout.
The repository refuses current-schema files missing queue fields or containing
malformed nested debt. It does not interpret corruption as fully recovered.

Disconnect checkpoints both entries before eviction. A new process restores
remaining work without crediting unobserved offline time, matching the existing
conservative Stage09 policy. Checkpoint cadence remains once per second. The
repository migration test verifies the original schema5 file is recoverable in
its `.bak`; this is not a substitute for an operator's full versioned checkpoint.

Rollback requires cohortB code **and a pre-migration schema5 save checkpoint**.
Do not load schema6 queued debt into old code, strip the queue, or reset charges.
No live save was migrated or deployed. The live R023/schema3 installation remains
independent and needs its own stopped-world backup before any future deployment.

22 new tests cover serial ordering/carry, capacity swaps, normal→Second Wind debt
preservation, persistence failures, reconnect/new-service restart, exact refund
identity, Aura expiry across the queue boundary, malformed queues, positive and
two negative compatibility fixtures, real movement charge payments, failed-second
dispatch rollback, and file migration/backup. Native timing/input/HUD behavior
still needs connected evidence; these are unit/runtime and disk tests only.

### Cohort C local gate

`clean build`: **741 tests PASS**, zero failures/errors/skips. Normal isolated
three-mod network boot and clean exit0 PASS, with readiness now reporting actual
player schema6 instead of the historical hardcoded5. Native ability cost and
cooldown assets remain zero. Packaged CustomUI9 documents PASS; no live deployment.

Artifact: `evidence/stage-11/cohort-c/artifacts/HytaleRPG-0.0.23.jar`.
SHA-256: `A7ED226057EEC5292524D84E4CE5162016BBD1E9E86EC8AF93451D2B89E3C30E`.
Rollback code SHA-256:
`F7000238722525048C3A018A858F6F590DEFBE38612054356A40FBDA883AD082`.
Machine evidence and full test-case inventory: `evidence/stage-11/cohort-c`.
Local gate PASS; connected UNVERIFIED. Seven of40 Stage11 primitives now have
local evidence;33 remain before full matrix and hardening closure.

## Cohort D — Reversal and Momentum

Baseline C `0a7ffc0`. Master LP028/LP031 read in full. R030/0.0.23,
player schema6 unchanged, compiled-plan schema13. No new skills in this cohort.

Reversal resolves the authored reaction window×1.30 before the existing native
`ReactionWindowService.arm` call. Riposte0.8s becomes1.04s, with the same qualifying
native blocked-damage signals and one-shot event identity. Retaliation receives
+0.25 Increased through the existing kernel bucket (+Potency gives+0.40, not two
multipliers). No ordinary strike or active shield gains a reaction window, and
cost/cooldown/strike geometry/timing remain unchanged.

Momentum records a bounded, per-motion accepted path. Installed0.7.0-pre.1 bytecode
confirms Player.moveTo delegates to Entity.moveTo, which writes the Transform
position; the native adapter now rereads that authoritative position afterward.
Only positions lying on the collision-approved segment count, with continuity
from the previous observation. This is server implementation evidence, not proof
that a connected client accepted or displayed the movement. Raw bytecode and
identity are archived in `evidence/stage-11/cohort-d/api`.

The bonus is0.05 Increased per accepted meter, capped at10m/+0.50. It counts actual
3D segments, including leap height, rather than requested distance or endpoint
displacement. A partial two-meter result of a ten-meter request earns+0.10. Zero
travel, zero-time teleport, overshoot, off-segment movement, or discontinuity cannot
grant a bonus. An external discontinuity invalidates that cast's Momentum evidence
without changing the existing movement executor's mechanics. This conservative
behavior needs connected QA around native corrections and ordinary input during
movement. The existing `MOVEMENT_END.distance` endpoint field is retained; separate
validatedTravelMeters/travelEvidenceValid/momentumIncreased fields expose the new
evidence, so it cannot be confused with planned distance.

At the existing landing-strike boundary, the accepted bonus is added to a derived
copy of the original immutable combat snapshot. Payment, cooldown, equipment,
rootCastId/skillInstanceId/correlationId remain unchanged; no second authority or
damage path was introduced. Cancellation never dispatches a landing strike.
Motion teardown already removes the ledger on completion/death/logout/unload.

The imported Momentum clauses incorrectly required Movement AND DamagingCharge,
although the master explicitly says OR and names Charge as positive. Compatibility
now assesses an actual damaging movement component (or the canonical catalog
declaration for not-yet-implemented profiles). It does not add global tags. Pounce
and Charge qualify; Quick Shot and non-damaging Quickstep reject specifically.
Charge still has no runtime profile at this point: compatibility is not skill
implementation evidence and its remaining Stage04 work remains tracked.

22 new tests cover the above, real shared commit/reaction fixtures, expiry,
identity, additive magnitude, no repeated payment, path caps and invalid movement.
Initial test compilation corrected a Java compound-var declaration and the
finalTags accessor; no production failure was hidden by altering expectations.

### Cohort D local gate

`clean build`: **763 tests PASS**, zero failures/errors/skips. Normal isolated
three-mod network boot and clean exit0 PASS. Packaged CustomUI9 documents PASS.
Native resources/HUD/XP and neutral native ability assets are unchanged.
No live deployment or save migration occurred.

Artifact: `evidence/stage-11/cohort-d/artifacts/HytaleRPG-0.0.23.jar`.
SHA-256: `3DB526C4211FEE01DC1F2F69843C312086689300FDA4FC9A47DB82C7B86D4FDA`.
Rollback: C, SHA-256
`A7ED226057EEC5292524D84E4CE5162016BBD1E9E86EC8AF93451D2B89E3C30E`.
Both use player schema6; ordinary stopped-world checkpoint precautions apply.
Earlier schema5 rollback still requires the pre-migration checkpoint noted above.
Local gate PASS; connected UNVERIFIED. Nine of40 Stage11 primitives now have local
implementation evidence;31 remain before matrix/hardening closure.

## Cohort E — Executioner and Opportunist

Baseline D `9aa113a`. Master LP029/LP030 read in full. R030/0.0.23,
compiled-plan schema14; player schema6 unchanged. This cohort adds two primitives,
not a new executor, resource authority or native damage submission path.

### Earliest authoritative boundary

Installed bytecode confirms native Gather precedes Filter, and both
ScaleOutgoingDamageFromEntityEffects and ArmorDamageReduction belong to Filter.
The existing RPG Gather system now consumes an optional single-use per-Damage
conditional input before recording DAMAGE_GATHERED. It samples native current/max
Health at that point, and current server status state. Archived audit:
`evidence/stage-11/cohort-e/api`. Normal boot proves registration is accepted; it
does not prove this handler ran for connected damage.

- Executioner adds0.35 Increased only when living target Health/max is strictly
  below0.30. Exactly30%, unavailable/invalid Health and dead targets do not qualify.
- Opportunist adds0.25 Increased for actual ROOT/FROZEN/FEAR in the existing server
  status authority, or the installed native `Stun` effect. A STAGGER label alone
  is insufficient: the native stun effect must exist on the target. Chill, Taunt,
  ordinary Slow and immune-boss Frozen-substitute Slow do not qualify. Multiple
  qualifying controls still grant only one0.25 bonus.
- Conditions are evaluated separately for every target and hit, including DoT
  slices. They are not folded into the commit snapshot. Frozen outgoing DoT or
  summon snapshots preserve their original offensive data while the current
  victim condition is still evaluated at Gather.
- Increased is added to the original shared bucket. More/Less/Reduced retain their
  existing semantics; the previously rolled crit is reused, never rolled again.
  This also handles an original additive bucket clamped to zero without division.
  An initial fixture used0.2 where the existing More representation expects1.2;
  the fixture was corrected rather than changing the kernel's representation.
- The per-Damage request is consumed before mutation, so repeated handler invocation
  cannot amplify damage. Native cancellation remains authoritative. If another
  earlier Gather writer changes the submitted amount unexpectedly, this hit is
  cancelled with the exact `NATIVE_GATHER_AMOUNT_CHANGED` boundary; the integration
  does not guess how to rebase an unknown modifier or overwrite it. This explicit
  fail-closed interoperability boundary needs retesting when additional mods or a
  different native build are introduced.

All normal skill, projectile, area, connection, periodic and owned-summon hits
already pass through the shared native damage adapter and now carry this optional
input when their plan requests it. The adapter returns the gathered pre-mitigation
amount, so hit traces and future secondary calculations do not use an obsolete
pre-condition amount. Gather records targetHealthAtGather/targetMaxHealthAtGather,
targetControlAtGather/targetConditionalIncreased/conditionalPreMitigation and the
gate result. Inspect retains all three cast identifiers and the gathered amount.
Earlier DAMAGE_CALC/MODIFIERS/CRIT events describe the initial calculation; Gather
is explicitly where the additional victim-conditioned contribution appears.

Native support reflection from a damaging skill uses that skill's own captured
plan and already-scaled reflection basis. It keeps noProc/noLeech/noCredit and
reflection-recursion guards. Redirected shield transfers are never amplified.
Reflective Ward's fixed absorbed-damage secondary is not treated as a newly
introduced generic damage capability on a shield-only parent.

### Cohort E local gate

23 new tests: canonical positive/two-negative compatibility fixtures; strict
thresholds; control expiry/substitution; additive bucket composition; metadata
identity and single-use consumption on actual Damage objects; per-hit changes;
periodic/reflected/redirected distinctions; unchanged crit; float narrowing;
unexpected native writers; and Thorns reflection composition. These are automated
backend/native-object tests, **not connected native damage proof**.

`clean build`: **786 tests PASS**, zero failures/errors/skips. Normal isolated
three-mod network boot and clean exit0 PASS. Packaged CustomUI9 documents PASS.
No live deployment, HUD alteration, native ability mutation or save migration.

Artifact: `evidence/stage-11/cohort-e/artifacts/HytaleRPG-0.0.23.jar`.
SHA-256: `F878FC515E4BF7119A703A9C48469CDC2E5E7432ADE35A23D0EC43DAEA1861BA`.
Rollback: D, SHA-256
`3DB526C4211FEE01DC1F2F69843C312086689300FDA4FC9A47DB82C7B86D4FDA`.
Local gate PASS; connected UNVERIFIED. Eleven of40 Stage11 primitives now have
local implementation evidence;29 remain before matrix/hardening closure.

## Cohort F — Impact Force, Widening and Focused Channel

Baseline E `fd07ce3`. Master LP032/LP040/LP041 read in full before changes.
R030/0.0.23, compiled-plan schema15, player schema6 unchanged. The cohort extends
the existing resolve-before-validation profile step; no new native APIs are used.
The existing native knockback, collision, area control, line bounds and status
resistance adapters were inspected as consumers of these resolved fields.

Impact Force scales authored knockback/Stagger fields×1.75, before the retained
native displacement and hard-control resistance checks. Ground Slam's displacement
2 becomes3.5; Stone Bolt's knockback parameter1.5 becomes2.625; Shield Bash's
Stagger0.6s becomes1.05s (elite0.525s after the existing resistance multiplier).
Direct damage coefficients×0.90, without changing projectile speed/collision,
area radius/height, unrelated Root/Chill/Burn duration, DoT DPS, cost or cooldown.
Knockback parameters are not proof of actual connected meters traveled. Protected
and boss control rejection and the rolling control-resistance cap remain intact.

Widening changes resolving Beam/Line full width×1.50 and damage/effect coefficient
×0.85. Focused Channel uses full width×0.65 and+0.30 Increased in the kernel.
Both retain reach, height, lifetime, speed, cadence, cost and cooldown. Focused
Channel also applies to non-channelled Lightning Bolt. Minimum width0.10m is
enforced after composition with Concentration. Long Reach remains independent.
All changes reach the same geometry used by hit queries and existing presentation;
no particle-only proxy hit area was introduced.

Component policy distinguishes real resolving width from the small target-lock
width on Root Lash/Life Drain/Chain Lightning. Those target-lock fields are not
made into new beam geometry by equipping a width passive. Missing imported impact
tags are resolved from authored fields, locally, without granting global tags.
The profile cache key now includes typed geometry operators to prevent a focused
plan from returning a cached widened profile.

The catalog's Widening and Focused Channel entries name each other's conflicts,
but the compiler previously only compared identical group strings. Both now
normalize to BEAM_WIDTH_MODE. Linking the second conflicts and preserves the
complete saved state, original plan and graph routes in either order. The typed
record independently rejects contradictory flags. Owned passive copies are not
deleted. Existing unsupported-graph migration remains part of Stage11 closure.

23 new tests include each positive/two-negative fixture, unchanged unrelated
fields, control safeguards, actual full-width hit queries, a shared wave fixture
with exactly one cost, a twenty-slice Beam with unchanged upkeep/end cooldown, and
a shared Ground Slam fixture receiving the new displacement/damage parameters.
Fixture corrections: the local area runtime was renamed to avoid the inherited
support runtime field; rollback compares serialized state content rather than
object identity because the presentation service returns defensive copies.

### Cohort F local gate

`clean build`: **809 tests PASS**, zero failures/errors/skips. Normal isolated
three-mod network boot and clean exit0 PASS. Packaged CustomUI9 documents PASS.
Artifact: `evidence/stage-11/cohort-f/artifacts/HytaleRPG-0.0.23.jar`.
SHA-256: `352B10F04699FD7B360BF43435451D4A9F51BD2890D18DC94E8DF2657202DAC6`.
Rollback: E, SHA-256
`F878FC515E4BF7119A703A9C48469CDC2E5E7432ADE35A23D0EC43DAEA1861BA`.
No live deployment, save migration, native ability or HUD changes. Local gate
PASS; connected geometry, displacement, control and casting UNVERIFIED.
Fourteen of40 Stage11 primitives now have local evidence;26 remain.

## Cohort G — Mobile Domain

Baseline F `ac0492e`. Master LP033 read in full; existing area lifecycle, native
Transform/Health/world access and ground/query adapters inspected before changes.
R030/0.0.23, compiled-plan schema16, player schema6 unchanged. No new native API
assumption: the existing authoritative Transform is read, never written, for this
passive. The retained Stage11 movement audit documents that installed component.

Poison Cloud, Vortex and Earthquake now support finite caster-attached zones.
Blizzard remains ineligible because each stratified impact owns a specific warned
terrain footprint; following a moving caster would contradict that placement and
warning contract. The component policy also excludes traps, Auras, walls, corpse
consumers and any unimplemented/unknown mobile component. No generic relocation
capability is inferred merely from a broad family tag.

The typed zone modifier is part of the plan hash. Scalable magnitude receives one
0.20 Less factor in the existing commit snapshot (0.80 final factor), inherited by
direct/periodic damage and the existing Poison status snapshot. It composes with
Potency, Expanded Radius, Skill Delay and Echo without modifying costs, cooldowns,
radius, lifetime, pulse spacing, status duration, Vortex pull rules or target caps.
The runtime remains AreaRuntime, not AuraRuntime: no new reservation, upkeep,
passive resource drain, self-buff or native HUD mutation.

At release, the zone uses the current caster position, not the previously aimed
terrain point. Each world-thread field tick updates the same footprint origin,
including its vertical position. It does not sweep damage along the movement
path, create a trail, reset the lifetime, rebuild per-target ledgers or allocate
new fields. Query/LOS/control/collision checks and one-second unobserved-simulation
gap rejection remain. Presentation consumes that same current footprint; this is
implementation structure, not proof of visible connected behavior.

OwnerAnchor explicitly carries actor and world identity. Missing, dead/removed,
foreign-owner or changed-world anchors terminate and release the field/root budget.
The native port reads live Health/DeathComponent and Transform before supplying
that anchor. Existing logout/world teardown remains idempotent. No native entity
reference is stored in the field. Invalid mobile starts reserve no field capacity.

The first focused test run exposed a real integration requirement: immediate area
casts previously skipped CommittedTarget capture. Mobile casts now capture their
world identity before payment even without Skill Delay/Echo; other immediate area
casts keep their prior path. Failure to capture rejects before resource mutation.
The static-area fixture was corrected to retain its normal nullable-target path.
The first full suite also caught the retained schema15 assertion, now updated to16.

### Cohort G local gate

26 new tests cover positive/negative component fixtures, finite non-Aura ownership,
one payment/cooldown, snapshot composition, current release position, no damage
trail, vertical movement, static-area invariance, lifetime/ledger preservation,
missing/foreign anchors, adapter exceptions, capacity cleanup, LOS/overflow, lag,
Vortex, Earthquake, Lingering, Skill Delay and Echo identity. These fixtures do not
exercise connected Hytale movement, damage, input or rendering.

`clean build`: **835 tests PASS**, zero failures/errors/skips. Normal isolated
three-mod network boot, clean shutdown/exit0 and packaged CustomUI9 validation PASS.
Artifact: `evidence/stage-11/cohort-g/artifacts/HytaleRPG-0.0.23.jar`.
SHA-256: `8902BD6C3D3B129AD28B6BBF1806E715CC03531C8AFDEC40DADE6F6EC45DBA6A`.
Rollback: F, SHA-256
`352B10F04699FD7B360BF43435451D4A9F51BD2890D18DC94E8DF2657202DAC6`.
No live deployment, save migration, art changes, native ability or HUD changes.
Local gate PASS; connected gate UNVERIFIED. Fifteen of40 Stage11 primitives now
have local evidence;25 remain before Stage11 matrix/hardening closure.

## Cohort H — Rapid Pulse

Baseline G `338333a`. Master LP034, existing AreaRuntime/ConnectionRuntime,
AuraTimeline/SupportRuntime and native Aura damage/Chill adapters read before
changes. R030/0.0.23, compiled-plan schema17, player schema6 unchanged. This uses
the existing native damage/status boundaries; it does not introduce an engine API.

The component gate accepts actual periodic areas (including Wall of Fire), finite
Ground Zone pulse schedules, Beam/Drain/Orb pulses and Aura damage/Chill clocks.
It rejects flight, orbital contact sampling, target-to-target chain sequencing,
single attacks/traps, summon attack cadence and non-periodic Auras. Avalanche and
Void Cataclysm's authored bombardment sequences are not granted PeriodicPulse;
Blizzard explicitly has that capability and its primary family is Ground Zone.
Imported tags alone neither prove nor deny a distinct runtime component.

The existing immutable profile resolver applies interval×0.70 after other duration
operators. Pulse context applies one×0.80 snapshot factor. Integrated area/channel
profiles store DPS, so their coefficient is divided by0.70 before multiplying by
the shorter interval; otherwise the implementation would accidentally reduce each
pulse to56% rather than80% of its original size. The80% factor is never compensated
away. Root snapshots, constant Aura benefits, resource rates and non-pulse effects
remain unchanged. The typed pulse record is included in plan identity/cache keys.

Schedule-derived hit caps grow with the new count; explicit smaller victim caps,
Blizzard's0.75s victim interval, fixed terrain warnings, radii and movement speed
remain. Area schedules retain minimum interval0.05s and existing48-impact/256-tick
limits. There is no final partial damage pulse. Discrete schedules with an authored
initial impact retain that initial impact; Rapid Pulse does not delay it.

- Poison Cloud: interval0.25→0.175s,45 full pulses in8s, each80% of the previous
  quarter-second payload. Integrated coefficient sum after the80% factor is2.7
  rather than baseline2.4; the exact finite-endpoint gain is12.5%, not the asymptotic
 14.2857% rate increase. One24Mana cost, no upkeep/reservation and unchanged cooldown.
- Earthquake: interval1.125→0.7875s,6 impacts including the initial impact; each
  direct pulse80%, authored Stagger0.4→0.32s before native resistance. Vortex's
  per-pulse pull also receives80% of its old slice, without changing its core radius.
- Blizzard:23 planned impacts across the same active lifetime;0.25s warnings and
 0.75s victim gate stay unchanged. Lingering+Rapid Pulse yields32 planned impacts,
  still inside the retained root effect budget.
- Reaping Storm:11 reduced pulses in8s, with the same32 quarter-second upkeep
  payments,8 paid seconds and total Mana expenditure. Chilling Aura's damage clock
  becomes0.70s and Chill clock1.05s; its constant slow/radius and Mana/sec do not change.
- Void Beam:28 full damage pulses in5s. Twenty-eight0.175s resource slices plus the
  final0.10s upkeep-only slice still pay20Mana at4Mana/sec. The tail never generates
  damage or healing. An unaffordable tail stops normally without a free final pulse.
  Exactly one end cooldown remains. Drain healing still derives from actual damage;
  Orb speed, travel distance and lifetime do not change.

Integer Chill requires explicit rounding policy. A bounded per-effect/per-victim
ledger stores fifths: each authored stack contributes4/5, granting only whole
stacks and retaining the fractional remainder. Five accepted one-stack pulse
opportunities yield four application attempts, not five rounded-up stacks. New
victims cannot inherit another victim's remainder; duplicate/older pulse ordinals
grant nothing. Native immunity, resistance and Chill→Frozen authority still decide
the actual result. Rejected native applications are not refunded into the ledger.
At most256 victims are retained; the whole ledger dies with its finite field/Aura.
This is implementation accounting, not connected status proof.

The first focused suite failure was a fixture timing assumption: SupportRuntime
already evaluates on a100ms owner cadence, so a5.25s deadline immediately after a
5.20s owner tick is observed on the next eligible tick. The test now advances to
5.30s; production scheduling was not changed to satisfy that assertion.

### Cohort H local gate

26 new tests cover compatibility, formulas, independent clocks, cap/warning
preservation, fractional Chill accounting/bounds, actual shared area/Aura/channel
fixtures, payment ordering, no partial-tail damage, identity inheritance, cleanup,
Mobile Domain/Lingering combinations, native-adapter-shaped rejection and retained
Drain healing dependency. Connection fixtures now record payload contexts; their
simulated Health arithmetic is not relabeled native damage evidence.

`clean build`: **861 tests PASS**, zero failures/errors/skips. Normal isolated
three-mod network boot/clean exit0 and packaged CustomUI9 validation PASS.
Artifact: `evidence/stage-11/cohort-h/artifacts/HytaleRPG-0.0.23.jar`.
SHA-256: `2BAFBF70462776033959FF8F6AFF6B6CA43176EA93F0156C7FD8A9338C9C197A`.
Rollback: G, SHA-256
`8902BD6C3D3B129AD28B6BBF1806E715CC03531C8AFDEC40DADE6F6EC45DBA6A`.
No live deployment, save migration, art, native ability or HUD changes. Local gate
PASS; all connected pulse/status/input/rendering gates UNVERIFIED. Sixteen of40
Stage11 primitives have local evidence;24 remain before matrix/hardening closure.

## Cohort I — Combustion, Virulence and Concentrated Venom

Baseline H `fd11808`. Master LP043/LP046/LP047 and normative04.2 source-owned
status math read in full, along with existing periodic application, snapshot,
native effect projection and tick adapters. R030/0.0.23, plan schema18, player
schema6 unchanged. Three passives, within the four-passive limit.

Combustion changes direct hit coefficients×0.85, Burn duration×1.25 and Burn
DPS×1.50. The hit penalty is not put in the generic offensive snapshot: doing so
would incorrectly reduce the separately specified Burn offensive base. Fire Bolt
Burn therefore becomes0.15 offensive-base/sec for5s, without inheriting the direct
hit coefficient or critical multiplier. Wall of Fire's continuous field damage
is not a direct hit and stays unchanged; its applied Burn becomes6.25s at0.15/sec.
That Burn still includes its proportional0.25s final tick. Lingering composes
duration once (Fire Bolt7s), and the projectile's tick metadata follows duration.

Virulence changes only applied Poison duration×1.30 and requests two stacks per
legal application instead of one, retaining the source cap3. Poison Cloud's field
still lives8s; its Poison package lasts10.4s. Concentrated Venom uses the canonical
base source cap3: max(1,floor(3×0.60))=1, with0.06×1.75=0.105 offensive-base/sec
per stack. Virulence+Concentrated Venom requests two but still retains at most one.
Direct damage, critical behavior, current resource values, costs, cooldowns and
zone pulse intervals are unchanged by those two Poison passives.

The typed DoT record belongs to compiled identity/cache keys. Duration resolves
once with the immutable profile; DPS/added-stacks/source-cap resolve once at the
shared native periodic application boundary. The existing PeriodicStatusRuntime
remains the sole Burn/Poison clock/stack owner. No new damage engine or independent
status store was introduced. Its one-second ticks, proportional final remainder,
live victim protection/filtering and noCrit/noTrigger flags remain unchanged.
Projectile periodic application now selects its authored supported status kind
instead of hardcoding Burn. Existing Fire Bolt's native Projectile damage channel
is retained; a future Poison projectile uses the existing Poison channel.

### Retained snapshot and source-cap correction

The prior runtime retained a stronger damage snapshot on weaker refresh but used
the incoming application's source cap. With Concentrated Venom implemented, that
could turn a retained1.75× package into three empowered stacks simply by unlinking
the passive and refreshing it with a weaker plain cast. Source cap now travels
with the retained offensive package. A genuinely stronger replacement carries its
own cap; previously accrued damage remains attached to its original snapshot and
stack count. Equal/weaker refreshes do not mutate the retained package's cap.

This preserves the normative caster+skill+victim source key instead of using a
per-cast or per-plan key that could bypass the source cap by creating parallel
packages. Other skills, other casters and the victim-wide12-stack strongest-package
replacement rule retain their independent limits. Expired packages cannot lend
their strength/cap to a later cast. Existing logout/native rejection cleanup drops
the package; no state migration is needed for these unsaved finite effects.

STATUS_APPLIED/REJECTED now records requestedAddedStacks/requestedSourceCap and
coefficientPerSecond, plus retainedSourceStacks/retainedSourceCap/
retainedCoefficientPerSecond when a package exists. Those names deliberately
distinguish a weak refresh request from the authoritative stronger retained result.
Cast identifiers and subsequent native damage/Inspect traces remain unchanged.

Known executable profiles are refined using actual Burn/Poison components;
elemental damage alone is not an application. Canonical Venom Spray's positive
Virulence compatibility fixture is preserved, but it is still one of the missing
Stage04/05 profiles: this cohort does **not** claim to implement that skill by
passing a catalog test. Remaining skill coverage is still required before closure.

### Cohort I local gate

26 new tests cover positive/two-negative fixtures, independent hit/DoT scaling,
duration composition, source/victim caps, Virulence cap clamping, accrued snapshot
transitions, weaker refresh/unlink safety, unrelated source isolation, expiry,
native-port rejection, no replay, and a shared Fire Bolt commit retaining its one
payment while the kernel calculates non-critical Burn independently of the hit.
These are backend and adapter-shaped fixtures, not connected Burn/Poison proof.

`clean build`: **887 tests PASS**, zero failures/errors/skips. Normal isolated
three-mod network boot/clean exit0 and packaged CustomUI9 validation PASS.
Artifact: `evidence/stage-11/cohort-i/artifacts/HytaleRPG-0.0.23.jar`.
SHA-256: `417D1F13F829A61F6A2B158C127C1DEE744A9EF8BB2468387EE1127E77C6C60B`.
Rollback: H, SHA-256
`2BAFBF70462776033959FF8F6AFF6B6CA43176EA93F0156C7FD8A9338C9C197A`.
No live deployment, save migration, art, native ability or HUD changes. Local gate
PASS; connected gates UNVERIFIED. Nineteen of40 Stage11 primitives now have local
evidence;21 remain before matrix/hardening closure.

## Cohort J — Deep Freeze and shared Chill application

Baseline I `b25cb73`. Master LP044, the five-stack Chill threshold and Frozen
immunity rules read in full. R030/0.0.23, plan schema19, player schema6 unchanged.
One passive. Deep Freeze adds one Chill stack after a successful authored Chill
application, with a shared caster/root/victim one-second bonus cooldown. Derived
projectiles, area pulses and Aura pulses cannot each claim a separate bonus for
the same root and victim during that second. It reduces direct hit coefficients
by10%; periodic Aura damage and the authored Chill count remain unchanged. The
typed control modifier participates in compiled identity and profile-cache keys.

Known executable profiles must contain actual Chill payloads: Cold damage alone
does not qualify. Frost projectile, Cold Chill areas, Chill Aura and alternating
ice/stone Avalanche are covered. Unknown Stage04/05 profiles retain their catalog
compatibility fixtures without being claimed as executable implementations.

StatusService now exposes one source-owned batch application used by all three
native delivery adapters. Baseline stacks use the existing status authority,
then the optional bonus uses that same authority. The batch ends when Frozen is
created or baseline application is rejected. This also fixes a safety defect in
the prior area loop: another authored stack could otherwise be left behind
immediately after crossing the Frozen threshold. Already-Frozen victims reject
new Chill instead of refreshing Frozen. The five-stack threshold, normal2s Frozen,
following3s immunity, elite duration scaling and boss Slow substitution are not
rebalanced. Boss Slow is not reported as Frozen. Rapid Pulse's fractional stack
ledger still determines whether an actual application opportunity exists before
Deep Freeze is considered.

The bonus ledger is bounded to4096 global/256 per caster entries, expires after1s
and is removed on owner teardown or victim removal. Saturation rejects only the
extra stack, not the already-accepted base application. The source key retains
root identity; it is not reset by a projectile child or a different hit index.
STATUS_REQUEST includes authoredStacks, deepFreeze and bonusGate; result events
come only from the real kernel result, never invented native execution evidence.

### Native projection boundary correction

Code inspection found that projectile Chill called the RPG status service without
the AreaStatusProjection marker/synchronization used by area and Aura Chill. The
projectile adapter now requires the installed effect controller, uses the shared
application, attaches the existing marker and invokes the same native projection.
It also uses the existing audited target-control profile instead of discarding
elite classification. Missing native projection capability rejects with
PROJECTILE_NATIVE_STATUS_ADAPTER_UNAVAILABLE. Other projectile statuses are not
rewritten by this cohort.

The existing5Hz projection and RPG_Frozen/Chill effect assets remain authoritative
for native effect writes. Frozen supplies the actual remaining duration to the
installed effect controller rather than relying on the asset's default duration.
The packaged call sites and installed EffectControllerComponent bytecode are
captured under `evidence/stage-11/cohort-j/api`; this proves API/call-site structure
only. It does not prove native movement suppression, client animation or casting
interruption. Those connected gates remain UNVERIFIED.

### Cohort J local gate

23 new tests cover component compatibility, coefficient isolation, immutable
profiles/cache identity, shared root cooldowns, exact cooldown boundary, victim
and caster isolation, atomic Frozen threshold, immunity, boss/elite policies,
Rapid Pulse composition, memory bounds, expiry, teardown and malformed inputs.
`clean build`: **910 tests PASS**, zero failures/errors/skips. Normal isolated
three-mod network boot/clean exit0 and packaged CustomUI9 validation PASS.
Artifact: `evidence/stage-11/cohort-j/artifacts/HytaleRPG-0.0.23.jar`.
SHA-256: `93252D1109F75C49B3C7C83AFD7A4AD8499258D6F4A55B7E2381BB46DB1460AC`.
Rollback: I, SHA-256
`417D1F13F829A61F6A2B158C127C1DEE744A9EF8BB2468387EE1127E77C6C60B`.
Player schema6 remains unchanged; rollback to I uses the same schema. No live
deployment, art, native ability or HUD changes. Local gate PASS, connected gates
UNVERIFIED. Twenty of40 Stage11 primitives have local evidence;20 remain before
matrix/hardening closure. All output remains in the GitHub checkout, not Drive.

## Cohort K — Lifeblood and Attunement

Baseline J `42a853b`. Master LP061/LP062, resource rounding, nonlethal Health
exception, snapshot and rollback contracts read before implementation. Installed
EntityStatMap/EntityStatValue/DefaultEntityStatTypes APIs audited; their packaged
call sites and bytecode are retained in `evidence/stage-11/cohort-k/api`.
R030/0.0.23, plan schema20, player schema6 unchanged. Two passives.

Lifeblood converts only a legal finite upfront Mana/Stamina cost into Health.
Cost factors retain fractional precision until the final integer boundary:
ceil(baseCost × ordinary cost factors × (1−0.03×Attunement stacks) ×1.50).
Thus Quick Slash5 becomes8Health; with Efficiency,5×0.85×1.50=6.375 becomes7.
There is no intermediate Mana/Stamina rounding followed by a second Health round.
The named exception adds0.10 Increased damage/heal/barrier magnitude through the
existing kernel snapshot; it does not change the authored resource declaration,
upkeep, regeneration, reservations, attributes or cooldown formula.

The resource transaction now supports an explicit HEALTH type, but regeneration
still accepts only Mana/Stamina, and Health upkeep is rejected. The development
resource command explicitly retains its Mana/Stamina-only scope. Affordability
accounts for concurrent pending costs and requires the final Health to remain at
least1, both before reservation and immediately before payment. Equality with the
cost, non-finite Health and fractional remnants below1 fail without dispatch.
Native writes use DefaultEntityStatTypes.getHealth/EntityStatMap.setStatValue,
not DamageSystems: a self-cost cannot become damage, recovery or reflection credit.
The float narrowing guard rejects an attempted subtraction that would round back
to unchanged native Health. No new Health pool or HUD presentation exists.

A cooldown persistence failure before dispatch refunds the Health transaction.
After entering a Lifeblood executor, a later adapter exception retains cost and
cooldown: it cannot establish that no effect or native hit already happened, and
refunding could grant free Health-funded damage. This conservative branch is
specific to the new Health exception; it does not rewrite the retained ordinary
Stage04/05 cancellation mechanics. Lifeblood+Leeching and nonmanual Retaliation
Health spending are explicitly incompatible. The runtime independently rejects
nonmanual Lifeblood activation. Failed graph links retain the existing graph and
the owner's passive copy.

Attunement is an ephemeral per-actor/skill-slot ledger, capped at4096 entries.
The first manual commit uses zero stacks then installs one. Later commits use the
existing count, add0.03 Increased magnitude per stack and one additive0.03 cost
reduction per stack (not repeated0.97 multiplication), then increment up to five.
Stacks expire exactly4s after that skill's last successful commit. Cooldown,
resource and cancelled windup failures do not extend the deadline. Misses do
count because this passive's contract is successful commit, not successful hit.
Windup completion re-evaluates stack expiry and price before payment. Derived
Echo/Barrage/other snapshot copies inherit their parent's benefit without another
payment or stack. A typed MANUAL/TRIGGERED request origin prevents string-based
guessing about trigger eligibility.

A saved loadout mutation now has a separate notification from generic progression
mutations. Changes to equipped slots, joints or graph clear the owner's stacks;
attribute/XP saves and using another equipped skill do not. Failed persistence
does not publish the change. Ordinary damage-interrupted windup cancellation
retains previously earned stacks, while terminal native owner cleanup removes
them. Transaction rollback cannot erase a later commit or resurrect state already
cleared by a loadout change. The ledger is not persisted or credited offline.

### Cohort K local gate

31 new tests cover positive/two-negative fixtures, factor order, Health edge cases,
native float narrowing arithmetic, pending payment conservation, cooldown-save
rollback, post-dispatch uncertainty, graph rollback, explicit trigger origin,
Attunement expiry/cap/identity, windup timing, exact loadout notifications and Echo
snapshot inheritance. Initial compile errors were fixture API naming mistakes;
the first running suite also used a Sword where Heavy Swing requires Longsword,
and incorrectly tried Echo on Quick Slash. Fixtures were corrected to the real
contracts, with Fire Bolt as the eligible Echo pilot; production gates were not
relaxed. Fixture cooldown clearing enables rapid repeated commits and is not
evidence of normal connected cooldown timing.

`clean build`: **941 tests PASS**, zero failures/errors/skips. Normal isolated
three-mod network boot/clean exit0; packaged CustomUI9 validation PASS.
Artifact: `evidence/stage-11/cohort-k/artifacts/HytaleRPG-0.0.23.jar`.
SHA-256: `6A7D067D901DFD4A149CD9A6AE718AF1CDF8C5B067B9B7A4F4E81D2FCE2F9C8B`.
Rollback: J, SHA-256
`93252D1109F75C49B3C7C83AFD7A4AD8499258D6F4A55B7E2381BB46DB1460AC`.
No live deployment, save migration, artwork, native ability projection or HUD
changes. Connected Health payment/casting/animation gates remain UNVERIFIED.
Twenty-two of40 Stage11 primitives have local evidence;18 remain before closure.

## Cohort L — Leeching from observed native Health loss

Baseline K `5607912`. Master LP063 and resource/periodic/secondary safety contracts
read before implementation. Installed ApplyDamage, the existing real
HytaleDamageAdapter return boundary, resource float representation and separate
reflection path audited. R030/0.0.23, plan schema21, player schema6 unchanged.
One passive; no new native damage engine.

The normal skill damage port invokes Leeching only after HytaleDamageAdapter's
DamageSystems.executeDamage dispatch returns. Input is that call's observed
Health-before/Health-after and cancellation result, not the RPG calculation or
native damage amount. Hostility and target protection are checked before damage,
so a victim dying during the hit does not erase its previously validated hostile
identity. Loss is capped to nonnegative starting Health to exclude overkill.
Zero loss, shield-only absorption, cancellation, missing/non-finite Health,
friendly damage and reflected/redirected receipts provide no recovery.

Return is3% of eligible Health loss to the skill's authored Mana or Stamina type.
Each successful commit initializes one budget with8% of that resource's current
spendable maximum. Mana reservations are excluded. Descendant contexts share the
same object through Echo, Barrage and every snapshot replacement, including
projectile continuations and retained periodic snapshots. It is not copied or
reset per target, hit, tick, child instance or refreshed DoT. A later maximum
increase cannot expand the committed root cap; a decrease constrains future
returns. Different genuine commits have independent budgets.

The root ledger contains only identity, a resource enum and bounded scalar state;
it retains no native entity/resource adapter and no ever-growing hit-ID set. Its
lifetime follows the existing context/effect ownership. A one-use observation
receipt prevents duplicate consumption of the same dispatch result without
storing an unbounded history for an indefinite Aura. It is an internal server
receipt, not an endpoint accepting client-supplied damage claims. Full resource
does not bank damage for later; a new eligible hit is required after depletion.
Only actual credited resource consumes the cap when native completion is known.

The native Mana/Stamina credit adapter rounds the destination float downward,
never above the permitted increase or spendable cap. Tiny unrepresentable credits
return zero, rather than rounding upward into a cap bypass. It reads back native
current value. A failed or uncertain write consumes its attempted allowance and
disables further recovery for that root; it is never retried or refunded into
the budget. A native resource read failure is contained after the already-applied
damage, so a recovery adapter failure cannot throw through and replay the hit.

The existing reflection/redirect adapter does not enter this Leeching call site.
Periodic damage is allowed despite noProc, because proc recursion and Leeching
eligibility are distinct contracts. Lifeblood remains incompatible in either
link order. Health costs never create a damage receipt. RESOURCE_RECOVERY records
source=LEECHING, resource, gate, actualHealthLoss, requested, actualRestored,
totalRootRestored, rootCap, effectInstanceId and targetId, with the original cast
trace identities. Failed writes are not falsely reported as successful recovery.

### Cohort L local gate

26 new tests cover component/resource gates, actual-vs-overkill Health loss,
cancellation/friendly/reflection exclusions, exact shared caps, reservation-aware
maxima, capacity changes, full/partial pools, receipt deduplication, uncertain
writes, native float bounds, real shared commit initialization, derived-context
identity and Lifeblood conflicts. These are deterministic backend and adapter-
shaped fixtures, not connected damage or recovery evidence.

`clean build`: **967 tests PASS**, zero failures/errors/skips. Normal isolated
three-mod network boot/clean exit0; packaged CustomUI9 validation PASS.
Packaged native call sites and installed ApplyDamage bytecode are retained under
`evidence/stage-11/cohort-l/api` as structural evidence only.
Artifact: `evidence/stage-11/cohort-l/artifacts/HytaleRPG-0.0.23.jar`.
SHA-256: `B520F218707079CA855ECF6CDD7341CF333441C237DAD68179B979837A41F129`.
Rollback: K, SHA-256
`6A7D067D901DFD4A149CD9A6AE718AF1CDF8C5B067B9B7A4F4E81D2FCE2F9C8B`.
No live deployment, save migration, native input/ability projection, HUD or art
changes. Connected gates remain UNVERIFIED. Twenty-three of40 Stage11 primitives
have local evidence;17 remain before matrix/hardening closure.

## Cohort M — Multistrike and Ruthless

Baseline L `dbc4bea`; master LP024/025 and installed EntityEffect/ApplicationEffects/
EffectController/AbilityEffects implementation inspected before native wiring.
R030/0.0.23, compiled-plan schema22, unchanged player schema6. Two passives;
no new combat engine and no changes to canonical Stage04/05 skill assets.

Multistrike refines compatibility to an independent damaging discrete Strike,
excluding Reaction/Movement secondary strikes and authored multihit sequences
(including the Dagger Flurry negative fixture). The effective profile preserves
the primary coefficient and sets three hits with .25s spacing. The existing
StrikeRepeatSchedule and world-tick dispatcher execute primary, .25s and .50s;
each repeat receives a distinct `/multistrike-N` skillInstance child under the
original rootCastId/correlationId and a .65 magnitude snapshot. Descendants share
the root Leeching cap and cannot schedule another Multistrike, Echo or Barrage.
Derived damage disables secondary procs. Payment and cooldown occur once at the
original commit; children bypass activation/payment rather than receiving free
synthetic activations. Hit ledgers are child-scoped and removed after each repeat.

Committed facing is retained, but each repeat queries from the owner's current
native position against current targets, range and LOS. Existing family selection
is reused. A world-tick gap exceeding one second cancels the finite sequence
instead of delivering a stale catch-up burst. Root lifecycle remains STRIKE_REPEAT
until completion, so other RPG casts cannot interleave. Native basic attacks also
need to be restricted: the new effect `RPG_Strike_Action_Lock` uses Hytale's existing
AbilityEffects.Disabled list for Primary, Secondary and Ability1–4. It changes no
movement, stats, HUD or key mapping and adds no damage. Explicit removal occurs
after the final repeat or local cancellation; missing repeat ownership clears it
on the next owner tick/rejoin. A finite five-second native expiry is crash/unload
fallback, **not** the intended .50s action duration. Disconnect without an available
world store cannot prove immediate removal; bounded expiry/rejoin cleanup and
connected input-lock behavior must be tested. Native Signature Move ownership and
Ability4 capability policy are not replaced by this temporary action restriction.

Ruthless uses a bounded4096-entry actor/slot cadence ledger. Successful manual
root commits advance modulo3, including misses; attempts rejected before commit
do not. The third commit adds .60 to the existing Increased damage bucket and
multiplies an existing authored Stagger duration by1.5 before control caps. It
does not invent Stagger on Heavy Swing's current empty status payload. Derived
repeats inherit the empowered snapshot without consuming another cadence count;
triggered roots neither advance nor consume the manual third-use bonus. Counter
state has no time expiry, but actual loadout changes and owner teardown clear it.
Attribute-only edits and failed persistence mutations do not clear it. Existing
pre-dispatch rollback and ordinary-resource refunded dispatch paths reverse the
cadence transaction; paid Lifeblood/uncertain Health dispatch retains the committed
use. Identity-checked rollback cannot overwrite a later commit or resurrect a
cleared owner. This is ephemeral runtime state, not an offline progression grant.

The shared native strike call previously applied its status after a cancelled or
zero-Health-loss damage result. It now requires uncancelled actual Health loss
before status application, preserving native protection/cancellation at the
strike payload boundary. This correction applies to retained strikes as well;
it does not change their coefficient, geometry, resource costs or cooldowns.

### Cohort M local gate

30 new deterministic tests: compatibility/copy rejection, exact repeat timing,
root lifecycle lock, one cost and durable cooldown write, derived identities and
shared Leech cap, recursion exclusion, third-use damage/Stagger, Potency/Impact
composition, committed misses, triggered exclusions, interrupted windup, resource/
cooldown persistence failures, paid versus refunded dispatch, loadout/attribute/
disconnect semantics, and bounded ledger/rollback. The first focused build found
a test-only wrong hit-ledger class name; after fixing that, one test compared two
decreasing wall-clock cooldown readings. It was corrected to inspect durable
cooldown write count and non-increasing remaining time. No production timer was
disabled and no tolerance was used to hide duplicate payment.

`clean build`: **997 tests PASS**, zero failures/errors/skips. Normal isolated
three-mod network boot and clean exit0. The native asset decoder resolved the
action lock; startup audit verified exactly six disabled interaction types,
unchanged movement and no damage calculator. Packaged CustomUI9 validation PASS.
API bytecode and normal-server resolution evidence are under
`evidence/stage-11/cohort-m/api`; these do not prove client input suppression,
animations, native hit execution or repeat positioning.

Artifact: `evidence/stage-11/cohort-m/artifacts/HytaleRPG-0.0.23.jar`.
SHA-256: `DC931839E6A36129BFCD270F699B884FC7AE4EFE20D9F3049FFAD23044631977`.
Rollback L: `B520F218707079CA855ECF6CDD7341CF333441C237DAD68179B979837A41F129`.
No live deployment or save migration. All outputs remain in the GitHub repository;
owner art/lost-and-found files remain untouched. Connected gates are UNVERIFIED.
Twenty-five of40 Stage11 primitives have local evidence;15 remain, followed by
full compatibility-matrix and legacy-state hardening before Stage11 closure.

## Cohort N — Cleaving Edge and Phantom Reach

Baseline M `8ff6d73`. Master LP023/027, finite-root safety, installed native
WorldSupport attitude query, DamageSystems dispatch and existing spatial/LOS
adapters reviewed first. R030/0.0.23, compiled-plan schema23, player schema6.
Two passives, piloting an immediate nonrecursive strike-secondary primitive.

Both passives require a frontal damaging Strike rather than a radial, Movement
or Reaction-only component. Shield Bash is accepted; Ground Slam, projectile
skills and reaction-only strikes reject with a typed reason. Existing frontal
arcs remain eligible. Their original target list and full damage are not replaced
by an invented single-target attack. All original selected targets—not merely
the first—are excluded from each additional-target query. This preserves the
retained Quick Slash/Heavy Swing arc behavior while preventing a passive from
duplicating damage to an original target.

Cleaving Edge runs one80-degree/3m frontal query at the initial primary execution,
up to four additional targets at .60 snapshot magnitude. A committed primary miss
does not erase the cleave's independently valid arc. Geometry uses the same
authored-facing/committed-direction rule as the strike. Long Reach modifies its
explicit reach, and Concentration narrows its angle; baseline values remain80/3.
Targets are sorted by distance then stable identity, deduplicated, and reject
protected, dead, nonhostile or blocked candidates. Native queries reuse the
existing4096-NPC scan guard and256-candidate fail-closed bound. No native entity
reference is kept in the root ledger. Cleave performs ordinary direct damage
calculation from the inherited offensive snapshot at60% magnitude, with no
secondary proc or new status/controller invocation.

Phantom Reach requires a successful uncancelled, actual-Health-losing initial
primary contact. It selects one nearest additional hostile within3m of that
impact, including vertical distance, with LOS from impact and stable tie order.
Its direct packet is exactly .60 of that contact's resolved pre-mitigation amount;
it does not use Health loss as its coefficient, reroll crit, recalculate attributes,
or apply the offensive modifiers a second time. The same HytaleDamageAdapter /
DamageSystems path still owns native mitigation, filtering and actual Health.
No projectile is spawned and the compiled family remains Strike.

Each child has a distinct SkillInstance identity, retains rootCastId/correlationId,
and shares the committed Leeching cap. Both bypass activation/payment/cooldown and
mark canProc=false. Multistrike children cannot start them; neither can their own
secondary children. The two independently equipped passives may each affect the
same additional victim, under **different** admitted effect IDs; that is two
authored passive effects, not a replay of either packet. Each controller and each
effect can be admitted only once. No original primary target receives either
extra packet.

A root-owned finite effect ledger enforces48 total gameplay effects including the
primary,16 triggered secondaries and generation3, with a bounded16 controller-key
set. Multistrike repeats spend non-triggered effect entries; these strike
secondaries share its counters. Snapshot/Echo/Barrage copies preserve the same
object. Projectile carrier admission remains in its existing exclusive-family
registry; this cohort does not claim the later full cross-family matrix is done.
The new ledger stores bounded keys and counters only and follows context lifetime.
An uncertain secondary native write keeps its admission spent and cannot replay
the controller. The native wrapper contains that error rather than unwinding into
the legacy synchronous root-refund branch after the primary may already have hit.

The existing procedural presentation gateway receives the cleave's actual range/
angle and a short .25s Phantom impact-to-target connection. Presentation errors
cannot erase paid gameplay. These are reuse of the approved fallback templates,
not new HUD elements, art, colliders or damage authorities. Connected rendering,
animation suitability and client readability remain UNVERIFIED. Trace adds
STRIKE_SECONDARY_DISPATCH/RESOLVED/REJECTED with parent/effect identity, target,
actual Health loss, cancellation, noProc and whether offense was recalculated.
Already-resolved Phantom damage does not fabricate new calculation/crit traces.

### Cohort N local gate

30 new tests: positive/negative compatibility, immutable original profiles,
primary exclusion for single/multitarget strikes, exact cone and impact-centred
geometry, range/angle modifier composition, stable nearest ties, LOS/protection,
candidate overflow, per-controller dedup, exact resolved Phantom arithmetic,
cancelled/zero-loss exclusions, original misses, shared effect/Leech caps, distinct
child identities, recursion rejection, resource/cooldown conservation, uncertain
writes,48/16/3 bounds, and presentation failure isolation. The focused suites and
full retained suite passed; no connected evidence is inferred from these fixtures.

`clean build`: **1027 tests PASS**, zero failures/errors/skips. Normal isolated
three-mod network boot/clean exit0, native action-lock asset audit retained,
packaged CustomUI9 validation PASS. Bytecode/call-site evidence retained under
`evidence/stage-11/cohort-n/api`.
Artifact: `evidence/stage-11/cohort-n/artifacts/HytaleRPG-0.0.23.jar`.
SHA-256: `BB39ABDAC3C80139F85134D12D7B3CD6E09678DB7B06D3B4D6B22F0A4221CA44`.
Rollback M: `DC931839E6A36129BFCD270F699B884FC7AE4EFE20D9F3049FFAD23044631977`.
No live deployment, save migration, native HUD/input/ability projection or owner
art changes. Twenty-seven of40 Stage11 primitives now have local evidence;
13 remain before matrix/legacy-state closure. Connected gates remain UNVERIFIED.

## Cohort O — Shockwave and introduced-component scoping

Baseline N `97cda1e`. Master LP026, geometry defaults and component modifier contract
reviewed; installed BoundingBox, native attitude, collision/LOS and damage adapter
boundaries audited. R030/0.0.23, compiled-plan schema24, unchanged player schema6.
One passive, extending the immediate strike-secondary primitive from cohort N.

Shockwave triggers at the first successful uncancelled actual-Health-losing root
Strike contact, once per root. A missed first authored hit can qualify on a later
authored hit. Multistrike/other derived children cannot qualify. It creates one
3m PHYSICAL radial Burst at40% of the contact's resolved pre-mitigation amount.
No additional critical roll, attribute calculation, resource cost, cooldown,
status application or proc controller is executed. Native filtering/mitigation
and authoritative Health loss still go through HytaleDamageAdapter. The primary
victim may receive the authored burst as well; unlike Cleaving Edge/Phantom Reach,
the Shockwave contract does not exclude it. A victim is hit at most once within
that burst. One burst consumes one root effect and one triggered-secondary
admission, not one admission per radial target; at most64 valid targets resolve
under the retained256-candidate/4096-NPC spatial guards. The cylinder height is3m.
No terrain operation, projectile carrier or retained field is created.

Shockwave's Burst/Area/Damage/HasRadius capabilities belong to its introduced
component. The original compiled family remains Strike and does not gain global
Burst/HasRadius tags. Expanded Radius can therefore be linked after Shockwave
without changing the parent strike's reach or damage: only the burst radius*1.25
and its magnitude*.90 apply when the parent had no radius. Removing Shockwave
while that dependent modifier remains linked rejects/rolls back rather than
leaving a silently inactive graph.

Concentration is handled similarly. When the original skill already has an area
component, its ordinary Increased bucket/footprint behavior remains and the burst
inherits that resolved amount once. When only Shockwave or Cleaving Edge introduces
an eligible area, compiler output marks CONCENTRATION_SCOPE=SECONDARY_ONLY: parent
snapshot damage and parent geometry remain unchanged. The cleave child adds .30
to its own Increased bucket and narrows its angle. The Shockwave child adds the
equivalent .30 Increased contribution to its already-resolved source amount,
then applies the40% factor; its radius*.70. Phantom Reach's non-area direct copy
does not gain that secondary-area bonus.

To preserve additive arithmetic without rerolling/recalculating offense, the
existing damage result now exposes the value of +1 Increased for that contact:
raw skill damage × its already-selected critical multiplier × its More/Less
factors. Native strike outcomes carry that value beside the resolved pre-mitigation
amount. Conditional victim Increased already resolved during native Gather remains
in the source amount. Example: a100-unit raw hit with Potency gives115; an exclusively
secondary Concentration yields(115+30)*.40=58, **not**115*1.30*.40. Missing/nonfinite
additive data or overflowing secondary damage rejects rather than guessing.
The kernel's base resource, cooldown, attribute and damage formulas are unchanged.

### Collision-bounds correction to cohort N

Review against the normative geometry contract found that N inherited the retained
strike selector's point-centre convention. This was structurally testable but did
not meet the master requirement to use target collision bounds. The new secondary
port supplies translated native BoundingBox bounds. Cleaving Edge now tests its
actual sector against those bounds with the2.5m humanoid height; Phantom Reach
uses impact-to-bounds distance and stable nearest ties. Shockwave intersects the
3m-high cylinder with bounds. Large valid entities are not missed merely because
their centre is outside the radius. The existing primary Stage04 selector is not
silently changed in this cohort; its independent geometry hardening remains an
explicit later release-candidate audit item. N's archive is retained unchanged.

Procedural fallback outlines receive the same resolved geometry and .25s cosmetic
lifetime. AREA_PRESENTATION records dimensions and connectedProof=false. They are
not gameplay colliders or proof of a rendered client result. New secondary writes
retain the same once-only admission and paid-root error containment as N. Actual
native damage, attack animation, LOS scenarios and presentation remain connected
QA requirements.

### Cohort O local gate

25 new tests cover positive/negative gates, component-local capabilities,
parent noninterference, dependent-link rollback,40% resolved arithmetic,
Potency/Concentration additive composition with crit/More/Less, conditional
source amounts, shared cost/cooldown/Leech ownership, first-success semantics,
derived-proc exclusion, primary-in-burst semantics, stable collision-bound target
selection, cylinder height,64-target/256-query limits, shared admission limits,
uncertain-write dedup and missing-value rejection. The first test compile used a
node ID where unlink requires an EdgeId; the fixture was corrected to resolve the
existing persisted edge. No production compatibility or safety gate was weakened.

`clean build`: **1052 tests PASS**, zero failures/errors/skips. Normal isolated
three-mod network boot/clean exit0, retained native asset/root audits and packaged
CustomUI9 validation PASS. API/call-site evidence under
`evidence/stage-11/cohort-o/api` is structural, not connected execution proof.
Artifact: `evidence/stage-11/cohort-o/artifacts/HytaleRPG-0.0.23.jar`.
SHA-256: `0B5F56C8B256ED5D49B99C64E7F159EB3D5C4DF0894506F82E41143933671642`.
Rollback N: `BB39ABDAC3C80139F85134D12D7B3CD6E09678DB7B06D3B4D6B22F0A4221CA44`.
No live deployment, save migration, HUD/input/ability projection or owner art
changes. Twenty-eight of40 Stage11 primitives have local evidence;12 remain,
followed by matrix/legacy-state closure. Connected gates remain UNVERIFIED.

## Cohort P — Vacuum / Repulsion (R030, plan schema25)

LP037/038 were reread against the supplied master. Vacuum requests2m toward the
resolving area's centre; Repulsion requests2.5m away. The planner is horizontal,
clamps a pull before it crosses the centre, and does not invent a push direction
for an exactly centred target. Native control policy scales elite displacement
and rejects immunity/protection. Existing Impact Force, where independently
compatible, scales the new push by1.75, not the pull. No velocity unit is guessed
to mean metres and no damage event is generated just to move an entity.

The new native helper reads fresh NPC Role, Transform and BoundingBox on the
owning world thread. It requires a hostile, live, unprotected NPC, positive native
knockback support, ground-category role and physical support under its feet.
Movement is planned in at-most.25m swept segments; every prospective endpoint
must still have ground support. Invalid collision fractions fail closed. At most32
segments are inspected; the largest current request4.375m needs18. The final
validated Transform position is written once. This is a structural native adapter,
**not** proof of client motion, animation, navmesh behaviour or replication.
Connected tests must include slopes, ledges, walls, large NPCs and immune roles.

Each committed root now owns one bounded rolling displacement ledger, shared by
Echo, Multistrike and secondary contexts. A target receives at most one attempted
passive position write per root per1s. Rejected/uncertain native writes consume the
attempt, avoiding retries after an ambiguous mutation. The ledger holds at most256
target/time pairs and prunes expired entries; reversed/invalid clocks cannot reset
the allowance. Indefinite Aura control also consumes its existing8-per-second
secondary allowance. New helper exceptions are recorded and contained: an already
resolved damage hit never becomes free because optional position control failed.

Integration covers existing area impacts/pulses, eligible frontal/radial/landing
strike components, resolving Line/Beam/Orb/Orbit components, hostile damage/Chill
Auras, corpse-area bursts, Cleaving Edge, Shockwave and Shrapnel. Connection runtime
now passes its actual resolved shape centre into the native port; a target-locked
Tether/Drain/Chain does not gain area-control semantics. Cleave passes caster
origin; Shockwave/Shrapnel pass their impact centre. Position is applied only after
an uncancelled native damage/application path; it is not repeated on DoT ticks.
The old authored area push/pull paths are retained separately; this cohort's new
support-path checks do not retroactively prove all older displacement behaviour.

Compatibility uses implemented hostile resolving components, not an imported
blanket capability tag. Frost Nova/Ground Slam are positive fixtures; Snipe,
single-target heals, root-locked connections and support-only Auras reject.
Vacuum/Repulsion conflict in one compiler stacking group. For a single-target
parent with introduced Cleave/Shockwave/Shrapnel, POSITION_SCOPE=SECONDARY_ONLY
keeps the parent's damage and geometry unchanged. The eligible area alone receives
the.90 damage factor. Otherwise the root's inherited damage snapshot receives the
factor once. Resolved Shockwave/Phantom arithmetic is not double-penalized;
non-area Phantom does not gain the introduced-area movement. Costs/cooldowns and
all native resource/HUD ownership remain unchanged. Dependent-component unlink
rejects and rolls back instead of leaving an inert position passive.

The full introduced-capability combination audit remains part of Stage11 closure:
for example, Impact Force currently still requires its own authored impact gate;
Repulsion alone does not yet satisfy that gate. This is a specific rejected pair,
not a claim that every remaining passive combination is already implemented.

### Cohort P validation and rollback

29 new tests cover geometry/control/collision/support bounds, exact-second ICD,
rolling capacity, identity/clock rejection, conflict/link rollback, component
gates, scoped damage, centre propagation, shared root identity and unchanged
single resource/cooldown commitment. An initial compile missed a Java import;
one test used nonexistent arcane_tether instead of canonical root_lash. Both were
corrected before the complete run; no safety/compatibility gate was weakened.

`clean build`: **1081 tests PASS**, no failures/errors/skips. Normal isolated
three-mod network boot and clean exit0 PASS; packaged CustomUI9 validation and
retained native asset tests PASS. Bytecode/call-site evidence is saved under
`evidence/stage-11/cohort-p/api`; connected execution remains UNVERIFIED.
Artifact: `evidence/stage-11/cohort-p/artifacts/HytaleRPG-0.0.23.jar`.
SHA-256: `20B6636A05D63C1B03CAB9ACE36A219A28950B406C618365247F2F6F818B58DD`.
Rollback O: `0B5F56C8B256ED5D49B99C64E7F159EB3D5C4DF0894506F82E41143933671642`.
Player schema6 unchanged. No live deployment, native input/HUD, art or Google Drive
writes. Thirty of40 Stage11 primitives now have local cohort evidence;10 and the
cross-catalog/legacy-state gate remain. Continue automatically under owner authority.

## Cohort Q — Cascade (R030, plan schema26)

LP035 now uses the existing finite AreaRuntime, not another executor or paid cast.
The primary is unchanged. One root controller attempts two child fields, offset
along committed aim-right by ±1.2 times the canonical unmodified radius. Each child
uses.60 of the parent's resolved radius, including its sub-impact/core radii, and
inherits.45 of its magnitude snapshot with unchanged authored duration/cadence.
Expanded Radius/Concentration affect the footprint, not the base offset. Native
prepareImpact resolves each candidate onto legal terrain with the existing ground
collision and parent-to-child LOS check. A rejected child records its boundary;
no resource/cooldown is refunded and no primary hit is replayed. Missing terrain,
adapter failure and field capacity are independently bounded per child.

Ground-targeted radial profiles are positive targets, including Blizzard and
overhead bombardments whose imported summary tags omitted AREA_OF_EFFECT. This is
a local compatibility assessment, not a global family/capability conversion.
Caster-only bursts, nonradial walls, traps, corpse consumers, collision cages,
projectile carriers and Auras reject with a typed ground-radial-component reason.
Children cannot Cascade/Echo or invoke secondary strike controllers. Echo of the
primary adds only its own field. Native derived area damage is marked canProc=false.
Root, correlation, Leech and displacement ledgers remain shared. Status application
timers now also share the root where Cascade is active; overlapping areas cannot
bypass an authored status/target interval. Status duration and integer-stack rules
remain inherited from the original authored status service.

Mobile Domain composition keeps the two committed lateral offsets while following
the current owner position. No swept damage trail is created. All child fields
share the existing8/caster and128/global field authority and clean up on expiry or
owner cancellation. Losing a child does not terminate a valid sibling.

### Important finite-budget interaction

Three Blizzard containers plus their16 impacts each total51 gameplay effects.
The master cap remains48. For Cascade roots, field containers are admitted first,
then each stratified impact is admitted against the shared root budget as its
warning is prepared. Both child fields therefore exist; later sub-impacts beyond
the cap are explicitly skipped with ROOT_SPAWN_EFFECT_BUDGET. This preserves the
hard safety gate rather than claiming all48 impacts plus3 containers fit it.
The unchanged non-Cascade conservative pre-reservation path retains its existing
regression coverage. A Cascade child consumes one of16 triggered-secondary slots;
its authored sub-impacts consume spawn slots, not new replay controllers. The
generation limit stays3. Lingering/Echo compositions can reach these limits sooner.

### Cohort Q local evidence

21 new tests cover exact offsets/radii/magnitude, immutable authoring, native-port
terrain failure, cost conservation,8-field and16-secondary admission,48-effect
clamping, cleanup, status-overlap intervals, mobile offsets, delay/echo composition,
derived-controller rejection and independent child target footprints.
`clean build`: **1102 tests PASS**, zero failures/errors/skips. Normal isolated
three-mod network boot/clean exit0 and packaged CustomUI9 audit PASS. Bytecode
evidence under `evidence/stage-11/cohort-q/api` verifies call structure only.
No connected terrain, damage, timing, replication or VFX proof is claimed.

Artifact: `evidence/stage-11/cohort-q/artifacts/HytaleRPG-0.0.23.jar`.
SHA-256: `95E2363F76FC71455FCCF0714EDE0ACFEA041A1F274AA5CF76CC99D0FDA51C81`.
Rollback P: `20B6636A05D63C1B03CAB9ACE36A219A28950B406C618365247F2F6F818B58DD`.
Player schema6, native HUD/input, existing art and live three-mod installation
remain unchanged. All files are in the GitHub workspace, none on Google Drive.
Thirty-one of40 Stage11 primitives have local cohort evidence; nine remain before
the final compatibility/legacy-state gate. Connected gates remain UNVERIFIED.

## Cohort R — Aftermath (R030, plan schema27)

LP042 is implemented through the existing AreaRuntime and ConnectionRuntime expiry
paths. On normal primary expiry, one shared root controller creates one child with
.60 horizontal geometry,.40 resolved parent duration and.50 inherited magnitude.
The child's profile is an immutable derived copy, not another Link compilation or
another cost/cooldown/executor commit. Original authored profiles remain unchanged.
Physical height, cadence, targeting safety and status-duration rules are retained.
Sub-impact counts are truncated to scheduled instants inside the new lifetime;
integrated areas suppress a partial final pulse. No extra catch-up pulse is added.

Eligible components are finite persistent areas, noncolliding damage walls,
untriggered finite traps, travelling pulse Orbs and native Orbit fields. A single
impact's warning or applied DoT does not turn it into a persistent spatial field.
Channels, Auras, recipient buffs, projectiles still in flight, collision cages and
ordinary single bursts reject. Trap detonation is not expiry: it creates no child.
A trap that expires unused leaves one smaller finite trap; that trap's later
expiry cannot repeat Aftermath. Forced cancellation, owner/world loss, simulation
gaps, candidate failures and any derived child's termination create no Aftermath.

Areas release the expired lease before admitting the child, revalidate terrain,
and reuse the same bounded field registry. This allows replacement at the8-field
cap without borrowing a ninth slot. The shared finite effect/secondary limits
remain authoritative. Root status/displacement/Leech limits and correlation are
preserved across the transition. Existing Cascade children and Echo instances
cannot independently create more Aftermath children. A Mobile Domain child retains
its finite caster attachment and then expires; no Aura/reservation is introduced.

For Ball Lightning, the child keeps the existing pulse/LOS/collision-bounds port
but is stopped at the parent's final position: Aftermath does not grant another
flight/reach segment. Its two-second child keeps the.75s interval, so exactly two
complete pulses are possible. For Orbiting Shadow Blades, the same swept blade
geometry remains: orbit/contact radius*.60, four-second duration, unchanged blade
count, angular speed and shared.75s victim contact interval. It is not converted
into a broad all-target disc. The usual owning-world validation continues on every
tick. Native derived connection/area hits cannot restart generic hit procs.

### Failures found and correction rationale

The first focused run found that8+3.2 can compare below the nominal expiry due to
binary floating-point subtraction. Periodic area expiry now uses the existing
nanosecond-scale timing tolerance; it does not add a partial damage pulse. Two
expiry fixtures caught this real boundary. A separate fixture incorrectly counted
the channel-only COOLDOWN_STARTED trace on an upfront Orb; it now checks the one
SKILL_COMMITTED event, one native-resource fixture write and actual active cooldown.
No fake trace was added to satisfy the test. Single-impact derived schedules also
retain their finite impact scheduler instead of accidentally entering instant-burst
dispatch. All original retained area/connection regressions pass.

### Cohort R evidence and rollback

25 new tests cover expiry versus cancellation, every eligible canonical derived
profile, finite geometry/magnitude/cadence, no partial tail, immutability, root
identity, budget/terrain failures, full-capacity replacement, Cascade/echo recursion
exclusion, trap expiry, Mobile Domain, Orb final-position ownership, Orbit blade
semantics, unchanged payment/cooldown and cleanup. `clean build`: **1127 tests PASS**,
zero failures/errors/skips. Normal isolated three-mod network boot/clean exit0,
retained native asset checks and packaged CustomUI9 validation PASS. API evidence
in `evidence/stage-11/cohort-r/api` remains structural, not connected execution.

Artifact: `evidence/stage-11/cohort-r/artifacts/HytaleRPG-0.0.23.jar`.
SHA-256: `0C7DA5E5D7DC652A442A2DCB249150E64ACD9115E77440D0AF51C176AC92C6EC`.
Rollback Q: `95E2363F76FC71455FCCF0714EDE0ACFEA041A1F274AA5CF76CC99D0FDA51C81`.
Player schema6 and native resource/HUD/input ownership unchanged. No art or live
mods changed; no Google Drive writes. Thirty-two of40 Stage11 primitives have local
cohort evidence; eight and compatibility/legacy-state closure remain. Connected
damage, expiry timing, collision, NPC state and presentation remain UNVERIFIED.

## R030 cohort S — Orbit conversion (LP039)

Local gate complete; connected gate UNVERIFIED. Compiled plan schema28 invalidates
older cached plans; player schema6 is unchanged. Orbit now transforms implemented
projectile/Orb payloads into the existing finite ConnectionRuntime ORBIT family.
The immutable conversion retains equipment, ammunition, damage and status metadata.
It does not spawn a native projectile carrier or duplicate native damage/resources.
Approved parameters are data in `rpg/runtime/orbit-conversion.json`:4 seconds,
70% magnitude,2.5m orbit radius,1.3m origin height,120 degrees/sec, at most3 orbs,
shared.75s victim contact interval. Contact geometry uses the original payload's
radius and existing swept capsule/AABB/LOS queries, not artwork or a broad disc.

One original payload makes one orb; Volley makes three at its existing magnitude
factor. Converted multi-orb input clamps to three (unconverted Shadow Blades stays
at its authored four). Echo/Barrage multiplicity is rejected at compile time if
the resulting count exceeds three. Separate converted instances share the original
root contact ledger, effect budget, Leech/displacement/status ownership and one
resource/cooldown commitment. Live converted-orb capacity also checks other casts;
a later pending child can be rejected if another cast uses capacity meanwhile.
Capacity is never expanded to rescue a paid repeat. Barrage now assigns distinct
converted instance IDs without changing existing projectile instance behavior.

Carrier continuations and unused flight-only modifiers reject after conversion,
independent of link order. Radius, Concentration, finite duration, Aftermath and
position modifiers assess the introduced spatial component; they do not quietly
stay attached to a removed flight component. Projectile direct-hit penalties are
retained in the converted connection coefficient. Burn/Poison/Chill and authored
knockback still use their existing native adapter after observed Health loss.
Ammunition is checked and consumed once through HytaleAmmoAdapter. No uncertain
late native exception refunds ammo after a field may already have hit.

Integration inspection found delayed converted releases were checking a stale
aim endpoint. Converted Orbit now revalidates the current caster origin while
retaining world/equipment/action-state checks. It owns space around the caster,
not the old aim point. This is a code-boundary correction, not connected proof.

27 new deterministic tests cover immutable conversion, exact configuration,
dispatch, contact cadence, geometry, duration, status metadata, removed capability
rejections, multiplicity, shared root identity/budgets, live caps, cancellation,
simulation gaps, movement, Aftermath and unchanged payment. Full `clean build`:
**1154 PASS**, no failures/errors/skips. Normal isolated three-mod network boot,
asset resolution, clean exit0 and nine packaged CustomUI documents pass. Structural
native call-site evidence is in `evidence/stage-11/cohort-s/api`; neither it nor the
fixtures establish connected collision, input, damage or rendering.

Artifact `evidence/stage-11/cohort-s/artifacts/HytaleRPG-0.0.23.jar`:
`771E6A1596D543F3642C6BD64D06593B79F169520B4C2859052CB59F47FD6CB6`.
Rollback R: `0C7DA5E5D7DC652A442A2DCB249150E64ACD9115E77440D0AF51C176AC92C6EC`.
All87 skills/66 passives retained; native HUD/input/XP and live three mods unchanged.
No owner art or Google Drive writes. Seven Stage11 passives plus matrix/legacy-state
closure remain; progression/hardening stages have not started.

## R030 cohort T — Hemorrhage, Terror, Shatter

LP045/048/049, master damage/proc coefficient rule and existing native damage,
status, periodic and Fear adapters were reviewed before implementation. Plan schema29;
player schema6 unchanged. These passives now consume completed native damage receipts
from the existing HytaleDamageAdapter path, not an activation request or guessed hit.
Receipts carry actual before/after/minimum Health, cancellation, hostile/protected
classification, resolved pre-mitigation magnitude and the component's damage channel.
Frozen is sampled before lethal damage. Player candidates never enter this adapter.

HitProcRuntime owns decisions only. A root admits at most256 unique proc contacts;
duplicate receipts cannot reroll. Successful proc attempts also claim the shared
48-effect/16-triggered-secondary budget. Child identity preserves rootCastId,
correlationId, actor, resource/Leech budgets and snapshot ownership, while receiving
a distinct skillInstanceId. Derived effects cannot recurse. Native secondary failure
is traced without refunding the already-applied parent hit, cost or cooldown.

Hemorrhage rolls25% times proc coefficient on a surviving target after a direct
Physical hit loses Health. It stores exactly12% of that hit's pre-mitigation amount
as resolved DPS for4 seconds. PeriodicStatusRuntime gained BLEED in its existing
bounded source-owned timer; one stack, strongest package retained on weaker refresh.
Each tick calls applyResolved with Physical damage and periodic/no-proc metadata:
no offensive stat, modifier, conditional or crit recalculation. Native filtering
still runs. The new RPG_Bleed_Visual uses the existing EntityEffect tint schema,
not a guessed particle ID; startup audits its resolved asset for no native damage,
movement or ability mutation. StatusService's Bleed projection is a read model,
not a second damage timer. Source ownership and teardown follow existing DoTs.

Terror rolls15% times proc coefficient, then attempts1.5s Fear through the existing
HytaleSupportSystem finite-effect/AI adapter. It retains target hostility, protection,
native ground-navigation requirements, elite duration reduction, rolling hard-control
resistance and the existing.25s direct-damage-break grace. Bosses/players are excluded.
The8s victim ICD is shared across the caster's skills and bounded at256 targets per
caster/4096 globally. An uncertain native failure retains that attempted ICD rather
than immediately rerolling. Owner teardown clears it. ECS marker writes use the
existing command buffer when available. No new Fear movement engine was introduced.

Shatter requires an actual killing Cold component and pre-hit Frozen, not the boss
Slow substitute. One killed victim admits one3m Cold explosion at50% of the killing
hit's resolved pre-mitigation amount. The existing bounded cylinder/AABB query,
hostility/protection and LOS checks select targets; overflow fails closed at256
candidates/64 accepted targets. applyResolved preserves native mitigation without
rerolling offense/crit. Shatter's child has no proc authority and cannot Shatter.
Primary Cold pulse kills may qualify independently of direct-hit chance procs.
Secondary Leech, when present, still uses the shared root actual-Health-loss budget.

Simultaneous Volley projectiles and Orbit orbs use1/count proc coefficient; sequential
contacts do not discount damage. Converted Orbit introduces a direct-contact component
for compatibility, not a global permission for pulse-only or support effects.
Focused tests exposed missing Physical/Cold restrictions in the imported gate parser;
explicit typed component checks now reject those invalid links. Two initial fixture
compile errors used incorrect windup helper names; corrected to the retained harness
API. No production gate or trace was weakened to satisfy a fixture.

38 new tests cover positive/negative compatibility, exact magnitudes/chances,
cancelled/shield-only/friendly/protected/player/periodic exclusions, deduplication,
root caps, child ownership, native failure retention, Terror ICD/resistance/teardown,
Frozen-kill distinctions, simultaneous-count weighting and existing Bleed/Fear timers.
Full `clean build`: **1192 PASS**, zero failures/errors/skips. Normal isolated three-mod
network boot/clean exit0 and resolved Bleed asset audit PASS. Packaged CustomUI9 and
all retained regressions pass. `evidence/stage-11/cohort-t/api` is structural evidence
only. Connected proc delivery, native Health changes, NPC retreat and tint/area
presentation remain UNVERIFIED; no claim that the owner can cast on this build yet.

Artifact `evidence/stage-11/cohort-t/artifacts/HytaleRPG-0.0.23.jar`:
`3CBB13F9A8FC79A5D2991AFD4757F18BA2688AD594B67B6440324BE6688C1154`.
Rollback S: `771E6A1596D543F3642C6BD64D06593B79F169520B4C2859052CB59F47FD6CB6`.
Native HUD/XP/input and the live three-mod set are unchanged. No owner art or Google
Drive writes.36/40 Stage11 primitives have local cohort evidence; Proliferation,
Retaliation, Critical Trigger, Kill Trigger and matrix/legacy-state closure remain.

## R030 cohort U — Proliferation

LP050 and the installed DeathSystems.OnDeathSystem implementation were reviewed.
Plan schema30; player schema6 unchanged. HytaleStatusDeathSystem now consumes actual
native NPC death-component additions with authoritative Health at/below minimum.
Ordinary removal only discards ownership; it cannot manufacture a death or spread.
The existing PeriodicStatusRuntime atomically exports/removes the dead victim's
unexpired source-owned Burn/Poison packages before callbacks. Bleed is not eligible.
ChillSourceRegistry tracks attributable contributions only, not another Slow/Health
store. It does not assign another caster's stacks to the linked skill. Reaching
Frozen clears transferable Chill provenance; Frozen itself is never a payload.

One strongest package per owner/linked skill/status selects at most three nearest
unique living hostile, unprotected NPCs within4m of the death point, with LOS and
native AABB distance. Queries fail closed above256 candidates; a death permits at
most32 source packages. Magnitude and remaining duration are multiplied by.70;
Chill stack count floors at70%, minimum1. Poison's source cap/count are retained,
without reapplying Virulence/Concentrated Venom. Burn/Poison use the existing periodic
service and frozen offensive snapshot; Chill uses the real StatusService with the
reduced remaining duration and no new Deep Freeze bonus. A recipient can naturally
reach Frozen from its own accumulated Chill, distinct from transferring Frozen.

Each child preserves rootCastId/correlationId, source skill/owner, snapshot and shared
budgets while receiving a distinct skillInstanceId. Derived packages cannot spread
again. Finite roots use their bounded contact and48-effect/16-secondary ledgers.
Aura sources instead use the existing eight-secondary epoch budget, with a bounded
4096-global/256-owner,120s death-deduplication window. This avoids incorrectly imposing
a finite lifetime cap on an indefinite Aura. World/owner loss, expiry and removal
discard provenance; failures never refund an already-applied parent effect.

34 deterministic tests cover ownership, strongest-package selection, exact scaling,
nearest-three/AABB/LOS rules, Frozen exclusion, duration, deduplication, recursion,
bounded failure, Aura epochs, teardown and native-service payload semantics. Full
clean build: **1226 PASS**, no failures/errors/skips. Normal isolated three-mod network
boot and clean exit0 PASS; nine packaged CustomUI documents and retained asset gates
PASS. Native hook registration/API inspection is recorded under
`evidence/stage-11/cohort-u/api`. These are structural/local proofs only. Connected
native death ordering, status transfer, visual effects and actual Health changes
remain UNVERIFIED.

Artifact `evidence/stage-11/cohort-u/artifacts/HytaleRPG-0.0.23.jar`:
`B160EC472928883C07AA8264E58929214C5BF946675F9FEF0E84A419C604741F`.
Rollback T: `3CBB13F9A8FC79A5D2991AFD4757F18BA2688AD594B67B6440324BE6688C1154`.
Native HUD/XP/input, live three mods, owner art and Google Drive remain untouched.
37/40 Stage11 primitives have local cohort evidence; the three trigger controllers
and component-matrix/legacy-state closure remain. No connected PASS is asserted.

## R030 cohort V — Critical Trigger and Kill Trigger

LP065/066, the master exclusive-controller/release contracts and existing native
receipt, geometry, projectile registry and release queue were reviewed. Plan schema31;
player schema6 unchanged. The imported EXCLUSIVE_REPEAT_CONTROLLER group already
rejects combinations with Echo/Barrage/Multistrike/Retaliation or the other trigger.
Typed runtime component checks additionally exclude support, movement, reaction,
channel, summon and consumer replays. Critical Trigger requires direct damage.

ConditionalRepeatRuntime consumes completed native Health-before/after receipts,
not requests or a speculative crit roll. It requires a root manual source, actual
hostile unprotected HP loss and the appropriate critical/lethal condition. Critical
Trigger requires direct/proc-enabled critical damage, queues once after.20s at50%
effect, and has a1.5s actor/skill ICD. Kill Trigger queues once at50% with a1s ICD;
an original source-owned periodic kill can qualify, but a derived kill cannot.
ICDs are bounded4096 globally/16 per owner and expire independently of loadout changes.
One root gets one opportunity: missing targets, budget/queue rejection or uncertain
native failure do not repeatedly retry the same opportunity. No failed child refunds
the already-paid primary or starts a second resource/cooldown transaction.

Children use SkillReleaseScheduler, its6-owner/256-global capacity, normal world tick
and existing family executors. They retain the paid root/correlation, frozen snapshot,
Leech and effect budgets, with a distinct instance and50% magnitude. Volley claims
all three derived launches. Skill Delay does not apply again. Fresh release validation
checks actor/world/equipment/target and the currently equipped plan hash. Successful
loadout mutation cancels queued conditional children with a trace; stale projectiles
cannot rearm a removed passive. Ordinary unrelated repeat reservations are retained.

Critical Trigger retains the original target or ground/aim point without retargeting.
Target death, changed ground, lost LOS/reach or equipment cancels release. Kill Trigger
is the explicit retargeting exception: bounded native NPC/AABB query, nearest valid
enemy within12m of the corpse AND legal caster reach, deterministic UUID ties, hostility,
protection and LOS. Ground-targeted skills resolve legal ground at the new target;
caster bursts/cones stay caster-centered. No eligible enemy means no spawn. Conditional
melee uses current actor origin with committed aim, not damage from the old position.
Connected targeting and geometry are still UNVERIFIED.

Focused tests exposed a real missing seam: immediate casts previously omitted target
capture unless another modifier needed a committed solution. Trigger-linked casts
now capture it before payment. A separate review found the projectile registry discarded
root accounting when the last carrier ended, before a delayed repeat. The existing
registry now retains its bounded lifetime accounting through the root context, not a
new combat scheduler. Conditional batches rejoin that same48-effect/16-secondary
accounting even after carrier cleanup; duplicate IDs, foreign roots and capacity
overflow fail atomically. It does not reserve live carrier slots for an untriggered
passive. Current capacity is checked when the actual conditional batch releases.

Native root-carrier classification excludes Fork/Splinterburst generations and Return
contacts from these root-only triggers. Shrapnel cannot trigger them. Status packages
from these secondary carriers retain derived provenance after the carrier disappears,
so a later Burn/Poison kill cannot masquerade as an original root kill. Existing damage,
cost, periodic strength and continuation formulas are unchanged. Trace canTrigger on
periodic damage now explicitly reflects the eligible root Kill Trigger path.

53 new tests cover exact timing/magnitude/payment, root/correlation identity, all
exclusions, original-target retention, nearest/legal-reach selection, LOS/protection,
ICDs, invalid targets, owner/loadout cancellation, source provenance, repeat groups,
Skill Delay, Volley, registry lifetime/duplicate/48-effect/16-secondary bounds and
native-dispatch failure retention. Initial compile missed two imports; fixed. The first
full suite caught the expected schema assertion still at30; updated to31. Final clean
build: **1279 PASS**, zero failures/errors/skips. Normal isolated three-mod network boot,
clean exit0, asset gates and nine packaged CustomUI documents PASS. Bytecode/call-site
audit under `evidence/stage-11/cohort-v/api` proves structure only, not native connected
input, Health loss, repeat delivery, timing, animation or rendering.

Artifact `evidence/stage-11/cohort-v/artifacts/HytaleRPG-0.0.23.jar`:
`03B66B087149C940579D7A29A95AB2DC344F394B9CE9B97A9047058050EAB0AB`.
Rollback U: `B160EC472928883C07AA8264E58929214C5BF946675F9FEF0E84A419C604741F`.
39/40 Stage11 primitives have local cohort evidence. Retaliation and matrix/legacy
closure remain. Native HUD/XP/input, all87/66 records, the live three-mod set and owner
art remain unchanged. No Google Drive writes or live deployment. Connected gates remain
IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION, not PASS.

## R030 cohort W — Retaliation

LP064, master repeat-controller rules, the installed ApplyDamage system and existing
SupportDamageSystems pre-Apply capture were reviewed. Plan schema32; player schema6
unchanged. Manual activation of a Retaliation-linked skill now rejects before payment.
An internal TRIGGERED request without the active, actor/slot/plan/correlation-bound
native-loss permit also rejects. Corpse/minion consumers, conversion, movement,
reaction, channel and Aura operations are excluded; existing repeat-group and
Lifeblood conflicts remain authoritative.

The native observer reads Health captured after absorption and before Apply, then
actual post-Apply Health in Inspect. It requires a surviving player and a real,
non-self hostile native EntitySource. Environmental/self costs, cancelled or shield-only
damage do not accumulate. Reflected, redirected and triggered provenance is excluded.
TRIGGERED metadata now follows automatic/derived damage through the existing adapter;
it does not change native mitigation, damage calculation, ordinary Leech or reward
ownership. The damage callback records history only. Activation occurs later on the
owner's world tick, never recursively inside DamageSystems.executeDamage.

RetaliationLedger keeps actual-loss samples for exactly10s against15% of current
maximum Health. Each linked slot has its own history, capped at one threshold; an
owner has at most one pending attempt/windup and one attempt per second across slots.
Least-recently-attempted ready slots prevent an unaffordable first slot starving another.
The native-loss event ID is deduplicated. Capacity is bounded512 owners,256 event IDs
per owner and128 samples per slot; overflow rejects further credit with a typed reason
instead of dropping safety limits. Local pruning is owner-scoped; global idle pruning
occurs only on new-owner admission. Players with no recorded Retaliation history do
not poll loadouts/resources from the extra tick path.

Attempts use the existing SkillExecutionService, normal equipment/family/target,
resource/cooldown checks, interruptible windup and durable commitment. Damage/healing/
shield magnitude uses one.70 snapshot factor. Finite percentage support effects use
70% of their authored bonus/reduction; Weakening correctly scales the reduction,
not the remaining damage multiplier. Authored duration, reach, cooldown, resource
cost, regeneration and attributes are unchanged. A paid Skill Delay blocks another
queued Retaliation and does not pay again at release. Successful payment resets the
history; failed validation retains at most one threshold without refreshing the
original damage timestamps. Retaliation-specific late dispatch errors retain payment
and reset the history because a native hit cannot be ruled out. No uncertain hit is
automatically replayed for free. Existing non-Retaliation executor behavior is retained.

Loadout mutation drops old plan credit/permits but preserves the attempt throttle and
receipt deduplication. Windup interruption releases the pending permit without payment;
revalidation runs again at commit. Death/logout/world teardown clears owned history.
RETALIATION_DAMAGE_OBSERVED traces have no fabricated cast IDs: they precede a cast.
RETALIATION_ATTEMPT and the real activation/payment/dispatch traces share the generated
attempt correlation only once the normal request path is entered.

50 new tests cover threshold/window math, sample expiry, actual-loss/dedup rules,
reflection/recursion exclusions, actor/slot isolation, fair one-per-second attempts,
manual/spoof rejection, normal costs, payment failure, interrupt/revalidate, Skill Delay,
support magnitudes, max-HP changes, teardown and bounded capacity. Fixture corrections:
the canonical IDs are Revive Fallen and Battle Cry, not inferred names; Weakening Hex
requires Wand/Spellbook, so its Staff fixture correctly rejected and was corrected.
Final full clean build: **1329 PASS**, no failures/errors/skips.

The first normal server smoke failed at plugin setup with exit9:
`SystemType dependency isn't registered: SupportDamageSystems$Reflect`.
Retaliation declared a BEFORE-Reflect execution dependency while Reflect had not yet
been registered. Registration now occurs after Reflect is registered; execution still
orders Retaliation before Reflect in Inspect. No dependency was weakened. Failed log
and summary are retained as `failed-setup-order-*` in cohort-w. The failed build hash
was `3EF1DC3246EB859FBEECED729F1695E7D6AD0F3A5AF341E69634E4A490852525`.
The audit script also checks this setup order to guard regression.

Final normal isolated three-mod network boot, resolved asset gates, clean exit0 and
nine packaged CustomUI documents PASS. The API/call-site audit is structural evidence
only. Native connected incoming damage, exact observation timing, casting, payment,
visuals and resource readback remain UNVERIFIED. No live world or mods were modified.

Artifact `evidence/stage-11/cohort-w/artifacts/HytaleRPG-0.0.23.jar`:
`1BB2E2C9A86AA49B46A29EC8A0EBB2C5C504EDCE46BE5ABAEB6A49CEAE0DFE0F`.
Rollback V: `03B66B087149C940579D7A29A95AB2DC344F394B9CE9B97A9047058050EAB0AB`.
All40 Stage11 primitives now have local cohort evidence. Component-introduction,
complete matrix and legacy inactive-node closure still remain: this is not a Stage11
connected PASS or final closure. All87/66 catalog records, native HUD/XP/input, owner
art and the live three-mod set are retained; no Google Drive writes.

## R030 cohort X — introduced-component compatibility correction

Master LP007/008/009/032 and Shatter/Hemorrhage/Repulsion were reread. All four
modifier primitives existed, but their compatibility checks did not account for
components introduced by other valid passives. This was a compiler/runtime gap,
not evidence of native input or damage failure. Plan schema33 invalidates older
compiled plans; player schema6 and authored profiles remain unchanged.

Repulsion now supplies a local knockback component for Impact Force. Vacuum does
not: a pull is not knockback. The existing position port requests4.375m for this
push before the same collision/control caps. Impact's direct-damage penalty is
applied once to the affected primary or secondary component, not an unrelated
carrier. Hemorrhage supplies a finite Bleed duration for Lingering:5.6s instead
of4s, with the existing1.15 cost factor. No primary strike duration, projectile
flight, CC duration or Burn/Poison capability is invented.

Shatter supplies a secondary radius/area for Expanded Radius, Concentration and
position control. Radius3m scales1.25 and/or.70; its50% resolved killing-hit
magnitude receives only modifier factors not already present on that hit.
Concentration uses the retained pre-Increased unit to add.30 Increased rather
than multiplying an already Increased hit by1.30. Missing/invalid resolved units
fail closed. The primary Frost Bolt collision radius, range and damage remain
unchanged when only its Shatter component qualifies. Native Shatter recipients
reuse the existing bounded displacement port and shared per-root target ICD.
Shrapnel's introduced area now also supports Concentration; carrier collision
and primary magnitude are not modified. All these paths retain the existing
native damage adapter, status timer, area query, protection/LOS and finite budgets.

Profile-cache keys now include component-scoping flags and introduction context,
preventing a cached primary transformation from leaking into a secondary-only plan.
The new18 tests include a valid six-Link Shatter graph, invalid introducers,
conversion-removal rules, exact additive/multiplicative math, no double penalty,
profile immutability and rollback when an introducer is removed. One initial
negative fixture incorrectly assumed Fire Bolt had no finite duration; its
authored Burn legitimately qualifies for Lingering. The fixture now uses Arcane
Bolt; the production compatibility rule was not weakened.

Full clean retained suite:1347 PASS, zero failures/errors/skips. Normal isolated
three-mod server network boot, clean exit0, native asset checks and9 packaged UI
documents PASS. Packaged bytecode/call-site evidence is in cohort-x/api. None of
these gates proves connected damage, movement, rendering or casting.

Artifact SHA256: `A28C6D8269E3425C638B2ACD1F29E8BB461D3939DC97BF6BA584A3EE267BDD71`.
Rollback W: `1BB2E2C9A86AA49B46A29EC8A0EBB2C5C504EDCE46BE5ABAEB6A49CEAE0DFE0F`.
Both are archived under evidence/stage-11/cohort-x. Connected gates remain
UNVERIFIED; no live deployment, Google Drive write, owner-art mutation or native
HUD/XP/input change. Legacy inactive-node and full matrix closure are next.

## R030 cohort Y — preserve unsupported saved passive nodes

The Phase11 rollback contract requires retaining owned passives and marking
unsupported equipped nodes inactive. Previous RpgLoadoutService.load cleared
all graph edges when any compatibility check failed. It also missed conflicts
detected only by final compiler groups. Structural corruption and changed content
compatibility now have separate validation paths.

Player schema7 adds a bounded map of inactive passive-slot reasons; plan schema34
invalidates prior compiled output. On load, InactivePassiveRecovery checks at most
64 subsets of the six linked passive nodes and retains the largest valid subset.
Ties favor the earlier slots. Final compiler rules and implemented numeric-profile
resolution are both checked. This is not a greedy pass: an introducer and its
dependent modifiers can remain together even if the dependent occupies an earlier
slot. Unknown definitions, duplicate copies, repeat conflicts and removed component
capabilities receive specific reasons. Missing skills remain degraded, never given
a fabricated executor. The separate27 legacy runtime-profile gaps are not concealed
by this recovery and remain scheduled for Stage13 coverage completion.

Saved edge identities/routes, equipped IDs, owned copies, learned skills, mastery,
XP, levels, attributes, support deficit and cooldown debt are retained. The compiler
omits inactive nodes only; retained routes still describe the actual saved topology.
The existing read-model warning surface and loadout command display INACTIVE plus
the node/reason. No packaged UI document or native HUD control changed. Actual
connected warning readability remains unverified.

This recovery runs only on load. A new link, reassignment, changed route or changed
destination skill must pass strict normal validation; the mutation path cannot add
inactive flags to hide an invalid edit. An explicit valid relink/reassignment
reactivates a node. Invalid edits preserve revision, graph and prior inactive state.
Unrelated edits can proceed while an unchanged legacy node remains inactive.
Malformed/cyclic/dangling topology still uses the previous graph-backup recovery;
inactive content is not permission to bypass structural safety.

Migration6->7 adds an empty inactive map without altering other fields. Content
reconciliation then saves only when needed. File migration now also preserves an
immutable `<player>.json.schema-v6.bak` (or the actual source-schema suffix), because
a subsequent reconciliation save would otherwise overwrite the sole pre-migration
`.bak`. Rollback requires the matching old schema checkpoint and cohortX code;
never run schema7 state on old code or refill resource/cooldown debt. No live save
has been migrated by this implementation program.

25 new tests cover maximal subset/dependencies, duplicates/conflicts, missing
definitions, joint routes, rejoin idempotence, defensive views, explicit reactivation,
strict edit rollback, failed save, structural recovery, migration and checkpoint
survival. Full clean build1372 PASS in43s; zero failures/errors/skips. Normal isolated
three-mod network boot, all retained native asset checks,9 packaged CustomUI files
and clean exit0 PASS. No new native API is introduced in this cohort.

Artifact: `C92890159DD27E9F0AA74E969B3AFB9D3DCC94A834F89B4BB39A98BFEC6756F7`.
Rollback X: `A28C6D8269E3425C638B2ACD1F29E8BB461D3939DC97BF6BA584A3EE267BDD71`.
Archived under evidence/stage-11/cohort-y. Connected gates remain UNVERIFIED.
No live deployment, Google Drive write, owner-art mutation or HUD/input change.
The complete eligibility/pair matrix and six-Link property regression close next.

## R030 cohort Z — matrix and local Stage11 closure

The complete production compiler/profile path was exercised for87*66=5742 single
skill/passive cells and2145 unordered passive pairs across all87 skills (186615
pair/skill assessments). Rejections retain their specific compiler code; a catalog
match without a runtime profile is explicitly not implementation evidence.

| Single-cell classification | Count |
| --- | ---: |
| Compiled and numeric profile resolved; connected unverified | 1041 |
| Catalog eligible but legacy Stage04/05 runtime not yet implemented | 643 |
| Typed rejection | 4058 |

| Pair classification | Count |
| --- | ---: |
| Valid on at least one listed implemented profile | 1212 |
| Only catalog-eligible on pending legacy profiles | 57 |
| No valid implemented or catalog-only skill | 876 |

Every one of the66 passive primitives has at least one implemented-profile positive
fixture. Machine matrices list exact skill IDs and rejection counts, not just these
aggregates. They do not certify native availability, input, presentation or executor
delivery. In particular Bone Cage and other adapter-specific gates retain their
recorded restrictions even where a numerical profile compiles.

Deterministic property seed110033 generated1000 valid six-Link graphs over57
implemented profiles. Each was checked through direct routes, two three-input
Joints and reversed passive-slot ordering. Semantic modifier order, tags, kernel
values and geometry/power operations match; finite48-effect/16-secondary/generation3
limits remain unchanged. Routes remain part of diagnostic plan identity; they do
not apply a passive again. Rejected candidate extensions were not silently equipped.

The first exhaustive run caught one genuine boundary, repeated across19 pairs:
Lingering on unconverted Orbiting Shadow Blades produces14s at.05s sampling, but
ConnectionProfile incorrectly capped those280 swept contact samples at256 as if
they were damage pulses. Orbit spatial sampling now has an explicit512-sample
finite cap; all actual damage-pulse profiles retain256. Runtime sample cadence,
four authored blades, per-target.75s hit cooldown, candidate64/victim-memory256,
owner/global field admission and one-second simulation-gap cancellation are unchanged.
The regression runs all14s through the existing connection scheduler and observes
19 eligible victim hits, one payment and final cleanup—not280 damage events.
25.65s at.05s remains rejected by the finite sampling cap. No native API was added.

Numeric constructor failures wrapped by Gson are now normalized back to typed
IllegalArgumentException at the profile boundary, allowing prepayment validation
and saved-node recovery to report the actual component gate. Unexpected nonvalidation
exceptions still propagate rather than masquerading as content incompatibility.
Plan schema35; player schema7 retained.

Final clean build:1377 PASS in46s, zero failures/errors/skips. Matrix/property test
class:3.444s on this machine; this is not a server combat-performance measurement.
Normal isolated three-mod network boot, all retained native asset checks,9 packaged
CustomUI documents and clean exit0 PASS. All evidence and the precise input hashes
are under `evidence/stage-11/cohort-z`; the matrix directory is directly shareable.

Artifact: `57A8688729BB33ABE1C76711B3777C98AF35351F98FEDA74365E6912811D9378`.
Rollback Y: `C92890159DD27E9F0AA74E969B3AFB9D3DCC94A834F89B4BB39A98BFEC6756F7`.
Stage11 local engineering scope is complete. Stage11 connected status remains
IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION, not PASS. The27 missing Stage04/05
runtime profiles remain explicitly tracked for Stage13; this local Stage11 closure
does not declare all87 mechanics complete. Continue to Stage12 under the owner's
continuous-program authorization. No live deployment, owner-art mutation, Google
Drive write or native HUD/XP/input change occurred.
