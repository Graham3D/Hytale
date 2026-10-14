# Master Enemies v1.2 — Stage 2 engineering handoff

Stage 2 implements isolated offline planning and persistence under ME12-ARCH-AD1
and ME12-STAGES-4P-01. Production v2 births remain **DISABLED**. Stage 1 remains
**BLOCKED_OFFLINE_COVERAGE**, Monster Affix remains **OPEN**, and `ownerAccepted=false`.
There is no deployment, active-save migration, native server launch, or Stage 3 authorization.

## Source and evidence authority

The starting commit is `b0844f8a013305cffa0b6a9c8912dad571027c3c` on
`codex/me12-stage1`, verified against its remote, final manifest, and Guardian
receipt: 4,325 JUnit and 16 Python tests. The older snapshot checkpoint
`2d021f9c727c6de039c26e87fc32488d6f67f53b` is not the tested final source.
The snapshot's historical `stage2Authorized=false` is preserved; the owner's
subsequent explicit Stage 2 authorization controls this pass.

Development is on `codex/me12-stage2`, in the separate
`C:/Users/Zemio/.codex/worktrees/me12-stage2/Hytale-main-clean` checkout.
Revision: `R262ME12S2-CANDIDATE`; version `0.2.0-R262ME12S2-CANDIDATE`;
native target `=0.7.1`. This is a provisional candidate, not an accepted cumulative revision.
Implementation checkpoint: `572ee68ffeaed4936bd9630e9a029511139e27b5`;
its reviewed verification successor is tested and published as the final HEAD.
The exact final tested commit, test totals, build hashes and publication results
are in `build/reports/me12/stage2-final-manifest.json` and
`build/guardian/verification.json`. Those generated receipts supersede preliminary
timings in this document. The final cumulative log is
`build/guardian/me12-stage2-final.log`.

Preserved dependencies:

| Input | Exact identity |
|---|---|
| Reviewed remote main | `b839526e7106f780648c315692b04bb372b58d9e` |
| Elite reviewed selective dependency | `3327a5650cadf0173b336072f614a13c63cecc56`, QA_PENDING |
| HRO reviewed selective dependency | `f7d414e6348c84653d03a51117bdfe2fc427dded`, QA_PENDING |
| Hytale server SHA-256 | `a60fa0b31f1418691f8d8f7cc1159693e3ec504d4f0567fb91493a842d71f8ca` |
| Hytale assets SHA-256 | `b9164eb07d7c42e697d208ba3d35078a9b4a0125bef6261847a2f91e293e53b4` |
| Server revision | `69c03ffa58d7e2f46bce8b0356ed206cfc7f10e6` |
| Saved native binding wire revision | `hytale-0.7.0-pre.5.1-larva-owner-qa-1`, deliberately unchanged |
| 309-role CSV normalized SHA-256 | `ea0d3bf680cf90972c8fceedf1763c35f0ee4d45c144359d0f9be0519c291956` |
| Installed Hywind JAR, unchanged | `ee5a5d9ffc7200e2308798332ee1f7cb34c9068d82dc4cb75b32d07ca0c11639` |

All 21 source/policy pins in `ME12_STAGE2_INPUT_SNAPSHOT.json` are checked by
`Me12StaticCoverageTest`. Main, Stage 1, the primary checkout/artwork, installed
game, active Hywind save and release gate are not modified by this pass.

Final source review added a red/green regression for ambiguous policy JSON:
duplicate keys, numeric strings and integer overflow are rejected before archive
publication. The approved policy bytes remain unchanged.

The first cumulative run passed 4,357 JUnit tests and the native patch checks;
SpotBugs required six explicit non-null guards in the new validators. No findings
were suppressed. The failed first log remains available at
`build/guardian/me12-stage2-d52d326a-first.log`; the final receipt requires a fresh
successful run at the corrected commit.

## Implemented boundaries

