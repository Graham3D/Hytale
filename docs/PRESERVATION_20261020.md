# Hywind development preservation — 2026-10-20

Owner-requested preservation before removing the local development environment.
The latest published development candidate is `codex/me12-072-modonly` at
`f3c1433f1758817c6e66966dc81c6abe73780043`, version
`0.2.0-R262ME12S3B-072-QA5-CANDIDATE` for Hytale 0.7.2.
Remote `main` is `96d748e09cc86aa6fc8015c47ae08ffa92f09c06`, four commits behind it.
Neither candidate status nor preservation establishes connected-game acceptance.

This guide branch, `codex/preserve-20261020`, retains that candidate's gameplay
source unchanged and adds only this recovery documentation and archival material.
All preserved unfinished work is **QUARANTINED**, not promoted as a release.
The original primary checkout and its working/staged edits were left in place.

## Resume development

```powershell
git clone --branch codex/me12-072-modonly https://github.com/Graham3D/Hytale.git Hywind
cd Hywind
git fetch origin
git branch -r
```

A normal full clone fetches the remote branch histories. Do not use a shallow or
single-branch clone if you want all the preservation snapshots. For a complete
Git-only archival copy, `git clone --mirror https://github.com/Graham3D/Hytale.git`
preserves all published refs; it has no editable working checkout.

Use separate checkouts when exploring the old snapshots. They preserve older,
incompatible development states and must not be copied wholesale over the latest
candidate. Restore individual source/art files only after comparison and review.

## Working and staged snapshots

| Snapshot | Exact commit | Purpose |
| --- | --- | --- |
| [primary](https://github.com/Graham3D/Hytale/tree/codex/preserve-20261020-primary) | `e82e15002ad09232df388c401c58612685f2c539` | Current local policy, PSD, Cyclone/Spirit Shield icons and icon tooling |
| [legacy-primary](https://github.com/Graham3D/Hytale/tree/codex/preserve-20261020-legacy-primary) | `52996061622515ba6760c05ca1124495559107c3` | Old R141-era primary working source, art and separate NPC changes |
| [separation](https://github.com/Graham3D/Hytale/tree/codex/preserve-20261020-separation) | `f72302b185f4ca3a5f1b800b5269af69dc6a9c04` | Legacy separated RPG/NPC working source and documents |
| [separation-staged](https://github.com/Graham3D/Hytale/tree/codex/preserve-20261020-separation-staged) | `fe89a2ff7125e4887744e9031b0e12b8c9d4f449` | Distinct staged source versions from the separation checkout |
| [separation-worldconfig-index](https://github.com/Graham3D/Hytale/tree/codex/preserve-20261020-separation-worldconfig-index) | `22fc2e1b484ace8e433b33ccfea9c0de90c19782` | Alternate source index (same source tree as staged snapshot) |
| [original-rpg](https://github.com/Graham3D/Hytale/tree/codex/preserve-20261020-original-rpg) | `1fef3de23a03dc93bcd7c131ccebe06b4e975d15` | Original RPG design/source documents; cloud shortcuts recorded as CSV snapshots |

The preserved `primary` branch includes the owner's current local `AGENTS.md`
bytes, including the Git integration audit policy. Historical snapshots retain
their original policies; current owner instructions govern any resumed work.

The unpublished Elite nameplate fix is retained at exact commit
`3327a5650cadf0173b336072f614a13c63cecc56` on
`codex/preserve-20261020-elite-nameplate`. Its full offline build remains blocked
by the historical SDK/version gate; focused tests recorded in its baseline are
not a full release qualification. Its HRO dependency remains separate. No feature
is accepted or merged as a consequence of this archive.

Seventeen historical preservation branches retain unpublished branch tips and
orphaned source checkpoints; see [recovered-history.json](preservation/2026-10-20/recovered-history.json)
for exact commits and provenance. One binary-bearing orphan (`bb939cd8`) is
retained as a source-only root snapshot at `1a67332b2c8b69b478253ff2f41f7b63acad1493`;
the original commit is not claimed reachable. Its original commit text and the
182 excluded generated/runtime paths are recorded in `docs/PRESERVATION_ORIGIN.json`
on that archive branch. All source blobs in its reviewed unpublished history
were independently found in the downloaded repository.

## Supplementary development material

The [preservation directory](preservation/2026-10-20/) includes small authored
scratch scripts from ignored build folders, icon-update JSON recovery records,
and readable CSV snapshots plus links for the two original Google Drive sheets.
The CSVs preserve the connector's readable cell content; native formatting,
formulas and complete workbook fidelity are not claimed. Original Sheets remain
on Google Drive with their existing access settings.

Archived scratch/deployment scripts are historical source evidence. Do not run
them as current deployment instructions. Follow the owner's current active-save,
backup, checksum and no-standalone-server rules.

## Verification and exclusions

A fresh independent bare clone was downloaded from GitHub. `git fsck --full`
passed, all 23 archive branch tips matched, and 9,718 preserved file versions
(8,250 distinct blobs) matched SHA-256; 12,512 deleted-path states also matched.
Working source files still matched the captured bytes after publication. The
modified icon PowerShell script parsed successfully, Spirit Shield JSON parsed,
and the preserved PNG/PSD headers were checked. No new gameplay build, revision,
deployment, game launch or connected-QA acceptance was performed.

Generated JARs/classes, compiler and dependency caches, oversized QA logs, runtime
data, credentials, and player/save/NPC data are outside this source-preservation
scope. The explicit per-snapshot exclusions are in
[source-snapshots.json](preservation/2026-10-20/source-snapshots.json). Existing
published repository history was not rewritten or cleaned.

To rebuild after uninstalling the environment, reinstall Git, Java 25, the matching
licensed Hytale SDK/server/assets (0.7.2 for the newest candidate), Python where
required by the Gradle audits, and the referenced HYTALEDEVLIB 0.5.0 dependency.
Gradle wrapper source is already tracked; dependency caches can be regenerated.
Older snapshots may require old SDK/assets that are no longer installed.
The archive does not replace a backup of a playable save or an installed build.

## Git and Guardian status

`main` was unchanged. Every retained unpublished feature remains QUARANTINED,
with original acceptance/build blockers and next integration action recorded.
The [Git inventory](preservation/2026-10-20/git-inventory.json) maps all discovered
local branch tips to commits recoverable from the independent remote clone.
Its INTEGRATED labels describe existing remote-main reachability, not a new QA
approval or an acceptance reassessment.

Existing Guardian preflight/completion checks were run and their blockers are
recorded. The comprehensive integration-audit code gate required by owner policy
is still an UNVERIFIED enforcement gap; it was not implemented or claimed passed
in this preservation task. These archival pushes are the owner's explicit request
to preserve incomplete development work, not ordinary verified-release publication.
Before any future integration, reconcile dependencies and run the applicable full
offline and owner-connected QA gates. No standalone Hytale server should be launched.
