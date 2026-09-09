# Stage 13 H — bounded durable encounter group commit

Date: 2026-09-09. Branch: `RPG`. Starting checkpoint:
`f25bf99764285e3fb340bad4bd693a9fe5cd03d8` (Stage 13 G).
Revision/version remain R032 / 0.0.25; the exact H binary is identified by SHA256 below.

## Outcome and limits of this checkpoint

**Stage13 = BLOCKED. This is a tested development checkpoint, not a release candidate.**
The bounded group-commit and asynchronous snapshot-publication implementation is in place.
All **1,963 tests pass**: all 1,913 retained tests, plus 50 new tests; no failures,
errors or skips. The normal isolated three-mod server boots and shuts down successfully.
Packaging, archive checks, retained-test identity checks and rollback checks pass.

The unchanged 60-sample, four-actor, sixteen-victim, **64 durably acknowledged updates
per sample** benchmark is still outside the release gate:

| Measurement | G pushed baseline | H final full-suite run | Required |
|---|---:|---:|---:|
| p50, ms | 125.8220 | 9.0060 | reported, not a pass criterion |
| p95, ms | 258.6245 | 16.0318 | <= 4 |
| p99, ms | 508.1182 | 19.8859 | <= 8 |
| Contribution force calls, 3,840 updates | 3,840 | 119 | actual grouping, no weakened durability |
| Force-only time per 64-update sample, p95 ms | 126.7933 | 5.9862 | contributes to the same 4 ms budget |

Physical contribution barriers decreased **96.901%**. This is real batching, but not
a performance PASS. The release-readiness script exits nonzero and retains the
earliest failing gate `SYNCHRONOUS_DURABLE_ENCOUNTER_CONTRIBUTION_EXCEEDS_RPG_TICK_BUDGET`.
The identifier is retained for continuity; H's API now separates submission from
durable completion, and its measured workload includes waiting for completion.

No live deployment, live data mutation, connected QA, gameplay/balance change,
UI/XP/resource-bar change, native skill change or unrelated Stage 13 completion was
performed. Connected rendering, input, native execution and multiplayer behavior
remain **UNVERIFIED**. The owner does not need to test Hytale for this checkpoint.

## Authority and pre-change audit

The complete G report, its implementation/evidence, retained encounter/reward
tests, release benchmark and crash/rollback tests were reviewed before implementation.
The [G report](encounter-wal-correction-report.md) remains intact as historical evidence.
The first H freeze is in `evidence/stage-13/cohort-h/before/checkpoint.json`; it records
the exact starting commit and source/save hashes. Its private coordinated data copy
is ignored by Git. Work stayed in the C: GitHub checkout; owner `art/` and
`art/lost and found` were not modified. No Google Drive files were written.

The batching-boundary audit found **A + C, not useful B**:

- Native damage: `HytaleEncounterRewards.Inspect.handle -> damage ->
  PersistentEncounterRuntime.damage -> FileEncounterStore.save -> WAL append/force`.
- Effective control and actual absorption use the same synchronous runtime/store boundary.
- Healing is already a bounded higher-level operation: one healing observation can
  update up to 64 eligible encounters, but G forced each saved context separately.
- Although multiple world threads can call the service, one synchronized runtime
  serializes all contribution operations and waits for storage. On one world thread,
  update 2 cannot be submitted while update 1 waits for its force.
- The G benchmark likewise called the blocking method 64 times serially.

A collection delay around those blocking methods could not batch the serial workload.
H therefore introduces explicit ordered submission plus durable completion in the
actual production callbacks. It does not rely on incidental concurrent callers or
an artificial collection window. Legacy blocking methods remain available and
retain force-before-success compatibility; the native hot path uses the new methods.

## Implementation and durability contract

### Submission is not durable acknowledgement

`PersistentEncounterRuntime.submitDamage`, `submitControl`, `submitAbsorb` and
`submitHeal` return `Submission<T>` with two deliberately distinct fields:

- `provisional`: the ordered, in-memory calculation/admission result. This is **not**
  a persisted-success signal and may not authorize a reward, death freeze or teardown.
- `durable()`: a completion stage which succeeds only after the covering WAL force.
  The same Boolean/count is returned at that later durability boundary.

The existing synchronized runtime remains the one contribution-calculation authority.
It reserves bounded persistence capacity **before mutating EncounterContributions**.
Later submissions see the ordered provisional state, rather than recalculating from
the last forced snapshot. Each queued snapshot is immutable. A single WAL writer
assigns every global sequence and per-context predecessor. It uses a group-local
provisional map so repeated changes to one context chain correctly inside a group.

