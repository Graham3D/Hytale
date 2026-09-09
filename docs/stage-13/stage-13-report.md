# Stage 13 — runtime completion and release hardening

Revision R032, version 0.0.25; branch RPG. Resumed from pushed Stage12 H
`de60a02`. All work is in the C: GitHub checkout. Owner art, including
`art/lost and found`, is untouched. No Google Drive writes or live deployment.

Status: **IMPLEMENTATION_IN_PROGRESS**. This is not a release candidate or a
Stage13 closure. Connected status: **UNVERIFIED**. The latest completed local
stage is Stage12 H: 1653 retained tests, isolated normal three-mod smoke,
packaging/archive checks, and the actual archived schema8 reader rollback drill.

## Evidence and scope

Master v1.2 Markdown SHA256:
`750483855846FF6DB2564B4D6D626C2A12F1AC6EBBBBF12232B2C3ECCF667010`.
The pinned native implementation remains 0.7.0-pre.1. A local test, asset
resolution or loopback boot cannot prove native input, client animation,
rendering, authoritative connected hits, multiplayer performance, or rejoin.

Owner permits targeted testing during implementation, then the full retained
regression suite, normal isolated three-mod smoke, packaging/archive checks and
rollback validation at stage closure and again for the final release candidate.
Intermediate results below must not be substituted for those final gates.

The native R024 interaction callback still needs connected testing. The owner's
R023 control proved native Fireball visibly cast while the packet watcher saw
nothing; neither the R022 synchronization hint nor any later unit test erases
that observation. No casting fix is claimed here.

## Cohort A — shared strike corrections and six remaining Stage04 profiles

Local targeted gate: PASS, 815 tests (791 root tests and 24 native-control tests),
zero failures/errors/skips in the final run. These include all retained Stage04,
Stage05 and Stage11 test classes, the compatibility matrix, and 30 new strike
closure tests. CanvasUI and the complete Stage12 suite are not rerun for this
intermediate cohort; they remain mandatory at Stage13 closure and final RC.

Normal server smoke: PASS, process exit0, `Hytale Server Booted`, clean shutdown,
exactly three mods, all retained plugin/damage/progression registrations and
the newly resolved strike assets. Captured 2026-09-09T00:42:08.2828473Z.

Build SHA256:
`D48AB2C380BA57FC3867C71920B3586994E89EEA9C30E32E63C42809D0BB9E8F`.
Archived in `evidence/stage-13/cohort-a/artifacts/` together with unchanged
CanvasUI0.1.0 and HytaleDevLib0.5.0. Stage12 H rollback RPG JAR SHA256:
`C55DD5C1A939E5727AD01945FDC6DC0B7D7ECF1C185D94AC95B0BDAE885EB87B`.
Player schema remains9; compiled-plan schema increments35 to36 because strike
geometry and component interpretation changed. Persisted ownership/progression
is not discarded. Do not use a single player JSON as a complete rollback:
players, earned-rewards and encounter state must remain a coordinated snapshot.
The new Stage13 archived-reader drill is still required at closure/final RC.

### Irreversible dispatch, not a family-specific refund whitelist

Audit found that SkillExecutionService exempted area, connection, support,
summon and Health-paid casts from late refunds but still refunded ordinary
strike, projectile, movement and reaction failures. A native effect can happen
before a later presentation/status adapter throws. That exception cannot prove
the absence of a hit or other effect. The old branch could give free damage and
restore a cooldown charge and Attunement/Ruthless commit counters.

The new fault fixture performs a test-double effect before throwing and proves
the payment/cooldown remains charged for all four families, the lifecycle ends,
and an immediate duplicate cannot dispatch. This is a local failure-boundary
test, not a native Health-loss claim. Pre-dispatch transaction rollback remains;
its existing tests were retained. An executor returning an uncommitted result
after SKILL_COMMITTED now terminates the paid cast rather than retaining a
phantom movement/reaction/projectile lifecycle. Windup revalidation exceptions
release lifecycle and Retaliation-attempt state without paying or dispatching.

