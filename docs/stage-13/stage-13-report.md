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

## Remaining required Stage13 work — not optional refinements

- 15 remaining profiles: Dive Strike, Guard, Jump Strike, Charge, Finishing Strike,
  Execution Strike, Backstab, Frenzy, Void Dash; Blunderbuss Shot, Snipe,
  Explosive Flask, Bomb Toss, Arcane Missiles and Fireball. Next content batches stay
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