The external persisted-state mirror advances only after force. A failed append,
force, acknowledgement deadline or checkpoint publication poisons the store;
subsequent operations fail with `ENCOUNTER_PERSISTENCE_UNCERTAIN_RESTART_REQUIRED`.
The runtime cannot continue spending provisional credit after uncertainty. It must
be reconstructed from persisted state on restart. There is no retry of individual
uncertain records, no drop-and-continue mode and no volatile write-behind success.

### Physical grouping

`EncounterGroupCommit` owns one long-lived writer thread and drains work already queued
when it obtains ownership. It does not sleep to collect traffic. A bounded group is
encoded into a contiguous buffer, written and then covered by one
`FileChannel.force(true)`. The implementation uses two bounded write sequences per byte group
(retrying partial writes when necessary):
the first complete frame separately, then the remainder. This also supplies a real
process-halt boundary after the first frame, rather than a mock of partial progress.

The existing WAL v1 frame contents and CRC32C are unchanged: individual global
sequence, world/enemy IDs, context predecessor, absolute watermarks, lowest Health
fraction, exclusion state, changed credits and removed credits. No records are merged
semantically. The 16 KiB frame cap remains. Larger encoded batches split at the byte
limit and each physical subgroup is forced. A logical multi-context submission receives
no completion until all its subgroups have crossed their barriers.

The lifetime writer lock and active FileChannel are retained. There is no per-record
open, lock acquisition, temp-file snapshot rewrite, database, dependency, preallocation
or substitution of `force(false)`.

### Bounds and admission

| Boundary | H limit / rule |
|---|---|
| Logical records in one operation/group | 64 |
| Encoded physical group | 256 KiB |
| Individual frame | 16 KiB, plus its existing four-byte length prefix |
| Pending/in-flight records | 256 |
| Pending/in-flight operations | 256 |
| Pending records for one world/enemy | 64 |
| Pending physical groups | conservative derived maximum 40, not an unbounded queue |
| Snapshot credits | existing 256 per context; pending snapshots therefore also bounded |
| Durable acknowledgement deadline | 5 seconds; timeout fails closed, never fabricates success |
| Collection delay | none; sparse work is immediately eligible |
| Checkpoint backlog | 2 pending publications, plus the current active segment |
| Post-durability effects | 256 reservations/tasks, one ordered worker |

The 40-group bound follows from at most eight next-fit 64-record operation groups
within the 256-record admission cap, including a partly filled in-flight group;
each can require at most five byte subgroups at the maximum frame size. The
record/operation/context limits enforce this bound without allocating 40 unbounded
queues. Group size, byte rollover, queue capacity and per-context rejection are tested.
An overloaded reservation is rejected before encounter-ledger mutation and does not
poison otherwise healthy state; the caller can retry after capacity is released.

### Side effects and native object ownership

The native adapter reserves a bounded `DurableEncounterEffects` entry before submitting
contributions. It captures IDs, scalar trace values and the original mastery eligibility
predicate/timestamp on the world thread. Only after durable completion can the ordered
worker emit a successful contribution trace and invoke the existing mastery award path.
It does not carry a native Store, Ref, world, entity, position handle or execution-context
graph into the worker. The shared MasteryRootBudget is already synchronized; its root
identity, ordinal, manual/sustained rules and five-second interval are unchanged.

The existing earned-reward service was inspected: it serializes on the player's holder,
persists the same player/reward transaction and does not call loadout/ECS listeners for
earned awards. Death waits for preceding contribution completions **and** deferred
mastery effects before it reads contributors, captures native participant positions and
freezes the immutable plan. Those native reads remain on the world thread. Exclusion,
detach, delivery and shutdown likewise fence earlier work. Public storage finalization
also awaits preceding submissions; death-capacity admission excludes new submissions
while the runtime freezes its plan.

Death-queue admission, earned-reward intent/receipt, cursor advancement and permanent
completion receipt keep their previous algorithms. H does not introduce a second reward
engine or alter Health, support credit, XP, learning/pity, party share or random-roll
formulas. A crash after a successful force can replay records whose Java waiters did not
complete. Replay is authority; waiter delivery is not a second journal commit marker.

## Asynchronous checkpoint/recovery ordering

The checkpoint threshold remains 1,024 logical records. Before rotating, the writer
reserves one of two publication slots. If compaction cannot keep up, it backpressures
under a deadline **before creating a third sealed segment**. It never discards history
to satisfy a performance target.

