# Master Enemies v1.2 Stage 3 engineering handoff

Historical handoff for `63fcb510`. The authorized completion attempt is recorded in
[Stage 3 continuation](ME12_STAGE3_CONTINUATION.md). That record supersedes current
provider/configuration status below; this original evidence is retained. The
continuation is still blocked and does not declare Stage 3 complete or Stage 4 ready.

Contract: ME12-ARCH-AD1 / ME12-STAGES-4P-01, Stage 3A–3F. The owner explicitly
authorized isolated implementation against Stage 2, retaining Stage 1 blockers.
State: offline implementation with mandatory native/source/visual gates **BLOCKED**;
connected acceptance **QA_PENDING**. Monster Affix **OPEN**, `ownerAccepted=false`.
Production v2 births remain **DISABLED**; ENABLED still fails closed. No deployment,
active-save migration, main integration, native server launch or Stage 4 work occurred.

## Source and verification identity

| Item | Exact identity |
| --- | --- |
| Stage 3 branch | `codex/me12-stage3` |
| Implementation checkpoint | `1bfbd3484daebb783bd67386259f7cb33683b187`; final successor in exact-HEAD receipt |
| Worktree | `C:/Users/Zemio/.codex/worktrees/me12-stage3/Hytale-main-clean` |
| Starting Stage 2 HEAD | `a030722af4f9e75db012f18eadfb332f50cbf191` |
| Stage 1 parent | `b0844f8a013305cffa0b6a9c8912dad571027c3c` |
| Reviewed remote main | `b839526e7106f780648c315692b04bb372b58d9e` (11 commits behind Stage 2) |
| Selectively reconciled Elite dependency | `3327a5650cadf0173b336072f614a13c63cecc56`, QA_PENDING |
| Selectively reconciled HRO dependency | `f7d414e6348c84653d03a51117bdfe2fc427dded`, QA_PENDING |
| Stage 3 build | `R262ME12S3-CANDIDATE`, `0.2.0-R262ME12S3-CANDIDATE`, Hytale `=0.7.1` |
| Installed mod, unchanged | R262C1ELITE4, SHA-256 `ee5a5d9ffc7200e2308798332ee1f7cb34c9068d82dc4cb75b32d07ca0c11639` |
| Native server, unchanged | 0.7.1 / `69c03ffa58d7e2f46bce8b0356ed206cfc7f10e6`, SHA-256 `a60fa0b31f1418691f8d8f7cc1159693e3ec504d4f0567fb91493a842d71f8ca` |
| Assets, unchanged | SHA-256 `b9164eb07d7c42e697d208ba3d35078a9b4a0125bef6261847a2f91e293e53b4` |
| Stage 2 JAR verified at start | SHA-256 `8e672149c28c41f6716747e5757f3d7d2dc781526528870c64cf02b1320fc369` |

The final exact source HEAD, changed-file inventory, JAR and report hashes, actual
test counts, measurements and publication receipt are in
`build/reports/me12/stage3-final-manifest.json` and `build/guardian/verification.json`.
These ignored receipts are produced after the final source commit. This tracked
document does not predeclare their result. Required cumulative command:
`gradlew --offline --no-daemon check jar testReleaseNativePatch guardianSpotBugs -PguardianSpotBugs=true --continue`.
Log: `build/guardian/me12-stage3-final.log`. Python audit tests and the independent
12-vector/96-draw RNG oracle run separately. Stage 2's 4,357 JUnit / 16 Python
baseline and all 21 pinned inputs remain the comparison baseline.

## Authoritative owners and changes

