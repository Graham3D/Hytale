# Stage 13 G — durable encounter WAL correction

Date: 2026-09-09. Branch: `RPG`. Baseline: pushed checkpoint `344bfed`, implementation `14a0f42` / Stage13 F. This is an isolated persistence correction, not Stage13 closure or a release candidate. R032 / 0.0.25 and player/compiled-plan schemas 9/41 remain unchanged; the cohort, source hashes and binary SHA256 distinguish this build.

## Outcome and scope

The high-frequency full-snapshot replacement path has been replaced by a compact append-only, force-before-acknowledgement encounter WAL. The unchanged release benchmark still fails its **4 ms p95 / 8 ms p99 per 64-update sample** gate. The measured force calls alone exceed that budget. There is no durability relaxation, hidden write-behind queue, lowered threshold, discarded warm-up sample, database, framework, new dependency, or change to expected gameplay.

No live deployment or live-world modification was performed. Connected Hytale rendering, input, native execution, multiplayer, restart/rejoin and full-system performance remain **UNVERIFIED**. Local recovery tests and isolated server smoke do not substitute for them. Other Stage13 blockers remain outside this correction.

## Why this change

F synchronously reopened/locked the encounter store, reread and validated the full context, serialized/checksummed a complete JSON replacement, created a temporary file, forced it and atomically replaced the file for every accepted contribution. G retains the existing snapshot encoding and low-frequency finalization protocol, but removes those repeated operations from active contribution persistence.

The runtime's `EncounterContributions` remains the gameplay authority. `EncounterJournal` keeps a bounded immutable durable-state mirror solely for transition validation and delta generation. Replay applies recorded absolute credit values and watermarks; it does **not** recalculate damage, support credit, eligibility, XP, learning, party shares, rewards or random rolls.

Production changes are limited to `EncounterJournal`, `EncounterPersistenceTimings`, `FileEncounterStore`, admission around existing operations in `PersistentEncounterRuntime`, and closing the store during plugin shutdown. No executor, skill/resource/cooldown formula, reward formula, XP projection, HUD, native ability integration, catalog or artwork was changed. Archive validation compares protected packaged non-class content against F.

## Storage and ordering contract

1. The store lazily opens `writer.lock` and holds the process-level `FileLock` and channel until close. A second store/process cannot enter between mutations. Plugin shutdown explicitly closes it. Closing is not the durability boundary.
2. Before a runtime contribution changes the ledger, it reserves bounded journal capacity. Healing reserves for its already-bounded affected encounter set, up to 64. A live reservation blocks another reservation until released, including after its final append. Capacity rejection is distinct from uncertain persistence and does not poison an otherwise unchanged runtime.
3. Under the serialized store, the resulting immutable snapshot is compared with the last durable state. Existing spawn identity, disqualification, monotonic watermarks and exhausted-farm checks are retained. Death and exclusion receipts still prevent reopening an ineligible encounter.
4. A binary frame records a global monotonic sequence, world/enemy IDs, previous sequence for that context, absolute first-combat/progress/last-observed/lowest-health/disqualification fields, removed contributor IDs and changed absolute credits. Credit entries retain actor, contribution kind, time and amount. Frames are length-bounded to 16 KiB, include CRC32C integrity protection, and are generated in deterministic contributor order. CRC is corruption detection, not authentication.
5. The frame is appended to the existing open `FileChannel`, then `force(true)` completes **before** the contribution is acknowledged. There is one force for every accepted persisted contribution, with no group acknowledgement or volatile queue. An I/O/force/checkpoint uncertainty fails closed as `ENCOUNTER_PERSISTENCE_UNCERTAIN_RESTART_REQUIRED`.
6. The store's death-plan freeze remains after all previously acknowledged journal forces. Pending death plans, award delivery cursors and permanent completion receipts still use the existing forced atomic JSON protocol. The player earned-reward ledger still deduplicates a crash after award but before cursor advancement. Death queue capacity is now checked under the store monitor before freezing the runtime's in-memory plan, so overload cannot leave a partially frozen death.

An interrupted operation that did not return success may nevertheless have a complete record on disk. Recovery can replay that record; it must never roll back acknowledged records or independently issue a reward twice. A malformed/torn/checksum-invalid journal is retained and rejected, not silently truncated or interpreted as an empty encounter. These tests do not simulate hardware dishonoring a completed force, physical power loss, or arbitrary deletion of complete acknowledged files.

## Checkpoints and deterministic recovery

Checkpointing occurs before admitting an operation that would exceed 1,024 journal records since the last checkpoint. It is not required for each contribution. The policy is a storage bound, not a benchmark exclusion: checkpoint work remains included in timed benchmark samples.

