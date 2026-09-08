# Stage 09 — support, barriers and auras (R028)

## Current state

**COHORT_B_LOCAL_GATE_COMPLETE / STAGE_IMPLEMENTATION_IN_PROGRESS**.
Version is `.21` / R028, not deployed. Stage 08 local completion is committed/pushed as `31d4a74`, with 320
passing retained tests and its final `.20` artifact preserved in
`evidence/stage-08/cohort-b/`. Connected RPG casting remains UNVERIFIED after the
R024 implementation. No live deployment is authorized/performed by this program.

Authority: owner continuous-program attachment, master v1.2 MD-18–20, 00.3/00.4,
03.3, 04.1–04.3, Phase 09 and individual assigned skill/passive closures. Exact
Minor Heal, Managuard and Emanatism records, the complete 03.3/04 combat section
and Phase 09 were read before the pilot audit. Master hash remains
`750483855846FF6DB2564B4D6D626C2A12F1AC6EBBBBF12232B2C3ECCF667010`.

## Planned bounded cohorts

The prescribed pilots are Minor Heal, Managuard and Emanatism. Remaining skill
cohorts must contain at most six records, passive cohorts at most four. Exact
later records will be read before their implementation. All 16 Stage 09 skills
and seven passives are required for the local stage gate. Cohort validation,
artifact/evidence archives, rollback and commits remain required; the continuous
authorization removes only the owner-approval pause between local gates.

## Audit findings before implementation

- Existing ReservationService calculates percentage reservations against total
  maximum, but adding a reservation only clamps current Mana to the new cap.
  It does not require sufficient current Mana or debit the newly reserved
  amount when already below that cap. This does not meet the exact 03.3
  activation contract and must be corrected transactionally before Managuard.
- Native player Mana regeneration is currently the Stage 02 resource asset's
  1.5% maximum each second. The production code does not run a second regen
  timer. Reservation-cap and Emanatism integration must operate coherently with
  that native path, not double regeneration or amplify on-hit recovery.
- DerivedStatEntityAdapter currently preserves percentage while replacing native
  maximum modifiers. Any reservation projection must avoid a total-vs-spendable
  feedback loop and must not refill Mana on unreserve or maximum changes.
- Managuard's normative closure supersedes its older descriptive paragraph:
  recharge after six seconds without hostile damage at 10% capacity/second;
  a saved deficit survives toggle/loadout/reconnect, including reduced/re-raised
  capacity. Offline recharge is forbidden without valid time evidence. The
  allocation is integer 1–50%, default 50%; active effects cannot resume blindly
  on login. Durable deficit/allocation/order/toggle-lock state needs an explicit
  persistence and rollback contract before integration.
- Minor Heal uses baseline HealingPower 20 and Wisdom, not its legacy Magic/INT
  row. Its recipient must be an actual valid ally or self within 18 m/LOS, and
  actual healing is capped to missing native Health.
- Emanatism membership is 0.10 s, radius 8 m, includes self, reserves 10% total
  Mana and contributes +0.25 Increased Mana regeneration only. Identical named
  auras select the strongest valid effect; leaving membership removes the
  contribution. Aura lifetime is conditional on paid/reserved validity, not a
  finite 120-second field timeout.

`Capture-Stage09Api.ps1` records the exact pinned native stat, regeneration,
modifier, damage-filter/application, effect and NPC attitude implementations plus
shipped stat assets. Public signatures alone are not sufficient proof of a
mutation path; relevant bytecode must be inspected before selecting an adapter.
No new packet, HUD control, party identifier or engine behavior is assumed.

## Pilot foundation work (not a cohort completion)

ReservationService now charges only the positive reservation delta, requires
enough current Mana, observes the native debit before publishing the allocation,
and attempts explicit rollback on a failed native write. Reducing/removing an
allocation does not refund Mana. Maximum reconciliation cancels newest
allocations first; resizing an existing allocation retains its activation order.
Fourteen new deterministic reservation tests pass. A retained bed/home fixture
previously reserved from zero Mana; it now supplies the 25 Mana required to
reserve 25%, retaining all of its restoration assertions.

