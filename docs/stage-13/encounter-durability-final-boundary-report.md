# Stage 13 I — final encounter durability boundary assessment

Date: 2026-09-09. Branch: `RPG`. This is a blocked development checkpoint, **not a release candidate or connected-client approval**.

## Outcome and exact checkpoint identity

Final classification: **`RPG_PERSISTENCE_ARCHITECTURE_BLOCKED`**.

The unchanged 60-sample, 64-update real-storage workload measured **4.5529 ms p50 / 7.8891 ms p95 / 16.1037 ms p99**. The required **4 ms p95 / 8 ms p99** gate fails. All **2,053 tests** passed, with zero failures, errors, or skips. **48 real `Runtime.halt(73)` crash-boundary cases** passed, including all 15 retained G/H cases. Isolated three-mod smoke, package/archive checks, and rollback validation passed. No live deployment, live save conversion, or connected Hytale QA occurred.

This correction removed standalone foreground successor-preparation forces and consolidated the workload's checkpoint-related forces from 102 to 9. It did **not** manufacture a shared gameplay transaction for the benchmark's 64 independent damage submissions. Those still required 119 foreground forces across 3,840 records. The independent 40,000-barrier storage qualification passed the requested aggregate p95/p99 in all four modes; it also recorded rare force-call stalls exceeding 100 ms. Neither those outliers nor local functional tests justify a platform-only diagnosis or release PASS.

| Identity | Exact value |
|---|---|
| Starting pushed H HEAD, verified before implementation | `eecd64cc4b7c5345288ffef51083fadc3c8348e4` |
| Validated Stage I implementation commit | `f5f9cb3f9859b55ca263d85f9d63a099933512eb` |
| RPG binary metadata, unchanged by this bounded correction | R032 / `0.0.25` |
| Final archived RPG SHA-256 | `0082FA775EB2C7C42445194E3E0736515ED21CBCEF77BEC42E04ECA1CFBF3D36` |
| Final archived RPG size | 1,812,529 bytes |
| SHA-256 of the complete per-file source/test/resource hash inventory | `1C1244979CA46D5A3E0145EA2453CB40838DBFA708F92E8488E115F2C8117E4F` |
| Master v1.2 Markdown SHA-256 | `750483855846FF6DB2564B4D6D626C2A12F1AC6EBBBBF12232B2C3ECCF667010` |
| Installed Hytale | `0.7.0-pre.1`, build `e8b4d191fc98a977bf5546a951a7b25473d323e3` |
| Installed server SHA-256 | `EC57E9BD6E2CA3CB16CC5883D42B04A0C64D382DEE532C5BC1CFCF68421E1EE3` |
| Installed assets SHA-256 | `46F6AA12DECF4F900FCFDF28ECC67568403C37A3AD0A03A4E4CF7653A324AE39` |

The implementation commit identifies the exact validated source and binary. A following documentation-only publication commit records that implementation SHA and regenerates the report checksum; it does not change Java, tests, resources, or the archived binary. This avoids pretending a Git commit can contain its own hash.

All paths below are repository-relative for GitHub review. The working repository was `C:\Users\Zemio\OneDrive\Documents\GitHub\Hytale`. No task files were saved to the stale Google Drive workspace. The owner's untracked `art/` was left untouched and excluded from commits.

Primary evidence: [I verification](../../evidence/stage-13/cohort-i/verification.json), [release assessment](../../evidence/stage-13/cohort-i/release-readiness.json), [source hashes](../../evidence/stage-13/cohort-i/source-sha256.json), [final durable workload](../../evidence/stage-13/cohort-i/hardening/durable-load.json), and [checkpoint checksum manifest](../../evidence/stage-13/cohort-i/checkpoint-manifest.json). Historical authority remains the [H report](encounter-group-commit-report.md) and [G report](encounter-wal-correction-report.md); neither was rewritten to change the baseline.

## I1–I2: semantic batch audit and the decision not to invent an epoch

Master §12.3 describes a per-server-tick RPG budget in a four-player scenario. `Stage13DurabilityLoadTest`, however, is a pure real-storage stress fixture, not a native tick measurement. Each of its 60 samples submits four actors' contributions against sixteen victims through 64 independent `PersistentEncounterRuntime.submitDamage` calls. It then awaits all 64 durable completions before ending that sample. It does not invoke a production tick-finalization callback or a single native transaction that owns all 64 events.

The production analogue is the independent native damage/control/absorb contribution callbacks in `HytaleEncounterRewards`. The adapter/runtime synchronize ordering, but there is no existing producer-owned, world-wide durability epoch with a legitimate final seal for all those callbacks. Adding one would require a separate native lifecycle/ownership design. This task expressly forbids changing native execution. The fixture is therefore best described as **synthetic worst-case throughput stress over independent gameplay-event submissions**, not a proven one-transaction production cycle.

Consequences:

- The benchmark was not changed to `submitBatch(64)`, and its expected force histogram was not rewritten to 60.
- Existing immutable operation sealing is retained. A producer reserves bounded capacity, produces its ordered snapshots, seals the operation, and receives a shared covering durability receipt. A late append to a consumed reservation fails; a later contribution requires the next reservation/operation.
- There is a legitimate existing multi-context operation: `submitHeal` can credit up to 64 encounter contexts from one healing operation. A new test drives that actual runtime path, proves 64 records share one force and one receipt, and checks that no durable receipt/dependent effect escapes before force. Separate sealed-record tests cover repeated same-context predecessors and cross-context global order.
- This healing proof is not evidence that 64 independent damage callbacks can be relabeled one healing-like transaction. Conditional I2 implementation of a new 64-damage epoch is **not applicable without a real production owner**.
- The fixture submits asynchronously and awaits the resulting receipts. It is not 64 strictly serial force-before-next-submit calls, so this report does not claim the user's release requirement is mathematically contradictory. The unresolved issue is the production ownership boundary needed to make batching deterministic.

The retained writer drains already queued sealed operations immediately. It can consume a small first group while the producer is still submitting the rest. A 0.5 ms collection-window alternative was explicitly applied to both the production factory and benchmark, with the delay included in latency and a separate sparse test. It reduced 119 forces to only 116 and worsened both the full workload and sparse p95. That experiment was **rejected and removed**. There is no hidden sleep or collection window in the final implementation.

### Ordering, authority, and capacity retained

`Submission<T>.provisional` is still not durable authority. Success of its durable completion still requires the covering `FileChannel.force(true)`. Immutable snapshots/deltas, not Hytale entities or ECS handles, enter the writer, preparation, and checkpoint workers. `EncounterContributions` remains the running calculation authority. Replay restores that state after restart.

Existing limits remain: 64 records per operation/dequeued group, 256 KiB encoded physical group, 16 KiB individual record payload, 256 pending records, 256 pending operations, 64 pending records per context, and 40 bounded pending physical groups. Completion/backpressure deadlines remain 5 seconds, with bounded shutdown waits. No capacity limit was increased to pass a benchmark. Admission rejection occurs before provisional ledger mutation. A logical operation that genuinely exceeds the byte cap may require more than one force; its receipt waits for the last covering barrier. The ordinary 8,320-byte benchmark group fits the original cap.

Global sequences and per-context predecessors remain separate checks. Same-context provisional updates are ordered before encoding. Death/finalization, mastery, reward delivery, exclusion, detach, unload, and shutdown retain their contribution/effect fences. Actual persistence uncertainty still poisons the store with `ENCOUNTER_PERSISTENCE_UNCERTAIN_RESTART_REQUIRED`; it is not retried as if nothing happened. Successful barrier completion before a Java waiter runs can leave replayable history, but existing reward/state idempotency still prevents duplicated earned effects.

## I3–I5: prepared WAL v2 and durability mode

`EncounterLog` is a small storage-version interface. The retained `EncounterJournal` implements v1 for old-format maintenance/read/migration tests. New production construction explicitly calls `FileEncounterStore.durableV2(...)`, whose `EncounterJournalV2` implements the following protocol:

1. A background preparation worker creates a unique `.preparing` file, writes an immutable header and an empty activation area, establishes the optional final extent, and forces preparation with `force(true)`.
2. After preparation, the candidate becomes a `.wal2` file and a prepared channel handle. Existence alone does not add it to committed history.
3. At rotation, the writer writes the new segment's activation/linkage bytes and first normal contribution frames. **The normal group force covers both**, with no additional foreground creation/header force.
4. Only after that covering force does Java switch the active handle. The predecessor remains recoverable until checkpoint publication permits retirement. The next spare is prepared asynchronously.
5. If the spare is not ready, the existing bounded backpressure/deadline path waits for preparation. It never silently falls back to a new foreground metadata force.

The 64-byte header contains magic `RPGWAL02`, format 2, its UUID, the derived extent, and CRC32C. The 80-byte activation contains magic `RPGACT02`, format, its own UUID, predecessor UUID, predecessor final sequence, first sequence, and CRC32C. Compact per-contribution frame encoding preserves individual deltas and integrity/ordering checks; records are not semantically merged.

The prepared extent is derived, not guessed:

`64 + 80 + 1024 × (16384 + 4) = 16,781,456 bytes`.

Both growing and preallocated layouts are tested. The final production-like factory uses the bounded preallocated layout. Logical end comes from validated framing, not file size. A clean all-zero tail is unused space; a torn or nonzero invalid tail fails closed without truncation. Header/activation CRC failures, missing required active history, duplicate activation, impossible linkage, and sequence gaps fail closed. A zero-activation candidate is an orphan/prepared file, not a committed successor. Valid startup under the lifetime writer lock may clean such unused candidates only after required manifest/index/history validation succeeds.