Checkpoints are synchronous and store-monitor confined in this minimal implementation. There is no background worker and no Hytale ECS/world reference in checkpoint work. Moving them off-thread is not needed to establish the measured force boundary and was deliberately deferred rather than expanding scope.

The durable layout is:

- `contexts/<prefix>/<key>.json`: original schema-1 full snapshot, retained as the legacy base.
- `journal/<20-digit-first-sequence>.wal`: versioned/checksummed header followed by compact frames; one long-lived active channel, rotated only at checkpoints.
- `checkpoints/<prefix>/<key>.json.<sequence>.snapshot`: immutable full snapshot using the existing exact `{schema, checksum, payload}` envelope and `Snapshot` shape.
- `checkpoints/<prefix>/<key>.json`: checksummed atomic pointer to that context's checkpoint sequence.
- `checkpoint-floor.json`: checksummed global recovery floor.
- Existing `excluded/`, `pending/`, `deaths/` retain their meanings and formats.

Publication order is full snapshot force/atomic publish → pointer force/atomic publish → successor journal header force/atomic publish → global floor force/atomic publish → retired segment cleanup. Old segments remain replayable until every required checkpoint is durable. Obsolete snapshot files are removed only after their replacement pointer has been published. A crash after rotation reuses the already-published empty successor on restart.

Startup validates the recovery floor and segment ordering, loads the checkpoint or original context, and replays later records deterministically. Global gaps/duplicates/reordering, inconsistent per-context sequence chains, invalid headers/lengths/kinds and CRC mismatches fail closed. A context checkpoint may already cover records still present because a checkpoint was interrupted; those records are integrity/sequence checked but not applied twice. Checkpoint state ahead of the available journal is rejected. Permanent exclusion tombstones are overlaid after recovery and cannot be cleared by old journal data.

The cache is bounded to 4,096 contexts; clean entries can be evicted, and dirty entries are bounded by the checkpoint record budget. Segment enumeration/replay and record allocation are bounded. Unique incomplete temporary files are never replayed as committed data. As with the retained snapshot protocol, durable persistence relies on the operating system/filesystem/device honoring `force(true)` and atomic replacement; no new claim of power-cut certification is made.

## Verification and measurement

Authoritative final counts, timings and binary hashes are in [cohort G verification](../../evidence/stage-13/cohort-g/verification.json), [full test inventory](../../evidence/stage-13/cohort-g/test-results.json), [final benchmark](../../evidence/stage-13/cohort-g/hardening/durable-load.json), and [release-readiness result](../../evidence/stage-13/cohort-g/release-readiness.json). The retained runner must exit unsuccessfully while the performance gate remains unmet.

The final RPG JAR SHA256 is **`9B81FAA34D8F41D5C7B43205C52F3E17A44F585D1420C87EB1EEADAB0B7D4FEE`**. The exact-build final command was `gradlew.bat build --rerun-tasks --console=plain` (all 16 applicable tasks executed, full suite passed); preceding clean builds also passed. The isolated three-mod smoke completed at **2026-09-09T12:44:27.4777842Z**, exit 0, with all retained registration/asset gates passing and clean shutdown. `Capture-Stage13CohortEvidence.ps1 -Cohort g` verified 1,913 retained tests, matrix/archive checks, zero-cost native triggers, unchanged protected packaged content, and exactly three archived mods. `Test-Stage13ReleaseReadiness.ps1 -Cohort g` remains an intentionally failing release check, not a waived one.

The baseline instrumented benchmark was run before introducing the WAL and is preserved separately in [baseline/durable-load.json](../../evidence/stage-13/cohort-g/baseline/durable-load.json). It measured p50 **194.3553 ms**, p95 **253.3057 ms**, p99 **1699.2053 ms**. Its 3,840 snapshot forces had p50 **1.8603 ms** and p95 **2.0453 ms**. Prior F's historical p95 **731.5307 ms** is retained in F; the fresh baseline is not substituted for or presented as that earlier measurement.

The first targeted WAL run, preserved in [targeted/durable-load.json](../../evidence/stage-13/cohort-g/targeted/durable-load.json), measured p50 **128.7582 ms**, p95 **262.7196 ms**, p99 **488.9012 ms**. Journal append p95 was **0.0192 ms**, journal force p50/p95 **1.8313/1.9844 ms**. This is a median improvement, **not** a passing gate or a consistent tail-latency improvement. The final run additionally records the sum of journal force durations within each unchanged 64-update sample, separately from application/checkpoint cost.

The original 60 samples, 4 actors, 16 victims, 64 synchronous accepted updates per sample, post-load contributor assertions and 4/8 ms limits are retained. Timings separately cover journal append, journal force, checkpoint serialization, checkpoint write excluding force, and checkpoint force. Header rotation force/write are included in checkpoint phases. Production samples are bounded to 8,192 per phase; cumulative totals/counts remain available. Benchmarks reset instrumentation after fixture creation, without discarding workload samples. No native physics, AI, networking or rendering is timed here.