Four existing assertions encoded the unsafe old refund branch. They were not
deleted or skipped: the Stage05 synchronous spawn-exception test, Stage11 normal
resource Attunement test, second Second-Wind dispatch test, and normal-resource
Ruthless test now assert retained payment/commit debt and rejection of an
immediate retry. This correction follows master02.2 and the observed partial-
effect failure model, not a desire to make failing tests green. Before this
change the new injected-effect tests failed because the resource returned100.
Old pre-executor failed-cooldown-save/Health rollback assertions remain intact.

### Shared target selection and cadence

The previous melee selector collected entity origins in a radius, permitted
non-hostile NPCs into that candidate path, accepted a 5m vertical band despite
the canonical 2.5m full height, sorted equal distances by input order, and
silently used `limit(targetCap)`. These were architectural issues, not reasons
to alter skill coefficients or add per-skill executors.

The native path now queries actual BoundingBox intersections through the shared
native hostility query, requires real UUIDs, filters LOS, and uses the current
actor origin plus committed aim. It no longer uses a stale saved melee origin
after a delayed release. Volume height is2.5m from the actor-foot plane. Spear
line length3.6/full width0.8 is a forward rectangle, not radius0.8. Large target
bounds intersect even if their entity origin lies beyond range. Point-only
constructors remain test-fixture conveniences, not the native selector.

The ordinary query bound is64. Overflow rejects explicitly in preflight before
payment; an unexpected post-commit overflow terminates the paid resolve rather
than silently dealing to a subset. The old arbitrary16 caps on Quick Slash,
Heavy Swing and Pounce become the canonical ordinary64 bound. Single-target
assist remains one nearest eligible target and reports other candidates as
not selected. Stable distance/UUID sorting makes ties deterministic. Full
production oversize-encounter/load-test handling remains a Stage13 gate.

Strike data now carries element, full height, action-window duration and movement
factor. Shared native damage calls (including strike children) use that element.
Numeric profile validation now rejects nonfinite cost, cooldown, windup, innate
power, strike geometry/status, movement, reaction and projectile numeric fields.
It does not accept NaN as an ordinary nonnegative value.

Authored repeated hits and the attack-lock end are separate deadlines. Dagger
Flurry's last hit at0.6 does not unlock before0.8; Whirlwind's last hit at0.8 does
not unlock before1.2. Repeated hits keep distinct ledgers. The native temporary
effect disables the six attack/ability interactions during an owned window;
Whirlwind's separate effect also requests movement0.70. It does not replace the
HUD, Signature Move slot or any hotkey. Ownership cleanup and finite effect
expiry prevent permanent restrictions. Connected action-lock behavior is not
proven by asset resolution.

### Six canonical profiles

| Skill | Exact local configuration |
|---|---|
| Spear Thrust | Spears, Stamina6, CD1.1, line3.6/full width0.8,1.10 weapon, Heavy; native spear equipment mapping still unavailable |
| Dagger Flurry | Daggers, Stamina10, CD4, arc2.4/80°,4×0.35 at0/.2/.4/.6, lock.8; no generic Multistrike |
| Maul Swing | Mace/Battleaxe, Stamina9, CD2, arc2.8/120°,0.90 weapon; no invented stun |
| Scythe Sweep | No weapon, Mana8, CD4, arc4/160°, innate20×1.00 INT, Necrotic; not a projectile |
| Spark | No weapon, Mana7, CD2, arc2.8/120°, innate20×0.90 INT, Fire; no default Burn/Combustion |
| Whirlwind | Sword/Longsword/Battleaxe, Stamina16, CD8, radius4.2,3×.55 at0/.4/.8, lock1.2/movement.70; held weapon selects attribute |

No per-skill executor or native damage interaction was added. Six native
Ability2/Ability3 trigger items join the existing60; all66 native items retain
Cost0, Cooldown0, CostTypeNone and the unchanged RPG bridge root. Native resource
HUD, XP assets/geometry, input projection, balance and canonical catalogs are
unchanged, enforced by the evidence capture script.

### Native animation audit: failure retained, not bypassed

The installed AnimationUtils overload with its boolean true sends the animation
packet to the invoking player as well as observers; the default false overload
excludes that player. The adapter uses only finite humanoid Action animation
packets, never a shipped gameplay attack root. Procedural footprints use the
same resolved geometry and a0.12s lifetime. Animation/footprint appearance still
requires connected inspection.