Recovery starts at the v2 manifest's active anchor, validates checkpoint-covered prefixes, and replays later frames deterministically. It preserves exclusions, watermarks, anti-farm state, and contributor credit. There are bounded scans for at most 64 activated segments plus four prepared candidates; normal operation has one active and one spare, plus predecessor history awaiting the bounded checkpoint queue. Tests cover multiple orphans, missing/corrupt active history, repeated restart, maximum-sized deltas, and preallocated tails.

Important evidence limit: bytes alone cannot reveal whether a valid activation was written immediately before or immediately after a successful OS force. After process halt, complete unacknowledged bytes may survive and replay as a valid ordered prefix; torn bytes fail closed. The protocol distinguishes prepared/nonactivated files from valid activated history and enforces the durable-ack fence. It does **not** claim an impossible on-disk oracle for whether an earlier `force()` returned. `Runtime.halt` is not sudden power-loss qualification.

Preallocation showed no established standalone latency benefit (see I9). It remains an explicit bounded-layout choice, not a claimed performance win. **All durability barriers remain `force(true)`.** No `force(false)`, `SYNC`, or `DSYNC` variant was promoted. Layout reasoning and process-halt tests do not independently prove the necessary metadata/power-loss equivalence on this Windows/filesystem stack; therefore the conditional content-only experiment was not justified. No native library, database, framework, or new external dependency was introduced.

## I6: consolidated checkpoint publication

`EncounterCheckpointBundle` captures immutable dirty context snapshots through sequence S and packs them with a copy-on-write radix index into bounded version-2 bundle chunks. Unchanged index branches continue to reference earlier immutable bundles. This avoids rewriting the entire historical context catalog and avoids introducing a new cap on all historical encounters.

Each snapshot retains its context identity, sequence, and existing snapshot payload. Bundle framing, bounded JSON shape, SHA-256 envelopes, node references, and sequence/index validation protect those values. The index reference includes bundle UUID, node index, and node hash. A checksummed manifest records format, generation, covered S, legacy recovery floor, active WAL UUID, root index reference, and newly published bundle identities/hashes. Referenced historical index branches are validated too.

Publication is ordered: serialize captured values, write/force each necessary bundle, then write/force and atomically publish the single manifest. Only after the new complete manifest is published may covered predecessor WAL files be retired. A failed unpublished checkpoint leaves the previous valid manifest plus WAL usable. A corrupt or incomplete **published** manifest cannot silently fall back to stale authority.

Bounds are explicit: at most 1,024 dirty records' context capture per rotation epoch; 32 entries per leaf; radix depth at most 64 hexadecimal nibbles; 16 MiB maximum bundle chunk; 64 chunks maximum per epoch; bounded node size; two pending checkpoint slots; four cached bundle files. Journal/context admission caches retain the existing 4,096 active-context bound, with captured entries pinned while needed. A 48-context test proves a fitting epoch publishes exactly **two forces**—one bundle and one manifest—not a forced snapshot and pointer per context. A second generation test proves unchanged old index branches remain reachable.

In the final workload, three checkpoint epochs produced **three bundles totaling 43,332 bytes**, three bundle forces, three manifest forces, and three successor-preparation forces. That is **nine checkpoint-related forces versus H's 102**, using the same broad accounting category; publication alone is six. Serialization/writes may run in parallel with foreground calculation, but they are not claimed to be free of storage contention.

Historical bundles are intentionally retained, including unreachable bundles from superseded/failed publications. Safe bundle garbage collection is deferred; disk growth and long-history startup traversal cost are not certified by this cohort. Working sets/files/epochs are bounded, but that is not a claim that total retained history never grows.

## I7: foreground-priority barrier arbitration and validation overhead

`EncounterBarrierArbiter` owns one physical encounter-persistence force permit. Foreground WAL and authority writes have priority over successor preparation, bundle publication, and manifest publication. Encoding/checksumming is not globally serialized by that permit. Foreground work is counted before imminent encoding and through completion; a low-priority force cannot begin while eligible foreground work is pending.

A force already executing inside the OS is nonpreemptible. If a new gameplay burst arrives just after maintenance has acquired the permit, its first force must wait. This remains measurable, and is one concrete architectural latency owner—not something hidden by calling checkpointing asynchronous.

When bounded checkpoint/preparation prerequisites stop the writer, the arbiter marks it suspended so the very maintenance needed for progress is not starved by its pending operations. The existing 5-second deadline still bounds this wait. Tests fill the two-slot checkpoint backlog, exercise progress under pending foreground traffic, and reject uncontrolled overlap. This is not permission for arbitrary maintenance to bypass runnable foreground work.

Final workload observations: 119 foreground, 3 preparation, 3 bundle, and 3 manifest forces; 18 contended permit attempts; no simultaneous project-owned physical forces. All nine low-priority force-start events in that workload recorded zero queued operations and `writerSuspended=false`. Foreground permit wait nevertheless reached **1.7867 ms p95 / 3.5750 ms p99**, because new arrivals cannot preempt a maintenance force that already started. Maintenance wait was **10.2303 ms p95/p99**. Maximum measured checkpoint backlog was one; the focused backlog test exercised two.

