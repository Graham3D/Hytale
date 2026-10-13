# HRO Stage 3 — comparison, reuse and release qualification

The focused completion pass after Stage 3 is documented in
[HRO_FINALIZATION.md](HRO_FINALIZATION.md); this report remains the Stage 3 record.

Candidate: **R262C1HRO3-CANDIDATE**, version `0.2.0-R262C1HRO3-CANDIDATE`,
Hytale `=0.7.0`. Stage 3 continues Stage 2 commit
`985c9c06f3d370d2afc882299a16682aa0cd66c3` in `C:/hyhro3`, branch
`codex/hro-stage3`. The parent already contains integrated Chapter I Stage 4
`ce3865dd5f81bee95003cb5dde8dc2f959bdac2b`. The owner explicitly requested
safe integration of the completed HRO architecture into main. That permission
does not establish connected QA acceptance or authorize deployment.

Engineering is complete and integrated into remote `main` at
`64a3ba4e051c9b39a453a0c9ea98335eac6d72de`. The full gate passed **4,292 tests**
(3,749 root, 489 native, 49 CanvasUI, 5 Taverns), zero failures/errors/skips,
strict SpotBugs, asset/CustomUI/package checks and Guardian. GitHub PR creation
was unavailable to the connector; the reviewed exact SHA was integrated with
an authorized non-force fast-forward after a fresh main check. This handoff
update is documentation-only and repeats Guardian's exact-commit gate.
Source, hashes, deployment and remaining acceptance are recorded in the
[development baseline](DEVELOPMENT_BASELINE.md).

## Delivered workflow

The same operator-only recorder and seventeen contracts remain the runtime
foundation. Stage 3 adds offline tools rather than gameplay owners. No damage,
resource, inventory, spawn, RNG, save schema, plugin identity or entrypoint
changes are made in this stage. The separate ImmersiveNPCs boundary remains.

Each finalized bundle now includes an implementation/source map, detailed
finding report and cascade groups. Groups require explicit shared operation
correlation; they do not claim causality from adjacent events or timestamps.
The compact extractor emits at most 64 events / 24 KiB of characters and
reports omissions. Evidence locators still identify the original gzip segment
and line. Missing terminal events remain inconclusive.

Three baseline concerns remain separate: the captured implementation identity
and source map, the approved expectation recipe, and the accepted observation.
`baseline` creates one immutable file per contract/scenario using CREATE_NEW.
It records run ID, manifest and checksum-index hashes, capture build, approval
provenance, expected outcome and semantic matching recipe. There is no automatic
"latest accepted" pointer or overwrite. A failure may be accepted as a known
failure baseline; this does not turn that failure into a pass.

`compare` re-evaluates the candidate against current contract code, then matches
exactly one operation by declared semantic facts. UUIDs and wall clocks are
not matching keys. Results distinguish REGRESSION, PERSISTING_FAILURE,
RESOLVED_FAILURE, UNCHANGED_PASS, PERSISTING_BLOCKER, INTENTIONAL_CHANGE and
INCOMPARABLE. Lost evidence, unknown binary origin, ambiguous operations,
changed scenario/configuration/gear/gates/difficulty/role ancestry/native patch,
or unapproved expectation changes cannot become regressions. A changed native
or Hywind binary remains visible; comparison alone never attributes cause to
Hypixel or a particular Hywind change.

Approval fields are explicit operator-supplied provenance, not authentication
or a cryptographic signature. Recipe contexts are declarations: the engineer
must substantiate them from the referenced evidence. The tool cannot approve
gameplay for the owner. Client/protocol gaps in captureBuild remain gaps;
server evidence must not be relabeled client visual acceptance.

## Offline command syntax

The Java entrypoint is `com.inigmasgames.hytalerpg.diagnostics.hro.HroTool`.
It never starts Hytale. Its positional arguments are:

