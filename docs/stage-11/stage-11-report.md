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