`FileEncounterStore` also uses a bounded admission-state cache under its lifetime process-level writer lock. This removes repeated active-context filesystem probes from each frame's precommit validation. Freeze/disqualification invalidate the cache before changing authority. Public reads/restart still validate persisted data. This does not permit another writer or suppress corrupt published-state checks. Final precommit validation p95 was 0.0253 ms.

## I8: unchanged release workload and complete measurements

The final full run retained 60 samples, four actors, sixteen victims, 64 individually accepted logical updates per sample, all 64 durable receipts awaited, all original contributor assertions, and the exact 4/8 thresholds. No samples were discarded; no rotation/checkpoint samples were omitted. Sixteen context spawns remain setup outside the sample timer as in H. Initial spare preparation is awaited before timing reset; no contribution samples are used as a hidden warmup. Checkpoints run concurrently during the workload. A final checkpoint drain is separately reported (0.0044 ms); it does not subtract concurrent checkpoint effects from measured samples.

| Workload | Foreground forces / 3,840 records | p50 ms | p95 ms | p99 ms | 4/8 result |
|---|---:|---:|---:|---:|---|
| G WAL checkpoint | 3,840 | 125.8220 | 258.6245 | 508.1182 | FAIL |
| Authoritative H checkpoint | 119 | 9.0060 | 16.0318 | 19.8859 | FAIL |
| Final I binary | 119 | 4.5529 | 7.8891 | 16.1037 | FAIL |

| Records per physical group | H group count | I group count |
|---:|---:|---:|
| 1 | 45 | 36 |
| 2 | 14 | 22 |
| 3 | 0 | 1 |
| 61 | 0 | 1 |
| 62 | 14 | 22 |
| 63 | 45 | 36 |
| 64 | 1 | 1 |

Both H and I had one sample with one foreground force and 59 samples with two. Final sealed-operation histogram is `1:3840`; the 119 dequeued physical groups each used one barrier. Corresponding I group byte sizes/counts are `130:36, 260:22, 390:1, 7930:1, 8060:22, 8190:36, 8320:1`. No byte rollover explains the 1+63/2+62 splits. Maximum pending records/operations was 64/64; admission rejections were zero.

### Separated timings from the final binary

The separately instrumented H baseline is preserved for comparison. Checkpoint serialization counts differ because H serialized individual context/pointer payloads while I serializes bundled epochs; their per-call percentiles are not equal-work comparisons.

| Phase | H count / p50 / p95 / p99 ms | I count / p50 / p95 / p99 ms |
|---|---|---|
| Journal append | 119 / 0.0173 / 0.1147 / 0.1732 | 119 / 0.1034 / 0.1868 / 0.2164 |
| Journal force(true) | 119 / 2.1532 / 3.3796 / 4.2529 | 119 / 1.8476 / 2.0637 / 2.1493 |
| Checkpoint serialization | 99 / 0.0408 / 0.0693 / 0.3319 | 3 / 1.5162 / 2.4206 / 2.4206 |
| Checkpoint write | 102 / 0.5164 / 0.6851 / 1.4250 | 6 / 0.2561 / 0.2882 / 0.2882 |
| Checkpoint-related force | 102 / 3.6219 / 4.9717 / 5.0960 | 9 / 1.8639 / 3.8482 / 3.8482 |

All units below are milliseconds. Counts differ by phase. Per-record queue/ack metrics overlap each other and group times; **do not add their percentile columns together**. Raw evidence contains counts, totals, histograms, force-start queue states, and all 60 sample timings.

| Phase | Count | p50 | p95 | p99 | max |
|---|---:|---:|---:|---:|---:|
| Journal append | 119 | 0.1034 | 0.1868 | 0.2164 | 0.2854 |
| Journal force(true) | 119 | 1.8476 | 2.0637 | 2.1493 | 2.1819 |
| Encode/validate | 119 | 0.0614 | 0.2453 | 0.5346 | 5.2084 |
| Precommit validation | 119 | 0.0101 | 0.0253 | 0.0334 | 1.1775 |
| Queue/group wait | 3,840 | 1.7914 | 3.7163 | 5.4581 | 5.6556 |
| Sealed operation to covering force completion | 3,840 | 3.9599 | 7.3145 | 10.8663 | 13.4354 |
| Force completion to waiter completion | 3,840 | 0.1780 | 0.4532 | 0.6382 | 0.9830 |
| Durable acknowledgement | 3,840 | 4.1313 | 7.3963 | 11.5315 | 13.5246 |
| Group wall | 119 | 2.0843 | 3.6949 | 5.6129 | 8.6929 |
| Foreground barrier wait | 119 | 0.0029 | 1.7867 | 3.5750 | 3.7266 |
| Maintenance barrier wait | 9 | 4.2943 | 10.2303 | 10.2303 | 10.2303 |
| Checkpoint serialization | 3 | 1.5162 | 2.4206 | 2.4206 | 2.4206 |
| Checkpoint write | 6 | 0.2561 | 0.2882 | 0.2882 | 0.2882 |
| Checkpoint-related force | 9 | 1.8639 | 3.8482 | 3.8482 | 3.8482 |
| Checkpoint queue | 3 | 0.0364 | 0.8331 | 0.8331 | 0.8331 |
| Checkpoint scheduling | 3 | 0.0201 | 0.7030 | 0.7030 | 0.7030 |
| Rotation/capture | 3 | 0.0076 | 0.0360 | 0.0360 | 0.0360 |
| Checkpoint backpressure | 3 | 0.0038 | 0.0220 | 0.0220 | 0.0220 |
| Successor preparation, background | 3 | 5.3614 | 10.7754 | 10.7754 | 10.7754 |
| Successor activation | 3 | 0.0553 | 0.0778 | 0.0778 | 0.0778 |
| Prepared-handle wait | 3 | 0.0008 | 0.0008 | 0.0008 | 0.0008 |