First smoke build `50C4D8E4A0E40B686F746BE9714598F0B495AB8443095D6132D2FF78AF5E2CAA`
failed at `STRIKE_FEEDBACK_ASSET_INVALID:Battleaxe/SwingLeft`. Diagnostic build
`F88C1F6CBAD86F29A79D4577A201F46929B7DB20E387F72D014FD83C2FE7FA0D`
reported both Battleaxe swings as looping=false with valid first/third-person
paths but speed0.0. The shipped JSON omits Speed, and the protocol object's
default speed field is0. The validator's positive finite speed requirement was
not removed. An RPG-only Battleaxe animation profile inherits Battleaxe, reuses
the same first/third-person animation files, and explicitly specifies Speed1 and
Looping=false. It does not mutate the native Battleaxe asset or change damage
cadence. The subsequent real native asset-load smoke passes all seven animation
profiles and both native action-lock assets. Both failed logs and their exact
JARs are retained under `failed-animation-audit-1/` and `-2/`.

### Matrix and verification artifacts

The real compiler/profile matrix was regenerated:5742 skill/passive cells,
2145 passive pairs across87 skills,1000 valid six-Link graph fixtures. Both
single-profile and pair-profile construction-failure maps are empty. This is
compiler/number coverage, not native gameplay proof. The repo has66 runtime
profiles now; it does not have87 implemented runtime skills yet.

Reproduce the intermediate gate with `:test --tests '*Stage04*' --tests '*Stage05*'
--tests '*Stage11*' --tests '*Stage13*' :nativeControlTest :jar`, then
`tools/Run-Stage13CohortSmoke.ps1 -Cohort a` and
`tools/Capture-Stage13CohortEvidence.ps1 -Cohort a`. Evidence capture refuses
failed/skipped tests, reduced retained class counts, unmatched build/smoke hashes,
missing packaged assets, native gameplay costs and changed protected paths.
Never overwrite an archived cohort with a different build.

## Cohort B — projectile payloads and native equipment authority

Targeted local gate: PASS,1251 tests (1227 root +24 native-control), zero
failures/errors/skips. Retained affected Stage02/04/05/06/07/08/09/11/13 tests
ran, including all30 cohort-A tests,43 new projectile-closure tests and7 native
equipment/source tests. This is not the complete retained regression suite.
Normal isolated three-mod smoke passed at2026-09-09T01:26:19.8147772Z, exit0,
normal boot and clean shutdown. Build SHA256:
`EBF09AB702D8CAC5DA9EE0A3F39201499B8CDFC494C49396ECE068262AD559F6`.
Evidence and exact three JARs: `evidence/stage-13/cohort-b/`. Player schema9 is
unchanged; plan schema37 invalidates old compiled projectile interpretation.
The Stage12 H rollback JAR remains archived; the Stage13 rollback drill has
not yet run. Nothing was deployed to the live RPG world.

### Canonical profiles and payload routing

| Skill | Implemented data |
|---|---|
| Spear Toss | Spear/Heavy, Stamina7, CD5,1.15 weapon, speed22, range24, radius.25, gravity0; no spear consumption |
| Crossbow Bolt | Crossbow/Light, Stamina5, CD2,1.25 weapon, speed40, range32, radius.075, gravity10, one Weapon_Arrow_Crude |
| Web Shot | Empty-hand allowed, innate20/DEX, Stamina7, CD7,.25, speed16, range18, radius.35; Root2.5s |
| Void Bolt | Wand/Spellbook, Mana8, CD1.6,1.00, speed21, range24, radius.32; Void, no invented Fear/Leech |
| Bone Shard | Wand/Spellbook, Mana8, CD1.8,1.00, speed25, range25, radius.25; Necrotic, lifetime1s |
| Cold Blast | Staff/Wand, Mana14, CD5,1.10, speed19, range22, radius.45; Cold and two Chill stacks on actual Health loss |

Crossbow uses the exact installed registered native physics, not the development
fallback numbers. The projectile family now carries typed elements through
direct, orbit-converted and explosion-derived damage. Existing Fire/Frost/Arcane
profiles gain explicit element metadata without coefficient/cost/CD changes;
Arcane gets a registered Elemental child cause. Burn ticks use Fire. Missing
damage causes reject before payment. Nonzero native gravity combined with the
existing zero-gravity Ballistics passive rejects before payment rather than
throwing after commitment; that combination is explicitly unsupported, not
silently flattened to zero gravity.

