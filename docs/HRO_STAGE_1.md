# HRO Stage 1 — diagnostic foundation

The integrated completion workflow and current command syntax are in
[HRO_FINALIZATION.md](HRO_FINALIZATION.md); this report remains the Stage 1 record.

State: **ENGINEERING_READY**, with native performance/connected acceptance
**UNVERIFIED**. Candidate `R262C1HRO1-CANDIDATE` /
`0.2.0-R262C1HRO1-CANDIDATE`. Branch `codex/hro-stage1`, worktree `C:/hyhro`,
parent `3e359b8e036ad023c9c8bd93064bcb0f78068f83` on
`codex/chapter1-stage3-elite-restoration`. All Chapter I ancestors remain
unaccepted/unmerged. This assignment does not implement HRO Stages 2 or 3,
deploy either mod/native JAR, launch Hytale, or modify player saves.
Implementation commit: `486db1d2af40c8884bd0dc9387772a5b0777c568`.
The following handoff commit changes documentation only; its exact HEAD and
packaged identity are recorded in the completion receipt.

The architecture DOCX supplied on October 12, 2026 was read in full. Its
three-stage execution charter is background; the explicit Stage 1 assignment
sets this delivery boundary. The primary checkout's newer, uncommitted
`AGENTS.md` and artwork remain untouched. The inherited worktree policy has
older save-target wording; the user's current supplied policy governs.

Concurrent-work handoff: Stage 3 subsequently published test/documentation
follow-up `d28d92f49e7639a6c75da71a810088918456b79b` (`R262C1S3V2-CANDIDATE`).
It changes no production owner code. Stage 4 has separate unfinished changes
to `HytaleGearLoot` and `build.gradle` in `C:/hystage4`. Neither was silently
imported into this established HRO task; reconcile both branches intentionally
before integration. HRO's provisional revision does not supersede their QA status.

## Verified source and installed environment

Remote `main` was verified at `6d3ef15ff0145532ef193c42e3b648307ee6e915`.
It is the integrated R261QA2 source, not the Chapter I candidate; its record
does not establish full connected acceptance. The current deployed mod was
read-only verified as INV1, source
`afdf9bfbce9d5fb395bd68848833480a849f0e0b`, SHA-256
`3B80550CCA85E59061C40B393A574FA42F7009180E5B711F91930BE1AF719B47`.
It remains in `UserData/Saves/Hywind/mods/Hywind.jar`.

Installed stock release server: `0.7.0`, revision
`648ebf375a034beabf119d7d790cd11d4f665ff4`, SHA-256
`C3B5985B9E72727BF3BCF220923D7E61E78FE0F37CD1CD9C2A73F51E4F408FF8`.
Installed Assets.zip: 3,942,734,752 bytes, 68,756 entries, SHA-256
`DD53C25076A8AD9521103A765E7D3C71B18FC9AF62E60519F14F757765257FA5`.
`prepareHroNativeIdentity` independently verifies referenced PlayerRef,
AbilitySlots, PlayerConnectEvent and HudManager classes and packages these
offline fingerprints. Compilation and existing release asset audits validate
the referenced signatures/assets. The inherited Stage 2 native patch remains
offline; no HRO hook needs that patch to record an observability gap.