ManaguardLedger stores missing shield rather than a fresh shield value. Its
capacity is actual reserved Mana times eligible barrier modifiers. Lowering or
raising capacity preserves all deficit; recharge reduces deficit at 10% of the
last validated capacity, only for explicitly observed eligible connected time.
SupportProgress stores this ledger, allocation, monotonic Aura epoch and toggle
locks, but deliberately no active Aura or native HMS values. Eleven ledger/heal
tests pass, including save/read without offline recharge or automatic activation.

Player schema is now **4**. The v3→v4 migration initializes the empty support
ledger because older versions had no support effects. Current-schema records
with a missing/null ledger fail closed instead of resetting shield. Support-only
mutations persist under the existing per-player authority lock and use a separate
support revision: they do not recompile/reproject an unchanged loadout on every
shield hit. Before any eventual live upgrade, retain the schema-3 player
directory alongside the Stage 08 `.20` rollback artifact. An older build must not
read schema 4; downgrade requires the matching pre-upgrade player-state backup.

### Native resource integration choice and rejected alternatives

- Native `Regenerate` calls each `RegeneratingValue.regenerate`, accumulates
  deltas, then calls `EntityStatMap.addStatValue`. Its accumulation buffer is
  package-private; no reflection/package injection is used.
- A non-static `Modifier` is unsuitable: `Modifier.toPacket()` explicitly throws
  `Only static modifiers supported on the client currently.` No custom modifier
  packet or HUD surrogate was introduced.
- NativeManaReservationProjection uses a client-supported static additive MAX
  modifier. Native Mana's maximum becomes its spendable capacity; the resource
  port still reports total pre-reservation Mana for RPG formulas. The adapter
  compensates using native static multiplication semantics (sum the factors,
  then call the native calculation), not an assumed Increased percentage.
  Unknown non-static Mana maximum modifiers reject instead of guessing.
- Derived-stat updates temporarily detach this capacity modifier and preserve
  absolute current Mana while it is active, avoiding feedback into total maximum
  or percentage-based reservation refill. No Health/Stamina logic or HUD controls
  are added. A server stat maximum projection is gameplay capacity, not an RPG
  resource-bar presentation layer; native rendering still needs connected QA.
- NativeManaRegenerationAdapter decorates live per-player Mana regeneration
  entries. It delegates once to each original native entry, preserving timer and
  Conditions, normalizes percentage regeneration against total Mana, and applies
  the Mana-regeneration-only Increased bucket. It does not schedule a second
  timer or amplify direct recovery, Stamina or Health. Positive native regeneration
  deltas are not max-clamped by RegeneratingValue; native addStatValue clamps the
  final result. A native-stat fixture verifies this fact directly.
- The corrected installed-item audit searches the codec field `Regenerating`,
  not the Java getter name `RegeneratingValues`. It found two shipped debug armor
  records, both without Mana regeneration. Future/third-party item Mana regen is
  an additional integration boundary to handle explicitly, not silently claim.

Seven isolated native-method tests now pass in the nativeControlTest JVM. They
exercise actual native stat/modifier/regeneration code with a minimal registered
stat asset: native total/spendable arithmetic, no unreserve refill, packet support,
1.5→1.875 rate, unchanged timer when membership changes, and the raw positive
delta before native clamping. They do **not** prove connected native ECS execution,
client prediction/rendering or any Aura skill activation.

Failures recorded during foundation development: the first Java compile used
asset spelling `Percentage` instead of the native enum `PERCENTAGE`; inspection
corrected it. Four initial native tests lacked the required EntityStatType asset
registry. The next fixture initialization exposed the indexed asset store's
mandatory `setReplaceOnRemove`; the fixture now provides the actual native
unknown-stat replacement function. No production checks were weakened to make
those fixtures pass.