Root uses the existing status authority and native RPG_Root effect projection,
including protected-target checks, elite duration reduction and hard-control DR.
Boss substitution is a source-owned30% Slow only when an exact encounter role
opts in. The production opt-in set is empty; the test-only boss fixture is not
a shipped encounter classification. Cold Blast atomically applies two Chill
stacks and composes with Deep Freeze without inventing extra stacks after Frozen.

Shared projectile knockback previously returned the requested displacement after
setting a velocity, with no observation of displacement. It now reuses the
bounded area displacement planner: rank/protection, grounded support, swept
obstruction checks and ledge safety, then native Transform write/readback.
Only measured authoritative position delta is returned. This is still not
connected-client movement proof. Strike-secondary queries now reject more than
64 candidates explicitly; Shockwave no longer silently truncates a100-target
query to64. The old truncation assertion was replaced with a64-target success
case and65/100/257-target rejection cases with an exact overflow reason, following
the master's explicit overflow contract, not weakening the gate for green tests.

### Audited base power, not aggregate native damage summaries

Master04.1 requires versioned per-item base power before attributes, mastery,
crit, charge and native bonuses. The prior production adapter instead inferred
families from names and averaged native BasicDamageBreakdown. The initial real
asset smoke exposed why that is unsafe: Crossbow Iron's summary is44 although
its ordinary projectile base is10 and Signature damage is78; Sword Iron's
summary40 differs from its basic swing10. Those are not interchangeable values.

`rpg/runtime/native-item-power-r032.json` now explicitly records14 supported
item IDs, their resolved native family/type constraints, chosen base, selection
policy and installed source provenance. Audited uncharged native bases are:
Sword Iron10, Sword Copper8, Longsword Iron16, Daggers Iron6, Battleaxe Iron18,
Mace Iron29, Spear Iron6, Crossbow Iron10, Shortbow Iron1 and Crystal Flame
Staff10. Crystal Ice Staff, Demon Spellbook, Wooden Wand and Iron Shield use an
explicit RPG-authored reference base20, separately labeled; no claim is made
that their native attack deals20. These explicit choices correct production
power authority and can change effective damage versus the old aggregate
summary. They do not change resource, attribute or damage-scaling formulas.

Unknown item IDs are unsupported, not guessed. Crystal Flame Staff's shipped
Family=Magic exception is tied to that exact registered ID. Battleaxe Iron has
no Family tag at all: its explicit audited absence is valid only for that exact
ID with Type=Weapon, not an absent-family wildcard. Unexpected/ambiguous tags
fail closed. Diagnostic summary extraction remains read-only and is never used
as production base power. Gun power still needs its own audit in the next cohort.

The startup audit resolves actual native assets, checking13 projectile configs,
models/bounds, typed causes, empty gameplay interactions, exact speed/gravity/
radius, the shipped Crossbow configuration, and all14 registered weapon IDs.
`native-projectile-equipment-audit.json` preserves the resolved results and raw
summary comparisons with connectedProof=false. Static JSON checks alone are
not the native asset-load gate, and asset-load success is not native execution.

Two informative builds are retained. `pre-power-boundary-audit/` preserves the
initial summary-based adapter, SHA256
`6367306B41AA486EDED48D30FC5190B1A250CF26B325394B3101CA64934B7388`.
`failed-equipment-audit-1/` preserves the strict-family failure, SHA256
`364860E1263CE222881254442D3227587ED1AA43010691E652BD99FBE803CC71`:
exit9 before network boot, earliest boundary
`NATIVE_EQUIPMENT_FAMILY_UNRESOLVED:Weapon_Battleaxe_Iron`. Installed template
inspection established the missing Family tag; the fix is the narrow audited
absence rule above, not removal of native type/family validation.

### Readability and regression evidence limits

Projectile presentation adds finite cast/contact/expiry primitives and10Hz trail
sampling without catch-up bursts; presentation failure logs once per carrier.
It uses existing finite animation packets and procedural world primitives only,
never native gameplay roots. Native projectile configs have empty Interactions;
all72 native ability items retain Cost0/Cooldown0/CostTypeNone. Reused art is
installed art, not newly designed owner graphics. Web Shot's pale orb and Bone
Shard's Fishbone model are provisional templates needing visual review. No
third-person animation, trail readability, control effect or casting success is
claimed. All remain connected UNVERIFIED.