At a checkpoint boundary, the writer captures immutable entries through exact durable
global sequence **S**, creates/forces the v1 successor segment and hands the capture to
a low-priority, single-threaded checkpoint worker. New commits S+1..N continue in that
successor. The worker writes/forces the unchanged full snapshot envelope, publishes its
checksummed pointer, publishes/forces the floor, and only then retires the captured old
segments. Pointer reads/publication are protected against deletion races. Dirty cache
entries are pinned until their publication completes. Two queued publications cannot
publish their floors out of order.

Unlike G, the successor is established before background snapshot publication. This
allows newer WAL records to continue while immutable snapshots are serialized. The old
WAL remains recoverable throughout partial publication. Recovery loads the published
checkpoint and applies individually validated later records exactly once. It checks all
CRC/global sequence structure even when a context checkpoint already covers a frame.
Corrupt/torn input is retained and rejected, not truncated into apparent success.

Snapshot serialization/publication has left the contribution hot path. **Successor
creation/force remains on that path** to preserve G's segment-publication safety. This
remaining cost is measured separately; H does not claim all checkpoint-related I/O is
free or hidden. Compaction still competes for the same physical storage.

Shutdown stops admission, drains the writer, then drains checkpoint publication before
releasing the lifetime handles. Worker and checkpoint drains each have a five-second
grace and a five-second interrupt/join fallback. Deferred effects have a five-second
drain. A failure/timeout is reported, retains replayable WAL and does not pretend a
still-running writer has safely released ownership. Close is idempotent and is not the
durability boundary. None of the crash proofs relies on graceful shutdown.

## Verification

Final full run: `gradlew.bat build --rerun-tasks --console=plain`, **58 seconds**,
16 tasks executed. An earlier clean build also passed. Existing native API deprecation
and Java restricted/Unsafe warnings remain; there are no compilation errors.

| Suite | Tests / evidence |
|---|---|
| Root retained + H tests | 1,914 |
| Installed-native control tests | 28 |
| CanvasUI retained tests | 21 |
| Total | **1,963; 0 failures, errors, skips** |
| New group/checkpoint deterministic tests | 35 |
| New real child-JVM group crash tests | 8 |
| New G reader/F preflight rollback tests | 2 |
| New grouped contribution + real reward/death fault composition | 5 |
| Retained G WAL matrix | 26, including all 7 original real halt boundaries |
| Retained pre-WAL F rollback | 1 |
| Retained actual archived Stage12 H coordinated rollback | 1 |

The capture script checks every G suite and test-case identity, not just a total count.
No retained test was removed or weakened. Only the benchmark invocation changed, as
authorized, from blocking `damage` to the production submission/completion API. It
still requires all 64 durable results and all restored contributor assertions.

The 35 new deterministic cases cover a 64-frame/single-force batch, force-before-every
completion, repeated/context/global ordering, serial and concurrent producers, actual
closed-channel append/force errors, failure at five group boundaries, corrupt/torn/invalid
predecessor frames, byte rollover, capacity rejection, sparse delivery, deadline failure,
multi-encounter healing, pending death/exclusion/detach/shutdown, bounded ordered dependent
effects, immutable S while S+1..N commit, two-publication backlog, worker failure, and five
checkpoint publication faults. Retained tests continue covering anti-farm expiry,
watermarks, exclusion tombstones, ownership, exhaustion, full death-queue preflight,
source/pity freezing, restart, and exact-once reward transactions.

The additional grouped-reward tests commit 64 updates in one force, then use the **real**
player, earned-reward and death authorities. Interruptions after freeze, award, cursor,
permanent completion and cleanup are each followed by three restarts. Both recipients
retain exactly one reward sequence, 65 XP and one insight; no recomputation or duplicate
payment is accepted. These supplement, not replace, the retained 43 encounter-persistence
and 42 earned-reward tests.

The extended real-process matrix uses `Runtime.halt(73)` after first frame, all group
bytes, before force, after force, before first waiter, midway through waiter completion,
with another group queued, and during checkpoint S while S+1 is forced. Each new case
is reopened three times. Combined with G's seven retained cases, **15 real halt boundaries
pass**. Complete unforced bytes can survive a JVM halt because the OS remains alive;
their replay is allowed but they were never acknowledged before force. This is not a
hardware power-cut experiment or a claim beyond the existing filesystem/force contract.

## Performance evidence and remaining boundary

Reference: Windows 11 Home, Ryzen 9 7900X (12 cores/24 threads), 63.15 GiB RAM,
Samsung SSD 980 PRO 2TB NVMe, JDK 25.0.4. The fixture uses real Java temporary-directory
storage on C:, not the live RPG world. No native physics, AI, network or rendering is
included; these measurements cannot certify a connected server's full tick budget.