| Source | Responsibility |
|---|---|
| `Me12Planner` | Immutable static context, exact four-step generator, BigInteger completion DAG, complete feasibility and canonical ownership |
| `Me12Random` | Separate versioned SHA-256 streams and unbiased bounded integer sampling |
| `Me12PlannerCache` | Two off-thread builders, single flight, accounted cache/in-flight memory, request limits, cancellation and generation fences |
| `Me12Policies` | Strict immutable documents, approved asset content, exact revision/digest identities; no policy activation |
| `Me12Identity` | Frozen procedural identity, signatures/ownership, threat and policy references; separate inherited-minion representation |
| `EnemyDescriptor`, `EnemyBirthPlan`, `EnemyRewardContext` | Explicit v1/v2 validation with unchanged omitted-field v1 wire shapes and unchanged v1 arithmetic |
| `EnemyBirthPlanner.planV2Offline` | Values-only composition through existing immunity, inheritance, effect-parameter, pack and palette owners |
| `FileEncounterStore` | Existing sealed authority; archive-before-reference, collision rejection, dependency holds and recoverable projections |
| `PersistentEncounterRuntime` | Policy archive work on existing bounded IO owner; dependency holds do not poison the ordered worker |
| `HywindWorldConfiguration`, `Me12FeatureState` | Existing configuration owner understands DISABLED/SHADOW/ENABLED/DRAIN; ENABLED is rejected in Stage 2 |

No native admission caller or command invokes `planV2Offline`. Uncertified
production contexts fail explicitly as `ME12_DEPENDENCY_UNCERTIFIED_PRODUCTION_CONTEXT`;
they are not zero-mass contexts. Static fixtures are labelled
`CERTIFIED_STATIC_FIXTURE`, which is not a native execution certificate.

The context freezes canonical and concrete native role IDs, source receipt,
generation, binding capabilities/affix routes, defense/status/immunity traits,
base and Horde rosters, guard reachability, and resolved per-selector weights.
Its canonical key includes these fields and every policy digest. Set-valued key
fields are sorted, so JVM collection iteration order cannot alter identity.

The offline composer requires the supplied native candidate roster to match the
compiled snapshot, preserves original entities, checks remaining spawn capacity,
and validates the selected graph path before calling the existing immunity RNG
exactly once per actor. No feasibility probe consumes immunity randomness.
It never downgrades a selected budget or broadens an outgoing attack permission.

## Exact selection and resource contract

The compiler first builds complete name-pair feasibility. It selects a tier pair
with joint mass `qP*qS*I`, then a name pair with authored name-weight product,
then a distinct affix-ID/selector set with product weights. Signatures are locked
and count toward `TP+TS+D`; their constant product is factored out within a
name-pair graph. Reported completion masses therefore exclude that constant.

The DAG processes each operator once, with mutually exclusive selector branches.
State contains the candidate index, selected count, semantic group counters,
future pair/roster flags, and a 27-bit set of reachable allocation triples.
Terminal ownership feasibility contributes one unit, regardless of how many
matchings exist. Selection un-ranks exact BigInteger masses. Ownership is assigned
only afterward, in affix-ID/selector order, preferring prefix filler, suffix
filler, then difficulty while retaining a complete matching. Structurally equal
name graphs share memo data; adding names does not duplicate completion mass.

Analytical results:

- All nine tier masses: **4900, 1750, 350, 1750, 625, 125, 350, 125, 25**.
- Three-affix Normal conditioning: **7/12, 5/24, 5/24** for 1/1, 1/2, 2/1.
- Five-affix count-only normalizers: **0.9975 / 0.9725 / 0.84**.
- Distinct-set toy: **6:10:15**, total **31**, including selector-aware exhaustive-oracle comparisons.
- An actual graph total of **300000000000000000000** verifies arithmetic beyond `long`.
- Adding 100 names preserves tier masses and memo-state counts. A separate test
  adds first feasibility to a previously absent tier. Name weights remain
  independent of differing completion masses.

RNG domains are exactly `me12.tier-pair/v1`, `me12.name-pair/v1`, and
`me12.affix-set/v1`. Encoding is a big-endian 32-bit byte length followed by UTF-8
domain bytes, then the same for seed bytes, then a big-endian 64-bit block counter.
SHA-256 blocks are concatenated, high unused bits masked, and values outside the
bound rejected. A 1,024-attempt watchdog fails diagnostically; zero bounds fail,
singleton bounds return zero without consuming blocks. Legacy and gear streams
are untouched. The independent Python oracle pins 12 vectors / 96 draws in
`src/test/resources/enemies/me12-rng-vectors.json`.

