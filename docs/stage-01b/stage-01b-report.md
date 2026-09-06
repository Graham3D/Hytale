# Stage 01B — RPG backend foundation report

Status date: 2026-09-06  
Branch: `RPG`  
Starting commit: `dd5766553ab1a73af6807cd44bb0172e407efa7f`  
Ending implementation commit: `8a933e7894cf0c8264451d2f0cd51dbf39fbc6bb`  
RPG revision: `R009`  
Hytale target: `0.7.0-pre.1`  
RPG version: `0.0.2`

## Decision and scope

R009 implements the server-authoritative Stage 01B foundation while CanvasUI
interaction work is intentionally paused. It does not implement damage,
projectiles, combat execution, cooldowns, resource consumption, progression
awards, or any other Stage 02 behavior. Canvas coordinates and widgets are not
part of the persisted graph contract.

The backend is suitable for continued data, graph, compiler, persistence, and
diagnostic development. Stage 01B is not being declared complete until the
required connected-client command and restart/rejoin sequence is observed.

## Canonical content model

The build contains generated JSON catalogs derived from the authoritative
master specification:

- 87 canonical skills in `rpg/catalog/skills.json`;
- 66 canonical passives in `rpg/catalog/passives.json`;
- typed `SkillId` and `PassiveId` values;
- exact lookup plus case/space/hyphen/underscore-insensitive name resolution;
- fail-closed ambiguous fuzzy matches;
- the source label Expanded Area resolves to canonical Expanded Radius.

All 87 × 66 skill/passive pairs return a typed compatibility verdict. The
compatibility service evaluates required, forbidden, and added/removed tags,
family constraints, copy limits, and explicit conflicts; it does not infer
compatibility from display names.

## Slot and graph invariants

The permanent topology is fixed and typed:

- four skill slots: `skill01` through `skill04`;
- six passive slots: `passive01` through `passive06`;
- two joints: `joint01` and `joint02`;
- each joint accepts at most two incoming edges and one outgoing edge;
- a passive or joint has at most one outgoing edge;
- only passive → skill/joint and joint → skill/joint relationships are legal;
- self-links, cycles, unterminated routes, unknown nodes, empty source slots,
  invalid target slots, and incompatible terminal skill routes are rejected.

Direct routes, one-joint routes, and chained-joint routes are supported. Six
passives may specialize one skill when compatibility and joint capacities
permit it. Edges carry stable IDs and schema versions; the persisted graph is
independent of any eventual Link Tree screen layout.

## Schemas introduced

`RpgPlayerState` schema version is **2**. Its server-owned fields are:

- player UUID, level, current XP, pending level-up points;
- STR, DEX, INT, WIS, and LUCK attributes plus unspent points;
- learned skill IDs and owned passive counts;
- exactly four equipped skill IDs and six equipped passive IDs;
- exactly two joint IDs and the link-edge list;
- skill mastery, state revision, and explicit degraded-state reasons.

`LinkEdge` schema version is **1**. `CompiledSkillPlan` schema version is **1**.
The persistence envelope schema is **1** and includes a SHA-256 checksum.

A deterministic v1 → v2 migration maps legacy player, level, XP, slot, and
revision names. Missing optional collections are normalized without changing
the fixed slot counts. Unsupported future state versions fail closed.

## Persistence and transaction behavior

`FileRpgPlayerStateRepository` stores one checksummed JSON envelope per player
beneath the RPG plugin data directory. Writes use a temporary file followed by
an atomic replace when the filesystem supports it; an existing state is copied
to `.bak` before replacement. An unreadable file, checksum mismatch, or player
UUID mismatch is refused rather than silently reset.

Graph corruption is isolated from unrelated progression. Invalid edges are
removed from the working graph, level/XP/attributes and other progression are
preserved, and a `GRAPH_RECOVERED` degraded reason is retained. The next valid
save backs up the prior file.

All command mutations flow through one transactional service:

```text
copy current state -> apply proposed mutation -> validate/compile
-> persist atomically -> replace cached state
```