The [official API index](https://docs.hytale.com/api/) returned a 0.6.8 heading
during review, so it was not treated as binary proof for installed 0.7.0.
The [asset reference](https://docs.hytale.com/assets/) and
[Update 7 notes](https://hytale.com/news/2026/10/modder-facing-patch-notes-update-7)
were context; installed binaries and source fixtures govern this implementation.

## Recorder and evidence semantics

`HroRuntime` connects existing semantic receipts before legacy trace filtering.
It does not start the skill, UI, combat, gear or spawn recorder, change their
levels, or stop another operator's recorder. `BoundedTraceWriter` and HRO
share a 16 MiB queued-data reservation. HRO uses a separate streaming worker
because the existing writer serializes `submit` on its caller, queues through
a blocking-lock executor, and its archive verifier materializes whole segments.
The existing archive formats and recovery behavior are preserved; HRO's
streaming recovery does not use the recovering executable's BuildIdentity.

Hot admission checks enabled/scope first, copies at most 20 allowlisted
96-character scalar facts plus explicit identity gaps, then uses `tryLock`
and a fixed 512-entry queue. No file operations, JSON, compression, scans,
gameplay RNG, native mutation or worker drain are added to gameplay callbacks.
Snapshots are copied while the existing authority already owns its state;
the worker never receives a Store, PlayerRef, World or mutable item container.
One worker owns hashing, serialization, gzip, indexes, evaluation and recovery.
Session start/stop/status do not perform diagnostic filesystem work.

The queue reserves 16 KiB per event (8 MiB maximum for HRO alone), below the
16 MiB process-wide queue cap. Retained HRO heap limit is 64 MiB. Events cap
at 256 MiB uncompressed; segments are 4 MiB, index data caps at 96 MiB.
With at most one raw/gzip temporary segment pair, bounded metadata and no
attachments, worst-case generated physical usage stays below 512 MiB.
Disk headroom below 64 MiB, contention losses, write errors and capacity
exhaustion remain explicit. Queue pressure suspends capture rather than
waiting. Eight retained directories is a refusal threshold, not permission
to delete evidence. No auto-import, profiler, network export or save scan runs.

Every admitted event has run/boot/process identity, event and coordinator
sequence, provider, operation, UTC milliseconds, monotonic nanoseconds,
world/connection/tick/revision where observed, and explicit gaps otherwise.
World and item snapshot UUIDs use per-run aliases. Existing operation/receipt
IDs remain causal references. Null native identities are not invented from
the current UUID alone. Registration witnesses distinguish configured
production preload hooks from mere implementation presence. Existing
NativeRpgTickMetrics samples are retained only for the scoped observed world.

PASS means the named observed boundary, not entire gameplay acceptance.
FAIL requires a directly contradicted invariant. BLOCKED preserves actual
prerequisite/native reasons. Unexercised, explicitly inapplicable and missing
evidence are separate. Missing observers, transport loss, interrupted capture,
revision/world/connection mismatches invalidate positive and negative verdicts.
There is no timeout-based gameplay FAIL without an independently established
deadline. Findings retain last observed boundary and source anchors; loss
is never used to infer that a player failed to press a key.

## Stage 1 contract/provider map

| Contract | Production boundary and assertion | Deliberate proof limit |
| --- | --- | --- |
| BOOT.ARTIFACT | Actual running mod/server hashes, packaged identity, production Cyclone/shared preload and connect-registration witnesses | Actual loaded asset archive and client build are not exposed here; complete boot agreement remains INCONCLUSIVE. Missing required preload witness is BLOCKED. |
| PLAYER.ADMISSION | Queued ready generation, support/load/cooldown/lease/AbilitySlots waits, actual installer return | A command cannot recover earlier boot/preconnect history. Stale world evidence is INCONCLUSIVE. |
| SKILL.ASSIGN | Existing commit correlation must include compile and save at the same observed revision | Does not claim native projection or rendered CanvasUI success. |
| SKILL.PROJECTION | Saved slots copied from existing presentation-state ownership; actual native binding/readback, conflicts, unbound slots | Saved slot without successor is an observation gap; slots 3/4 stay explicit capability blockers. |
| SKILL.EXECUTION | Existing activation/commit/dispatch/payment correlation; duplicate dispatch and rejected debit contradictions | Executor dispatch does not prove native effect; rejected payment absence stays INCONCLUSIVE without complete debit evidence. |
| HUD.PUBLICATION | Existing HUD_LAYOUT_READY and HUD_REFRESH_FAILED | Server publication only; no client acknowledgement/render proof. |
| UI.LIFECYCLE | Existing inventory page events and Canvas graph document token; single close and HUD restore witness after cleanup calls | Owner cleanup, not visual confirmation. Inventory close without HUD ownership proof stays INCONCLUSIVE. |
| INVENTORY.OWNER | Existing bag component presence, ownership mode and revision at UI authority reads | Missing bag is FAIL only with an explicit expected-spatial fixture/config contract. Production missing expectation remains INCONCLUSIVE; no save repair. |
| INVENTORY.TRANSFER | UI operation correlation, raw/resolved native slot, durable equipment-finalization receipt | Owner-finalized evidence alone cannot prove all item quantities/custodians. Full conservation predicate is tested with immutable complete fixtures; production gaps remain visible. |

CombatDamageTraceService, GearQaTrace and MonsterSpawnTrace were inspected.
Their mechanical/affix/spawn contracts belong to HRO Stage 2. They remain
independent and are not silently enrolled at maximum verbosity.

## Output and offline use

Each folder includes `manifest.json`, `summary.md`, `findings.json`,
`coverage.json`, `capabilities.json`, `performance.json`, `integrity.json`,
`events/segment-NNNN.jsonl.gz`, `indexes/operations.jsonl`, and
`checksums.sha256`. In-progress metadata explicitly refuses final verdicts.
Stop distinguishes FINALIZING from COMPLETE; errors retain PARTIAL evidence.
Findings are bounded to 128 examples, with failure/blocker priority; counts
and operation indexes retain wider coverage. Analyzer state caps at 2,048
operations; overflow invalidates certainty rather than retaining unlimited state.

Offline commands, from this checkout (no game/server launch):

```powershell
.\gradlew.bat --offline hroAnalyze -PhroBundle=C:/absolute/run-folder
.\gradlew.bat --offline hroAnalyze -PhroAction=extract -PhroBundle=C:/absolute/run-folder -PhroOperation=operation-id
.\gradlew.bat --offline hroAnalyze -PhroAction=recover -PhroBundle=C:/absolute/interrupted-folder
.\gradlew.bat --offline hroHeapQualification
```

Analysis verifies checksums and streams one bounded line/segment at a time;
the entire event corpus is never loaded. Recovery retains `captureBuild`,
records a separate unknown/offline recovery identity, preserves complete
lines, and marks all recovered evidence incomplete. Do not combine bundles
from different runs. `analysis.json` is a derived report, not original evidence.
Native/client or missing conservation evidence is not filled by assumptions.

Optional arm-next, baseline comparisons, legacy import, UI backend parity,
and other later-stage features are not added. No owner QA is requested as a
prerequisite for the next assignment; native connected qualification remains
an explicitly unmeasured dimension.

## Qualification and release gate

Focused fixtures cover all nine families and six outcomes, omitted production
preloads, admission/HUD blocking, saved slots without projection, absent bag,
inactive `-1`, stale generation/world, missing registration, dropped data,
expiry, interrupted recovery, checksum tampering, slow/full disk, scope,
disabled allocation, retained heap and independent legacy recorder operation.
The real command-registration test checks operator-only HRO permissions.
The production bridge fixture runs in the existing native-codec test JVM;
it initializes no Hytale server or world.

Initial warmed offline runs measured zero disabled per-call allocations,
active observer p99 0.0076–0.0135 ms, and synthetic batches of 16 observations
p99 0.0304–0.0762 ms. These pass the 0.05 ms / 0.50 ms producer targets for
that workload. A test-only Instrumentation agent measured a saturated slow-disk
session graph of 1,691,896 bytes; adding a conservative 16 MiB non-queue reserve
gives 18,469,112 bytes, below 64 MiB. This agent is not in the game mod.
Exact final-run metrics are generated under `build/reports/hro/`.
`example-path.txt` points to the generated synthetic example bundle, containing
missing-preload, blocked-admission, absent-projection and explicit missing-bag
fixtures. It is labeled synthetic and contains no player-save data.

Whole-world tick p95/p99 regression <=2%, client smoothness and end-to-end
connected behavior are **UNVERIFIED**. Synthetic timing does not substitute
for those measurements. No claim of perceptible-lag acceptance is made.
Full offline Gradle check passed 4,210 JUnit tests (3,672 root, 484 native,
49 CanvasUI, 5 Taverns), zero failures/errors/skips, plus retained module
checks and package/asset validation. Strict SpotBugs reported zero findings
without an HRO baseline suppression. Combined JaCoCo covered 466/528 HRO
lines and 383/632 branches. Uncovered areas include environment identity
fallbacks, safety/path limits and some combined contract states; these counts
do not prove gameplay correctness. The final committed source is checked
again because Guardian requires exact-HEAD evidence. Its JAR hash, version,
source HEAD, check and package results are bound by the generated receipt
at `build/guardian/verification.json`; an offline candidate is not QA accepted.

Next integration action: retain/publish the reviewed `codex/hro-stage1`
candidate. Do not merge unaccepted Chapter I ancestors or deploy without
separate authorization. Stage 2 requires its own assignment.