Two fresh JVMs (installed JDK 25.0.4+7-LTS, default and tier-1 compilation) produce
byte-identical 128-plan transcripts. This is offline JVM evidence, not a claim
that other operating systems or connected game runtimes were exercised.
Captured baseline vectors remain **rarity=617, roster=4, gear=729292**.

Measured focused evidence, superseded by the final generated reports:

| Measurement | Observed / enforced |
|---|---|
| 756 configured-role/era/base-roster contexts | Max 71,324 memo states; bound 100,000 |
| Cold compile across that sweep | About 10 ms maximum in the focused run; hard deadline 500 ms |
| 900,000 representative samples | 100,000 each for Trork Warrior, Grizzly Bear and Skeleton Scout in each era; zero illegal plans |
| Distribution criteria | Predeclared seven binomial standard deviations plus seven counts, across 81 tier cells; zero-mass cells exactly zero; no reseeding |
| Warm sampler | Microsecond-scale p95/p99 in the focused report; required p95 <=500 us, p99 <=1000 us |
| Broad Hell graph retained heap | About 11.7 MB by instrumentation; graph accounting 36,549,120 bytes |
| Cache plus workers | About 39.9 MB accounted for the broad Hell fixture, including a 3 MiB worker/request reserve; shared cap 67,108,864 bytes |
| Concurrency | Two builders maximum; single flight verified; 32/world and 256/server request limits enforced |

Warm cache latency is also measured while both builders compile. Missing cache
entries return immediately; world code never compiles or waits synchronously.
State, time, memory, cancellation, stale-generation and cache-collision failures
are engineering errors, never zero feasibility or tier fallbacks. Canceling a
subscriber's future copy does not release retained work's admission slot.
World registrations have explicit lifecycle removal and memory accounting.

Reports: `stage2-distributions.json`, `stage2-static-feasibility.json`,
`stage2-cache.json`, `stage2-heap.json`, and `stage2-replay.json`, all under
`build/reports/me12/`. No connected CPU, rendering, projectile or combat
performance is claimed.

## Persistence, archives and rollback

`EnemyDescriptor.schemaVersion=1` requires absent `v2`; version 2 requires a
validated `Me12Identity`. Existing constructors delegate with null, which Gson
omits. V1's four-affix cap and existing QA exception remain intact. V2 is limited
to natural procedural Unique leaders and their initial minions; no blanket
eight-affix expansion applies to Champion, Normal, authored Super Unique or Boss.

V2 identity freezes the prefix/suffix IDs, text, tiers, signatures and filler
definitions, display name, difficulty bonus, selected IDs/selectors and canonical
ownership, context/source/roster/generation digests, per-affix threat, and exact
policy references. Resolved numerical effects remain in the descriptor's existing
`AffixInstance` list. `EnemyBirthPlan.schemaVersion` must agree with every actor.
The existing root, source attachments, spawn/death contexts, quantity receipts,
journal and checkpoint envelopes keep their own formats; nested records dispatch
their ME versions. Encounter journal format 2 is distinct from ME schema version 2.

V2 minions carry a leader reference and inherited effects, zero own affixes, and
the original frozen minion reward context. The existing minion economics are not
assumed to be an unmodified 1.0 multiplier. V2 leaders carry a versioned reward
reference. Calling a legacy XP/quantity/learning evaluator on such a leader fails
with `ME12_STAGE3_REWARD_EVALUATOR_REQUIRED`; Stage 2 does not apply Stage 3 formulas.
Existing player reward/acquisition intent formats are not extended or rewritten.

The existing writer archives exact policy bytes under content hashes, binds each
asset/revision to its digest, then publishes a complete bundle. Six documents are
retained: the three inactive v2 policies plus the existing affix, balance and
affinity definitions. References cannot be persisted or read successfully without
the matching bundle and documents. Revision collisions and invalid definitions
are rejected. Old referenced content is not garbage-collected or substituted.