| Area | Existing owner extended | Stage 3 behavior |
| --- | --- | --- |
| Native birth | NativeEnemyBirthDecision / Reservation / Owner, EnemyBirthPlanner | Async capture/provider seam, revalidation, selected roster only, original-group rollback, archived policies plus durable root before existing publication |
| Planner lifetime | Me12PlannerCache | Compiled-graph leases stay pinned/accounted until callback completion or world unload; 32/world, 256/global, two builders, shared 64 MiB limit |
| Persistence | PersistentEncounterRuntime / FileEncounterStore | Archive and reserve in the existing IO submission; missing policy holds retain their cursor while unrelated deliveries progress |
| XP | EnemyRewardContext / Me12RewardMath, existing Spawn.rewardXp | Exact frozen factors replace only the existing rarity-factor position; v1 arithmetic retained |
| Gear | GearLootService / existing loot-picks, loot, cursors and custody | Freeze K0/N, claims, MF, time and generator revision before children; durable held entitlements, bounded retry, exact-item recovery |
| Acquisition | LearningSources / RpgLoadoutService / AcquisitionCheckpoint | Typed Skill/first-copy Passive opportunity, deterministic v2 roll, existing player transaction/pity and purchase serialization |
| HUD | EnemyDisplayDto / RpgHud / RpgHudCoordinator / RpgEnemyTarget.ui | Full selected-target details, explicit wrapping/height, all signatures/descriptions/defenses; existing overhead owner retained |
| Diagnostics | RpgTraceCommand / HroRuntime / Recorder / Bundle | One existing bounded capture, new ME12 observations and human/JSON sidecars; no second observer service or reward ledger |

`EnemyModifierDescription`, `EnemyTargetLayout`, `Me12RewardMath` and the HRO
ME12 adapters are value/projection helpers, not independent authoritative owners.
There are no policy, population, native patch, affix-weight or attack-permission edits.

## Native integration and certification boundary

The production reservation owner now calls the asynchronous decision seam. A
future certified context provider must capture immutable original roles, sources,
generation, roster, capacity and policies on the world thread. The bounded cache
returns a leased immutable graph/plan. Finalization rechecks the admitted world
lifetime, original source identities, native headroom and pack reservation before
materializing only selected additional members. The existing reservation owner
archives policy bytes and writes the complete root before publishing native effects.
Recovery continues from that root; it does not resample names or affixes.

**This is not a certified production v2 route.** The shipped provider throws
`ME12_DEPENDENCY_UNCERTIFIED_PRODUCTION_CONTEXT`; static fixture evidence is
explicitly rejected at the native seam. The compiler retains its production
certification guard. No production-certified context representation/provider is
invented. DISABLED/DRAIN use the existing v1 birth path; SHADOW has no v2
authoritative side effects. Removing these guards requires actual certification
and a separately reviewed activation change, not a configuration workaround.

Stage 1's 309-ID matrix is unchanged: 84 configured, five authored exclusions,
179 uncertified/unmapped native IDs and 41 catalog-only entries (five retired).
The 16 Normal native-immunity exclusions remain. Static feasibility is 68 Normal,
84 Nightmare and 84 Hell configured-role plans; that is not native certification.

| Dependency | Offline work still needed | Execution evidence still needed |
| --- | --- | --- |
| Goblin Turret / Scarak Seeker | Certified modern projectile creation, owner, impact and removal routes with original mixed-route guard | Real native collision, creator/damage ownership, cleanup and no duplicated receipts |
| Firesteel Golem | Exact production attachment route and provenance certificate | Native attachment/lifecycle and accepted damage/effect observations |
| Exact role/spawn certificates | Concrete role/alias/inheritance and natural-spawn evidence for intended roles; policy scope decisions stay separate | Execution of selected legal effects on certified native actors |
| Native capture provider | Compose real certificates into immutable context through existing spawn ownership | Revalidation/publication/rollback/rebind on actual native opportunities |

Uncertified does not mean excluded, zero probability, or permission to downgrade
a tier. No 309-role scope decisions were made in Stage 3.

## Numerical economics and durable recovery

Threat uses the approved 100/115/130/150 integer units for distinct own affixes.
Factors are canonical decimal strings frozen in an additive v2 context field.
Stage 2 input-only v2 records derive those strings from their frozen inputs, not
live policy. Legacy records omit the extension and keep their original arithmetic.

| Own count | S | B | XP factor, rarity 2 | Q, rarity quantity 1.5 |
| --- | --- | --- | --- | --- |
| 2 | 2.00 | 1.00 | 2.300 | 1.725 |
| 4 | 4.65 | 3.65 | 3.095 | 2.32125 |
| 6 | 6.80 | 5.80 | 3.740 | 2.805 |
| 7 | 8.10 | 7.10 | 4.130 | 3.075 |
| 8 | 9.40 | 8.40 | 4.520 | 3.075 |

