# Stage 13 R032-Z cumulative deployment report

Date: 2026-09-11/12 UTC  
Source HEAD at build: `63b6f1471d73bc6bc284e6031df2bf4333558acd`  
Required trace implementation ancestor: `9bcee9d9def437ceaa955357b72d18fb2dbf2cc9` — **present**

## Status

- **IMPLEMENTED: YES**
- **PACKAGED: YES**
- **DEPLOYED: YES**
- **CONNECTED-VERIFIED: NO — owner connected QA has not run after this deployment**

The repository HEAD matched `origin/RPG`. No code commit followed Y; the only later commit was Y's technical report. The cumulative deployment was therefore constructed from the verified R032-Y package generated from HEAD, then the current owner icon set was applied. This avoided overwriting the newer Healing Beam icon change that existed in the live X-based JAR.

## Backup and clean trace boundary

Hytale was confirmed stopped before backup and replacement. The pre-deployment live JAR was:

- SHA-256: `CB8DCB4B56629A3CD9A4BF237D9B1A345E0620FD0935DE74219CE66DCBAE27CB`
- Backup: `evidence/stage-13/cohort-z/before/artifacts/HytaleRPG-0.0.25.jar`

All 13 files in the RPG-owned mod-data directory were copied before replacement: 49,358,002 bytes. The local rollback copy is under `evidence/stage-13/cohort-z/before/save/InigmasGames_HytaleRPGPhase00Audit` (intentionally Git-ignored because it contains connected player/session data). File and byte counts matched the source.

After the backup and atomic JAR replacement, these old trace files were moved—not deleted—to:

`Saves/RPG/mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/pre-r032-z-20260912T001306Z`

- `skill-trace.jsonl`: 7,040,974 bytes
- `skill-trace.jsonl.1`: 8,388,599 bytes
- `ui-trace.jsonl`: 361,513 bytes

The active `skill-trace.jsonl` and `ui-trace.jsonl` paths were absent immediately after the move, establishing a clean connected-QA boundary. The new runtime will create each path on its first corresponding trace event.

## Cumulative build/package/deployment

Recognized owner icons applied to the Y candidate:

- `SkillFirebolt.png`
- `SkillHealingbeam.png`
- `SkillQuickslash.png`
- `SkillSnipe.png`
- `SkillWhirlwind.png`

The installed target is:

`C:\Users\Zemio\AppData\Roaming\Hytale\data\pre-release\Saves\RPG\mods\HytaleRPG-0.0.25.jar`

Candidate and deployed SHA-256 both equal:

`0737EC5DA371A8BC9E0E910EE48A362158D20E3F3D463F0F0E946E0A818C7B84`

The cumulative distribution ZIP contains exactly HTLibrary, CanvasUI and RPG:

- ZIP: `evidence/stage-13/cohort-z/Hytale-RPG-Stage13-Z-cumulative-trace-and-icons.zip`
- ZIP SHA-256: `E9DA3A0F68286DEBD3FBA065C12F286E11A76EF5571B889E5913D0519E2333B7`

ImmersiveNPCs remains separately installed in the live QA world and was not added to the three-mod RPG distribution archive.

## Runtime startup confirmation

The exact JAR installed in the live mods directory was supplied to an isolated three-mod Hytale server after deployment. This validates the installed binary rather than merely the source tree.

Evidence:

- runtime candidate hash: `0737EC5DA371A8BC9E0E910EE48A362158D20E3F3D463F0F0E946E0A818C7B84`
- process exit: 0
- RPG JAR discovered: yes
- RPG plugin enabled: yes
- `HYTALE_RPG_SETUP revision=R032 version=0.0.25 ... stage=13`: yes
- server booted: yes
- clean shutdown: yes
- exactly three smoke mods: yes
- all retained Stage 03–13 smoke gates: pass
- runtime-created skill trace: `mods/InigmasGames_HytaleRPGPhase00Audit/logs/rpg/skill-trace.jsonl`
- runtime skill trace bytes after smoke: 26,887
- runtime metrics showed ordered growth from 315 bytes through 16,890+ bytes with zero dropped/failed writes

The isolated smoke did not generate a UI event, so it did not create `ui-trace.jsonl`; the deployed `Phase00Plugin`/`RpgUiTraceService` binary retains that exact active path. Connected UI creation remains part of owner QA.

## Deployed trace configuration

The deployed JAR contains `TraceSegmentWriter`, `TraceArchiveManager`, `TraceArchiveReader`, `TraceInput`, manifest and export classes. Its embedded defaults are:

```properties
skillTrace.enabled=true
skillTrace.level=NORMAL
skillTrace.maxFileMb=4
trace.archive.compression=GZIP
trace.archive.verify=true
```

No explicit runtime override was supplied to the connected world. Therefore the expected active segment threshold is 4 MiB. Active paths remain:

- `.../logs/rpg/skill-trace.jsonl`
- `.../logs/rpg/ui-trace.jsonl`

Verified archives will be written under `.../logs/rpg/archive` as `.jsonl.gz` plus `.manifest.json`; `.jsonl.pending` may be visible briefly or remain as safe authority if compression fails.

## Connected QA still required

The following cannot be labelled verified from isolated smoke or unit tests and must be observed after the owner starts this deployed build:

1. Join the RPG world and exercise normal skill/UI activity.
2. Confirm both active JSONL files appear and grow normally.
3. Continue until `skill-trace.jsonl` approaches 4 MiB.
4. Confirm the old complete segment becomes `.jsonl.pending`, then a substantially smaller `.jsonl.gz` plus `VERIFIED` manifest.
5. Confirm a new small `skill-trace.jsonl` accepts later events.
6. Expand the trace and compare event counts/order/SHA manifest evidence.
7. If the active file grows materially past 4 MiB plus at most one complete event, treat it as a real configuration/writer-ownership defect.

Until that test is performed, the honest state is **DEPLOYED, NOT CONNECTED-VERIFIED**.