## Cohort A — prescribed pilots

Minor Heal, Managuard and Emanatism now have executable typed support profiles,
native zero-cost/zero-cooldown Rune projections, and shared support runtime
integration. The complete retained build passes **374 tests**, with no failures,
errors or skipped tests. This includes the 320 retained Stage 01B/CanvasUI/02–08
tests, 14 reservation tests, 11 ledger/healing tests, seven isolated native-method
tests and 22 support/runtime tests. The normal isolated three-mod smoke reaches
network boot and clean shutdown; its exact JAR hash is archived with the machine
verification in `evidence/stage-09/cohort-a/`. Startup is not connected execution.

### Runtime decisions and verified local behavior

- Support dispatch reuses SkillExecutionService commitment, resource, cooldown,
  compiled snapshot and release authority. Direct healing uses WIS and the
  committed magnitude factor exactly once. An Echo regression verifies its 70%
  child healing and Potency while charging only the root. Expanded Radius changes
  Emanatism's membership radius, not its reservation or regeneration coefficient.
- Native healing resolves the committed entity identity again, validates alive,
  range, LOS and positive allegiance, caps the actual native Health write, and
  records before/after/actual healing. The runtime emits HEAL_RESOLVED separately
  from that native HEAL_APPLIED event, avoiding duplicate application evidence.
- Managuard runs after native damage filtering and before RPG filtered tracing
  and ApplyDamage. It persists increased deficit before reducing Damage.amount.
  Failed persistence grants no shield. Hostile input resets the six-second delay
  even when the shield absorbs the entire damage amount.
- Aura membership is bounded to 64 recipients, refreshed at 0.10 s, and uses a
  short validity lease to prevent stale bonuses if owner processing stops. Four
  Auras/player also consume the existing eight-field/player, 128-global budget.
  A 130-second deterministic run proves no accidental 120-second Aura expiry.
- Toggle, invalid equipment/loadout, death, native entity removal and reconnect
  cleanup release reservations, membership and field capacity. Durable deficits
  remain. No paid Aura automatically resumes after reconnect; native stale
  reservation capacity is cleared before derived-stat projection on ready.
- `/rpg managuard <1..50>` adjusts the authoritative integer allocation. Active
  changes enforce the three-second toggle lock, require only a positive reserve
  delta, and never refill on reduction. The durable minimum toggle lock remains
  authoritative even if the ordinary derived cooldown would be shorter.
- A failed durable allocation save originally attempted to re-add the previous
  reservation through the paid activation API. That would fail when downsizing
  from 50% at zero current Mana. An explicit bounded rollback snapshot restores
  the previous allocation/current value without treating it as gameplay recovery;
  the regression proves no shield reset, reserve loss or free Mana.

Further failures corrected during integration: the initial heal fixture assumed
baseline WIS contributed zero, but the existing derived WIS multiplier is 1.03;
the assertion now expects 20.6 healing rather than changing the formula. Three
retained profile-inventory tests counted the new support profiles as old pilots;
their original inventories are now selected explicitly, preserving their exact
Stage 04/05/08 coverage and assertions.

### Explicit remaining boundaries

- Affirmative native NPC FRIENDLY/REVERED allegiance and self are supported.
  NEUTRAL/no-PvP is not ally membership. No native player-party API was proven;
  player-party support requires the later authoritative Stage 12 provider.
- Skill mastery currently supplies the baseline multiplier 1 through the shared
  seam. Stage 12 must wire earned mastery; no mastery progression is claimed here.
- Current pinned armor assets have no item-based Mana regeneration. A third-party
  `Armor.Regenerating` Mana path needs an additional audited integration; the
  per-stat entry adapter is not proof of that separate path.
- Native ally targeting, healing, filtered barrier absorption, regen timing,
  native Mana presentation, movement/LOS membership and teardown/rejoin all need
  connected-client QA. Approved template VFX are presentation only; neither
  startup nor pure-runtime tests prove that they render.