XP maximum is 4.7. `mu=min(16,K0*Q)` precedes exact fractional rounding.
K0=0 gives zero. K0=1/Q=1.725 gives 1/2 with upper probability .725;
Q=3.075 gives K0=1: 3/4 (.075), K0=2: 6/7 (.15), K0=5: 15/16
(.375), K0=6: 16 exactly. Tests use 100,000 fixed seeds per row with a
predeclared seven-sigma bound, plus deterministic and monotonicity checks.

For existing pre-rarity chances .15/.05/.02, the B=1 results are
.22275/.07425/.0297; B=8.4 results are .2325/.0975/.04968. The separate
affix allowance remains at most .03, ordinary chance at most .95, and guaranteed
pity does not draw. Wisdom and the existing rarity adjustment stay in place.

Base child IDs/seeds and per-item generator/MF inputs are unchanged. Bonus
children use distinct existing quantity-child streams and guaranteed generation,
without another no-drop roll. No item is generated then discarded for the cap.
The frozen picks receipt precedes all child receipts. The cursor advances once
for an earned entitlement even when generation is held, preserving its recipient.
No-beneficiary v2 defeats retain the existing denial receipt without invoking
the guarded legacy quantity evaluator.

Held states are WAITING_FOR_GENERATOR_REVISION, WAITING_FOR_GENERATOR_CONTENT
and WAITING_FOR_POLICY_ARCHIVE. Already generated exact items retain their bytes
through custody/pickup recovery. The existing gear IO refresh retries up to 16
unresolved rows per scan, fairly rotating through the existing records. A revision
resolver can supply the exact historical generator; the default accepts only the
installed matching revision. Historical generators are **not automatically bundled**.
Before future generator changes, preserve the old evaluator/definitions or restore
them under the hold. Tests remove and restore exact dependencies and prove eventual
completion, unique item IDs and unchanged generation outcomes. Missing dependencies
are not converted into successful delivery.

Fault tests cover manifest/cursor/item writes, all earned-reward write boundaries,
player credit before encounter cursor, full inventory, pending native-save pickup,
restart/replay, and concurrent purchase/learning. Initial minions keep the actual
existing Normal initial-minion context (XP 1.05 in this baseline), not the leader's
threat factor. Whole-pack fixtures report leader and initial-minion sums separately;
Horde population cost is still a balance/connected gate. Currency, Gold Find,
Insight, mastery, milestones, quests, rank and level retain their original owners.

## Public acquisition readiness

| Kind | Real accepted canonical source | Implemented/tested boundary | Remaining dependency |
| --- | --- | --- | --- |
| Skill | None; shipped learning-sources.json has zero bindings | Existing unlock/pity transaction plus typed opportunity and deterministic v2 randomness | Author and verify one canonical source/ability assignment, exact aliases, rank gate, acquisition rarity and native signature behavior |
| First-copy Passive | None; no accepted native source ID | First acquisition uses existing ownedPassives and player journal; purchase/learning serialize | Author/verify canonical Passive source, provenance and explicit minimum encounter rank; Passive purchase tiers are not Skill combat tiers |

Synthetic `fixture_role/fixture_source -> potency` is test-only and is not shipped
content or connected evidence. Already-owned Passive keys, including zero-copy
entries, retain acquisition history; purchases and their costs are unchanged. No
current production path removes those ownership keys. A future importer that loses
historical keys must reconcile provenance before enabling learning, not infer that
missing copies mean never acquired. ME12-R08 remains **BLOCKED**.

## HUD and diagnostic evidence

The existing selected-target surface expands at the top right and polls at 4 Hz;
updates publish only when changed. Full name, rarity/species/level, all eight own
affixes, two signatures, modifier descriptions, inherited effects, immunity and
protection/state tags remain visible. No `+N` or two-line truncation remains.
The accepted overhead name/rarity outline and native health bar remain owned by
their original renderers. Selected-target failures hide that panel and back off;
they do not tear down the overhead renderer.

