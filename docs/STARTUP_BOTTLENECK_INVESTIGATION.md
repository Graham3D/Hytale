# Integrated-server startup bottleneck: evidence and capture

## Current boundary (2026-10-11)

The latest verified source is `codex/r258-icon-preserved-deploy` at
`21292be88598dde1a3e39a60d1eb5892c1f6d98b`; the active RPG
`mods/Hywind.jar` is R258-U7P5B, SHA-256
`3DA33D47F2990B25FE99EFB6CEAD8543630054D35193932F261BD2F51648CA12`,
embedding source `03abca2f55400bf157e42bae53f9a691267b79cd`.
The five available integrated-server logs below all used **R256-U7P5B**.
They are useful historical comparisons, not an R258 before/after test. No JFR
recording was present, and no launch was run for this investigation.

| RPG server log | Native reported boot | Setup to encounter-store marker | Encounter-store to gear-QA marker | Setup to asset loading | Asset load to NPC validation | Setup to boot |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `2026-10-11_10-37-45_server.log` | 29.463 s | ~3 s | ~9 s | ~13 s | ~11 s | ~29 s |
| `2026-10-10_22-55-21_server.log` | 19.344 s | ~2 s | ~1 s | ~4 s | ~10 s | ~18 s |
| `2026-10-10_19-05-48_server.log` | 18.708 s | ~2 s | ~1 s | ~4 s | ~9 s | ~18 s |
| `2026-10-10_18-10-57_server.log` | 20.148 s | ~3 s | ~1 s | ~5 s | ~10 s | ~20 s |
| `2026-10-10_17-35-39_server.log` | 20.626 s | ~3 s | ~1 s | ~5 s | ~11 s | ~20 s |

Log timestamps are rounded to one second; only the native boot totals have
subsecond precision. In the slowest captured run the client entered
`BootingServer` at 10:37:45.0485 local time and advanced to `Connecting` at
10:38:15.5391. The server logged its encounter-store marker at 14:37:49 UTC,
then its gear-QA marker at 14:37:58 UTC. These markers bracket a roughly
nine-second interval within Hywind's RPG setup. The corresponding interval in
the other four runs was about one second. Source between these markers includes
runtime profile loading and validation, codec/system registration, world and
enemy setup, and the gear-QA construction path. A silent log interval does not
identify which call was running or whether it used CPU, I/O, a lock, or a wait.
The source audit found no explicit 30–40-second timed wait in that setup
section; existing 30-second timeouts belong to later player-entry preparation.
The recurring roughly 5–7-second interval around native NPC configuration
loading also needs a JFR stack and I/O sample before attributing its cost.

ReadyPath is already partly present. `ReadyPathProbe` emits opt-in JFR spans;
Hywind setup, CanvasUI setup, RPG setup, native validation, startup, catalog
and runtime profile preparation already have markers. `RpgCatalog.prepared()`
keeps a classloader-local immutable content image. `PlayerEntryPreparation`
has bounded asynchronous preparation and generation checks; the existing
`/rpg readypath` inspection explicitly reports that full native gameplay
admission is unproven. No new content cache or admission redesign is justified
by the current startup logs. The ReadyPath architecture attachment describes
proposed stages and older observations; it is not evidence that every stage is
deployed or that the integrated server processes all installed files.

## Installed-size census, not launch I/O

Read-only metadata on this machine found approximately 4.25 GiB in the
pre-release installation package, including a 3,781,916,561-byte native
`Assets.zip`; 2.58 GiB in the active RPG save, including its world, logs and
mods; 19,735,294 bytes in `Hywind.jar`; 8,327,176 and 265,646 bytes in the
other two RPG mod JARs; 31.8 MB in `CachedAssets`; and 103.8 MB in
`PrefabCache`. The server log reports 27,914 common-asset index entries and
114 native asset stores. These are file sizes and registry counts, **not**
bytes read, decompressed, transferred, or held in memory for one launch. The
owner's approximate 54 GB content estimate may include other saves, releases
and development files. The existing logs cannot establish actual per-launch
I/O or cache hits.

## Owner-run capture on the existing R258 JAR

Run this from PowerShell after fully closing Hytale and its launcher:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\Zemio\.codex\worktrees\combat-damage-trace\Hytale-main-clean\tools\Capture-ReadyPathStartup.ps1" -CheckOnly
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\Users\Zemio\.codex\worktrees\combat-damage-trace\Hytale-main-clean\tools\Capture-ReadyPathStartup.ps1"
```

When the second command prints `ARMED`, launch Hytale normally and enter the
existing RPG save. Leave that PowerShell window open. The script does **not**
start Hytale. It temporarily sets the user-level `JAVA_TOOL_OPTIONS` so the
integrated Java server starts a bounded 150-second JFR recording and enables
existing Hywind ReadyPath spans before plugin initialization. It removes the
option once it sees the integrated server, or on timeout/error. Recordings and
manifests go under `Hytale/data/pre-release/StartupCaptures`, outside `Saves`.
The capture is limited to 256 MiB per run. It never edits the mod JAR or player
data. If PowerShell is forcibly closed before cleanup, run the same script with
`-Restore`; it removes only a ReadyPath-tagged option. An existing unrelated
`JAVA_TOOL_OPTIONS` causes the script to stop rather than overwrite it.

The owner should label at least two normal and two delayed launches; six to
ten total attempts may be needed to catch the intermittent stall. Keep the
same RPG save, game build, mod set and cache conditions. Provide each capture
folder and tell Codex which runs felt slow. Each `manifest.json` records mod
hashes, native asset size, process observation time, JFR validity and paths to
the matching client/server logs. The JFR begins at JVM startup **only if the
new launcher process inherits `JAVA_TOOL_OPTIONS`**. The first capture must be
checked for the JVM's `Picked up JAVA_TOOL_OPTIONS` line and `hywind.ReadyPath`
events; if missing, that run is not a valid before-startup capture. The
`-CheckOnly` command is read-only. The installed Java 26 runtime successfully
created and validated a short offline JFR using these options.

JFR profile events can show blocking stacks, allocation, class loading and
recorded I/O. Profile thresholds may omit short reads, so summed JFR file-read
bytes are a lower bound, not total bytes read. Client log stage timestamps
bound the loading-screen interval; server JFR events do not by themselves prove
the first playable frame. `/rpg readypath` can inspect post-launch server
observations but cannot capture the pre-plugin stall.

## Decision gate

No gameplay or startup behavior has been changed. The current logs narrow a
variable interval to RPG setup, but do not identify the method or prove the
owner's 30–40-second freeze is the same event. Compare valid normal/slow JFR
stack and wait events, ReadyPath spans, log timelines and cache conditions
before changing that path. After an evidence-backed correction, run the same
capture procedure on a verified candidate and compare repeated connected
launches. No performance improvement or connected acceptance is claimed yet.