The seal/force boundary is timestamped immediately after the last covering force, before fault hooks/publication/waiter delivery, rather than approximated by method return. Windows thread CPU accounting was too coarse for a fine-grained CPU attribution (group CPU p50/p95 zero, p99/max 15.625 ms, total 62.5 ms); the wall-clock phases above are the useful evidence.

Force-only sums per 64-update sample: H **4.9073 / 5.9862 / 8.8125 ms**, I **3.6909 / 3.9468 / 4.0093 ms** (p50/p95/p99). In the final run, those force-only percentiles fit the gate while end-to-end does not. That isolates remaining application pipeline cost independently of standalone device-force percentiles. H rotation/capture p95 5.8976 ms became I 0.0360 ms, with successor creation/force moved to preparation and activation folded into the next normal group force.

Final state: format 2, sequence 3,840, active segment first sequence 3,073, 768 active records, checkpoint through 3,072, generation 3, prepared successor ready, extent 16,781,456 bytes, zero prepared-segment misses. Final serial sparse production-path test: **1.9171 / 3.5230 / 3.6930 ms**, 60 individually awaited updates; [sparse evidence](../../evidence/stage-13/cohort-i/hardening/v2-sparse-load.json).

### All recorded I workload iterations, including unfavorable results

These are implementation experiments/earlier full builds, not alternate samples silently substituted into the final binary's result. Every raw result is retained in [iterations](../../evidence/stage-13/cohort-i/iterations).

| Iteration | Forces | p50 ms | p95 ms | p99 ms |
|---|---:|---:|---:|---:|
| First targeted, before cached admission validation | 119 | 7.5661 | 10.6814 | 43.6712 |
| Cached-validation targeted | 119 | 4.6031 | 8.0535 | 40.7456 |
| Explicit 0.5 ms coalescing experiment, rejected | 116 | 5.2922 | 8.2531 | 42.9564 |
| First full | 119 | 4.5405 | 8.1335 | 18.6367 |
| Second full | 119 | 4.7271 | 8.7371 | **792.9092** |
| Third full | 119 | 4.6890 | 8.1007 | 17.2643 |
| Final full, complete instrumentation and crash coverage | 119 | 4.5529 | 7.8891 | 16.1037 |

The coalescing experiment's sparse result was 3.1251 / 4.5037 / 4.8306 ms; collection delay p95/p99 was 0.5411/0.6019 ms. It did not provide the required material benefit and is absent from final code.

The second full run's bad tail is real and is not discarded: per-force p99 was **426.2545 ms**, maximum **777.0212 ms**; per-64 force sum p95/p99 was 5.8492/777.0212 ms. It demonstrates intermittent OS/JDK/storage-path stalls in a `force(true)` wall-clock call. It does not prove the hardware device alone caused the stall, and a 60-sample nearest-rank p99 is highly sensitive to one outlier. H's earlier sparse force tail around 653 ms likewise remains in its original evidence. The conclusion is not that the platform never stalls; it is that platform-only classification cannot explain away the final pipeline's independently measured application overhead.

## I9: independent portable storage qualification

`StorageDurabilityQualification.java` runs using only the JDK. It does not invoke Hytale, RPG calculation, encounters, checkpoints, rewards, mastery, AI, networking, or Gradle. Each mode performs a write, calls `FileChannel.force(true)`, waits for it to return, then proceeds to the next sample using one long-lived channel. Sparse writes are 130 bytes; grouped writes are 8,320 bytes, matching the workload's representative full physical group. There is no write-behind acknowledgement or artificial collection delay.