```text
analyze <bundle>
extract <bundle> <operationId>
recover <interrupted-bundle>
recovery-join <earlier-bundle> <later-bundle>
baseline <bundle> <recipe.json> <new-baseline.json>
compare <candidate-bundle> <baseline.json> <recipe.json> <new-report.json>
import <legacy.jsonl-or-jsonl.gz> <format-v1> <new-report.json> <manifest.json-or-dash>
fingerprint <native.jar-or-assets.zip> <bindings.json> <new-fingerprint.json>
compatibility <old-fingerprint.json> <new-fingerprint.json> <new-diff.json>
ui-fixtures <new-fixtures.json>
```

Run through Gradle with `-PhroAction=<action> -PhroBundle=<first-path>`.
For additional arguments, create a local JSON array of strings and pass
`-PhroArgsFile=<array.json>`. This avoids shell quoting of paths with spaces.
Existing `-PhroOperation` and `-PhroLaterBundle` options remain supported.
For example, an args file containing `["C:/diagnostics/recipe.json",
"C:/diagnostics/accepted-combat.json"]` supplies the last two baseline arguments:

```powershell
.\gradlew.bat --offline --no-daemon hroAnalyze -PhroAction=baseline '-PhroBundle=C:/diagnostics/run' '-PhroArgsFile=C:/diagnostics/args.json'
```

New report/baseline parent directories must exist. New tools never replace
existing output and reject destinations resolving inside Saves. Recovery is
the existing explicit interrupted-bundle operation, not a baseline rewrite.
Do not place private captures or approved baselines in Git by default.

A recipe uses schemaVersion 1, one of the seventeen contract IDs, a stable
scenario name and expectation version, a nonempty `match` object of up to
twelve semantic facts, and a `context` object with these required scalar keys:
configuration, featureGates, gear, difficulty, roleAncestry, nativePatch,
adapter, prerequisites, proofScope. Use explicit `none`/`SERVER_ONLY` where
justified; do not fill unknown values merely to force comparability.
Baseline creation additionally requires `approval` with `by`, `at`, `reason`
and `acceptedOutcome` matching the observed PASS, FAIL or BLOCKED outcome.
An intentional expectation change requires a new expectation version and an
`expectationChange` containing the exact `fromBaseline` ID, `by`, `at`, `reason`
and matching `acceptedOutcome`. The original baseline remains unchanged.

## Import and native compatibility boundaries

Explicit parsers `skill-v1`, `ui-v1`, `combat-v1`, `gear-v1`, `spawn-v1`
match the existing producer layouts. Imports retain raw event names, source
checksum, line and decompressed UTF-8 byte offset; scalar normalization is
allowlisted. Original files are never modified. Unknown event fields and
predecessor events are not invented. Missing correlation, mixed builds,
truncated tails and omitted rows are visible. Imports cannot directly become
approved HRO contract baselines.

Legacy `TraceArchiveManager` recovery could write VERIFIED manifests with the
recovery process build. Consequently archive build claims are retained
separately; original captureBuild stays UNKNOWN when it cannot be established.
Recovery build is also unknown unless explicitly identified. Checksums prove
bytes, not provenance or gameplay validity. Inputs are capped at 64 MiB
compressed and decompressed, 64 KiB per line, depth 16; output is bounded to
256 rows / 768 KiB retained event text / 1 MiB total report. Gzip is streamed;
arbitrary ZIP wrappers and archive execution are unsupported.

`docs/hro/native-bindings-v1.json` selects five actual referenced native
classes. Fingerprinting parses class bytes without loading them, retaining
public/protected descriptors, enum fields, interfaces and superclass names,
plus raw and normalized hashes. A bounded custom binding specification can
select meaningful asset JSON, interaction roots and UI documents. JSON key
ordering alone is normalized; all field values/defaults remain. Supported
sibling Parent ancestry is included, and unresolved/cyclic/unsupported parent
references remain explicit gaps. Effective native inheritance merge rules,
codec registration execution and behavior are not guessed. Raw UI documents
retain every selector. Missing bindings are capability blockers. Compatibility
reports retain old/new bindings, migration owner, source authority and hashes.

