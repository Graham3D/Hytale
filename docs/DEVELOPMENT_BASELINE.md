# HyARPG/Hywind development baseline

## R250 recovery and promoted development baseline (2026-10-10)

The source starting point was `codex/teleport-r250-latest` at
`44184d936d750e0a6729a8f797e7f6a4fe30356f`, revision
`R250-U7P5B` (`0.2.0-R250-U7P5B`). The isolated reconciliation branch is
`codex/hywind-r250-recovery`; its approved local recovery commit is
`e5ccefcb0fe228188c4f1d9d9b4aeee3dc9014c9`. The asset-audit correction
described below is recorded at
`c2f45417a53fe5ec5566f34d195bb88e491a4120`. The reviewed documentation
commit `e4927d06e9a3071bc2023aafec68b8fa68d76268` was fast-forwarded to
GitHub `main` through [PR #1](https://github.com/Graham3D/Hytale/pull/1).
Subsequent documentation updates do not create a new completed or deployed
gameplay revision. Before subsequent work, check the branch's actual HEAD and
status.

The active RPG save's `mods/Hywind.jar` was inspected read-only. SHA-256
`F56BB5120BFA764088A30EBFB4ED62C067C1A4899E90A50B0A7B5DA5CBE14C30`
matches the R250 build from that starting worktree. Its embedded source commit
is `44184d936d750e0a6729a8f797e7f6a4fe30356f`. Its internal plugin name
and entrypoint are `HyARPG` and `com.inigmasgames.hywind.HyArpgPlugin`.
Neither this observation nor deployment alone establishes connected-game
acceptance. Before this recovery, the [R249B receipt](enemies/R249B_DEPLOYMENT.md)
recorded the last full offline `check`; R250 had recorded targeted Teleport
tests. This candidate's fresh checks are recorded below.

## Source and ownership decisions

- R250's gameplay source remains the base. The second Teleport commit
  `ea63fd8a6c70ca808c51dc3697b948133476e37c` has the same patch ID as
  `44184d93`; the latter also retains three R249B deployment and release-gate
  documents. Before promotion, the original primary checkout's local `main`
  HEAD was `209e38c0`, 80 commits behind R250, with substantial uncommitted
  work and R141 build metadata. That checkout remains preserved separately.
- The separate ImmersiveNPCs runtime boundary remains mandatory. R250's Tavern
  plugin extends `JavaPlugin`; its package validator excludes `persistentnpcs`
  code and NPC-owned resources. The older split's neutral `ReadyPathProbe`
  source and inventory grid textures are already present in R250 at different
  build and resource paths. The internal `HyARPG` identity stays unchanged;
  any migration requires separate approval and save compatibility proof.
- Keep the newer R249/R250 skill, gear, summon, enemy, inventory, character,
  cooldown, and Teleport implementations. Do not replace them with primary R141
  files. Retain unique primary history, QA rules, and original art only after
  file-level review. See the [historical split report](HYWIND_SPLIT_BOUNDARY_RECOVERY.md)
  and [deployment ledger](DEPLOYMENT_REVISIONS.md).
- The primary-only R199 footprint catalog is byte-identical to R250's active
  `footprints-v1.json`; its older `InventoryGrid` textures are identical to
  R250's `RpgInventory` textures. Of 166 primary-only UI art files, 153 match
  packaged R250 images by SHA-256. The remaining original PSDs and distinct
  PNGs are preserved as source art without changing the game package.
- Four primary-only regression suites for timed defense, Fortune escape,
  original-hit health grouping, and player hit identity have been ported to
  the R250 test tree. The native adapter test's 925-byte, five-entry pin list
  is now a checked-in test fixture instead of an ignored evidence dependency.
  Explicit LF checkout rules cover byte-pinned Mantle and healing assets and
  the generated elite matrix on Windows; `managed-weapon-fire-v1.json` keeps
  its pinned CRLF bytes. These are checkout and test-reproducibility changes,
  not gameplay asset edits.

## Offline verification and inherited gate

The six focused regression/Teleport classes passed 29 tests. A fresh full
`check` reached `auditU7P5AssetCompatibility`, which rejected R250's JSON
asset set against its frozen deployed R248 baseline. This is an inherited
revision comparison: no gameplay resource is changed in this recovery diff.
That failure is retained here as the original recovery-commit result. The
historical `tools/u7p5-r248-package-baseline.json` is unchanged (SHA-256
`69464E8C153519CA2F97CF76F9A6E17055401A3F2F1A18084C1672CA57E8C168`
with CRLF normalized to LF; the Windows checkout's raw SHA-256 is
`5A4F9012188CFD99E6BE6EF35C4E9EF91567AE32ABD2B12A29C2A64F843D2E4B`).
The audit checks the normalized file hash before using this baseline, as well
as its internal R248 JAR identifier.

The separately approved R250 audit correction uses the verified R249B JAR
(SHA-256 `A0EA6EEB80577AA30AA787475E7B7750417F5DE7530A57EC7DF72DE7D8E13101`)
as its reference. Of 5,014 JSON assets, 1,727 differ from the recovered R250
candidate only in CRLF/LF bytes. The only two semantic JSON changes are the
Teleport skill catalog and lightning runtime definitions. A separate reviewed
record pins the normalized hash of all 4,917 other guarded JSON paths and the
individual normalized SHA-256 values of the remaining 97: 94 R249 additions,
one R249 native-binding change, and two R250 Teleport changes. Normalization
replaces CRLF with LF only; all other bytes still affect the digest. Existing
raw package-to-source checks on reviewed assets and byte-sensitive non-JSON
checks remain in force. The plugin identity, asset count, reference, and
packaging checks also remain in force.

The full `check` runs `auditU7P5AssetCompatibility` after `jar`; `jar` consumes
the root and Tavern `processResources` outputs, whose JSON resources are Gradle
task inputs. The audit also compares every packaged JSON asset path with the
root and Tavern source trees, except the single stock prefab generated by
`processResources`. It checks normalized content for guarded assets and exact
bytes for the 97 reviewed assets. A direct audit of an older JAR therefore
rejects a changed, added, or removed source JSON file instead of trusting the
packaged aggregate hash alone. Non-JSON byte-sensitive checks remain exact.

Offline adversarial probes passed: an intentional line-ending-only variant
was accepted, while a guarded-content edit, Teleport-content edit, missing
asset, and unexpected asset were each rejected. This correction is offline-only;
connected-game acceptance is pending. A direct audit against an older JAR also
accepted a source-only CRLF/LF change, rejected a guarded source-content edit,
and rejected an R248 baseline-file edit that left its internal JAR ID intact.

The earlier offline `check -x auditU7P5AssetCompatibility` passed: 3,488 root tests,
452 native-control tests, 49 CanvasUI tests, and 5 Tavern JUnit tests, all
with zero failures or skips. The retained Tavern feature checks and resource,
native-proc, spatial-grant, CustomUI, campaign-biome, and package audits also
passed. The package validator reported 19,436 entries, 2,158 classes,
`InigmasGames:HyARPG@0.2.0-R250-U7P5B`, and zero ImmersiveNPCs payload
entries. That pre-commit candidate build's SHA-256 was
`D57A803EA8D9519A12CD3EA5728D6D2698370C2D68F3EA1E77A382A95F5FB79D`;
it was not deployed or accepted in game.

Before the audit safeguard commit, the full unmodified offline `check` passed
with 3,488 root tests, 452 native-control tests, 49 CanvasUI tests, and 5
Tavern JUnit tests, all with zero failures or skips. The corrected asset audit
passed 5,014 JSON assets (4,917 aggregate-guarded and 97 individually pinned)
and 41,251 references. The package validator again passed with 19,436 entries,
2,158 classes, the same `HyARPG` identity, and zero ImmersiveNPCs payload
entries. That pre-follow-up **local, undeployed** JAR has SHA-256
`51574DA7B7006C550F0CB28F453767F41C8573FEF83E34A5EC2D2D63E081A919`.
The installed RPG save JAR remains at the earlier SHA-256 above.

After commit `c2f45417a53fe5ec5566f34d195bb88e491a4120`, the complete
unmodified offline `check` passed again: 3,488 root tests, 452 native-control
tests, 49 CanvasUI tests, and 5 Tavern JUnit tests, with zero failures or
skips. The corrected asset audit passed all 5,014 JSON assets and 41,251
references; direct package validation passed 19,436 entries, 2,158 classes,
the `HyARPG` identity, and zero ImmersiveNPCs payload. The JAR built from
that exact commit has SHA-256
`4D89CEC91E3B53C499624D155C88B48994771C86C742F441AB2D9AFF940B183C`.
It was not deployed or accepted in game. A later documentation commit changes
the embedded source-commit field and therefore produces a different JAR hash;
record that build separately rather than attributing it to `c2f45417`.

After documentation commit `e4927d06e9a3071bc2023aafec68b8fa68d76268`,
the complete unmodified offline `check` passed: 3,488 root tests, 452
native-control tests, 49 CanvasUI tests, and 5 Tavern JUnit tests, with zero
failures or skips. The asset audit passed all 5,014 JSON assets and 41,251
references; direct package validation passed 19,436 entries, 2,158 classes,
the `InigmasGames:HyARPG@0.2.0-R250-U7P5B` identity, and zero ImmersiveNPCs
payload. The JAR built from that exact commit has SHA-256
`D17B3A22E77C6A253C9FE09E25AEB501F8221C0A4AE887CA756072FDF2EEBF18`.
It was not deployed or accepted in game. A later documentation-only commit may
produce a different JAR hash because the build embeds its source commit; do
not attribute this checksum to a later commit.

## GitHub integration status

On 2026-10-10, GitHub `main` fast-forwarded from
`209e38c01ef5db153a18f723ddc3b05866844438` to
`e4927d06e9a3071bc2023aafec68b8fa68d76268` from
`codex/hywind-r250-recovery`. GitHub marked
[PR #1](https://github.com/Graham3D/Hytale/pull/1) merged, with the recovery
commit itself as the merge commit. GitHub `main` is now the authoritative
R250-U7P5B development baseline; subsequent documentation commits can advance
its Git HEAD without creating a new gameplay revision. The original primary
checkout remains on its older local `main` with modified and untracked work.
A separate clean clone tracks `origin/main`. Codex's saved Hytale project still
requires its primary folder to be changed to that clone. Future engineering
must verify the latest remote HEAD and worktree status rather than infer source
freshness from the old checkout or a revision label.

## Guardian engineering checkpoint (2026-10-10)

`codex/hywind-guardian` branches from authoritative GitHub `main` commit
`1dfa2cea3085196c7fd4492518c65a03d5e3b49d`. Guardian was implemented
at `b1a5389caa8a95fa99ce4306b6f62a62390890cd` and its native-control
report path was corrected at
`fec2fd65e24eb07cae5df2e0fa74bc10af4cc7d9`. The source remains
`R250-U7P5B` (`0.2.0-R250-U7P5B`), with the `HyARPG` plugin identity and
separate ImmersiveNPCs mod unchanged. At that checkpoint, Guardian's hook was
tracked but not installed, and the branch had not been pushed or merged.

At exact source commit `fec2fd65e24eb07cae5df2e0fa74bc10af4cc7d9`,
the complete unmodified offline Gradle `check` passed in 5m 55s. Its JUnit
reports recorded 3,488 root, 452 native-control, 49 Canvas UI, and 5 Tavern
tests with zero failures, errors, or skips. The asset audit covered 5,014 JSON
assets and 41,251 references. The existing offline package validator passed:
19,436 entries, 2,158 classes, `InigmasGames:HyARPG@0.2.0-R250-U7P5B`, and
zero ImmersiveNPCs payload entries. That commit's **local, undeployed** JAR has
SHA-256 `0E951FE0428AD3B66E9B4F034C6D209988F03B65609BA91A5252A33BE6786CB3`.
Guardian's 19 isolated Git fixture cases passed; its clean preflight took
653 ms and printed 14 bytes. The completion check validated the receipt and
warned, correctly, that the commit is local-only. The subsequent
documentation-only commit `15ffaff518813840d9f830c68e4fb16f7fa87a12`
passed another complete unmodified offline `check` (3,994 JUnit tests,
zero failures or skips), asset audit, and package validation. Its distinct,
undeployed JAR SHA-256 is
`47A1F081587BB545E40B4F6383661D59605C2F50CD97AE8CEFB46156B3154EF5`.
Neither offline validation nor this record establishes connected-game QA.

## Guardian remote synchronization follow-up (2026-10-10)

The local `codex/hywind-guardian` implementation commit
`14b3aac56cc9d1407f5eb1af2c38901efc1dbba2` adds a one-query,
read-only GitHub completion check and an optional reviewed, non-force
`codex/` task-branch publisher. Publication still requires explicit standing
authorization; this follow-up did not invoke the publisher, install hooks,
push, or integrate into `main`. The gameplay revision and version remain
`R250-U7P5B` and `0.2.0-R250-U7P5B`.

At that exact commit, 29 isolated Guardian fixture cases passed, including
local-only, published, divergent, failed-push, and integrated states. Clean
preflight took 609 ms and printed 14 bytes. The complete unmodified offline
Gradle `check` passed in 5m 52s: 3,994 JUnit tests with zero failures or
skips, 5,014 JSON assets and 41,251 references audited, and the existing
package validator passed with `InigmasGames:HyARPG@0.2.0-R250-U7P5B` and
zero ImmersiveNPCs payload. Its undeployed JAR SHA-256 is
`8C30C3117059FD4BF8BC1FD4BEAF48718A2168FA3446640182FCBF34832083EB`.
Guardian's live completion query found the task branch unpublished on GitHub;
`main` integration is pending. Deployment and connected-game acceptance
remain unverified. Any later documentation-only commit needs its own build
checksum because the JAR embeds the exact source commit.

The follow-up `1c3ae2a041541d78ab7a8217dcc7e7787349fccd` reserves
completion exit code 0 for a verified remote PASS; unpublished or unknown
remote state now returns 1, while blocked checks return 2. The 29 Guardian
fixtures passed again. A complete unmodified offline `check` passed in
5m 35s with 3,994 JUnit tests, the same 5,014-asset and 41,251-reference
audit, and passing offline package validation. The undeployed JAR built from
this exact commit has SHA-256
`5A444CF78BE481B296D202438689B0B5333029104F89E24320DD8A62E682A42D`.
A live completion query returned exit code 1: the task branch remained
unpublished, `main` integration pending, and deployment and connected-game
acceptance unverified. The gameplay revision and version remain R250-U7P5B
and 0.2.0-R250-U7P5B.

## Guardian production and analysis tooling (2026-10-10)

Infrastructure commit `a4bc11e6bb82870613c07434c8443b95ab458751`
continues the Guardian branch from `0c51f8b2141ad91f1be2939465618440e9f801ea`.
It installs opt-in JaCoCo 0.8.15 and SpotBugs Gradle plugin 6.5.11
(engine 4.10.2) for the R250-U7P5B source. The local Guardian worktree has a
worktree-specific pre-commit hook; the canonical main checkout does not. Its
uncommitted `AGENTS.md` addition remains preserved there. The policy and
regression guidance on this branch were reviewed separately; no gameplay
source, plugin identity, ImmersiveNPCs runtime, save, or deployed JAR changed.

Guardian's 32 isolated fixtures pass. Targeted JaCoCo instrumentation of the
existing Elite birth/persistence test produced root HTML and XML reports;
`FileEncounterStore.java` measured 446 covered of 709 lines and 333 covered
of 811 branches in that focused run. These numbers describe exercised source
paths, not defect status. The initial exploratory SpotBugs analysis reported
546 root, 32 CanvasUI, and 14 Taverns findings. Strict analysis passed against
the reviewed baseline files, and removing one baseline finding made it fail as
expected. See [engineering analysis](ENGINEERING_ANALYSIS.md) for commands,
reports, and initial triage.

At this checkpoint, the authoritative GitHub `main` starting point was
`1dfa2cea3085196c7fd4492518c65a03d5e3b49d`. Guardian's remote completion
check, rather than this historical statement, determines whether the task
branch has since been published and integrated. A full exact-commit `check`
and package receipt are required before publication. This infrastructure
work does not establish a new gameplay revision, deployment, or connected-game
acceptance.

## Preservation and release gates

The full primary checkout and both Teleport worktrees, including untracked
files and Git metadata, have verified copies outside active checkouts and saves
in a local recovery backup. All 297,892 copied files passed source-to-backup
SHA-256 comparison. The primary checkout, Teleport branches, and active RPG
save remain untouched.

Do not stage archived saves, player or NPC data, generated binaries, caches,
large QA evidence archives, sensitive configuration, or unrelated primary
files. Review every staged path and run compilation, relevant unit tests, and
offline package validation for subsequent work. Do not launch a native server,
deploy a JAR, alter the save, or push new commits to `main` without the
appropriate approval and release gate. Assign a new revision only when an
engineering pass actually meets its completion gate.