### Recovery matrix

Final exact-build full regression results: **1,913 tests** (1,864 root + 28 native-control + 21 CanvasUI), zero failures/errors/skips. Final 64-update timings: **125.8220 ms p50 / 258.6245 ms p95 / 508.1182 ms p99**. The journal-force-only sums within those same samples are **117.6058 / 126.7933 / 499.8145 ms**. Per-call journal append p95 is **0.0103 ms**, force p95 **1.9974 ms** (maximum **375.4538 ms**). Thus even excluding all application and checkpoint overhead, the force-only p95 is about **31.7 times** the 4 ms budget. The measured C: disk is a Samsung SSD 980 PRO 2TB, NVMe; this identifies the reference storage, not a diagnosis of its firmware or hardware health.

For the 3,840 accepted updates, G performs 3,840 appends/forces and only 48 full context snapshot writes across three checkpoints, plus 48 pointer and three floor writes. Checkpoint serialization p95 is **0.0851 ms**; checkpoint write excluding force p95 **0.5288 ms**; checkpoint force p95 **3.7717 ms**. The latter two phase counts are 102, including the three successor journal headers. Checkpoint stalls remain visible in the full sample measurements; they are not hidden by the force-only diagnostic.

The added matrix covers compact/forced acknowledgement without snapshot replacement; repeat restart replay; lifetime writer exclusion; actual closed-channel I/O failure and fail-closed retry; automatic checkpoint/retirement/envelope preservation; torn/checksum/sequence/duplicate/missing journals retained on rejection; support-credit/watermark/exclusion preservation; pre-mutation journal admission; full death-queue rejection and successful retry after draining; and rollback through the actual archived F binary.

All seven WAL/checkpoint boundaries are exercised both with injected failure and with a separate JVM calling `Runtime.halt(73)`, bypassing close/shutdown hooks: after append, after force, after snapshot file, after pointer, after all checkpoints, after rotation, and after global floor. Reopening, continuing checkpoint/append, and contributor uniqueness are asserted. The existing Stage12 matrix still exercises every death/award/cursor/completion/cleanup boundary and exact-once player reward delivery, plus support, anti-farm, corruption and persistence regressions.

Existing tests were not removed and assertions were not relaxed. Fixtures that previously opened another store without closing the first now explicitly close/reopen the lifetime writer, retaining actual restart checks. One assertion using an old runtime was moved before its simulated restart; its behavior and assertion remain. The evidence script rejects any missing F test case, reduced F suite, missing new WAL matrix, altered protected content or failed smoke gate. Full retained Stage01B/CanvasUI/Stage02–13 regressions, archived G/H rollback tests, matrix checks and normal three-mod smoke are retained.

## Rollback and operations

**Do not put the old F JAR on a post-WAL encounter directory.** The unchanged old reader does not understand WAL records and would see stale base context data. Rollback requires stopping all writers and restoring the matching pre-WAL coordinated backup of players, earned rewards and encounters together (and the matching world backup when applicable), with the archived prior mods. Never restore only one ledger or copy a live half-written store. G does not provide an in-place downgrade converter.

The pre-WAL F JAR SHA256 is `F7F55FCF05AFEA2A985AC2801CB4F78D346389C2E135E2DCEA22E1F193BCFA83`. `Stage13JournalRollbackTest` actually loads that archive, creates/persists an old-format contribution, copies its checkpoint, mutates/checkpoints a separate upgraded copy through the WAL, restores the pre-WAL copy to a fresh directory and verifies the old binary reads the exact original snapshot. The existing archived H coordinated player/reward/encounter rollback test remains separate and retained.

The cohort's private pre-change save copy is excluded from Git. Reports, code, artifact hashes and test evidence are saved in the C: GitHub repository, not Google Drive. Owner `art/` remains untouched. Exactly the established RPG, CanvasUI and HytaleDevLib mods are archived; this does not authorize live deployment.

## Remaining boundary

The application now uses the requested durable journal, but a 64-call serial workload requires 64 completed durable forces. The force-only sample measurements demonstrate that the reference machine's synchronous filesystem/device flush path alone cannot fit this release budget. An individual force's p95 being below 4 ms is **not** a pass: the gate applies to the entire 64-update burst. Storage-driver/cache/contention causes below the force call have not been separately isolated, so this is an observed synchronous storage-stack boundary, not a claim of defective hardware.

No further gameplay or unrelated Stage13 changes were attempted. Stage13 remains **BLOCKED**, with the original release-readiness threshold and connected-evidence requirements intact.