Validation, compilation, or persistence failure leaves the cached and stored
last-known-good state unchanged. The development entitlement mode is explicit
and configuration-controlled; it grants catalog content for testing without
pretending that the future progression/ownership system already exists.

## Compiler contract

`LinkCompiler` is pure and deterministic. It emits no combat behavior. Its
result includes:

- terminal skill slot/ID and plan hash;
- final family and sorted compatibility tags;
- stable passive application order and resolved graph routes;
- targeting/family conversion, geometry, multiplicity, continuation,
  resource, power, and trigger operations;
- VFX and sound recipe IDs;
- baseline spawn/safety budgets;
- explicit degraded status and reasons for missing unlinked content.

Ordering follows passive priority then canonical ID, never link insertion
order. Continuation ordering is separately canonicalized. Recompiling the same
semantic graph, including equivalent routes through joints, yields the same
plan semantics and fingerprint.

Representative accepted compile:

```text
skill02 = Fire Bolt
passive06 = Fork
route = passive06 -> skill02
continuation = FORK(children=2,angles=-20/+20,depth=1)
result = PASS
```

Representative rejected compile/mutation:

```text
skill01 = Quick Slash
passive06 = Fork
proposed route = passive06 -> skill01
result = INCOMPATIBLE_PASSIVE (rejected)
transaction = rolled back to the prior valid Fire Bolt route
```

Additional required compatibility proofs pass: Fire Bolt + Expanded Radius is
rejected; Frost Nova + Expanded Radius is accepted.

## Public services and command frontend

The reusable backend surfaces are:

- `RpgCatalog` and `CatalogResolution`;
- `RpgLinkGraphService` and `GraphValidationResult`;
- `CompatibilityService` and `CompatibilityResult`;
- `LinkCompiler` and `CompilationResult`;
- `RpgPlayerStateRepository` and `FileRpgPlayerStateRepository`;
- `RpgLoadoutOperations`, implemented by `RpgLoadoutService`;
- `RpgLoadoutView` and `MutationResult`;
- `RpgSkillTracer`, implemented by `RpgSkillTraceService`.

The temporary player command frontend delegates to `RpgLoadoutOperations` and
does not mutate storage directly:

- `/rpg equip <skill01..skill04|passive01..passive06> <name-or-id>`
- `/rpg unequip <slot>`
- `/rpg link <passive-or-joint> <skill-or-joint>`
- `/rpg unlink <passive-or-joint>`
- `/rpg loadout`
- `/rpg compile`

## Skill trace and diagnostics

Default configuration in `rpg-skill-trace.properties`:

```properties
skillTrace.enabled=true
skillTrace.level=NORMAL
skillTrace.maxFileMb=8
skillTrace.retainedFiles=4
developmentEntitlements.enabled=true
```

Structured JSONL is written asynchronously to:

```text
mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/skill-trace.jsonl
```

The trace vocabulary covers load, save, migration, equip, unequip, link,
unlink, compile acceptance/rejection, and reserved future combat lifecycle
events. Records include RPG revision, player UUID, correlation ID, result and
failure code details; execution identity types for root cast, skill instance,
and generation are defined now. Server logs receive concise summaries.

Trace rotation is bounded to one active 8 MB file plus three retained files.
Trace I/O is deliberately non-authoritative: one warning is logged on failure,
while the gameplay transaction proceeds according to validation/persistence,
not diagnostic availability.

## Automated verification

`tools/Verify-Stage01B.ps1` completed successfully for the exact deployed JAR:

- build succeeded;
- 38 tests passed, with 0 failures, 0 errors, and 0 skipped;
- 17 tests exercise the Stage 01B RPG backend;
- 21 retained CanvasUI regression tests continue to pass;
- catalog counts are exactly 87 skills and 66 passives;
- all required classes/resources are present in the JAR;
- Stage 02 implementation patterns are absent;
- state schema 2 and edge schema 1 are confirmed;
- JAR SHA-256 is
  `98E1DFC00620A3C15D6BA5DC08294F99CB0DCEF3A98F1BCF6522F45E54446BD5`.