The first broader regression run also identified two stale global count/schema
assertions: Stage08's35 base profiles now additionally include Stage13 profiles,
and Stage09's current plan-schema expectation is37. Their substantive profile
and migration assertions remain. A new test initially incorrectly assumed the
NONE pseudo-resource held100; it now snapshots every resource and proves exact
pre-payment preservation, including NONE=0. No failing tests were deleted/skipped.

The matrix was regenerated:5742 cells,2145 pairs,1000 valid six-Link graphs,
empty single/pair profile-failure maps. Packaged CustomUI and asset bytes passed.
Protected native-HUD/XP/input/Canvas, balance-formula and catalog paths are
unchanged. The freeze tool captures/checksums RPG-owned player/reward/encounter/
control-journal state before each cohort without stopping a live world. Private
raw save copies are git-ignored; this limited snapshot is not a full-world backup
or a completed rollback exercise.

## Cohort C — authored projectile patterns, explosions and shared lifetime safety

Local intermediate gate: PASS. Final affected-family suite is1298 tests (1274
root tests plus24 native-control tests), zero failures/errors/skips. All retained
Stage02/04/05/06/07/08/09/11 classes are present without reduced counts. This
includes40 authored-projectile tests and7 shared-root-budget tests, plus retained
A/B tests. Full CanvasUI/Stage12 regressions are still required at closure/final
RC; this intermediate run is not that gate.

Normal isolated three-mod smoke reached network boot and clean shutdown, exit0,
captured2026-09-09T02:20:15.3713422Z. Final JAR SHA256:
`B63A16E291713E3DE9D659F796F571B9B126D03AC1AB94C0F4AE25243EC35614`.
Artifacts, source hashes, tests, matrix and native resolved-asset evidence are in
`evidence/stage-13/cohort-c/`. Exactly three established mods are archived.
Stage12 H rollback JAR is retained and hash-checked; this is not a newly completed
Stage13 coordinated save/reader rollback drill. Player schema9 is unchanged;
compiled plan schema38 records new component semantics. No live deployment.

### Six canonical records, with one explicit activation blocker

| Skill | Implemented authored contract | Evidence limit |
|---|---|---|
| Blunderbuss Shot | Stamina6, CD3;8 deterministic symmetric pellets, each0.22,8m,22m/s,radius0.04;55° horizontal/20° vertical cone; proc1/8 | Native gun base200 is the actual uncharged bullet Damage field, not melee5 or summary DPS; connected scatter/hits unverified |
| Snipe | Stamina12, CD10,2.00 uncharged Weapon Power,one eligible arrow; fully-charged metadata | Production activation rejected before payment: `NATIVE_BOW_MAX_RANGE_UNVERIFIED`. The48m/45m/s/r0.10/g0 values are explicitly the master's development fallback, not a native-production claim |
| Explosive Flask | Stamina8, CD8;14m/s,g9.81,18m placement reach,r0.20,low-angle solution,3s safety; impact-only1.30 explosion,r3 | No extra direct damage, status or expiry explosion; connected collision unverified |
| Bomb Toss | Stamina8, CD9;15m/s,g9.81,20m placement reach,r0.20,3s airborne limit;1.50 explosion,r3.5 | Enemy impact or first-ground+1.5s fuse, once; airborne expiry safely detonates at last valid observed point; connected timing unverified |
| Arcane Missiles | Mana16, CD7;5×0.42,18m/s,26m travel/lock,r0.22,2s life; releases0/.08/.16/.24/.32s,180°/s steering,proc1/5 | Selected live eligible target first, otherwise nearest LOS enemy within6m of committed aim; connected steering/launch timing unverified |
| Fireball | Mana18, CD7;18m/s,30m,r0.50; direct1.55 plus splash0.90/r4; Burn5s/r3 | Separate direct/splash contact components; radius modifiers do not enlarge primary carrier; connected damage/status unverified |

