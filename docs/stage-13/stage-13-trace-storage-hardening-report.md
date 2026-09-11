# Stage 13 correction Y — production-safe trace storage hardening

Date: 2026-09-11  
Implementation/package commit: `9bcee9d9def437ceaa955357b72d18fb2dbf2cc9`  
Branch: `RPG`  
Build identity: R032 / 0.0.25 / Hytale 0.7.0-pre.1  
Deployment: **NOT PERFORMED**  
Connected-client verification: **UNVERIFIED** (intentionally outside this cohort)

## Outcome

The existing skill and UI event streams now use a bounded active plaintext JSONL segment followed by byte-preserving background GZIP archival. A completed raw segment is not removed until the compressed bytes have been decompressed, compared with the exact raw SHA-256 and byte count, checked for the same event count, moved to their final filename, forced durable, and accompanied by an atomically written/forced `VERIFIED` manifest.

This correction does not alter trace event producers, event payloads, game mechanics, persistence, native ability input, HUD behavior, or connected runtime semantics. The active paths remain `skill-trace.jsonl` and `ui-trace.jsonl`, so existing active-file tooling remains compatible.

The supplied connected traces passed an exact byte round trip:

| Stream | Original | GZIP | Reduction | Events before | Events after | Exact SHA-256 round trip |
|---|---:|---:|---:|---:|---:|---|
| Skill | 6,508,417 bytes | 341,313 bytes | 94.756% | 9,146 | 9,146 | PASS |
| UI | 350,967 bytes | 30,223 bytes | 91.389% | 882 | 882 | PASS |

Skill source SHA-256: `bac7c3e92bb4dd1015122fd4c7e079a37f17eaad7a45d764e7dcc8e0cb7dca0f`.  
UI source SHA-256: `58ce4e019c219ba41f2a97661b4bdb727306c598e6df7d8e979bb2ccf22a3fe9`.

The supplied traces were read in place and were not committed because connected traces contain player/session identifiers. The retained test records only aggregate size, count and integrity evidence.

## Existing architecture audited

- `BoundedTraceWriter` owned caller-side bounded JSON serialization, the 256-record/8 MiB asynchronous queue, writer-thread file append, old numbered rotation, `TRACE_GAP`, failure metrics and close/drain behavior.
- `RpgSkillTraceService` owned skill trace enablement, trace level routing, the skill file path, console rate limiting and the writer lifecycle.
- `SkillTraceRouter` owned the pre-existing NORMAL/DETAILED/PERFORMANCE logical event routing. It was audited and intentionally not changed.
- `RpgUiTraceService` owned UI envelope creation, UI file writing, console rate limiting and shutdown.
- `SkillTraceConfiguration` and `rpg-skill-trace.properties` owned enablement, level and file-size configuration.
- `Phase00Plugin` created `logs/rpg/skill-trace.jsonl` and `logs/rpg/ui-trace.jsonl` and closed both services at plugin shutdown.
- Repository capture tools read the stable active JSONL paths. They remain valid for current activity. Historical reconstruction is now provided through one archive-aware Java reader and an operator wrapper.
- `Stage13BoundedTraceTest` covered the previous bounded queue, explicit gap and file-failure contract. It was strengthened for the new non-destructive rotation contract.

## Files/classes changed

Production:

- `diagnostics/BoundedTraceWriter.java`
- `diagnostics/RpgSkillTraceService.java`
- `diagnostics/SkillTraceConfiguration.java`
- `diagnostics/TraceSegmentWriter.java` (new)
- `diagnostics/TraceArchiveManager.java` (new)
- `diagnostics/TraceArchiveManifest.java` (new)
- `diagnostics/TraceInput.java` (new)
- `diagnostics/TraceArchiveReader.java` (new)
- `diagnostics/TraceArchiveExport.java` (new)
- `ui/trace/RpgUiTraceService.java`
- `phase00/Phase00Plugin.java` (passes the same trace configuration to skill and UI writers)
- `rpg-skill-trace.properties`

Tests/tools/evidence:

- `Stage13BoundedTraceTest.java`
- `TraceArchiveRecoveryTest.java` (new)
- `TraceArchiveFixtureRoundTripTest.java` (new)
- `tools/Expand-RpgTrace.ps1` (new)
- `tools/Package-R032YTraceStorage.ps1` (new)
- `evidence/stage-13/cohort-y/**`

## Append, rotation and thread ownership

`BoundedTraceWriter.submit` retains its prior gameplay-safe ownership boundary: it serializes one bounded JSON record, accounts for queue bytes and submits it to the single trace writer executor. File I/O does not run on the producer. The existing overload/serialization safety behavior and explicit `TRACE_GAP` evidence remain.

`TraceSegmentWriter` is created lazily on the trace writer thread. It owns one long-lived append `FileChannel`, constant-time active-byte accounting, and the only event ordering/rotation decision. Before a complete next record would cross the configured threshold, it:

1. forces and closes the old active channel;
2. atomically renames the complete active file to a unique `<kind>-trace-<UTC>-<sequence>.jsonl.pending` path;
3. immediately opens the replacement active `skill-trace.jsonl` or `ui-trace.jsonl`;
4. non-blockingly submits only the pending path to `TraceArchiveManager`.

The event stays one complete UTF-8 line. A legal event larger than a test segment threshold is allowed to exceed that threshold by one intact event and is never split or truncated. The next append rotates it.

The active default changed from 8 MiB to **4 MiB** for both skill and UI streams. `rpg.trace.segment.maxMiB` can override the configured 1–64 MiB range. `rpg.trace.archive.compression` supports `GZIP` (default) and `NONE`; `rpg.trace.archive.verify` defaults true. `skillTrace.retainedFiles` remains accepted for configuration compatibility but is no longer used to delete history. No archive retention deletion is enabled.

GZIP, whole-segment reads, SHA-256, JSON metadata inspection, decompression verification, manifest writes and recovery scans run only on the dedicated daemon threads `rpg-trace-archive-skill` and `rpg-trace-archive-ui`. The executor has one worker and an eight-task queue. The filesystem `.pending` set is the durable backlog; queue rejection leaves raw data in place. After work completes, the worker scans and drains unscheduled pending files. There is no gzip, hash, decompression, directory scan or new per-event force on the gameplay callback.

## Archive lifecycle and integrity

For GZIP mode, the worker:

1. reads the immutable `.jsonl.pending` bytes;
2. calculates the uncompressed byte count, complete event count, SHA-256, first/last timestamps and optional first/last `traceSequence`;
3. writes raw bytes through `GZIPOutputStream` to `.jsonl.gz.tmp` without parsing/reserializing them;
4. closes and decompresses the temporary artifact;
5. requires decompressed SHA-256, byte count and event count to match the raw segment;
6. atomically promotes it to `.jsonl.gz` and forces the final file durable;
7. writes/forces an atomic sidecar manifest;
8. deletes `.pending` only after the final archive and manifest are verified.

`NONE` mode uses the same rotation, copy verification, manifest and raw-authority rules but leaves a verified historical `.jsonl` artifact.

Manifest schema version 1 records:

- trace kind, immutable segment ID and monotonic segment sequence;
- created/closed timestamps and event count;
- recovered first/last event timestamp;
- first/last trace sequence when the envelope supplies it (omitted when unavailable; never invented);
- compressed/uncompressed bytes and SHA-256 values;
- compression type;
- RPG revision, build version and Hytale build;
- `archiveState=VERIFIED`.

The existing envelope has no monotonic `traceSequence`. It was not added because the specification made it optional and explicitly preferred avoiding reader/schema risk in this low-risk cohort. Segment sequence, exact byte hash and event count provide segment/gap integrity. An additive per-stream sequence remains an optional future improvement.

## Failure and startup recovery behavior

Archive errors call the existing bounded diagnostic failure callback and never escape into a skill, UI action, tick or persistence operation. Warning keys are bounded to 64 entries. Compression failures retain raw `.pending`; manifest failures retain both raw and any verified compressed file; archive-directory failure allows the already-open active trace to continue, even if it must temporarily exceed its rotation target. Trace append failure retains the pre-existing metrics/`TRACE_GAP` behavior and cannot fail gameplay.

At startup the background archiver handles:

- raw `.pending` with no final archive: retry from raw authority;
- `.gz.tmp` plus raw: discard/recreate temporary output;
- orphan temporary output without raw: quarantine, never mark verified;
- final archive plus raw: compare decompressed/raw hashes, finalize/delete raw only on equality;
- mismatched/corrupt final plus raw: quarantine final and rebuild from raw;
- final archive missing manifest: decompress/inspect and reconstruct only recoverable fields;
- partial final raw line: retain the partial suffix in a `.partial-*` quarantine file and archive every complete preceding line;
- crash after manifest before raw deletion: verify the matching pair and finish deletion without duplicating the segment.

Normal discovery excludes `.pending`, `.tmp`, `.partial-*`, malformed manifests and other unverified artifacts. They remain available for recovery/forensics.

## Reader/export and legacy compatibility

`TraceInput` transparently opens plain UTF-8 JSONL or GZIP JSONL. `TraceArchiveReader` discovers, in logical order:

1. legacy numbered files (`.N` oldest to `.1` newest);
2. verified new archives by manifest segment sequence;
3. the stable active `.jsonl` file.

`TraceArchiveReader.exportLegacyJsonl` concatenates decompressed raw segment bytes without parsing or reserialization. `tools/Expand-RpgTrace.ps1` invokes the JAR-contained `TraceArchiveExport` CLI. A direct wrapper proof reconstructed the supplied 350,967-byte UI trace with the exact source SHA-256.