**10,000 barriers per mode; 40,000 total; no discarded samples.** All force/write/total raw nanoseconds and derived statistics are retained in [storage-qualification](../../evidence/stage-13/cohort-i/storage-qualification). Preallocation setup is measured separately before sampling. The resulting sparse files are 1,300,000 bytes and grouped files 83,200,000 bytes. This is a standalone force primitive comparison, not a claim to exercise production segment rotation with that larger file.

| Mode, all force(true) | p50 | p90 | p95 | p99 | p99.9 | max |
|---|---:|---:|---:|---:|---:|---:|
| Growing sparse | 1.8217 | 1.9212 | 2.0153 | 2.1674 | 5.8942 | 167.1164 |
| Growing group | 1.8447 | 1.9733 | 2.0466 | 2.1781 | 5.8099 | 147.9648 |
| Preallocated sparse | 1.8286 | 1.9250 | 2.0379 | 2.2139 | 5.8305 | 152.4218 |
| Preallocated group | 1.8260 | 1.9342 | 2.0593 | 2.3866 | 5.9333 | 137.3647 |

| Mode | Count >4 ms | >8 ms | >16 ms | >50 ms | >100 ms |
|---|---:|---:|---:|---:|---:|
| Growing sparse | 19 | 9 | 4 | 4 | 3 |
| Growing group | 19 | 8 | 4 | 4 | 3 |
| Preallocated sparse | 41 | 6 | 4 | 3 | 3 |
| Preallocated group | 17 | 8 | 4 | 4 | 2 |

Read-only environment capture: Eclipse Temurin JDK 25.0.4+7, Windows 11 Home amd64, C: NTFS, AMD Ryzen 9 7900X (12 cores/24 logical processors), 63.15 GiB RAM, Samsung SSD 980 PRO 2TB NVMe. Device identity is an observation, not a recommendation. No Hytale, Gradle, or RPG workload ran concurrently with qualification; normal Windows background activity remained enabled. Security software, storage caches, firmware, and administrator storage policy were not changed.

All four aggregate force p95/p99 measurements are inside 4/8 ms. Rare maxima are nevertheless high and can dominate a short 60-sample run. This evidence does not demonstrate a universal latency guarantee or prove that another production OS/device will behave identically. It also does not establish a preallocation speed benefit. The conditional content-only mode was not run because equivalent durability was not established.

Portable rerun, from the repository with a fresh nonexistent output directory (the utility rejects overwrite):

```powershell
& 'C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot\bin\java.exe' `
  src/main/java/com/inigmasgames/hytalerpg/progress/StorageDurabilityQualification.java `
  run/storage-qualification-new-run 10000
```

The Java source can be run independently on a future authorized qualification platform with its installed JDK and an output path on the actual intended persistence volume. No hardware purchase, recommendation, or system configuration change is part of this task.

## Tests, actual process crashes, and evidence limits

Final command: `./gradlew.bat build --rerun-tasks --console=plain` under the pinned Java 25 toolchain. It completed successfully, all 16 tasks executed. Total: **2,053 = 2,004 root + 28 native-integration module + 21 CanvasUI**. The capture validator compared every named H test identity, not merely a minimum count. All **1,963 H tests remain** and **90 focused cases were added**. Existing expectations/assertions were not weakened and no tests were removed or skipped.

| New test class | Cases | Principal coverage |
|---|---:|---|
| `Stage13BarrierPriorityTest` | 3 | Foreground priority, serialization of barriers, bounded maintenance progress |
| `Stage13DurabilityV2Test` | 41 | Real 64-context healing receipt, sealed records/order, pre-force fences, admission, IO uncertainty, prepared rotation/layouts, corruption, checkpoint publication, migration, qualification statistics |
| `Stage13V2CrashRecoveryTest` | 25 | Real halt at 18 new durable-state boundaries plus seven bootstrap variants, three restarts each |
| `Stage13V2GroupCrashRecoveryTest` | 8 | All H group halt boundaries repeated against the actual v2 store, three restarts each |
| `Stage13V2RecoveryEdgesTest` | 7 | One/three prepared orphans, invalid tail, two-slot checkpoint progress, maximum frame/extent, actual old-H rejection, authority-write uncertainty |
| `Stage13V2SparseLoadTest` | 1 | Sparse serial durable production-path latency |
| `Stage13V2GroupedRewardTest` | 5 | Existing real death/reward service fault stages on v2, repeated exact-once recovery |

The old constructor remains a v1 compatibility path so legacy format tests retain their original meaning. Thus the report does **not** claim every retained G/H test was secretly switched to v2. Production and the release fixture explicitly use v2; the added v2 crash, reward, recovery, and fence cases exercise the new path directly. The five new grouped-reward cases use the unchanged reward/death service, assert the expected 65 XP + 1 Insight and reward sequence 1 per participant, and restart three times without duplication.

### Real halt matrix: 48 cases, not mocked exceptions

Retained G contributes seven journal halt cases and retained H eight group halt cases. New v2 adds 25 preparation/activation/checkpoint/bootstrap cases plus eight group cases. Each child actually exits through `Runtime.halt(73)` without shutdown hooks; restart assertions validate authoritative recovery repeatedly.