Installed charged Shortbow Strength4 resolves85m/s,g25,radius0.075. The audited
root/config/parent/model path and ProjectileConfig/BallisticData API do not
provide the required finite maximum bow range. That missing native validation
boundary is preserved; absence in these paths is not proof that Hytale can never
support it. Snipe is not silently converted to the development fallback. The
gate survives Orbit conversion. Both Snipe and Bone Cage are now distinguished
in the matrix as `COMPILED_PROFILE_WITH_EXPLICIT_RUNTIME_GATE`, not mislabeled
as ordinary implemented profiles awaiting only client QA. Numeric construction
failures still fail the test independently.

Native startup resolves19 RPG projectile configs/models and15 exact registered
equipment IDs. Configs retain empty native gameplay Interactions and78 projected
ability items retain zero cost/cooldown, CostTypeNone, the existing bridge only.
Gun's native charged interaction cost is never invoked by the RPG bullet carrier.
Unknown equipment IDs still fail closed. Existing native damage causes, resource
and cooldown authority remain the sole gameplay path.

### Shared mechanisms and why they changed

- A pattern is planned as one committed cast. Every pellet/missile keeps root,
  skill instance and correlation identity; generation0 does not become a child
  merely because it is missile2. Volley multiplies carriers and normalizes proc
  coefficient without multiplying payment. Admission reserves the whole pattern
  plus promised Barrage/Echo work atomically, with24/caster and512 global carriers.
- Delayed missiles are removed from the bounded queue before allocation. Actor,
  world and full-radius muzzle clearance are revalidated. A launch more than100ms
  overdue is explicitly rejected, not emitted in an unbounded catch-up burst.
  The immutable plan time remains its scheduled deadline; native flight time
  starts once at allocation, cannot be rebased after observation, and excludes
  queue waiting. Ammo is refunded only before native allocation has been entered;
  an exception after possible native effects never proves a refund is safe.
- Ballistic placement uses a finite low-angle solver and full-radius swept block
  validation before payment and again at release. Placement reach is not confused
  with curved path length. The latter has a conservative finite physics bound,
  while the independent3s lifetime prevents immortal throws.
- Native ticks and impact callbacks share a monotonic flight clock. Unmodified
  contacts also enforce lifetime and clamp range before payload dispatch. Missing
  transforms cancel owned carriers instead of leaving them alive indefinitely.
  Bomb's3s airborne deadline terminates in one safe detonation even with Links;
  cancellation/logout never becomes a detonation trigger.
- First ground contact transfers Bomb to an owned stationary fuse and removes
  its native carrier. A bounded10Hz/full-radius hostile-body check can trigger
  that pending fuse before1.5s. It rejects candidate overflow and respects
  protection/LOS. Removing the fuse entry precedes payload dispatch, preventing
  duplicate detonation on a later tick. Owner/world invalidation cancels it.
- Explosion geometry is an actual sphere/bounds intersection, not a cylinder
  silently including its corners. Ordinary candidate overflow rejects above64;
  UUID ordering is stable. Payloads call the existing native damage adapter;
  Burn requires actual positive Health loss and its own inner radius. Pure
  explosion skills do not receive invented direct damage. Orbit conversion
  returns observed splash Health loss to the existing connection accounting.
- RootEffectBudget and ProjectileLifecycleRegistry previously tracked separate
  effect totals, permitting combined work to exceed the shared48/16 contract.
  They now share one monotonic RootWorkBudget. PRIMARY and its first carrier are
  one effect, while promised carriers, other effects and triggered work compete
  for the same limits. Failed admission publishes no partial batch; carrier
  removal does not refund lifetime work. Tests exercise both allocation orders
  and combined native-secondary/hit-proc limits.
- An authored explosion is a reusable owned contact component, not a newly
  spawned controller for every Orbit sampling tick. Existing projectile/Orbit
  contact ledgers still own deduplication. The three-orb conversion cap and
  shared0.75s victim interval are retained. Cosmetic glints coalesce within50ms
  per victim, but all eight independent damage ledgers remain independent.

Reused models/textures are verified installed templates (bullet, arrow, potion,
bangstick, Fireball/Void orb), not newly designed art. VFX is finite procedural
presentation with10Hz trail sampling. These choices do not prove humanoid
animation, separated missile trails, bomb readability or artist approval.

### Failure evidence and preserved assertions

