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

## Remaining required Stage13 work — not optional refinements

-21 remaining profiles: Dive Strike, Guard, Jump Strike, Charge, Finishing Strike,
  Execution Strike, Backstab, Frenzy, Void Dash; Spear Toss, Crossbow Bolt,
  Blunderbuss Shot, Web Shot, Void Bolt, Bone Shard, Snipe, Explosive Flask,
  Bomb Toss, Arcane Missiles, Fireball and Cold Blast. Next content batches stay
  at most six each and extend shared family primitives.
-Complete shared-authority audit: ordinary/basic-attack recovery hooks, partial
  native failures, admission/derived-work overflow, cancellation, movement
  witnesses, profile/equipment resolution, cross-family mastery and resource
  accounting. A source-gated capability is not a fabricated completed feature.
-Required performance/soak/fault work, including per-frame/per-victim compilation,
  unbounded tracing work, synchronous durable encounter writes and bounded reward
  delivery. Measure declared four-player and scaling profiles; do not turn local
  microbenchmarks into connected tick-budget claims.
-Complete87/66 runtime/eligibility/capability ledger; retained full regressions,
  isolated three-mod smoke, exact packaging/archive, coordinated rollback drill
  and final RC rerun. Stage13 remains IN_PROGRESS until these pass.
-Connected native input, skill execution/hit geometry, animation/readability,
  resource/CD exactly-once behavior, death/logout/unload cleanup, multiplayer,
  C/K UI capability gates, and restart/rejoin remain explicitly UNVERIFIED.

No Stage13 PASS, final release candidate, live deployment or owner-connected
test result is implied by this report.