The 18 new durable-state boundaries are:

`PREPARE_CREATED`, `PREPARE_HEADER_WRITTEN`, `PREPARE_FORCED`, `ACTIVATION_WRITTEN`, `ACTIVATION_FORCED`, `BEFORE_ACTIVE_SWITCH`, `AFTER_ACTIVE_SWITCH`, `BUNDLE_SERIALIZATION`, `BUNDLE_PARTIAL_WRITE`, `BUNDLE_BEFORE_FORCE`, `BUNDLE_AFTER_FORCE`, `BUNDLES_DURABLE`, `MANIFEST_PARTIAL_WRITE`, `MANIFEST_BEFORE_FORCE`, `MANIFEST_AFTER_FORCE`, `MANIFEST_PUBLISHED`, `BEFORE_WAL_RETIRE`, and `DURING_WAL_RETIRE`.

Seven initial-bootstrap variants cover preparation creation/header/force and manifest partial-write/before-force/after-force/publication. The eight group cases include first-frame/append/before-force, after covering force, before acknowledgements, and interrupted acknowledgement/checkpoint handoff boundaries; exact names and recovered frame counts are in the retained and v2 matrices.

Evidence: [v2 durable-state matrix](../../evidence/stage-13/cohort-i/hardening/v2-crash-matrix.json), [v2 group matrix](../../evidence/stage-13/cohort-i/hardening/v2-group-crash-matrix.json), [retained H group matrix](../../evidence/stage-13/cohort-i/hardening/group-crash-matrix.json), and [complete named test results](../../evidence/stage-13/cohort-i/test-results.json), including the seven retained G process-halt cases.

Fault-injection tests additionally cover real closed-channel append/force failures, missing bundles, corrupt bundle/index/manifest, old-checkpoint recovery before new publication, exclusions/tombstones, and preserved lifecycle fences. These are useful fault tests, but not all are counted as real halt cases. Neither kind of test proves sudden power loss, native client rendering, native input delivery, or a four-player native server tick. The full master fault-injection matrix remains a separate Stage 13 gate.

## Packaging, three-mod smoke, unchanged content, and live isolation

`Run-Stage13CohortSmoke.ps1 -Cohort i` passed a normal isolated server start through `Hytale Server Booted`, then a clean shutdown (exit 0). Exactly three mods were loaded. All retained installed-build asset/root/native-audit checks passed. An independent read of the isolated store's actual persisted manifest confirmed **format 2**, rather than assuming the factory change had been exercised. [Smoke summary](../../evidence/stage-13/cohort-i/server-smoke-summary.json), [server log](../../evidence/stage-13/cohort-i/server-smoke.txt), [isolated v2 manifest proof](../../evidence/stage-13/cohort-i/isolated-v2-store.json).

The archived/smoked binary is the exact hash in this report. `Capture-Stage13CohortEvidence.ps1 -Cohort i` passed test-identity, source-scope, content, resource-byte, archive, and rollback checks. The checkpoint manifest validates the archived evidence and requires exactly three artifact JARs. It is checksum verified, not cryptographically signed.

| Archived artifact | SHA-256 |
|---|---|
| `HytaleRPG-0.0.25.jar` | `0082FA775EB2C7C42445194E3E0736515ED21CBCEF77BEC42E04ECA1CFBF3D36` |
| `CanvasUI-0.1.0.jar` | `218DFFD40ABBCD57629EC57FC20436169C4AFCCC18B9B5A9F94D67835CBA07B6` |
| `HYTALEDEVLIB-0.5.0.jar` | `DE01E4BAAF1DAA679CB00E4182AD999DA67ECC49A8533942DE3EA87DA4129230` |

All 87 skills, 66 passives, 87 runtime profile records, player schema 9, and compiled-plan schema 41 remain. Gameplay formulas, reward amounts, XP, skills, native ability projection/execution, inventory, HUD, and resource-bar ownership were not modified. The only plugin wiring change selects the v2 store factory. Packaged gameplay/HUD resource bytes remain identical to the preserved baseline. Four explicit runtime gates remain: native bow maximum range; Bone Cage native-enemy-only collision; native guard held-item release routing; and per-actor native basic-attack cadence.

`Test-Stage13LiveStateUnchanged.ps1 -Cohort i` verified the original three live JARs and the exact three owned-data-file inventory/hashes against the frozen pre-task checkpoint. Live RPG remains its original `0.0.16` binary, SHA `D3CEEA9CEEA5995F515451317452AB9A9CBB62F3E6CF56953B5F707A1BF7FA42`; the live library hashes are unchanged. Backups are local/ignored, not published player data. [Live-state verification](../../evidence/stage-13/cohort-i/live-state-verification.json).

## Format migration and coordinated rollback runbook