`failed-profile-audit-1/` retains the failing JAR SHA256
`F7CEB95433B41D279232A8BCAA0700B5502F13A7925B1327B6B37668854D8C87`,
failure XML and matrix gates. The substantive failure was pure-explosion Orbit:
resolver normalization overwrote its positive contact coefficient with a zero
direct coefficient. The fix uses the authored explosion coefficient, leaving
the positive connection validator intact. Two stale assertions were updated:
Snipe is now explicitly gated instead of absent, and current plan schema is38.
No failing test was deleted, skipped, or weakened to accept a broken profile.

The intermediate1286-test/19-config-smoke build before shared-budget corrections
is retained under `pre-shared-budget-audit/`, SHA256
`8ABB2315CC3A80EDFA471CAE91C4916E805D1332AEE5419B24E6BF9E98C69C9F`.
The final1298-test build supersedes it. Final5742 cells/2145 pairs/1000 valid
six-Link graphs have no numeric profile-construction failures; explicit runtime
capability gates remain visible. Protected native resource HUD, XP assets/input,
balance formulas and canonical87/66 catalogs are unchanged.

## Cohort D — ground-targeted movement and an honest native Guard boundary

Local intermediate gate: PASS,1329 affected-family/native-control tests (1305+24),
zero failures/errors/skips. Includes31 movement tests and the retained1298-test
scope. The normal isolated three-mod server reached boot and clean exit0 at
2026-09-09T02:43:46.5731707Z. Exact JAR SHA256:
`4756B50FD4AE31348041AB36CBB619A3E64CD6BB871257BA71E03245A2B55E3C`.
Evidence/archive/rollback hashes are in `evidence/stage-13/cohort-d/`. Player
schema9 remains unchanged; compiled plan schema39 records movement component
and capability semantics.83 runtime records now exist; that is not83 proven
working native skills. The full Stage13 closure/RC/rollback gates remain open.

| Record | Authored behavior | Local/native boundary |
|---|---|---|
| Dive Strike | Stamina8, CD6;10m ground-targeted leap,clamp(distance/16,.25,.8)s,apex1.8; innate20/DEX,one1.15 landing burst,r2 | Full arc and loaded supported destination validated before payment/release; connected leap/landing unverified |
| Jump Strike | Stamina12, CD7;10m leap,clamp(distance/16,.25,.8)s,apex1.5; sword/longsword/spear selects attribute,one1.40 landing burst,r2.2 | No native weapon attack root, extra swing damage or midair landing burst; connected animation/hit unverified |
| Charge | Stamina14, CD8;spear,12m,clamp(distance/20,.25,.8)s,full width1.1;first enemy1.50,request3m push | Stop on first eligible enemy/wall/unloaded path; observed positive displacement excludes fallback; otherwise0.4s Stagger through existing native status/rank policy |
| Void Dash | Mana12, CD6;innate20/INT,9m/.25s,full width1.4;one0.65 Void hit per crossed target | Swept solid collision,loaded supported path,actual position readback; no immunity/teleport/wall phasing |
| Guard | Stamina0 RPG upfront,CD0.3;native held block only | Explicitly disabled at `NATIVE_GUARD_HELD_ITEM_RELEASE_ROUTE_UNVERIFIED`; not a timed RPG reaction/reduction/drain |

### Movement authority and shared corrections

The previous leap path assumed a selected hostile entity and reduced the endpoint
to a horizontal displacement. Ground leaps now capture their terrain point and
height. A pure planner subdivides the actual parabola into bounded chords no
longer than0.25m (conservative arc bound,maximum256 per operation), testing the
avatar's full native box. It no longer preflights a straight chord through a hill
instead of the leap arc. Collision fractions outside[0,1] or nonfinite values
reject rather than becoming allowed movement.

Native movement checks loaded chunks under each sampled box footprint without
requesting chunk loads. It revalidates owner/world/equipment and live swept
collision, then reads the actual transform after Player.moveTo. An external
discontinuity or unconfirmed write cancels rather than crediting requested travel.
Native fall distance is preserved; no immunity or fall-distance reset is added.
Only valid observed travel contributes to the retained Momentum calculation.
Landing strikes require current supported ground; a ceiling/wall interruption
in midair does not fabricate an arrival hit. Existing Quickstep/Pounce tests
remain, now with the shared bounded collision path.