The RPG tests cover all permanent slots, exact catalog resolution, every
catalog compatibility pair, direct/joint/chained routes, cycles and illegal
relationships, joint capacity, all six passives, required representative
accept/reject cases, deterministic ordering/idempotence, rollback, trace
correlation and failure isolation, v1 migration, file round-trip, simulated
service restart, corrupt-graph recovery, missing-content degradation, and
equip/unequip cleanup.

## Server and deployment results

The isolated bare-server smoke test discovered, set up, enabled, and shut down
R009 without an RPG-scoped exception. Its process exit code 9 is the known
result of the test's immediate `--boot-command=stop`, not a plugin exception.

The exact R009 JAR was then installed to:

```text
C:/Users/Zemio/AppData/Roaming/Hytale/data/pre-release/Saves/RPG/mods/HytaleRPG-0.0.2.jar
```

The real RPG save was booted without an explicit `--mods` argument, preventing
the save-local mods directory from being loaded twice. It discovered exactly
the intended HytaleDevLib, CanvasUI, and RPG JARs. R009 reported 87 skills, 66
passives, schema 2, then enabled and shut down cleanly. No duplicate RPG plugin
or RPG-scoped exception was observed. Base-game asset warnings were present
but are outside this change and did not prevent startup.

The previous R008 RPG JAR is retained locally at
`evidence/stage-01b/R009/rollback/HytaleRPG-0.0.2-R008.jar`. To roll back, stop
the world and restore it as `Saves/RPG/mods/HytaleRPG-0.0.2.jar`. Do not
downgrade schema-v2 player state files in place.

## Connected-client and restart result

The automated suite proves the required mutation sequence and a service-level
restart/reload. The actual save startup proves real plugin discovery and
lifecycle. This environment cannot join a player to the native Hytale client,
so it cannot honestly claim the following observations yet:

- commands accepted from a connected player's chat;
- the valid Fire Bolt/Fork loadout printed in the client;
- the invalid Quick Slash/Fork link rejected in the client without mutation;
- the same real player's state surviving a complete world stop, start, and
  rejoin;
- matching JSONL trace events from that connected-player session.

The exact closure procedure is in
[`client-verification-R009.md`](client-verification-R009.md).

## Files changed

The implementation commit changes 54 files. Major groups are:

- Gradle/JUnit and R009 build metadata;
- typed domain IDs, slots, edges, definitions, plans, and execution IDs;
- generated canonical catalog resources and their generator;
- catalog resolution, graph validation, compatibility, and compiler services;
- versioned state, migration, repository, entitlement, loadout transaction,
  and view types;
- commands and plugin wiring;
- bounded asynchronous skill tracing and configuration;
- four Stage 01B test classes plus shared test support;
- verification, smoke, and installation scripts.

CanvasUI implementation files were not changed in R009.

## Known limitations and stage gate

- Connected-client commands and a real-player restart/rejoin remain unverified.
- Development entitlements intentionally bypass the future ownership pipeline.
- Missing linked content fails validation; missing unlinked equipped skill
  content produces a non-crashing degraded plan.
- State is file-backed per player; no cross-server database or distributed
  locking is included.
- The compiler describes execution but performs no combat behavior.
- CanvasUI continuous pointer interaction remains separately blocked and is
  not a dependency of these commands or services.

**Stage 01B result: BLOCKED only on connected-client/restart evidence.**

**Safe to begin Stage 02: NO.** Do not begin Stage 02 until the R009 client
checklist is completed and this report is revised to PASS. No Stage 02 work was
started in this revision.

## Evidence index

- [`verification.json`](../../evidence/stage-01b/R009/verification.json)
- [`server-smoke-summary.json`](../../evidence/stage-01b/R009/server-smoke-summary.json)
- [`actual-save-smoke.json`](../../evidence/stage-01b/R009/actual-save-smoke.json)
- [`installation.json`](../../evidence/stage-01b/R009/installation.json)