All 60 samples, including cold worker/cache startup and checkpoint/rotation samples,
are retained. Each sample ends only when **all 64** completions are durable. The final
histogram is 45 groups of 1 record, 14 of 2, 14 of 62, 45 of 63 and 1 of 64: exactly
3,840 records and 119 forces. One sample used one force, 59 used two. Encoded byte
histogram: 130x45, 260x14, 8,060x14, 8,190x45 and 8,320x1. No admission rejection
occurred; maximum pending records/operations were 64 and checkpoint backlog reached 2.

| H final phase | Count | p50 ms | p95 ms | p99 ms |
|---|---:|---:|---:|---:|
| Per-group append | 119 | 0.0173 | 0.1147 | 0.1732 |
| Per-group force(true) | 119 | 2.1532 | 3.3796 | 4.2529 |
| Force-only sum per 64-update sample | 60 | 4.9073 | 5.9862 | 8.8125 |
| Per-update queue/group wait | 3,840 | 2.8055 | 3.4821 | 10.1681 |
| Per-update durable-ack latency | 3,840 | 8.6024 | 14.6169 | 17.4096 |
| Snapshot/pointer/floor serialization | 99 | 0.0408 | 0.0693 | 0.3319 |
| Checkpoint write excluding force | 102 | 0.5164 | 0.6851 | 1.4250 |
| Checkpoint forces, including rotation | 102 | 3.6219 | 4.9717 | 5.0960 |
| Successor rotation/capture | 3 | 5.4913 | 5.8976 | 5.8976 |
| Checkpoint scheduling | 3 | 0.0288 | 0.6118 | 0.6118 |
| Checkpoint worker queue | 3 | 0.0433 | 0.7678 | 0.7678 |
| Checkpoint backlog admission wait | 3 | 0.0138 | 0.0234 | 0.0234 |

The final checkpoint tail took another **31.3866 ms** to drain after the workload,
reported separately rather than omitted. The benchmark includes concurrent compaction
throughout its samples; its sample completion definition is contribution durability,
not waiting for an unnecessary snapshot of already-durable WAL. Three rotations and
all 102 checkpoint-related forces are accounted for. Phase totals overlap across
threads and are not added as though they were one serial timeline.

Group worker wall total was 534.5992 ms: 290.4329 ms journal force, 3.6431 ms append,
and 240.5232 ms other wall time (validation/encoding, metadata I/O, rotation, scheduling
and runtime costs). Thread CPU instrumentation reports 140.625 ms aggregate, but Windows
exposes coarse 15.625 ms steps here. Its per-group CPU percentiles are **not sufficiently
resolved to attribute sub-millisecond CPU cost**; other wall time is not mislabeled CPU.

A separate 60-sample sparse run, with no collection window, measured end-to-end p50
1.9744 ms, p95 3.7485 ms and **p99 653.2624 ms**. Its force p99 was **653.0265 ms**;
queue wait p99 was only 0.2453 ms. The rare stall is therefore localized to the synchronous
storage-force boundary, not an intentional batching delay. It is retained in the evidence.

Earlier development runs are not presented as passes or discarded to choose a favorable
result: initial targeted H p50/p95/p99 were 8.7924/16.6580/113.9515 ms; the first full
1,956-test run was 8.6798/15.4317/709.9181 ms; the 1,958-test run was
8.6599/15.3797/18.0192 ms. The latter's full raw workload evidence is retained under
`iterations/full-1958-durable-load.json`; the earlier two are recorded summaries from
their command output. The final 1,963-test run is the current archive and is **not**
the fastest of these runs. No per-run slow sample or startup sample was excluded.

**Next measured boundary:** already-queued batching usually produces two barriers per
serial 64-update burst. Their force-only p95/p99 already exceed 4/8 ms, before the
remaining application work. Rare single-force storage stalls and the synchronous
successor-header barrier additionally defeat a hard tail guarantee. Moving snapshot
serialization off-thread removed that requirement from each contribution, not all
storage contention or rotation cost.

This does **not** prove every file-based design is impossible. Meeting 4/8 ms would
need a separately authorized and measured change: a legitimate production boundary
that can form one full burst before the writer starts, lower-tail-latency durable
storage/runtime behavior, and/or a crash-proven way to establish the successor outside
the timed commit. No delayed acknowledgement cheat, volatile queue, weaker force,
new database, threshold increase or speculative optimization was attempted after this
boundary was measured. On the current implementation/reference run, 4/8 ms is not
achieved. Scope stops here as requested.