Charge/Void Dash use continuous swept bounds with a direction-relative full
width and2.5m full height. Their SAT includes the forward axis, so expanding
width does not add unauthorized forward endpoint range; diagonal travel does
not silently widen into an axis-aligned square. Contacts are sorted by geometric
fraction and UUID, not registry order. Each cast has a bounded256-victim ledger
and ordinary64-candidate rejection; revisiting a target cannot produce another
path hit. Charge stops before dispatching a later target. There is no final
landing strike for path-damage skills. Both use the existing damage adapter,
resource/CD authority, hostile/protection/LOS rules and observed native outcomes.

The native push helper is shared with projectile knockback and returns measured
position change, not requested velocity. Charge's fallback is exclusive: no
positive observed push means the existing0.4s Stagger request; protection,
resistance and native status projection still apply. A failed/rejected status
emits its actual result, not an unconditional applied claim. Impact Force scales
the authored displacement/fallback and hit penalty; Concentration changes the
real movement footprint. These remain local implementation tests, not native
connected control evidence.

### Guard is not an invented defensive subsystem

The exact installed Sword guard source is Wielding, Angle0/AngleDistance90°,
OnItemChangeBehavior=Finish, generic Damage-cost7. Sword Iron overrides that
cost with10 in its own Guard_Wield interaction variable. The shipped entry also
has its own startup Stamina condition/change and post-guard regeneration delay.
Spear's named block starts with Simple then its Spear_Block_Damage replacement;
the name is not proof of an equivalent held block.

The startup audit resolves the actual Wielding asset and checks angle/cost
fields; `native-movement-guard-audit.json` records that evidence with
connectedProof=false. A separate installed-archive regression verifies the Sword
Iron override and Spear structure. These checks never execute native block/drain.

The current RPG bridge queues a one-shot skill request from a projected Rune
item. It does not carry a verified held-main-hand/release route into Wielding.
Copying generic7 over the actual item10, adding an RPG reduction, or arming a
zero/finite timer would violate Guard's contract. Its record therefore rejects
before RPG payment/dispatch and has no RPG barrier or drain. The central
activation-gate policy is reused by compiler evidence and execution; Reversal
does not mistake this missing held route for a scalable reaction timer.

This is an explicit unfinished native integration boundary, not proof that
Hytale's API can never support Guard and not permission to omit it from the
final capability ledger. Guard requires a verified route/control experiment
before its gate can be lifted. Do not count its profile or resolved Wielding
asset as a working connected Guard.

The first new paid-dispatch-failure fixture incorrectly expected committed=false.
The existing service correctly returns TERMINATED/EXECUTOR_ERROR with
committed=true after the irreversible boundary. All four failure cases now
assert that exact result plus retained cost, cooldown and one dispatch; source
mechanics were not weakened. Original failure XML is retained in
`failed-new-assertion-1/`. Final matrix counts5742/2145/1000 remain exact with
empty numeric profile-failure maps and three separately recorded runtime gates.
All83 projected native ability items remain zero-cost/cooldown bridge items.
Protected HUD/XP/input/catalog/balance paths and owner art are unchanged.

## Remaining required Stage13 work — not optional refinements

- 4 remaining records: Finishing Strike, Execution Strike, Backstab and Frenzy.
  Guard still needs the native held-item/release integration described above;
  Snipe still needs validated native bow range. Next content batches stay
  at most six each and extend shared family primitives.
- Complete shared-authority audit: ordinary/basic-attack recovery hooks, partial
  native failures, admission/derived-work overflow, cancellation, movement
  witnesses, profile/equipment resolution, cross-family mastery and resource
  accounting. A source-gated capability is not a fabricated completed feature.
- Required performance/soak/fault work, including per-frame/per-victim compilation,
  unbounded tracing work, synchronous durable encounter writes and bounded reward
  delivery. Measure declared four-player and scaling profiles; do not turn local
  microbenchmarks into connected tick-budget claims.
- Complete87/66 runtime/eligibility/capability ledger; retained full regressions,
  isolated three-mod smoke, exact packaging/archive, coordinated rollback drill
  and final RC rerun. Stage13 remains IN_PROGRESS until these pass.
- Connected native input, skill execution/hit geometry, animation/readability,
  resource/CD exactly-once behavior, death/logout/unload cleanup, multiplayer,
  C/K UI capability gates, and restart/rejoin remain explicitly UNVERIFIED.

No Stage13 PASS, final release candidate, live deployment or owner-connected
test result is implied by this report.