`build/reports/me12/hud/` contains five Java2D render fixtures: 1280x720 at 1/1.5,
1920x1080 at 1.25/2 and 2560x1440 at 1.5. Text widths, last baselines and virtual
height are asserted. Eight affixes originate in an actual legal planner fixture;
a second immunity tag is a **presentation-only synthetic stress input**, not a
grant or proof that this same combat plan supports two immunities. The native font,
UI scaling, overlap and client rendering remain unverified. ME12-P08/P09 stay
BLOCKED for connected acceptance. No extra glyph/affix carrier entities are added.

Actually registered workflow: `/rpg-trace enemies start`, `stop`, `status`.
Permission `inigmasgames.rpg.trace.patch`, in-game player only; stop is owner-scoped.
The existing single HRO session is bounded to ten minutes; see
[COMMANDS](../COMMANDS.md) for byte/event/reconnect limits and
[future Stage 4 run sheet](ME12_STAGE4_RUN_SHEET.md). Output is outside Saves at
`%APPDATA%/Hytale/UserData/Diagnostics/Hywind/HRO/<runId>/`.
`master-enemies.md` and `master-enemies.json` join existing raw HRO evidence and
checksums. Synthetic examples are under `build/reports/me12/diagnostic-example/`.

The report keeps five outcomes and separate denominators for eligible opportunities,
tier selections, published natural encounters, observed attacks and committed reward
events. It records frozen economic/learning inputs, birth identity/ownership,
generator holds and committed receipts. Native hit readback distinguishes grouped
receipt evidence from individual-affix causality. Mana Burn/Vampiric have direct
resource readback observations; the other 25 operators retain existing raw HRO
combat/status/shield evidence but **do not gain a dedicated per-affix semantic PASS**.
The explicit 27-operator coverage view marks that limitation BLOCKED. Source/native
certification, client rendering and distribution claims cannot pass from a label or
short capture. Missing observations and event loss remain visible. Report detail
rows are bounded; full retained raw streams have locators.

## Performance, gates and next action

The shared compiler still has two workers, 32 requests/world, 256/server and a
64 MiB accounted budget. New leases prevent evicted graphs escaping accounting in
queued native callbacks. Dense fixtures hold all 256 leases across eight worlds,
reject overflow, then unload and prove zero outstanding leases. Ten offline
archive/root/cache cycles retain identity and release accounts; initial focused
measurement was 2,110 ms total and 39,909,088 peak accounted bytes (machine-specific,
not a tick-time benchmark). Final measurements supersede these in the manifest.
Stage 2 heap, probability and cache tests rerun unchanged. Native 8x-density CPU,
collision, carrier and world-thread measurements remain BLOCKED, not inferred from
the offline cache test.

[ME12_STAGE3_GATES.json](ME12_STAGE3_GATES.json) records R01–R12, P05–P10 and
dependent B/S gates on four separate axes. A focused offline pass is not a
normative connected pass. Final verification/publication requires the exact-HEAD
receipts described above. Rollback remains a v2-capable reader/evaluator with new
births DISABLED/DRAIN and retained archives. An old v1-only binary is not a valid
rollback after v2 data exists; no active data was migrated here.

Next engineering step: resolve the named native certificate/provider and public
acquisition source dependencies, complete the missing per-affix execution predicates,
and assemble an explicitly authorized cumulative Stage 4 candidate. Product approval
is still needed for any new source assignments or 179/41 population-scope changes;
neither is silently chosen. Stage 4 then requires its own activation/deployment
authorization and affirmative owner gameplay/visual acceptance. No intermediate QA
is requested and Stage 4 has not begun.

Guardian publication is to the matching feature branch only. Main remains the
reviewed integrated baseline, not the new development candidate. Stage 1/2 and
Elite/HRO branches are retained; their selective dependencies remain QA_PENDING.
The dirty primary checkout/artwork and its newer user-governed AGENTS instructions
were preserved. The Stage 3 tracked AGENTS retains the historical path text unchanged;
the user's current instructions govern. No accepted branch was silently abandoned.