## Artifact, smoke and rollback

Exact H RPG JAR SHA256:
`8849F88CB9225E847C05580CE1EBE3E2FB6BE0D487C73AA4408526C1ED6381D6`.
Archive: `evidence/stage-13/cohort-h/artifacts/HytaleRPG-0.0.25.jar`.
The archive contains exactly three mods: this RPG JAR, CanvasUI 0.1.0 and HytaleDevLib
0.5.0. Existing packaged resources, native zero-cost triggers, all protected UI assets,
87 skills, 66 passives, player schema 9 and compiled-plan schema 41 remain unchanged.

The isolated smoke uses installed Hytale 0.7.0-pre.1 build
`e8b4d191fc98a977bf5546a951a7b25473d323e3`, Server SHA256
`EC57E9BD6E2CA3CB16CC5883D42B04A0C64D382DEE532C5BC1CFCF68421E1EE3`
and Assets SHA256 `46F6AA12DECF4F900FCFDF28ECC67568403C37A3AD0A03A4E4CF7653A324AE39`.
It uses a separate repo-local run directory, offline loopback, a real server boot and
normal stop, exit 0. All existing registration/asset audit gates pass. This proves
isolated server setup/shutdown of the exact JAR, not connected contribution callbacks.

Rollback artifacts are never overwritten:

- Immediate G: `rollback/stage13-g/HytaleRPG-0.0.25.jar`, SHA256
  `9B81FAA34D8F41D5C7B43205C52F3E17A44F585D1420C87EB1EEADAB0B7D4FEE`.
  The actual archived G classloader read H's grouped WAL, immutable checkpoint S and
  successor record S+1 correctly. The storage format remains WAL v1/envelope v1.
- Pre-WAL F: `rollback/HytaleRPG-0.0.25.jar`, SHA256
  `F7F55FCF05AFEA2A985AC2801CB4F78D346389C2E135E2DCEA22E1F193BCFA83`.
  F must receive its matching **pre-WAL coordinated backup**, never an H/G data directory.
- Stage12 H: retained `rollback/HytaleRPG-0.0.24.jar`, SHA256
  `C55DD5C1A939E5727AD01945FDC6DC0B7D7ECF1C185D94AC95B0BDAE885EB87B`.
  Its existing real-archived-reader coordinated rollback drill still passes.

Procedure: stop all writers; retain a forensic copy of the current data; choose the
exact verified JAR and its matching coordinated players + earned-rewards + encounters
checkpoint (and the corresponding world/provenance context where required); validate
hashes; restore together into a separate checked target; run the read-only
`tools/Test-EncounterRollbackCompatibility.ps1` preflight; verify recovery before use.
The preflight rejects F on a WAL/checkpoint directory. It is an operational guard,
**not** a claim that the immutable F binary learned to detect WAL itself. It checks
encounter format, not whether arbitrarily selected player/world backups match.
No rollback was performed against the live save.

## Evidence index and handoff

Under `evidence/stage-13/cohort-h/`:

- `verification.json`, `test-results.json`, `source-sha256.json`: exact build/suite,
  retained identities, source/protected-content checks.
- `hardening/durable-load.json`, `hardening/group-sparse-load.json`, `hardening/group-crash-matrix.json`:
  raw performance samples, force/group counters and new real halt matrix.
- `release-readiness.json`: explicit failing performance and retained release gates.
- `server-smoke-summary.json`, `server-smoke.txt`, native audit JSONs: isolated server evidence.
- `artifacts/`, `rollback/`, `checkpoint-manifest.json`: exact binaries and checksum-verified archive.
- `before/checkpoint.json`, `live-state-verification.json`: pre-change boundary and read-only no-deployment verification.

Reproduction uses `JAVA_HOME` set to the pinned JDK, then full `gradlew.bat build
--rerun-tasks`, `Run-Stage13CohortSmoke.ps1 -Cohort h`,
`Capture-Stage13CohortEvidence.ps1 -Cohort h`,
`Test-Stage13ReleaseReadiness.ps1 -Cohort h` (expected nonzero for BLOCKED), and
`Write-Stage13CheckpointManifest.ps1 -Cohort h`. Existing archives reject replacement
by a different binary; use a fresh evidence cohort for a changed implementation.

Do not begin connected QA or unrelated Stage 13 work from an inferred PASS. The remaining
native integration, full-system load, 200-player scaling, master fault closure and final
release-candidate rerun gates from F/G are unchanged. This H checkpoint is a safe,
measured stopping point with real batching and preserved force-before-durable-completion,
not completion of Stage 13 or of the larger RPG project.
