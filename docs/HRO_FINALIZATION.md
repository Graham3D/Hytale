# HRO finalization and connected QA handoff

Candidate: `R262C1HROF-CANDIDATE`, version `0.2.0-R262C1HROF-CANDIDATE`,
Hytale `=0.7.0`. This completion pass starts at integrated HRO Stage 3 commit
`5287bb7ceecaeb7c6bcdfbfd748503886c6b1eb4` and retains all 17 contracts,
mechanical evidence, offline comparison and source maps. It changes diagnostic
orchestration, not gameplay authority, save schema, plugin identity, or
ImmersiveNPCs ownership. The original design document available locally was
`Hywind_Regression_Observatory_Design.docx`; the assignment's `(1)` copy was not
present. The supplied completion brief was applied to that design and the
merged Stage 3 implementation.

## Owner workflow

The ordinary path is `/rpg-trace patch start`, normal gameplay, `/rpg-trace
patch stop`, then `/rpg-trace patch status`. The operator permission is
`inigmasgames.rpg.trace.patch`. `start` returns STARTING and status exposes
RECORDING once the worker has admitted the run. `stop` closes admission
immediately and status exposes FINALIZING until the folder has been written,
verified and analyzed. COMPLETE means the capture finalized without dropped
observations; PARTIAL explicitly reports loss, interruption or failure. The
absolute folder path and run ID are printed in status. Markers are optional.

`/rpg-trace patch arm-next` is an optional 24-hour, one-shot boot capture. The
record is stored under the diagnostic root, outside Saves, with the invoking
subject UUID and a hash of the plugin installation data path. The next plugin
setup consumes it atomically before starting the worker capture. The capture
begins as early as this in-plugin observer can run, continues through the
owner's first admission, and expires after ten minutes even if the owner never
joins. A record for a different installation is not consumed. `/rpg-trace
patch cancel-arm` removes the pending arm. No launcher or JVM option is needed.
Code before plugin setup is `BOOTSTRAP_UNOBSERVED`; a plugin that never loads
cannot produce its own HRO bundle. Native logs may be examined separately
when establishing such a failure, but HRO does not infer it from silence.

The run ID survives explicit native owner world handoffs and a reconnect within
30 seconds. A new world segment or connection generation is recorded. The
native readiness lifecycle is the only source that advances these identities;
callbacks carrying an older generation or an unannounced world are rejected.
Other callbacks without a connection generation remain explicitly unjoined,
not silently attributed to the new connection. If the owner does not return,
the worker finalizes the existing capture as PARTIAL. No unrelated player is
added to the scope.

`/rpg-trace patch mark [label]` records a bounded ASCII checkpoint (64
characters, other characters replaced with `_`). Omission records
`USER_CHECKPOINT`. The run, timestamp, connection, world segment and event
locator appear in `checkpoints.json` and `report.json`, with the operation
locator in `indexes/operations.jsonl`. Automatic skill, UI, inventory, gear,
combat and spawn adapters remain the source of gameplay observations.

## Automatic report and proof limits

The existing worker finalizes gzip event segments, index, checksum manifest,
coverage, findings, mechanical report, cascade groups and implementation map.
It verifies the completed capture before publishing status. If an engineer has
placed explicitly approved, immutable contract baselines in
`%APPDATA%/Hytale/UserData/Diagnostics/Hywind/HRO/approved-baselines/`, the worker
compares at most 17 JSON files using their embedded recipes and writes
`comparisons/*.json`, `auto-analysis.json` and a separate
`analysis-checksums.sha256`. This is a reuse of Stage 3's
compatibility and semantic matching rules. A missing, invalid or incomparable
baseline is reported, never silently accepted. A 30-second analysis budget
marks remaining comparison work pending. Comparison reports are sidecars: a
write failure cannot invalidate the completed capture's original checksum
index. No baseline is created or approved by a recording.

`performance.json` includes producer p95/p99 histogram upper bounds, per-tick
observation histogram, queue peak, drops, writer cost/throughput, first-capture
initialization time, contention losses, heap/GC deltas and governor stop reason.
When native owner-tick samples are genuinely paired, it reports their
server-side p95/p99 separately. A trace-on/off native comparison requires
matched sessions and remains UNVERIFIED until supplied. Client FPS and frame
times remain `CLIENT_PERFORMANCE_UNVERIFIED`; owner-perceived smoothness is a
connected QA acceptance condition. The producer budget is 0.05 ms p99, the
owner-tick observation budget 0.50 ms p99, and the retained HRO heap cap 64 MiB.
All file and report work remains on the HRO worker. Diagnostic pressure stops
or degrades the capture rather than blocking gameplay.

The report lists unexercised contracts as such; a submitted server publication
does not prove a rendered client result or a native effect that lacks a native
receipt. Partial evidence and stale lifecycle callbacks are not converted into
PASS. The baseline registry is for an engineer's explicit approved recipes;
the owner needs no external analysis command for ordinary QA.

## Connected acceptance checklist

Launch Hytale normally and enter the active Hywind save. Start the HRO
recording. Exercise a valid skill assignment and cast, one valid and one
rejected inventory or gear transfer, a hit with known equipped affix gear,
ordinary travel with natural spawn opportunity, and one UI open/close cycle.
Stop, wait for COMPLETE or PARTIAL in status, and provide the single displayed
folder. A normal reconnect is optional unless recovery continuity is being
accepted. Report whether the game felt unchanged while HRO was active.

Offline verification, source integration, deployment and connected acceptance
are separate states in `DEVELOPMENT_BASELINE.md`. The owner need not launch a
standalone server, configure a profiler, or run an analyzer.

Engineering verification: 4,300 offline tests passed with strict SpotBugs,
package validation and Guardian. The uninstrumented synthetic producer p99
was 14.4 microseconds; its 16-observation tick p99 was 101.8 microseconds,
and the warmed disabled-path median allocation was zero. The bounded HRO
heap qualification was 41,305,320 bytes, below 64 MiB. The deployed build
and rollback hashes are recorded in `DEVELOPMENT_BASELINE.md`; connected
Hytale behavior and owner-perceived smoothness remain pending QA.