- R024 casting is still UNVERIFIED. No manual diagnostic/internal fixture has
  been relabeled owner E/R input evidence. Native resource/Signature/AbilitySlots
  ownership, XP assets/geometry and Ability4 policy are untouched.

Rollback for this cohort is the exact Stage 08 `.20` JAR (SHA-256
`16F580BE625AD71CE254807EDF4966B8BDAC52096DBCB6F949ED88BF80FADB5F`).
Before eventual deployment, a matching schema-3 player-state backup is mandatory
for downgrade. This program has not migrated or written live player state.

## Still required

Implement the remaining eight skills and seven passives in bounded cohorts, with
their own retained regression gates, smoke, archives, evidence and commits.
All client rendering/input/native execution
outcomes will remain IMPLEMENTED_AWAITING_CONNECTED_VERIFICATION until actual
connected evidence exists. Native resource/Signature/AbilitySlots ownership, XP
geometry and Ability4 capability policy remain unchanged.

## Cohort B — finite support and hostile utility

The five records are Taunt, Weakening Hex, Hunter's Mark, Intimidate and Battle
Cry. Cohort A was archived and committed as `38e400d` before these changes.
The exact records and master 04.1–04.3 were reread. The complete retained build
now passes **402 tests**, zero failures/errors/skips: 374 retained, 21 finite
support tests, four real native SteeringForceEvade tests with a fixture navigation
predicate, and three packaged presentation-structure tests. None is a connected
NPC, collision, movement, packet or rendering test.

### Native audit and implementation rationale

`evidence/stage-09/api-finite/` records the pinned default target-slot, role tick,
native evade force, 2D navigation probe, avoidance/steering, effect codec and
DamageEntityInteraction implementations. The default target slot is looked up
by its actual name; a missing or durable rebind slot rejects. No NPC role is
replaced and no permanent NPC rebind is written.

- Taunt uses the native default marked-entity target immediately before role
  evaluation for five seconds. Common and Elite retain the authored duration;
  Taunt is not hard-CC DR. Bosses require an encounter opt-in provider, which has
  not been authored here, and reject explicitly. After native role evaluation,
  the selected target is read back. A role that supersedes it produces
  `NATIVE_ROLE_SUPERSEDED_TAUNT_TARGET` and loses the override. This readback is
  not proof of a native attack. On expiry, only this adapter's matching temporary
  target is released; ordinary threat evaluation resumes, not a stale saved target.
- Intimidate applies two-second Fear through the shared resistance/DR service.
  Its native 2D evade request is generated after role evaluation, before native
  avoidance and movement. The actual MotionController probe rejects unsafe steps
  and edges; Steering.maxDistance bounds native translation to that probed step.
  There is no teleport. Root/Frozen still stop movement; native action queues are
  cleared while Fear is active. Flying/swimming controllers explicitly reject
  until a safe corresponding navigation adapter is audited.
- Direct damage after 0.25 s breaks Fear, not DoT or cancelled hits. RPG metadata
  now distinguishes direct, periodic, reflected and redirected origins. Pinned
  native DamageEntityInteraction supplies INTERACTION_TYPE; a bare EntitySource
  alone is not treated as evidence of a direct weapon hit.
- Weakening Hex contributes a single 0.85 outgoing multiplier for eight seconds.
  Hunter's Mark contributes +0.10 to the existing Increased bucket, only for
  that caster's RPG skill damage, for 15 seconds. One mark/caster replaces the
  previous target atomically. Battle Cry samples self and confirmed allies once
  in six metres, applies +0.10 Increased damage and +0.10 movement for eight
  seconds, and is not an Aura emitter or reservation.