## UI reuse seam

`HroUiParity` supplies fifteen immutable semantic specification fixtures:
assign/clear skill, connect/break link, search/select library, equip/unequip,
split/merge/transfer, open/close modal, restore HUD, and rejected transfer.
Logical controls are separate from backend selectors. Adapters receive value
objects only, with no player, world, packet sink or persistence repository.
Tests compare submitted commands, validation, state delta and projected
information independently, and detect a wrong reference fixture too.

These fixtures are an offline migration seam, not proof of a working Noesis
adapter or live Canvas/Noesis equivalence. No live Noesis integration is added.
Existing source anchors identify KEEP_AUTHORITY, KEEP_MODEL,
ADAPT_NATIVE_BOUNDARY and REPLACE_PRESENTATION recommendations. Presentation
retirement is conditional on demonstrated parity; unknown future bindings
remain unknown. Only the existing backend owns live actions.

## Qualification and limits

The expanded offline corpus tests actual regression versus persisting failure,
reversed clocks and distinct operation IDs, ambiguous/missing scenarios,
mixed configuration/build origin, approved expectation changes, immutable
baselines, importer privacy/limits/recovery ambiguity, native signatures,
meaningful asset changes, and four independent parity dimensions. Existing
Stage 1/2 tests retain the seventeen-contract fault corpus, real production
bridge tests, no-extra-RNG assertions and actual persistence byte equivalence.

`HroReleaseQualificationTest` repeats four matched trace-off/on workloads with
1,024 distinct item/operation IDs and a concurrent legacy recorder. It records
producer p95/p99, whole-process CPU, elapsed time including worker drain,
admission readiness, first worker write and GC collection time. CPU clock
granularity, pacing and JVM warmup are visible; these are not world-tick ratios.
Existing fault tests cover blocked writer, disk-full injection, burst pressure,
critical evidence retention, simulated ten-minute expiry, shared queue ceiling,
interrupted archive recovery and asynchronous stop. The heap agent saturates
both queue/session and analyzer state; it is never packaged in the mod.

Focused qualification passed 97 root HRO tests and six native production bridge
tests, strict SpotBugs, and JaCoCo review (1,045 covered / 1,168 HRO lines at
that checkpoint). The heap census was 41,300,256 bytes (39.4 MiB), below 64 MiB.
Offline producer p99 was 0.0085 ms; synthetic sixteen-observation batch mean
0.0215 ms and p99 0.0562 ms; disabled warmed allocation zero. Final exact-source
results, package checksum and full suite are recorded by Guardian and the
development baseline. Timing samples are qualification evidence, not promises
for arbitrary workloads; threshold breaches block performance qualification.

Real world-tick p95/p99, client frame times, production cold-start native hashing
and owner-perceived smoothness remain UNVERIFIED. Stage 2's native proc outcome,
complete formula lineage, shared-child generation, runtime asset identity and
client acknowledgment gaps remain explicit. No assertion substitutes for an
unavailable native hook. No standalone server, game, authentication flow, save
reset, fault injection in the owner's world or deployment was performed.

## One owner acceptance pass, after an authorized deployment

Launch normally and enter Hywind. Run `/rpg-trace patch start`, then perform
ordinary QA: assign/cast a valid skill, make a valid and a rejected gear
transfer, hit something with known equipped gear, travel normally, and open
and close UI. Use `/rpg-trace patch mark` only if useful. Stop with
`/rpg-trace patch stop`, then `/rpg-trace patch status` and provide the resulting
folder. A normal reconnect is optional and required for recovery coverage.
No separate subsystem command or manual log analysis is required.

The report identifies exercised contracts and uncovered boundaries. Owner
acceptance requires traceable findings and an unchanged-feeling capture;
offline success alone does not establish either connected behavior or
smoothness. Integration and deployment are recorded separately.