The new WAL and checkpoint manifest are explicitly **version 2**. Existing per-context snapshots, death plans, and earned-reward envelopes retain their previous formats. A v1 store is read/checkpointed using the retained reader before the v2 manifest authorizes v2 admissions. Migration/bootstrap crash coverage verifies restart safety. Historical v1 files remain available; there is no in-place downgrade.

The actual archived H JAR was tested, not merely a shell guard: it rejects a v2 store rather than treating it as v1. `Test-EncounterRollbackCompatibility.ps1` additionally rejects incompatible old binaries on v2 directories. Restoring an old binary without restoring matching state is forbidden, including renaming/removing manifests or WAL files to bypass the guard.

| Retained rollback checkpoint | SHA-256 |
|---|---|
| Immediate H, `eecd64cc4b7c5345288ffef51083fadc3c8348e4` | `8849F88CB9225E847C05580CE1EBE3E2FB6BE0D487C73AA4408526C1ED6381D6` |
| G, `f25bf99764285e3fb340bad4bd693a9fe5cd03d8` | `9B81FAA34D8F41D5C7B43205C52F3E17A44F585D1420C87EB1EEADAB0B7D4FEE` |
| Pre-WAL F, `14a0f42` / documentation `344bfed` | `F7F55FCF05AFEA2A985AC2801CB4F78D346389C2E135E2DCEA22E1F193BCFA83` |
| Stage 12 H, `de60a02` | `C55DD5C1A939E5727AD01945FDC6DC0B7D7ECF1C185D94AC95B0BDAE885EB87B` |

All four binaries are retained under the I evidence rollback archive, and the original G/H checksum manifests were revalidated without altering those baselines. The retained Stage 12 copied-checkpoint archived-JAR drill and G/F coordinated rollback tests remain green, alongside new actual H rejection and v1-to-v2 migration tests.

For a future explicitly authorized rollback:

1. Stop all world/store writers and verify they have released their lifetime locks. Do not edit an active save.
2. Preserve a recoverable complete copy of the current state and the matching current mod hashes, including encounter WAL/checkpoint/bundles, earned-reward intents/plans, and player state. Preserve the relevant coordinated world checkpoint too.
3. Restore the matching **coordinated** player/reward/encounter/world checkpoint from before the target migration, together with the exact three compatible archived mods. Do not mix a newer reward ledger with an older encounter/player snapshot or vice versa.
4. Run the compatibility preflight against the copied target. Verify archived hashes and old-reader recovery on that copy before any live replacement.
5. Only after separate authorization, replace the stopped live deployment with that validated coordinated copy and perform the required connected rejoin/persistence checks. Restoring a checkpoint intentionally loses progress after its capture; retain the newer copy for investigation/recovery.

This task executed isolated/copied rollback validation only. **No live save conversion or rollback was performed.**

## Final boundary ownership and explicit stop

The concrete remaining software owner is the **encounter submission/physical-group closure and barrier-admission pipeline**:

- Sixty synthetic bursts still close as 119 foreground groups because no legitimate native producer seal exists for the 64 independent events. Conditional batching is proven for an actual 64-context healing operation, not invented for damage callbacks.
- Low-priority maintenance barriers are correctly deprioritized once foreground work is pending, but a just-started maintenance force still delays the next independent burst. Final measured foreground permit wait and queue/ack phases expose that cost.
- Encoding, validation, scheduling, and receipt completion add measurable application latency. In the final run the per-sample force-only p95/p99 fit 4/8 while the full pipeline fails.

Therefore classification **A, `RPG_PERSISTENCE_ARCHITECTURE_BLOCKED`**, is supported. Classification B would require standalone force-tail failure at the stated percentile gate and no remaining avoidable application boundary; neither condition is established here. Rare storage/runtime stalls are real but do not remove the application owner. Classification C is false because the unchanged workload still fails.

The next possible software correction must specifically establish a legitimate producer-owned lifecycle/window and its admission/maintenance contract under a separately bounded scope, not guess at more timings, change gameplay executors, or batch only the benchmark. This report does not implement that extension. Per the anti-whack-a-mole stop rule, optimization ends here after identification, prepared rotation, consolidated checkpoints, priority arbitration, and independent qualification.

**Connected QA is not cleared from the release-architecture perspective and was neither begun nor requested.** Tests and isolated smoke do not certify connected mechanics. The release assessment also preserves unresolved native integration exceptions, combined four-player/200-player scaling, remaining master fault cases, and the final actual release-candidate closure workflow. Its `FINAL_STAGE13_REGRESSION_SMOKE_ARCHIVE_ROLLBACK_RERUN_NOT_PERFORMED` flag refers to that eventual final RC workflow, not denial of this I intermediate checkpoint's complete retained regression/smoke/archive/rollback run.

`Stage13 = BLOCKED`. Local functional and crash/rollback checks are green; performance is a separate failed gate. Durability, thresholds, assertions, connected-evidence standards, and scope exclusions have not been relaxed.