Example:

```powershell
.\tools\Expand-RpgTrace.ps1 `
  -TraceDirectory 'C:\path\to\logs\rpg' `
  -TraceKind SKILL `
  -Output 'C:\path\to\expanded-skill-trace.jsonl' `
  -Jar '.\evidence\stage-13\cohort-y\artifacts\HytaleRPG-0.0.25.jar'
```

## Verification

Focused correction tests were used during implementation. Once coherent, the complete retained validation ran once:

```powershell
.\gradlew.bat :test :nativeControlTest :canvas-ui:test :jar --rerun-tasks --console=plain
```

Result: **2,215 tests**, zero failures, zero errors, zero skips:

- RPG/unit/integration: 2,141
- installed native-control: 53
- CanvasUI: 21
- CustomUI validation: 32 documents valid

Trace-specific coverage includes exact fixture bytes/JSON/histogram equality, identical timestamps, concurrent accepted appends exactly once, intact oversized events, ordered multi-segment export, indefinite archive retention, manifest fields, GZIP and NONE, legacy mixed discovery, bounded queue pressure, delayed compression with continued active writes, permission/archive-directory failure, partial-line quarantine, mismatched duplicate quarantine and recovery at every injected archive boundary:

- during gzip write;
- after gzip close;
- after verification;
- after final move;
- after manifest before raw deletion;
- startup from already-closed/renamed raw pending authority.

A near-full physical filesystem was not separately emulated; deterministic archive-directory/permission failure covers the required nonfatal fallback, while real disk-full behavior remains an operator/environment test.

The isolated smoke then used exactly the packaged candidate plus CanvasUI and HTLibrary. It exited 0, discovered/enabled RPG, reached server boot, resolved retained Stage 03–13 assets/registrations, and shut down cleanly. It did not use or modify the live save.

## Package, hash and rollback

R032-Y package construction started from the accepted R032-X JAR and replaced/added only 20 trace/wiring classes plus `rpg-skill-trace.properties`. Every other R032-X JAR entry, including owner-supplied skill/UI assets, is byte-identical. The distribution ZIP contains exactly three mod JARs.

| Artifact | SHA-256 |
|---|---|
| `HytaleRPG-0.0.25.jar` | `655AC07586D07216D2130EDD81333D7121CD8889AE847049ED6F59707C314B9F` |
| `CanvasUI-0.1.0.jar` | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| `HYTALEDEVLIB-0.5.0.jar` | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |
| three-mod ZIP | `D26954F2421FF9C41018536385355A1111B8D964F621B0BCD0A0A99EFF3FBD87` |

An isolated atomic roll-forward X → Y and rollback Y → X restored each expected SHA-256. No save data was part of that drill. No live deployment occurred.

## Explicit telemetry/behavior declarations

- Telemetry event producer changed: **NO**.
- Event schema field removed: **NO**.
- Existing event field/payload changed: **NO**.
- Numeric precision changed: **NO**.
- Event sampled or coalesced by this cohort: **NO**.
- Event deduplicated by this cohort: **NO**.
- Event intentionally dropped for storage reduction: **NO**.
- Supplied fixture event lost, duplicated or reordered: **NO**.
- Compression/decompression/hash/archive scan on gameplay thread: **NO**.
- New force/fsync per trace event: **NO**.
- Archive failure can fail gameplay: **NO**.
- Old history automatically deleted: **NO**.
- Skill/damage/healing/resource/cooldown/persistence/HUD/input behavior changed: **NO**.

The pre-existing bounded producer queue still reports a `TRACE_GAP` if its established overload/serialization safety limit rejects diagnostic work; this cohort neither removed that safety boundary nor used it for disk reduction. All accepted fixture and test events reconstructed exactly once.

## Remaining limitations and next evidence boundary

- Connected-client behavior remains explicitly unverified because this task prohibited deployment/live QA. Local tests and isolated server smoke do not prove connected rendering or input.
- The archive manager reads one completed segment into memory on its background thread for exact-byte verification. The maximum ordinary configured segment is 64 MiB plus one legal event; this cannot stall gameplay but is relevant to operator memory sizing.
- Existing active-only capture scripts continue to see the stable current file; operators must use `Expand-RpgTrace.ps1` when a diagnostic window spans archives.
- `activeSegmentEvents` is the current writer-session count for the active segment; archive manifests contain authoritative completed-segment counts.
- Physical disk-full recovery should be confirmed in an environment capable of deterministic quota/fault injection. Failure-safe raw retention and archive-directory denial are automated.
- No retention deletion is implemented. Operators remain responsible for deleting verified archives if policy later requires it.

STOP: trace storage hardening is complete, packaged and locally verified. No deployment or connected QA was started.
