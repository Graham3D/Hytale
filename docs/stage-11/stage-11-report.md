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