- Source-owned finite effects share a bounded registry (4,096 global, 256/owner,
  32/target; 64 recipients per atomic cast batch). Identical effects refresh or
  choose strongest, never multiply by copy count. RPG damage combines the
  appropriate buckets at the existing calculation boundary. A separate native
  outgoing filter handles non-RPG hits; it skips tagged RPG hits to prevent double
  application. No Stage 04/05 executor or projectile mechanic was redesigned.
- Battle Cry's movement uses the actual EntityEffect HorizontalSpeedMultiplier.
  Missing/rejected native effect projection removes the corresponding gameplay
  lease and records the earliest native boundary, including exceptions. Short
  native leases expire on teardown failure; there is no permanent speed modifier.

### Catalog corrections and failures found

Intimidate's legacy 0.90 damage placeholder and damage/crit capabilities conflicted
with its normative zero-damage Fear closure. Its catalog now agrees with the
executable profile; Potency correctly rejects this utility skill. Battle Cry's
legacy moving-Aura description now states one-time recipient sampling. Canonical
counts remain exactly 87 skills and 66 passives.

The first compile used an incorrect NetworkId package, corrected against the
installed class. A full regression then exposed an old Stage 02 expectation that
boss Root must reject. Master 04.3 requires the eligible 30% Slow substitute for
two seconds; the shared status implementation and assertion now follow that
rule. Protected Root and boss Fear still reject, and the authored Root Snare
exception remains unchanged. No lifecycle trace or connected gate was weakened.

Before final validation, component additions during ECS processing were changed
to CommandBuffer.ensureComponent. Native Walk bytecode inspection confirmed that
Steering.maxDistance must be explicitly set to the successfully probed step.
Finite-effect admission is checked before shared Fear/DR application, then checked
again at publication; this prevents ordinary capacity rejection from consuming a
control application.

### Presentation and explicit remaining boundaries

Hunter's Mark no longer broadcasts the initial debug shape as a tracking marker.
An entity-bound, non-gameplay native tint fallback renews with a 0.2-second lease
only while a loaded owner has range-bounded LOS (64 m visual cap, not a gameplay
range extension). Four deterministic owner palette variants provide a bounded
ownership cue; collisions are possible and do not imply unique player identity.
When multiple marks share a recipient, stable owner ordering selects the visual;
all separate caster gameplay modifiers remain correct. Hex uses a short native
lower-body tint. Native model presentation remains the owner of rendering and
occlusion. These are fallback art, not the final authored overhead diamond, and
connected no-wall-reveal/visibility, lifetime and visual precedence still require
QA. A presentation failure cannot refund or cancel applied gameplay.

Native role behavior may reject a Taunt target despite the valid public target
slot; post-role readback diagnoses that exact boundary rather than claiming
success from the setter. Native retreat safety/movement, attack interruption,
native outgoing damage, native effect overlap and all five skill activations need
connected evidence. Gun equipment classification is currently unsupported by the
existing weapon adapter; Bow/Crossbow classification is retained, not evidence
of a working Gun path. Future third-party untagged reflection damage is not
automatically classified by this adapter. Cross-family snapshot and load-volume
hardening remains part of the stage's final cohort and Stage 13 gates.

The `.20` rollback artifact and schema-3 backup requirement remain unchanged;
cohort A's `.21` artifact is also retained. No live mods, player state, native
resource/Signature HUD, XP assets, Ability4 policy or gameplay cost/cooldown
formulas changed in this cohort.

The exact `.21` cohort B build reached normal isolated three-mod network boot and
clean exit 0. Native support and bridge assets resolved; the packaged audit found
43 zero-native-cost trigger items and no protected HUD/XP/projectile/cost-formula
changes. `evidence/stage-09/cohort-b/` contains the full smoke, 402-case results,
machine verification, build and rollback. SHA-256:
`5918C90D6F5B081388C74B4EA4DDDEB57F816560F2610D46D3405A87B9690EA3`.
This closes only cohort B's local engineering gate; eight skills and seven
passives remain before Stage 09 local completion.