Missing/corrupt dependencies yield `Me12PolicyHold`, distinct from write
uncertainty. Tests remove/corrupt a required document, restore its exact bytes,
and resume the same identity through both the store and bounded runtime IO owner.
Policy holds do not turn the whole IO worker permanently unavailable. Actual
uncertain writes retain their existing fail-closed behavior.

Legacy fixtures were produced using the verified b0844f8a serializer, before v2
model edits, and through a separately guarded baseline-bytecode capture. The
captured `EnemyDescriptor.class` SHA-256
`29b108ff2b9659947e71f38c052d6bc183a17b47d593800de463031ca4c460d3`
matches the verified Stage 1 JAR entry. These are synthetic offline encounters
serialized by the actual old implementation, not player-save exports or newly
invented DTOs asserted to be historical. Exact payloads/checksums include roots,
descriptors, inherited minions, authored actors, contexts, deaths, quantity and
reward intents. Preallocated WAL bytes are stored sparsely with original length
and SHA-256, reconstructed exactly only in test temporary directories.

Fault injection covers each new archive/content/revision/bundle, root, birth,
descriptor, native-index and pack reservation boundary. Reopening rebuilds the
same projections without reroll or duplicate identities. The real native actor
identity codec accepts recovered v2 pointers and rejects stale generations.
Mixed v1/v2 checkpoint/journal and frozen quantity replay are tested. Actual
native publication/effect execution and v2 reward delivery remain later-stage
integration gates; the pointer codec is not a substitute for that evidence.

DISABLED retains legacy births. SHADOW exposes only an isolated diagnostic plan
with an independent seed domain and no native/persistence/reward callback.
DRAIN blocks new v2 births while compatible readers and historical archives stay
available. ENABLED is rejected while the release gate is open. Config reload
publishes a fully validated immutable snapshot; old config files gain no default
v2 keys. Rollback after v2 history requires DRAIN and a compatible v2 reader;
rolling back to a pre-v2 binary is unsupported.

## Gate accounting and remaining dependencies

`ME12_STAGE2_GATES.json` maps every N01–N08, S01–S10, P01–P05 and offline P07 gate
to concrete tests. Each keeps `implemented`, `automatedPassed`,
`nativeConnectedPassed`, and `ownerAccepted` separate. Exact-HEAD cumulative
verification is additionally required in Guardian's final receipt.
P05's future native publication and reward-effect portions remain BLOCKED;
P07's connected drain/recovery qualification remains deferred. No offline pass
closes a normative native or owner-acceptance gate.

The 309-ID matrix remains 84 configured IDs, five approved authored exclusions,
179 uncertified/unmapped native IDs and 41 uncertified catalog-only IDs, including
five retired identities. Unknown feasibility stays unknown. Static minimum
feasibility remains 68/84/84; the 16 Normal native-immunity exclusions are unchanged.
The sweep preserves Stage 1 code's `base=3..5` **initial minions**, plus the leader
and two additional members only for Horde, within the existing eight-member cap.
This terminology does not create new spawn permission or roster certification.

Still blocked or pending:

- Exact native/spawn certificates; Goblin Turret and Scarak Seeker modern
  projectile creation, impact, removal and recovery; Firesteel production attachment.
- Owner scope decisions for neutral/retaliatory populations, marker/beacon and
  encounter variants, retired Bear_Voidtaken/Feastmaster successors. No remapping,
  new exclusion or policy downgrade was introduced.
- Elite/HRO gameplay acceptance remains QA_PENDING; Stage 1 mixed legacy/modern
  projectile certificate rejection remains unchanged and included in regression.
- Public Skill and first-copy Passive acquisition remain integration dependencies.
- Connected gameplay, effect ownership, eight-affix presentation and comprehensive
  trace acceptance remain deferred to the complete architecture in Stage 4.

Next owner authorization is isolated Stage 3 engineering against this exact
published Stage 2 commit and its archives/interfaces. Stage 3 must integrate the
bounded reward evaluators and exactly-once effect/delivery owners, acquisition
dependencies, native admission/final publication, presentation and the unchanged
Stage 3 trace contract/COMMANDS registration. It must retain production disablement
until the native and owner gates are satisfied. Stage 3 has not begun here.
